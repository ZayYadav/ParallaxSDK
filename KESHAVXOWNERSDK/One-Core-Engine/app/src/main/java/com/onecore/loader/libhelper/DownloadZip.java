package com.onecore.loader.libhelper;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.onecore.loader.R;
import com.onecore.loader.ui.ThemeManager;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadZip {

    private static final String ZIP_FILE_NAME = "native-artifacts.zip";
    private static final String PRIVATE_ARTIFACT_DIRECTORY = "native";
    private static final long MAX_DOWNLOAD_BYTES = 128L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final Set<String> ALLOWED_ARTIFACTS = new HashSet<>(Arrays.asList(
            "KESHAVXOWNER.so",
            "libpubgm.so",
            "libkorea.so"));

    private final Context context;
    private final ExecutorService executor;
    private final Handler handler;

    private LinearLayout downloadPanel;
    private RingProgressView ringProgressView;
    private TextView downloadTitleText;
    private TextView downloadMessageText;
    private TextView securityStateText;
    private ProgressBar downloadProgressBar;
    private static boolean isDownloading = false;
    private long downloadedBytes = 0;

    private native String PASSJKPAPA();

    public interface DownloadCallback {
        void onStart();
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }

    public DownloadZip(Context context) {
        this.context = context;
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
    }

    /** Circular percentage indicator used by the inline runtime download card. */
    private static final class RingProgressView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private int progress;

        RingProgressView(Context context) {
            super(context);
            trackPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            valuePaint.setTextAlign(Paint.Align.CENTER);
            valuePaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }

        void setProgressValue(int value) {
            progress = Math.max(0, Math.min(100, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ThemeManager.ThemeSpec theme = ThemeManager.current(getContext());
            float density = getResources().getDisplayMetrics().density;
            float stroke = 8f * density;
            float inset = 11f * density;
            trackPaint.setStrokeWidth(stroke);
            progressPaint.setStrokeWidth(stroke);
            trackPaint.setColor(ThemeManager.withAlpha(theme.muted, 50));
            progressPaint.setColor(theme.accent);

            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(oval, -90f, 360f, false, trackPaint);
            canvas.drawArc(oval, -90f, 360f * progress / 100f, false, progressPaint);

            valuePaint.setColor(theme.text);
            valuePaint.setTextSize(27f * density);
            labelPaint.setColor(theme.accent);
            labelPaint.setTextSize(8.5f * density);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            canvas.drawText(progress + "%", centerX, centerY + 2f * density, valuePaint);
            canvas.drawText(progress >= 100 ? "READY" : "DOWNLOADING",
                    centerX, centerY + 24f * density, labelPaint);
        }
    }

    private void showDownloadAnimation(String ignoredMessage) {
        if (isDownloading || !(context instanceof Activity)) {
            return;
        }
        isDownloading = true;
        Activity activity = (Activity) context;
        activity.runOnUiThread(() -> {
            TextView startButton = activity.findViewById(R.id.btn_start_game);
            View privacyMode = activity.findViewById(R.id.tv_hide_esp);
            if (startButton == null || !(startButton.getParent() instanceof ViewGroup)) {
                return;
            }

            ViewGroup runtimeContainer = (ViewGroup) startButton.getParent();
            if (downloadPanel != null && downloadPanel.getParent() instanceof ViewGroup) {
                ((ViewGroup) downloadPanel.getParent()).removeView(downloadPanel);
            }

            ThemeManager.ThemeSpec theme = ThemeManager.current(context);
            int pad = ThemeManager.dp(context, 16);

            downloadPanel = new LinearLayout(context);
            downloadPanel.setOrientation(LinearLayout.VERTICAL);
            downloadPanel.setPadding(pad, pad, pad, pad);
            downloadPanel.setBackground(ThemeManager.themedPanel(context, true));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                downloadPanel.setElevation(ThemeManager.dp(context, Math.max(6f, theme.elevationDp)));
            }

            LinearLayout hero = new LinearLayout(context);
            hero.setOrientation(LinearLayout.HORIZONTAL);
            hero.setGravity(Gravity.CENTER_VERTICAL);

            ringProgressView = new RingProgressView(context);
            ringProgressView.setProgressValue(0);
            LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(
                    ThemeManager.dp(context, 118), ThemeManager.dp(context, 118));
            ringParams.rightMargin = ThemeManager.dp(context, 16);
            hero.addView(ringProgressView, ringParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER_VERTICAL);
            hero.addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            downloadTitleText = new TextView(context);
            downloadTitleText.setText("DOWNLOADING FILES");
            downloadTitleText.setTextColor(theme.accent);
            downloadTitleText.setTextSize(19f);
            downloadTitleText.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
            downloadTitleText.setLetterSpacing(0.045f);
            copy.addView(downloadTitleText, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            downloadMessageText = new TextView(context);
            downloadMessageText.setText("Installing secure assets");
            downloadMessageText.setTextColor(theme.muted);
            downloadMessageText.setTextSize(12f);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = ThemeManager.dp(context, 5);
            copy.addView(downloadMessageText, messageParams);

            downloadProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            downloadProgressBar.setMax(100);
            downloadProgressBar.setProgress(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                downloadProgressBar.setProgressTintList(ColorStateList.valueOf(theme.accent));
                downloadProgressBar.setProgressBackgroundTintList(
                        ColorStateList.valueOf(ThemeManager.withAlpha(theme.muted, 45)));
            }
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ThemeManager.dp(context, 9));
            barParams.topMargin = ThemeManager.dp(context, 14);
            copy.addView(downloadProgressBar, barParams);

            securityStateText = new TextView(context);
            securityStateText.setText("SECURE   •   VERIFIED   •   OPTIMIZED");
            securityStateText.setTextColor(theme.success);
            securityStateText.setTextSize(10f);
            securityStateText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            securityStateText.setLetterSpacing(0.035f);
            LinearLayout.LayoutParams secureParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            secureParams.topMargin = ThemeManager.dp(context, 12);
            copy.addView(securityStateText, secureParams);

            downloadPanel.addView(hero, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            panelParams.topMargin = ThemeManager.dp(context, 14);
            panelParams.bottomMargin = ThemeManager.dp(context, 8);

            int insertIndex = privacyMode == null ? -1 : runtimeContainer.indexOfChild(privacyMode);
            if (insertIndex < 0) {
                insertIndex = Math.max(0, runtimeContainer.indexOfChild(startButton));
            }
            runtimeContainer.addView(downloadPanel, insertIndex, panelParams);

            downloadPanel.setAlpha(0f);
            downloadPanel.setTranslationY(ThemeManager.dp(context, 10));
            downloadPanel.animate().alpha(1f).translationY(0f).setDuration(240L).start();
        });
    }

    private void updateDownloadProgress(int progress, String message, long downloaded, long total) {
        if (!(context instanceof Activity)) {
            return;
        }
        ((Activity) context).runOnUiThread(() -> {
            if (!isDownloading || downloadPanel == null) {
                return;
            }
            if (ringProgressView != null) {
                ringProgressView.setProgressValue(progress);
            }
            if (downloadProgressBar != null) {
                downloadProgressBar.setIndeterminate(false);
                downloadProgressBar.setProgress(progress);
            }
            if (downloadMessageText != null) {
                if (progress >= 100 || (message != null && message.toLowerCase(Locale.US).contains("validat"))) {
                    downloadMessageText.setText("Validating encrypted runtime package");
                } else {
                    downloadMessageText.setText("Installing secure assets");
                }
            }
        });
    }

    private void hideDownloadAnimation(boolean success, String resultMessage) {
        if (!isDownloading || !(context instanceof Activity)) {
            return;
        }
        isDownloading = false;
        Activity activity = (Activity) context;
        activity.runOnUiThread(() -> {
            if (downloadPanel == null) {
                return;
            }
            ThemeManager.ThemeSpec theme = ThemeManager.current(context);
            if (ringProgressView != null && success) {
                ringProgressView.setProgressValue(100);
            }
            if (downloadProgressBar != null && success) {
                downloadProgressBar.setProgress(100);
            }
            if (downloadTitleText != null) {
                downloadTitleText.setText(success ? "RUNTIME READY" : "DOWNLOAD FAILED");
                downloadTitleText.setTextColor(success ? theme.success : theme.error);
            }
            if (downloadMessageText != null) {
                downloadMessageText.setText(success
                        ? "Secure assets verified and installed"
                        : cleanError(resultMessage));
            }
            if (securityStateText != null) {
                securityStateText.setText(success
                        ? "VERIFIED   •   READY TO START"
                        : "RETRY REQUIRED");
                securityStateText.setTextColor(success ? theme.success : theme.error);
            }

            long delay = success ? 850L : 1800L;
            handler.postDelayed(() -> activity.runOnUiThread(() -> {
                if (downloadPanel == null) {
                    return;
                }
                LinearLayout panel = downloadPanel;
                panel.animate().alpha(0f).translationY(ThemeManager.dp(context, -8))
                        .setDuration(220L)
                        .withEndAction(() -> {
                            if (panel.getParent() instanceof ViewGroup) {
                                ((ViewGroup) panel.getParent()).removeView(panel);
                            }
                            if (downloadPanel == panel) {
                                downloadPanel = null;
                                ringProgressView = null;
                                downloadTitleText = null;
                                downloadMessageText = null;
                                securityStateText = null;
                                downloadProgressBar = null;
                            }
                        }).start();
            }), delay);
        });
    }

    private String cleanError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Secure asset sync failed";
        }
        String cleaned = message.replace("✗", "").replace('\n', ' ').trim();
        return cleaned.length() > 90 ? cleaned.substring(0, 90) : cleaned;
    }

    public void startDownload(String downloadUrl) {
        startDownload(downloadUrl, null);
    }

    public void startDownload(String downloadUrl, DownloadCallback callback) {
        showDownloadAnimation("Preparing secure runtime");

        if (callback != null) {
            callback.onStart();
        }

        downloadedBytes = 0;

        executor.execute(() -> {
            boolean downloaded = downloadFile(downloadUrl, callback);
            if (!downloaded) {
                handler.post(() -> {
                    hideDownloadAnimation(false, "Download failed. Check the HTTPS connection.");
                    if (callback != null) {
                        callback.onError("Download failed");
                    }
                });
                return;
            }

            handler.post(() -> updateDownloadProgress(
                    100, "Validating ZIP...", downloadedBytes, downloadedBytes));

            File zipFile = new File(context.getCacheDir(), ZIP_FILE_NAME);
            File stagingDirectory = new File(
                    context.getCacheDir(), "native-staging-" + UUID.randomUUID());
            String password = PASSJKPAPA();
            ExtractionResult extractionResult = extractAndInstall(
                    zipFile, stagingDirectory, password);

            deleteRecursively(stagingDirectory);
            zipFile.delete();

            handler.post(() -> {
                if (extractionResult.success) {
                    hideDownloadAnimation(true, "Runtime ready");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    hideDownloadAnimation(false, extractionResult.message);
                    if (callback != null) {
                        callback.onError(extractionResult.message);
                    }
                }
            });
        });
    }

    private boolean downloadFile(String downloadUrl, DownloadCallback callback) {
        File outputZip = new File(context.getCacheDir(), ZIP_FILE_NAME);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(downloadUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                throw new java.io.IOException("Only HTTPS artifact downloads are allowed");
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new java.io.IOException("Artifact server returned HTTP " + responseCode);
            }

            long totalBytes = connection.getContentLengthLong();
            if (totalBytes > MAX_DOWNLOAD_BYTES) {
                throw new java.io.IOException("Artifact download is too large");
            }

            downloadedBytes = 0;

            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(outputZip)) {
                byte[] data = new byte[32 * 1024];
                int count;
                while ((count = input.read(data)) != -1) {
                    downloadedBytes += count;
                    if (downloadedBytes > MAX_DOWNLOAD_BYTES) {
                        throw new java.io.IOException("Artifact download exceeded its size limit");
                    }

                    int progress = totalBytes > 0
                            ? (int) Math.min(100, (downloadedBytes * 100) / totalBytes)
                            : 0;
                    final int finalProgress = progress;
                    final long progressBytes = downloadedBytes;
                    handler.post(() -> {
                        updateDownloadProgress(finalProgress, "Downloading...", progressBytes, totalBytes);
                        if (callback != null) {
                            callback.onProgress(finalProgress);
                        }
                    });

                    output.write(data, 0, count);
                }
            }

            return outputZip.isFile() && outputZip.length() > 0;
        } catch (Exception e) {
            outputZip.delete();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ExtractionResult extractAndInstall(File zipPath, File outputDir, String password) {
        if (zipPath == null || !zipPath.isFile() || zipPath.length() == 0) {
            return ExtractionResult.error("Downloaded archive is empty");
        }

        try {
            ZipFile zipFile = password == null || password.isEmpty()
                    ? new ZipFile(zipPath)
                    : new ZipFile(zipPath, password.toCharArray());

            if (!zipFile.isValidZipFile()) {
                return ExtractionResult.error("Downloaded file is not a valid ZIP archive");
            }
            if (zipFile.isEncrypted() && (password == null || password.isEmpty())) {
                return ExtractionResult.error("ZIP is encrypted but no password is configured");
            }

            if (outputDir.exists()) {
                deleteRecursively(outputDir);
            }
            if (!outputDir.mkdirs() && !outputDir.isDirectory()) {
                return ExtractionResult.error("Unable to create extraction directory");
            }

            try {
                zipFile.extractAll(outputDir.getAbsolutePath());
            } catch (Exception extractionFailure) {
                String message = extractionFailure.getMessage();
                String normalized = message == null ? "" : message.toLowerCase(Locale.US);
                if (normalized.contains("password") || normalized.contains("decrypt")) {
                    return ExtractionResult.error("ZIP password is incorrect or unsupported");
                }
                if (normalized.contains("corrupt") || normalized.contains("invalid")) {
                    return ExtractionResult.error("ZIP archive is corrupt or unsupported");
                }
                return ExtractionResult.error("ZIP extraction failed");
            }

            if (!moveSoFiles(outputDir)) {
                return ExtractionResult.error(
                        "ZIP extracted, but no supported native artifact was installed");
            }
            return ExtractionResult.success();
        } catch (Exception validationFailure) {
            return ExtractionResult.error("Unable to validate ZIP archive");
        }
    }

    private boolean moveSoFiles(File stagingDirectory) {
        File artifactDirectory = new File(context.getNoBackupFilesDir(), PRIVATE_ARTIFACT_DIRECTORY);
        if (!artifactDirectory.isDirectory() && !artifactDirectory.mkdirs()) {
            return false;
        }

        File[] files = stagingDirectory.listFiles();
        boolean installedAny = false;
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                installedAny = moveSoFiles(file) || installedAny;
            } else if (ALLOWED_ARTIFACTS.contains(file.getName())) {
                try {
                    NativeArtifactStore.install(file, new File(artifactDirectory, file.getName()));
                    installedAny = true;
                } catch (java.io.IOException ignored) {
                    // Keep internal file details out of logs/UI.
                }
            }
        }
        return installedAny;
    }

    private void deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) {
            return;
        }
        if (fileOrDir.isDirectory()) {
            File[] files = fileOrDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteRecursively(file);
                }
            }
        }
        fileOrDir.delete();
    }

    private static final class ExtractionResult {
        final boolean success;
        final String message;

        private ExtractionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static ExtractionResult success() {
            return new ExtractionResult(true, "OK");
        }

        static ExtractionResult error(String message) {
            return new ExtractionResult(false, message);
        }
    }
}