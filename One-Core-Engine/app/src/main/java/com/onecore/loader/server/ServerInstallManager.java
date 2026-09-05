package com.onecore.loader.server;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.onecore.loader.ui.ThemeManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.lingala.zip4j.ZipFile;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

import com.onecore.loader.libhelper.FileCopyTask;

/**
 * Downloads a server manifest, APK and multipart OBB archive, installs the APK into
 * OneCore, reconstructs/extracts the OBB and places it in OneCore virtual storage.
 *
 * The payload intentionally has no file hash/signature requirement. The downloaded
 * APK is still checked for the package name declared by the manifest so an accidental
 * wrong APK is not installed into the selected BGMI profile.
 */
@Obfuscate
public final class ServerInstallManager {

    private static final String CONFIG_ASSET = "server_download_config.json";
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final long MAX_MANIFEST_BYTES = 1024L * 1024L;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public interface Callback {
        void onCompleted(boolean success, String message);
    }

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneCore-ServerInstall");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private Dialog dialog;
    private TextView titleView;
    private TextView stateView;
    private TextView detailView;
    private TextView percentView;
    private ProgressBar progressBar;

    public ServerInstallManager(Activity activity) {
        this.activity = activity;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void start(String expectedPackageName, Callback callback) {
        if (!busy.compareAndSet(false, true)) {
            if (callback != null) callback.onCompleted(false, "A server install is already running.");
            return;
        }

        showProgressUi();
        worker.execute(() -> {
            boolean success = false;
            String message;
            try {
                performInstall(expectedPackageName);
                success = true;
                message = "BGMI downloaded and installed successfully.";
            } catch (Throwable error) {
                String detail = error.getMessage();
                message = detail == null || detail.trim().isEmpty()
                        ? "Server installation failed."
                        : detail.trim();
            }

            final boolean result = success;
            final String resultMessage = message;
            main.post(() -> {
                busy.set(false);
                renderFinalState(result, resultMessage);
                if (callback != null) callback.onCompleted(result, resultMessage);
            });
        });
    }

    private void performInstall(String expectedPackageName) throws Exception {
        updateUi("CONNECTING", "Loading server manifest", 2);

        String manifestUrl = readManifestUrl();
        requireHttps(manifestUrl, "Manifest");
        ManifestSpec spec = fetchManifest(manifestUrl);

        if (!expectedPackageName.equals(spec.packageName)) {
            throw new IOException("Manifest package does not match the selected BGMI profile.");
        }

        File workspace = new File(activity.getNoBackupFilesDir(),
                "server-install/" + safeName(spec.packageName));
        if (!workspace.exists() && !workspace.mkdirs()) {
            throw new IOException("Unable to create server-install workspace.");
        }

        File apkFile = new File(workspace, safeName(spec.apkFileName));
        updateUi("DOWNLOADING APK", "Preparing BGMI package", 5);
        downloadResumable(spec.apkUrl, apkFile, -1L, 5, 32, "APK");
        validateArchivePackage(apkFile, spec.packageName);

        updateUi("INSTALLING APK", "Installing BGMI inside OneCore", 35);
        InstallResult installResult = BlackBoxCore.get().installPackageAsUser(apkFile, 0);
        if (installResult == null || !installResult.success) {
            String reason = installResult == null ? "" : installResult.msg;
            throw new IOException(reason == null || reason.trim().isEmpty()
                    ? "OneCore could not install the downloaded APK."
                    : "OneCore install failed: " + reason);
        }

        File partsDir = new File(workspace, "parts");
        if (!partsDir.exists() && !partsDir.mkdirs()) {
            throw new IOException("Unable to create multipart download folder.");
        }

        List<File> downloadedParts = new ArrayList<>();
        int count = spec.parts.size();
        for (int i = 0; i < count; i++) {
            PartSpec part = spec.parts.get(i);
            File partFile = new File(partsDir, safeName(part.name));
            int start = 38 + (int) Math.floor((i * 34.0d) / Math.max(1, count));
            int end = 38 + (int) Math.floor(((i + 1) * 34.0d) / Math.max(1, count));
            updateUi("DOWNLOADING OBB",
                    "Part " + (i + 1) + " of " + count, start);
            downloadResumable(part.url, partFile, part.size, start, Math.max(start + 1, end),
                    "OBB part " + (i + 1));
            downloadedParts.add(partFile);
        }

        updateUi("PREPARING OBB", "Joining multipart archive", 74);
        File archive = new File(workspace, safeName(spec.archiveName));
        joinParts(downloadedParts, archive);

        // Once the complete archive exists, individual chunks are no longer needed.
        deleteRecursively(partsDir);

        File extractDir = new File(workspace, "extract");
        deleteRecursively(extractDir);
        if (!extractDir.mkdirs()) {
            throw new IOException("Unable to create OBB extraction folder.");
        }

        updateUi("EXTRACTING OBB", "Unpacking game data", 84);
        new ZipFile(archive).extractAll(extractDir.getAbsolutePath());

        File extractedObb = findFile(extractDir, spec.outputName);
        if (extractedObb == null || !extractedObb.isFile() || extractedObb.length() <= 0L) {
            throw new IOException("Expected OBB file was not found inside the archive.");
        }

        // Free the reconstructed ZIP before moving the large extracted OBB.
        if (archive.exists() && !archive.delete()) {
            archive.deleteOnExit();
        }

        updateUi("INSTALLING OBB", "Moving game data into OneCore storage", 92);
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

        updateUi("FINALIZING", "Cleaning temporary download files", 98);
        deleteRecursively(workspace);
        updateUi("READY", "BGMI server installation complete", 100);
    }

    private String readManifestUrl() throws Exception {
        try (InputStream input = activity.getAssets().open(CONFIG_ASSET)) {
            byte[] bytes = readLimited(input, MAX_MANIFEST_BYTES);
            JSONObject config = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String url = config.optString("manifest_url", "").trim();
            if (url.isEmpty() || url.contains("YOUR-DOMAIN") || url.contains("example.com")) {
                throw new IOException("Set manifest_url in assets/server_download_config.json first.");
            }
            return url;
        }
    }

    private ManifestSpec fetchManifest(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .get()
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Manifest request failed: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Manifest response was empty.");
            byte[] data = readLimited(body.byteStream(), MAX_MANIFEST_BYTES);
            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));

            String packageName = required(root, "package_name");
            JSONObject apk = root.getJSONObject("apk");
            String apkUrl = required(apk, "url");
            requireHttps(apkUrl, "APK");
            String apkFileName = apk.optString("filename", "bgmi.apk").trim();
            if (apkFileName.isEmpty()) apkFileName = "bgmi.apk";

            JSONObject obb = root.getJSONObject("obb");
            String archiveName = obb.optString("archive_name", "bgmi_obb.zip").trim();
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
                String name = part.optString("name",
                        String.format(Locale.US, "bgmi_obb.zip.part%02d", i)).trim();
                long size = part.optLong("size", -1L);
                parts.add(new PartSpec(name, partUrl, size));
            }

            return new ManifestSpec(packageName, apkFileName, apkUrl,
                    archiveName, outputName, parts);
        }
    }

    private void validateArchivePackage(File apkFile, String expectedPackage) throws IOException {
        PackageManager pm = activity.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (info == null || info.packageName == null) {
            throw new IOException("Downloaded APK is not a readable Android package.");
        }
        if (!expectedPackage.equals(info.packageName)) {
            throw new IOException("Downloaded APK package is " + info.packageName
                    + ", expected " + expectedPackage + ".");
        }
    }

    private void downloadResumable(String url, File destination, long expectedLength,
                                   int overallStart, int overallEnd, String label)
            throws IOException {
        requireHttps(url, label);
        if (expectedLength > 0L && destination.isFile()
                && destination.length() == expectedLength) {
            updateUi(label.toUpperCase(Locale.US),
                    humanBytes(expectedLength) + " ready", overallEnd);
            return;
        }
        if (expectedLength > 0L && destination.isFile()
                && destination.length() > expectedLength) {
            if (!destination.delete()) {
                throw new IOException(label + " partial file could not be reset.");
            }
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create download folder.");
        }

        boolean retried = false;
        while (true) {
            long existing = destination.isFile() ? destination.length() : 0L;
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Accept-Encoding", "identity")
                    .get();
            if (existing > 0L) builder.header("Range", "bytes=" + existing + "-");

            try (Response response = HTTP.newCall(builder.build()).execute()) {
                if (response.code() == 416 && !retried) {
                    if (!destination.delete()) {
                        throw new IOException(label + " resume state could not be reset.");
                    }
                    retried = true;
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new IOException(label + " download failed: HTTP " + response.code());
                }

                ResponseBody body = response.body();
                if (body == null) throw new IOException(label + " download body was empty.");

                boolean append = existing > 0L && response.code() == 206;
                if (existing > 0L && !append) existing = 0L;

                long incoming = body.contentLength();
                long total = incoming > 0L ? existing + incoming : -1L;
                long downloaded = existing;

                try (InputStream input = new BufferedInputStream(body.byteStream(), BUFFER_SIZE);
                     OutputStream output = new BufferedOutputStream(
                             new FileOutputStream(destination, append), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    int lastOverall = -1;
                    while ((read = input.read(buffer)) != -1) {
                        if (read <= 0) continue;
                        output.write(buffer, 0, read);
                        downloaded += read;

                        int overall;
                        if (total > 0L) {
                            double fraction = Math.min(1d, downloaded / (double) total);
                            overall = overallStart
                                    + (int) Math.round((overallEnd - overallStart) * fraction);
                        } else {
                            overall = overallStart;
                        }
                        if (overall != lastOverall) {
                            lastOverall = overall;
                            updateUi(label.toUpperCase(Locale.US),
                                    humanBytes(downloaded)
                                            + (total > 0L ? " / " + humanBytes(total) : ""),
                                    overall);
                        }
                    }
                    output.flush();
                }

                if (!destination.isFile() || destination.length() <= 0L) {
                    throw new IOException(label + " download produced an empty file.");
                }
                if (expectedLength > 0L && destination.length() != expectedLength) {
                    throw new IOException(label + " size does not match the manifest.");
                }
                return;
            }
        }
    }

    private void joinParts(List<File> parts, File archive) throws IOException {
        if (parts.isEmpty()) throw new IOException("No OBB parts were downloaded.");
        try (OutputStream output = new BufferedOutputStream(
                new FileOutputStream(archive, false), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int i = 0; i < parts.size(); i++) {
                File part = parts.get(i);
                if (!part.isFile() || part.length() <= 0L) {
                    throw new IOException("OBB part " + (i + 1) + " is missing.");
                }
                try (InputStream input = new BufferedInputStream(
                        new FileInputStream(part), BUFFER_SIZE)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > 0) output.write(buffer, 0, read);
                    }
                }
                updateUi("PREPARING OBB",
                        "Joined " + (i + 1) + " of " + parts.size() + " parts",
                        74 + (int) Math.round(((i + 1) * 8.0d) / parts.size()));
            }
            output.flush();
        }
        if (!archive.isFile() || archive.length() <= 0L) {
            throw new IOException("Multipart OBB archive could not be reconstructed.");
        }
    }

    private static void moveLargeFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create OBB destination.");
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Unable to replace previous OBB file.");
        }
        if (source.renameTo(destination)) return;

        try (InputStream input = new BufferedInputStream(
                     new FileInputStream(source), BUFFER_SIZE);
             OutputStream output = new BufferedOutputStream(
                     new FileOutputStream(destination), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) output.write(buffer, 0, read);
            }
            output.flush();
        }
        if (!source.delete()) source.deleteOnExit();
    }

    private static File findFile(File directory, String expectedName) {
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && expectedName.equals(child.getName())) return child;
            if (child.isDirectory()) {
                File nested = findFile(child, expectedName);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read <= 0) continue;
            total += read;
            if (total > maxBytes) throw new IOException("Configuration response is too large.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String required(JSONObject object, String key) throws Exception {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IOException("Manifest field is missing: " + key);
        return value;
    }

    private static void requireHttps(String url, String label) throws IOException {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException(label + " URL must use HTTPS.");
        }
    }

    private static String safeName(String value) {
        if (value == null) return "file";
        String name = new File(value).getName().replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isEmpty() ? "file" : name;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes / 1024d;
        if (value < 1024d) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024d;
        if (value < 1024d) return String.format(Locale.US, "%.1f MB", value);
        value /= 1024d;
        return String.format(Locale.US, "%.2f GB", value);
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        return file.delete();
    }

    private void showProgressUi() {
        main.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                busy.set(false);
                return;
            }
            ThemeManager.ThemeSpec theme = ThemeManager.current(activity);

            dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(22), dp(21), dp(22), dp(21));

            GradientDrawable background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{theme.surfaceAlt, theme.surface});
            background.setCornerRadius(dp(theme.cardRadiusDp));
            background.setStroke(dp(Math.max(1f, theme.strokeDp)),
                    ThemeManager.withAlpha(theme.accent, 220));
            root.setBackground(background);

            titleView = text("SERVER INSTALL", 20f, theme.text, true);
            titleView.setLetterSpacing(0.06f);
            root.addView(titleView, matchWrap(0));

            stateView = text("CONNECTING", 11f, theme.accent, true);
            stateView.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams stateParams = matchWrap(0);
            stateParams.topMargin = dp(9);
            root.addView(stateView, stateParams);

            detailView = text("Preparing download", 12f, theme.muted, false);
            LinearLayout.LayoutParams detailParams = matchWrap(0);
            detailParams.topMargin = dp(5);
            root.addView(detailView, detailParams);

            progressBar = new ProgressBar(activity, null,
                    android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(theme.accent));
                progressBar.setProgressBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                ThemeManager.withAlpha(theme.muted, 45)));
            }
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            progressParams.topMargin = dp(18);
            root.addView(progressBar, progressParams);

            percentView = text("0%", 11f, theme.text, true);
            percentView.setGravity(Gravity.END);
            LinearLayout.LayoutParams percentParams = matchWrap(0);
            percentParams.topMargin = dp(7);
            root.addView(percentView, percentParams);

            TextView footer = text(
                    "APK + MULTIPART OBB  •  RESUMABLE DOWNLOAD",
                    9f, theme.success, true);
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.05f);
            LinearLayout.LayoutParams footerParams = matchWrap(0);
            footerParams.topMargin = dp(14);
            root.addView(footer, footerParams);

            dialog.setContentView(root);
            dialog.show();

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.dimAmount = 0.80f;
                window.setAttributes(attrs);
                int width = activity.getResources().getDisplayMetrics().widthPixels;
                window.setLayout((int) (width * 0.88f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }

            root.setAlpha(0f);
            root.setScaleX(0.96f);
            root.setScaleY(0.96f);
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240L).start();
        });
    }

    private void updateUi(String state, String detail, int percent) {
        main.post(() -> {
            int safe = Math.max(0, Math.min(100, percent));
            if (stateView != null) stateView.setText(state);
            if (detailView != null) detailView.setText(detail);
            if (progressBar != null) progressBar.setProgress(safe);
            if (percentView != null) percentView.setText(safe + "%");
        });
    }

    private void renderFinalState(boolean success, String message) {
        ThemeManager.ThemeSpec theme = ThemeManager.current(activity);
        if (stateView != null) {
            stateView.setText(success ? "INSTALL COMPLETE" : "INSTALL FAILED");
            stateView.setTextColor(success ? theme.success : theme.error);
        }
        if (detailView != null) detailView.setText(message);
        if (progressBar != null) progressBar.setProgress(success ? 100 : progressBar.getProgress());
        if (percentView != null && success) percentView.setText("100%");

        main.postDelayed(() -> {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            dialog = null;
        }, success ? 1100L : 2200L);
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(ThemeManager.current(activity).headingFont,
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class ManifestSpec {
        final String packageName;
        final String apkFileName;
        final String apkUrl;
        final String archiveName;
        final String outputName;
        final List<PartSpec> parts;

        ManifestSpec(String packageName, String apkFileName, String apkUrl,
                     String archiveName, String outputName, List<PartSpec> parts) {
            this.packageName = packageName;
            this.apkFileName = apkFileName;
            this.apkUrl = apkUrl;
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
}
