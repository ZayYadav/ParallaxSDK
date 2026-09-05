package com.onecore.loader.server;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.onecore.loader.BoxApplication;
import com.onecore.loader.R;
import com.onecore.loader.activity.MainActivity;
import com.onecore.loader.libhelper.FileCopyTask;
import com.onecore.loader.security.HostedLicenseClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.lingala.zip4j.ZipFile;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * Crash-resistant foreground WorkManager job for the OneCore server installer.
 *
 * Downloads are resumable, large payload files are fetched in parallel, progress is
 * exposed through an ongoing notification, and WorkManager can retry after transient
 * network failures even when the activity is no longer on screen.
 */
@Obfuscate
public final class ServerInstallWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "onecore-bgmi-server-install";
    public static final String TAG = "onecore-server-install";

    private static final String CONFIG_ASSET = "server_download_config.json";
    private static final String INPUT_PACKAGE = "expected_package";
    private static final String PREFS = "server_install_state";
    private static final String PREF_RUNNING = "running";
    private static final String PREF_STATE = "state";
    private static final String PREF_DETAIL = "detail";
    private static final String PREF_PERCENT = "percent";
    private static final String INSTALL_PREFS = "install_status";

    private static final String CHANNEL_ID = "onecore_server_download";
    private static final int NOTIFICATION_ID = 6104;
    private static final int COMPLETE_NOTIFICATION_ID = 6105;

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_PARALLEL_DOWNLOADS = 4;
    private static final long MAX_MANIFEST_BYTES = 1024L * 1024L;
    private static final long FREE_SPACE_MARGIN = 256L * 1024L * 1024L;

    private static final Dispatcher DISPATCHER = new Dispatcher();

    static {
        DISPATCHER.setMaxRequests(12);
        DISPATCHER.setMaxRequestsPerHost(12);
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .dispatcher(DISPATCHER)
            .connectionPool(new okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES))
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final Context appContext;
    private NotificationManager notificationManager;
    private volatile long lastNotificationUpdate;
    private volatile int lastPublishedPercent;

    public ServerInstallWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        this.appContext = appContext.getApplicationContext();
    }

    public static boolean enqueue(Context context, String expectedPackage) {
        Context app = context.getApplicationContext();
        if (expectedPackage == null || expectedPackage.trim().isEmpty()) {
            return false;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data input = new Data.Builder()
                .putString(INPUT_PACKAGE, expectedPackage.trim())
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ServerInstallWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG)
                .build();

        setSnapshot(app, true, "QUEUED", "Waiting for network", 0);
        try {
            WorkManager.getInstance(app).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request);
            return true;
        } catch (Throwable error) {
            setSnapshot(app, false, "FAILED", ServerInstallStrings.START_FAILED_TOAST, 0);
            return false;
        }
    }

    public static boolean isRunning(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_RUNNING, false);
    }

    public static ProgressSnapshot getProgressSnapshot(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ProgressSnapshot(
                preferences.getBoolean(PREF_RUNNING, false),
                preferences.getString(PREF_STATE, "IDLE"),
                preferences.getString(PREF_DETAIL, ""),
                preferences.getInt(PREF_PERCENT, 0));
    }

    public static void cancel(Context context) {
        Context app = context.getApplicationContext();
        try {
            WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_WORK_NAME);
        } catch (Throwable ignored) {
        }
        setSnapshot(app, false, "CANCELLED", ServerInstallStrings.CANCELLED,
                getProgressSnapshot(app).percent);
    }

    private static void setRunning(Context context, boolean running) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(PREF_RUNNING, running).apply();
    }

    private static void setSnapshot(
            Context context,
            boolean running,
            String state,
            String detail,
            int percent) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_RUNNING, running)
                .putString(PREF_STATE, state == null ? "" : state)
                .putString(PREF_DETAIL, detail == null ? "" : detail)
                .putInt(PREF_PERCENT, Math.max(0, Math.min(100, percent)))
                .apply();
    }

    public static final class ProgressSnapshot {
        public final boolean running;
        public final String state;
        public final String detail;
        public final int percent;

        ProgressSnapshot(boolean running, String state, String detail, int percent) {
            this.running = running;
            this.state = state == null ? "" : state;
            this.detail = detail == null ? "" : detail;
            this.percent = Math.max(0, Math.min(100, percent));
        }
    }

    @NonNull
    @Override
    public ForegroundInfo getForegroundInfo() {
        ensureNotificationChannel();
        return buildForegroundInfo("PREPARING", "Starting OneCore server download", 0, true);
    }

    @NonNull
    @Override
    public Result doWork() {
        String expectedPackage = getInputData().getString(INPUT_PACKAGE);
        if (expectedPackage == null || expectedPackage.trim().isEmpty()) {
            setRunning(appContext, false);
            return Result.failure();
        }

        setSnapshot(appContext, true, "PREPARING", "Connecting to OneCore server", 0);
        ensureNotificationChannel();

        try {
            setForegroundAsync(
                    buildForegroundInfo("PREPARING", "Connecting to OneCore server", 0, true))
                    .get(20, TimeUnit.SECONDS);

            performInstall(expectedPackage.trim());

            appContext.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(expectedPackage.trim(), true)
                    .apply();

            publishProgress("READY", "BGMI installed successfully", 100, true);
            setSnapshot(appContext, false, "READY", "BGMI installed successfully", 100);
            notifyCompletion(true, "BGMI is ready inside OneCore.");
            return Result.success(new Data.Builder()
                    .putString("message", "BGMI installed successfully.")
                    .build());
        } catch (Throwable error) {
            String message = cleanError(error);
            boolean retry = !isStopped()
                    && getRunAttemptCount() < 3
                    && isTransientFailure(error);

            if (retry) {
                publishProgress(
                        "RETRYING",
                        "Connection interrupted • download will resume automatically",
                        Math.max(1, readPublishedPercent()),
                        true);
                // Keep the running flag set while WorkManager is waiting to retry.
                return Result.retry();
            }

            if (isStopped()) {
                setSnapshot(
                        appContext,
                        false,
                        "CANCELLED",
                        ServerInstallStrings.CANCELLED,
                        Math.max(0, readPublishedPercent()));
                notifyCompletion(false, ServerInstallStrings.CANCELLED);
            } else {
                setSnapshot(
                        appContext,
                        false,
                        "FAILED",
                        message,
                        Math.max(0, readPublishedPercent()));
                notifyCompletion(false, message);
            }
            return Result.failure(new Data.Builder()
                    .putString("message", message)
                    .build());
        }
    }

    private void performInstall(String expectedPackageName) throws Exception {
        publishProgress("CONNECTING", "Loading server manifest", 2, true);

        String manifestUrl = readManifestUrl();
        requireHttps(manifestUrl, "Manifest");
        ManifestSpec spec = fetchManifest(manifestUrl);

        if (!expectedPackageName.equals(spec.packageName)) {
            throw new IOException("Manifest package does not match the selected BGMI profile.");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            throw new IOException("All files access is required to place the BGMI OBB in OneCore storage.");
        }

        File stagingRoot = appContext.getExternalFilesDir("server-install");
        if (stagingRoot == null) {
            stagingRoot = new File(appContext.getNoBackupFilesDir(), "server-install");
        }
        File workspace = new File(stagingRoot, safeName(spec.packageName));
        if (!workspace.exists() && !workspace.mkdirs()) {
            throw new IOException("Unable to create server-install workspace.");
        }

        File extractDir = new File(workspace, "extract");
        deleteRecursively(extractDir);

        File apkFile = new File(workspace, safeName(spec.apkFileName));
        File partsDir = new File(workspace, "parts");
        if (!partsDir.exists() && !partsDir.mkdirs()) {
            throw new IOException("Unable to create multipart download folder.");
        }

        List<DownloadJob> jobs = new ArrayList<>();
        jobs.add(new DownloadJob(
                "apk",
                "BGMI APK",
                spec.apkUrl,
                apkFile,
                -1L));

        for (int i = 0; i < spec.parts.size(); i++) {
            PartSpec part = spec.parts.get(i);
            jobs.add(new DownloadJob(
                    "obb-" + i,
                    "OBB " + (i + 1) + "/" + spec.parts.size(),
                    part.url,
                    new File(partsDir, safeName(part.name)),
                    part.size));
        }

        publishProgress("CHECKING FILES", "Reading CDN payload sizes", 4, true);
        long totalPayloadBytes = 0L;
        long totalObbArchiveBytes = 0L;

        for (int i = 0; i < jobs.size(); i++) {
            DownloadJob job = jobs.get(i);
            if (job.expectedLength <= 0L) {
                job.expectedLength = probeRemoteLength(job.url);
            }
            if (job.expectedLength > 0L) {
                totalPayloadBytes += job.expectedLength;
                if (!"apk".equals(job.key)) {
                    totalObbArchiveBytes += job.expectedLength;
                }
            }
        }

        ensureFreeSpace(stagingRoot, jobs, totalObbArchiveBytes);

        publishProgress(
                "DOWNLOADING",
                "Parallel CDN download • up to " + MAX_PARALLEL_DOWNLOADS + " streams",
                5,
                true);

        ProgressTracker tracker = new ProgressTracker(jobs, totalPayloadBytes, 5, 72);
        downloadInParallel(jobs, tracker);

        if (!apkFile.isFile() || apkFile.length() <= 0L) {
            throw new IOException("BGMI APK download is missing.");
        }

        publishProgress("VERIFYING APK", "Checking downloaded BGMI package", 74, true);
        validateArchivePackage(apkFile, spec.packageName);

        publishProgress("ACTIVATING SDK", "Verifying OneCore SDK access", 76, true);
        HostedLicenseClient loaderLicense = new HostedLicenseClient(appContext);
        String storedKey = loaderLicense.getStoredActivationKey();
        BoxApplication application = BoxApplication.get();
        if (application == null || !application.activateSdkWithFallback(storedKey)) {
            throw new IOException(
                    "OneCore SDK activation failed. Reopen the loader and verify your key.");
        }

        publishProgress("INSTALLING APK", "Installing BGMI inside OneCore", 77, true);
        InstallResult installResult = BlackBoxCore.get().installPackageAsUser(apkFile, 0);
        if (installResult == null || !installResult.success) {
            String reason = installResult == null ? "" : installResult.msg;
            throw new IOException(reason == null || reason.trim().isEmpty()
                    ? "OneCore could not install the downloaded APK."
                    : "OneCore install failed: " + reason);
        }

        File archive;
        if ("split_zip".equals(spec.mode)) {
            publishProgress("PREPARING OBB", "Opening split ZIP archive", 81, true);
            archive = new File(partsDir, safeName(spec.archiveName));
            if (!archive.isFile() || archive.length() <= 0L) {
                throw new IOException("Final split ZIP file is missing: " + spec.archiveName);
            }
        } else {
            publishProgress("PREPARING OBB", "Joining multipart archive", 80, true);
            archive = new File(workspace, safeName(spec.archiveName));
            joinParts(spec, partsDir, archive);
        }

        if (!extractDir.mkdirs() && !extractDir.isDirectory()) {
            throw new IOException("Unable to create OBB extraction folder.");
        }

        publishProgress("EXTRACTING OBB", "Unpacking BGMI game data", 85, true);
        ZipFile zipFile = new ZipFile(archive);
        if ("split_zip".equals(spec.mode) && !zipFile.isSplitArchive()) {
            throw new IOException("The downloaded OBB set is not a valid split ZIP archive.");
        }
        zipFile.extractAll(extractDir.getAbsolutePath());

        File extractedObb = findFile(extractDir, spec.outputName);
        if (extractedObb == null || !extractedObb.isFile() || extractedObb.length() <= 0L) {
            throw new IOException("Expected OBB file was not found inside the archive.");
        }

        publishProgress("INSTALLING OBB", "Moving game data into OneCore storage", 94, true);
        File destinationDir = FileCopyTask.getExternalObbDir(spec.packageName);
        deleteRecursively(destinationDir);
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Unable to create OneCore OBB folder.");
        }

        File destination = new File(destinationDir, safeName(spec.outputName));
        moveLargeFile(extractedObb, destination);
        if (!destination.isFile() || destination.length() <= 0L) {
            throw new IOException("OBB installation did not complete.");
        }

        publishProgress("FINALIZING", "Cleaning temporary download files", 98, true);
        deleteRecursively(workspace);
        publishProgress("READY", "BGMI server installation complete", 100, true);
    }

    private void downloadInParallel(List<DownloadJob> jobs, ProgressTracker tracker)
            throws Exception {
        int threadCount = Math.max(1, Math.min(MAX_PARALLEL_DOWNLOADS, jobs.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "OneCore-CDN");
            thread.setDaemon(false);
            return thread;
        });

        List<Future<?>> futures = new ArrayList<>();
        try {
            for (DownloadJob job : jobs) {
                futures.add(pool.submit(() -> {
                    downloadResumable(job, tracker);
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw new IOException("Parallel download failed.", cause);
                }
            }
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void downloadResumable(DownloadJob job, ProgressTracker tracker) throws IOException {
        requireHttps(job.url, job.label);

        int reconnects = 0;
        while (true) {
            if (isStopped() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Download stopped.");
            }

            try {
                downloadPass(job, tracker);
                return;
            } catch (HttpStatusException status) {
                if (!status.retryable || reconnects >= 3) {
                    throw status;
                }
            } catch (EOFException | java.net.SocketTimeoutException error) {
                if (reconnects >= 3) {
                    throw error;
                }
            } catch (IOException error) {
                if (reconnects >= 3) {
                    throw error;
                }
            }

            reconnects++;
            tracker.force(job.label + " • reconnecting");
            SystemClock.sleep(Math.min(3000L, 500L * reconnects));
        }
    }

    private void downloadPass(DownloadJob job, ProgressTracker tracker) throws IOException {
        File parent = job.destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create download folder.");
        }

        while (true) {
            long existing = job.destination.isFile() ? job.destination.length() : 0L;

            if (job.expectedLength > 0L && existing == job.expectedLength) {
                tracker.update(job.key, existing, job.label, true);
                return;
            }

            if (job.expectedLength > 0L && existing > job.expectedLength) {
                if (!job.destination.delete()) {
                    throw new IOException(job.label + " partial file could not be reset.");
                }
                tracker.update(job.key, 0L, job.label, true);
                existing = 0L;
            }

            Request.Builder request = new Request.Builder()
                    .url(job.url)
                    .header("Accept-Encoding", "identity")
                    .get();

            if (existing > 0L) {
                request.header("Range", "bytes=" + existing + "-");
            }

            try (Response response = HTTP.newCall(request.build()).execute()) {
                int code = response.code();

                if (code == 416) {
                    long remoteTotal = parseUnsatisfiedRangeTotal(
                            response.header("Content-Range"));
                    if (existing > 0L && remoteTotal > 0L && existing == remoteTotal) {
                        job.expectedLength = remoteTotal;
                        tracker.update(job.key, existing, job.label, true);
                        return;
                    }

                    if (job.destination.exists() && !job.destination.delete()) {
                        throw new IOException(job.label + " resume state could not be reset.");
                    }
                    tracker.update(job.key, 0L, job.label, true);
                    continue;
                }

                if (!response.isSuccessful()) {
                    throw new HttpStatusException(
                            code,
                            job.label + " download failed: HTTP " + code);
                }

                if (existing > 0L && code != 206) {
                    // The server ignored Range. Restart cleanly instead of corrupting the file.
                    if (!job.destination.delete()) {
                        throw new IOException(job.label + " could not restart from zero.");
                    }
                    tracker.update(job.key, 0L, job.label, true);
                    continue;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException(job.label + " download body was empty.");
                }

                boolean append = existing > 0L && code == 206;
                long responseTotal = parseSatisfiedRangeTotal(response.header("Content-Range"));
                if (job.expectedLength <= 0L) {
                    if (responseTotal > 0L) {
                        job.expectedLength = responseTotal;
                    } else if (body.contentLength() > 0L) {
                        job.expectedLength = existing + body.contentLength();
                    }
                }

                long downloaded = existing;
                tracker.update(job.key, downloaded, job.label, false);

                try (InputStream input = new BufferedInputStream(
                             body.byteStream(), BUFFER_SIZE);
                     OutputStream output = new BufferedOutputStream(
                             new FileOutputStream(job.destination, append), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (isStopped() || Thread.currentThread().isInterrupted()) {
                            throw new IOException("Download stopped.");
                        }
                        if (read <= 0) {
                            continue;
                        }

                        output.write(buffer, 0, read);
                        downloaded += read;
                        tracker.update(job.key, downloaded, job.label, false);
                    }
                    output.flush();
                }

                if (!job.destination.isFile() || job.destination.length() <= 0L) {
                    throw new IOException(job.label + " download produced an empty file.");
                }

                if (job.expectedLength > 0L
                        && job.destination.length() < job.expectedLength) {
                    throw new EOFException(job.label + " connection ended before the file completed.");
                }

                if (job.expectedLength > 0L
                        && job.destination.length() != job.expectedLength) {
                    throw new IOException(job.label + " final size is invalid.");
                }

                tracker.update(job.key, job.destination.length(), job.label, true);
                return;
            }
        }
    }

    private long probeRemoteLength(String url) throws IOException {
        requireHttps(url, "Payload");

        Request head = new Request.Builder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .head()
                .build();

        try (Response response = HTTP.newCall(head).execute()) {
            if (response.isSuccessful()) {
                long length = parseLong(response.header("Content-Length"));
                if (length > 0L) {
                    return length;
                }
            }
        }

        Request range = new Request.Builder()
                .url(url)
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-0")
                .get()
                .build();

        try (Response response = HTTP.newCall(range).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpStatusException(
                        response.code(),
                        "Unable to read payload size: HTTP " + response.code());
            }

            long total = parseSatisfiedRangeTotal(response.header("Content-Range"));
            if (total > 0L) {
                return total;
            }

            long length = parseLong(response.header("Content-Length"));
            return length > 0L ? length : -1L;
        }
    }

    private void ensureFreeSpace(
            File stagingRoot,
            List<DownloadJob> jobs,
            long archiveBytes) throws IOException {
        long remainingDownload = 0L;
        for (DownloadJob job : jobs) {
            if (job.expectedLength <= 0L) {
                continue;
            }
            long existing = job.destination.isFile() ? job.destination.length() : 0L;
            remainingDownload += Math.max(0L, job.expectedLength - existing);
        }

        if (archiveBytes <= 0L) {
            return;
        }

        StatFs statFs = new StatFs(stagingRoot.getAbsolutePath());
        long available = statFs.getAvailableBytes();
        // During extraction the split archive and extracted OBB temporarily coexist.
        long required = remainingDownload + archiveBytes + FREE_SPACE_MARGIN;
        if (available < required) {
            throw new IOException(
                    "Not enough free storage. Need about "
                            + humanBytes(required)
                            + " free for download + OBB extraction.");
        }
    }

    private String readManifestUrl() throws Exception {
        try (InputStream input = appContext.getAssets().open(CONFIG_ASSET)) {
            byte[] bytes = readLimited(input, MAX_MANIFEST_BYTES);
            JSONObject config = new JSONObject(
                    new String(bytes, StandardCharsets.UTF_8));
            String url = config.optString("manifest_url", "").trim();
            if (url.isEmpty()
                    || url.contains("YOUR-DOMAIN")
                    || url.contains("example.com")) {
                throw new IOException(
                        "Set manifest_url in assets/server_download_config.json first.");
            }
            return url;
        }
    }

    private ManifestSpec fetchManifest(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-cache")
                .get()
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpStatusException(
                        response.code(),
                        "Manifest request failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Manifest response was empty.");
            }

            byte[] data = readLimited(body.byteStream(), MAX_MANIFEST_BYTES);
            JSONObject root = new JSONObject(
                    new String(data, StandardCharsets.UTF_8));

            String packageName = required(root, "package_name");
            JSONObject apk = root.getJSONObject("apk");
            String apkUrl = required(apk, "url");
            requireHttps(apkUrl, "APK");
            String apkFileName = apk.optString("filename", "bgmi.apk").trim();
            if (apkFileName.isEmpty()) {
                apkFileName = "bgmi.apk";
            }

            JSONObject obb = root.getJSONObject("obb");
            String mode = obb.optString("mode", "joined_parts")
                    .trim()
                    .toLowerCase(Locale.US);
            if (!"joined_parts".equals(mode) && !"split_zip".equals(mode)) {
                throw new IOException("Unsupported OBB mode: " + mode);
            }

            String archiveName = obb.optString(
                    "archive_name", "bgmi_obb.zip").trim();
            String outputName = required(obb, "output_name");
            JSONArray partsJson = obb.getJSONArray("parts");
            if (partsJson.length() <= 0) {
                throw new IOException("Manifest contains no OBB parts.");
            }

            List<PartSpec> parts = new ArrayList<>();
            for (int i = 0; i < partsJson.length(); i++) {
                JSONObject part = partsJson.getJSONObject(i);
                String partUrl = required(part, "url");
                requireHttps(partUrl, "OBB part");
                String name = part.optString(
                        "name",
                        String.format(Locale.US, "bgmi_obb.zip.part%02d", i))
                        .trim();
                long size = part.optLong("size", -1L);
                parts.add(new PartSpec(name, partUrl, size));
            }

            if ("split_zip".equals(mode)) {
                boolean hasFinalZip = false;
                for (PartSpec part : parts) {
                    if (archiveName.equals(part.name)) {
                        hasFinalZip = true;
                        break;
                    }
                }
                if (!hasFinalZip) {
                    throw new IOException(
                            "Split ZIP manifest must include the final .zip file in parts.");
                }
            }

            return new ManifestSpec(
                    packageName,
                    apkFileName,
                    apkUrl,
                    mode,
                    archiveName,
                    outputName,
                    parts);
        }
    }

    private void validateArchivePackage(File apkFile, String expectedPackage)
            throws IOException {
        PackageManager pm = appContext.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (info == null || info.packageName == null) {
            throw new IOException("Downloaded APK is not a readable Android package.");
        }
        if (!expectedPackage.equals(info.packageName)) {
            throw new IOException(
                    "Downloaded APK package is "
                            + info.packageName
                            + ", expected "
                            + expectedPackage
                            + ".");
        }
    }

    private void joinParts(ManifestSpec spec, File partsDir, File archive)
            throws IOException {
        if (spec.parts.isEmpty()) {
            throw new IOException("No OBB parts were downloaded.");
        }

        try (OutputStream output = new BufferedOutputStream(
                new FileOutputStream(archive, false), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int i = 0; i < spec.parts.size(); i++) {
                File part = new File(partsDir, safeName(spec.parts.get(i).name));
                if (!part.isFile() || part.length() <= 0L) {
                    throw new IOException("OBB part " + (i + 1) + " is missing.");
                }

                try (InputStream input = new BufferedInputStream(
                        new FileInputStream(part), BUFFER_SIZE)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > 0) {
                            output.write(buffer, 0, read);
                        }
                    }
                }

                int progress = 80 + (int) Math.round(
                        ((i + 1) * 3.0d) / spec.parts.size());
                publishProgress(
                        "PREPARING OBB",
                        "Joined " + (i + 1) + " of " + spec.parts.size() + " parts",
                        progress,
                        true);
            }
            output.flush();
        }

        if (!archive.isFile() || archive.length() <= 0L) {
            throw new IOException("Multipart OBB archive could not be reconstructed.");
        }
    }

    private static void moveLargeFile(File source, File destination)
            throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create OBB destination.");
        }

        if (destination.exists() && !destination.delete()) {
            throw new IOException("Unable to replace previous OBB file.");
        }

        if (source.renameTo(destination)) {
            return;
        }

        try (InputStream input = new BufferedInputStream(
                     new FileInputStream(source), BUFFER_SIZE);
             OutputStream output = new BufferedOutputStream(
                     new FileOutputStream(destination), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            output.flush();
        }

        if (!source.delete()) {
            source.deleteOnExit();
        }
    }

    private void publishProgress(
            String state,
            String detail,
            int percent,
            boolean forceNotification) {
        int safePercent = Math.max(0, Math.min(100, percent));
        lastPublishedPercent = safePercent;
        setSnapshot(appContext, true, state, detail, safePercent);

        setProgressAsync(new Data.Builder()
                .putString("state", state)
                .putString("detail", detail)
                .putInt("percent", safePercent)
                .build());

        long now = SystemClock.elapsedRealtime();
        if (!forceNotification && now - lastNotificationUpdate < 500L) {
            return;
        }
        lastNotificationUpdate = now;

        ensureNotificationChannel();
        if (notificationManager != null) {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(state, detail, safePercent, safePercent <= 0));
        }
    }

    private int readPublishedPercent() {
        return Math.max(1, lastPublishedPercent);
    }

    private ForegroundInfo buildForegroundInfo(
            String state,
            String detail,
            int percent,
            boolean indeterminate) {
        android.app.Notification notification =
                buildNotification(state, detail, percent, indeterminate);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    private android.app.Notification buildNotification(
            String state,
            String detail,
            int percent,
            boolean indeterminate) {
        Intent openIntent = new Intent(appContext, MainActivity.class);
        openIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                6104,
                openIntent,
                pendingFlags);

        PendingIntent cancelIntent =
                WorkManager.getInstance(appContext).createCancelPendingIntent(getId());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_license_timer)
                .setContentTitle(ServerInstallStrings.NOTIFICATION_TITLE)
                .setContentText(detail)
                .setSubText(state)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(percent < 100)
                .setAutoCancel(percent >= 100)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (percent < 100) {
            builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    ServerInstallStrings.CANCEL_DOWNLOAD,
                    cancelIntent);
        }

        if (indeterminate) {
            builder.setProgress(100, 0, true);
        } else {
            builder.setProgress(100, Math.max(0, Math.min(100, percent)), false);
        }
        return builder.build();
    }

    private void notifyCompletion(boolean success, String detail) {
        ensureNotificationChannel();
        if (notificationManager == null) {
            return;
        }

        Intent openIntent = new Intent(appContext, MainActivity.class);
        openIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                6105,
                openIntent,
                pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_license_timer)
                .setContentTitle(success
                        ? ServerInstallStrings.NOTIFICATION_COMPLETE_TITLE
                        : (ServerInstallStrings.CANCELLED.equals(detail)
                                ? ServerInstallStrings.NOTIFICATION_CANCELLED_TITLE
                                : ServerInstallStrings.NOTIFICATION_FAILED_TITLE))
                .setContentText(detail)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(success
                        ? NotificationCompat.PRIORITY_DEFAULT
                        : NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, builder.build());
    }

    private void ensureNotificationChannel() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager)
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    ServerInstallStrings.NOTIFICATION_CHANNEL,
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    ServerInstallStrings.NOTIFICATION_CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private boolean isTransientFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpStatusException) {
                return ((HttpStatusException) current).retryable;
            }
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof EOFException) {
                return true;
            }
            current = current.getCause();
        }

        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.US);
        return message.contains("connection")
                || message.contains("timeout")
                || message.contains("network")
                || message.contains("stream was reset")
                || message.contains("unexpected end");
    }

    private static String cleanError(Throwable error) {
        if (error == null) {
            return "Server installation failed.";
        }

        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Server installation failed safely. You can retry without losing completed downloads.";
        }
        return message.trim();
    }

    private static File findFile(File directory, String expectedName) {
        File[] children = directory.listFiles();
        if (children == null) {
            return null;
        }

        for (File child : children) {
            if (child.isFile() && expectedName.equals(child.getName())) {
                return child;
            }
            if (child.isDirectory()) {
                File nested = findFile(child, expectedName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static byte[] readLimited(InputStream input, long maxBytes)
            throws IOException {
        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;

        while ((read = input.read(buffer)) != -1) {
            if (read <= 0) {
                continue;
            }
            total += read;
            if (total > maxBytes) {
                throw new IOException("Configuration response is too large.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String required(JSONObject object, String key)
            throws Exception {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IOException("Manifest field is missing: " + key);
        }
        return value;
    }

    private static void requireHttps(String url, String label)
            throws IOException {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null) {
            throw new IOException(label + " URL must use HTTPS.");
        }
    }

    private static String safeName(String value) {
        if (value == null) {
            return "file";
        }
        String name = new File(value)
                .getName()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isEmpty() ? "file" : name;
    }

    private static long parseUnsatisfiedRangeTotal(String value) {
        if (value == null) {
            return -1L;
        }
        int slash = value.lastIndexOf('/');
        if (slash < 0 || slash >= value.length() - 1) {
            return -1L;
        }
        return parseLong(value.substring(slash + 1).trim());
    }

    private static long parseSatisfiedRangeTotal(String value) {
        if (value == null) {
            return -1L;
        }
        int slash = value.lastIndexOf('/');
        if (slash < 0 || slash >= value.length() - 1) {
            return -1L;
        }
        String total = value.substring(slash + 1).trim();
        return "*".equals(total) ? -1L : parseLong(total);
    }

    private static long parseLong(String value) {
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes / 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f KB", value);
        }
        value /= 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f MB", value);
        }
        value /= 1024d;
        return String.format(Locale.US, "%.2f GB", value);
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private final class ProgressTracker {
        private final Map<String, Long> downloaded = new HashMap<>();
        private final long totalBytes;
        private final int startPercent;
        private final int endPercent;
        private long lastUiUpdate;

        ProgressTracker(
                List<DownloadJob> jobs,
                long totalBytes,
                int startPercent,
                int endPercent) {
            this.totalBytes = totalBytes;
            this.startPercent = startPercent;
            this.endPercent = endPercent;

            for (DownloadJob job : jobs) {
                long existing = job.destination.isFile()
                        ? job.destination.length()
                        : 0L;
                downloaded.put(job.key, Math.max(0L, existing));
            }
        }

        synchronized void update(
                String key,
                long bytes,
                String label,
                boolean force) {
            downloaded.put(key, Math.max(0L, bytes));

            long now = SystemClock.elapsedRealtime();
            if (!force && now - lastUiUpdate < 350L) {
                return;
            }
            lastUiUpdate = now;

            long complete = 0L;
            for (Long value : downloaded.values()) {
                complete += value == null ? 0L : value;
            }

            int percent = startPercent;
            String detail = label + " • " + humanBytes(complete);

            if (totalBytes > 0L) {
                double fraction = Math.min(
                        1d,
                        complete / (double) totalBytes);
                percent = startPercent
                        + (int) Math.round(
                                (endPercent - startPercent) * fraction);
                detail = label
                        + " • "
                        + humanBytes(complete)
                        + " / "
                        + humanBytes(totalBytes);
            }

            publishProgress(
                    "DOWNLOADING",
                    detail,
                    Math.min(endPercent, percent),
                    force);
        }

        synchronized void force(String detail) {
            long complete = 0L;
            for (Long value : downloaded.values()) {
                complete += value == null ? 0L : value;
            }

            int percent = startPercent;
            if (totalBytes > 0L) {
                percent = startPercent
                        + (int) Math.round(
                                (endPercent - startPercent)
                                        * Math.min(1d, complete / (double) totalBytes));
            }
            publishProgress(
                    "DOWNLOADING",
                    detail,
                    Math.min(endPercent, percent),
                    true);
        }
    }

    private static final class DownloadJob {
        final String key;
        final String label;
        final String url;
        final File destination;
        long expectedLength;

        DownloadJob(
                String key,
                String label,
                String url,
                File destination,
                long expectedLength) {
            this.key = key;
            this.label = label;
            this.url = url;
            this.destination = destination;
            this.expectedLength = expectedLength;
        }
    }

    private static final class ManifestSpec {
        final String packageName;
        final String apkFileName;
        final String apkUrl;
        final String mode;
        final String archiveName;
        final String outputName;
        final List<PartSpec> parts;

        ManifestSpec(
                String packageName,
                String apkFileName,
                String apkUrl,
                String mode,
                String archiveName,
                String outputName,
                List<PartSpec> parts) {
            this.packageName = packageName;
            this.apkFileName = apkFileName;
            this.apkUrl = apkUrl;
            this.mode = mode;
            this.archiveName = archiveName;
            this.outputName = outputName;
            this.parts = parts;
        }
    }

    private static final class PartSpec {
        final String name;
        final String url;
        final long size;

        PartSpec(String name, String url, long size) {
            this.name = name;
            this.url = url;
            this.size = size;
        }
    }

    private static final class HttpStatusException extends IOException {
        final int code;
        final boolean retryable;

        HttpStatusException(int code, String message) {
            super(message);
            this.code = code;
            this.retryable = code == 408
                    || code == 425
                    || code == 429
                    || code == 500
                    || code == 502
                    || code == 503
                    || code == 504;
        }
    }
}
