package com.onecore.loader.libhelper;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
            "Parallax.so",
            "libpubgm.so",
            "libkorea.so"));

    private final Context context;
    private final ExecutorService executor;
    private final Handler handler;
    
    // Animation views
    private static LinearLayout downloadOverlay = null;
    private TextView downloadTitleText;
    private TextView downloadMessageText;
    private TextView downloadProgressText;
    private ProgressBar downloadProgressBar;
    private ImageView downloadIcon;
    private static boolean isDownloading = false;
    private Runnable dotRunnable;
    private long startTime = 0;
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
    
    private Typeface getPremiumFont() {
        try {
            if (androidx.core.content.res.ResourcesCompat.getFont(context, com.onecore.loader.R.font.acme) != null) {
                return androidx.core.content.res.ResourcesCompat.getFont(context, com.onecore.loader.R.font.acme);
            }
        } catch (Exception e) {
            // Fallback
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            return Typeface.create("sans-serif-condensed", Typeface.BOLD);
        }
        return Typeface.DEFAULT_BOLD;
    }
    
    private void showDownloadAnimation(String message) {
        if (isDownloading) return;
        isDownloading = true;
        
        ((Activity) context).runOnUiThread(() -> {
            if (downloadOverlay != null && downloadOverlay.getParent() != null) {
                try {
                    ((FrameLayout) downloadOverlay.getParent()).removeView(downloadOverlay);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                downloadOverlay = null;
            }
            
            downloadOverlay = new LinearLayout(context);
            downloadOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            downloadOverlay.setGravity(Gravity.CENTER);
            downloadOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            downloadOverlay.setOrientation(LinearLayout.VERTICAL);
            downloadOverlay.setClickable(true);
            downloadOverlay.setFocusable(true);
            
            Typeface premiumFont = getPremiumFont();
            
            // Download icon with rotation
            downloadIcon = new ImageView(context);
            downloadIcon.setImageResource(android.R.drawable.stat_sys_download);
            downloadIcon.setColorFilter(Color.parseColor("#FFD700"));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(70, 70);
            iconParams.bottomMargin = 20;
            downloadIcon.setLayoutParams(iconParams);
            
            // Title text
            downloadTitleText = new TextView(context);
            downloadTitleText.setText("✦ DOWNLOADING FILES ✦");
            downloadTitleText.setTextColor(Color.parseColor("#FFD700"));
            downloadTitleText.setTextSize(18);
            downloadTitleText.setTypeface(premiumFont);
            downloadTitleText.setGravity(Gravity.CENTER);
            downloadTitleText.setPadding(0, 10, 0, 10);
            
            // Message text
            downloadMessageText = new TextView(context);
            downloadMessageText.setText(message);
            downloadMessageText.setTextColor(Color.parseColor("#CCFFD700"));
            downloadMessageText.setTextSize(12);
            downloadMessageText.setTypeface(premiumFont);
            downloadMessageText.setGravity(Gravity.CENTER);
            downloadMessageText.setPadding(0, 5, 0, 5);
            
            // Progress bar
            downloadProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            downloadProgressBar.setMax(100);
            downloadProgressBar.setProgress(0);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(250, 6);
            progressParams.topMargin = 15;
            progressParams.bottomMargin = 10;
            downloadProgressBar.setLayoutParams(progressParams);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                downloadProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700")));
                downloadProgressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFD700")));
            }
            
            // Progress text
            downloadProgressText = new TextView(context);
            downloadProgressText.setText("0% • 0.00 MB / 0.00 MB");
            downloadProgressText.setTextColor(Color.parseColor("#FFD700"));
            downloadProgressText.setTextSize(11);
            downloadProgressText.setTypeface(premiumFont);
            downloadProgressText.setGravity(Gravity.CENTER);
            downloadProgressText.setPadding(0, 5, 0, 0);
            
            downloadOverlay.addView(downloadIcon);
            downloadOverlay.addView(downloadTitleText);
            downloadOverlay.addView(downloadMessageText);
            downloadOverlay.addView(downloadProgressBar);
            downloadOverlay.addView(downloadProgressText);
            
            ((Activity) context).addContentView(downloadOverlay, downloadOverlay.getLayoutParams());
            
            // Fade in
            downloadOverlay.setAlpha(0f);
            downloadOverlay.animate().alpha(1f).setDuration(300).start();
            
            // Rotate icon
            RotateAnimation rotateAnim = new RotateAnimation(0f, 360f, 
                    Animation.RELATIVE_TO_SELF, 0.5f, 
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnim.setDuration(1500);
            rotateAnim.setRepeatCount(Animation.INFINITE);
            rotateAnim.setInterpolator(new LinearInterpolator());
            downloadIcon.startAnimation(rotateAnim);
            
            // Pulse text
            ScaleAnimation scaleAnim = new ScaleAnimation(
                    1f, 1.05f, 1f, 1.05f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setDuration(800);
            scaleAnim.setRepeatCount(Animation.INFINITE);
            scaleAnim.setRepeatMode(Animation.REVERSE);
            downloadTitleText.startAnimation(scaleAnim);
            
            // Dots animation
            startDotsAnimation();
        });
    }
    
    private void startDotsAnimation() {
        final int[] dotCount = {0};
        final String[] dotPattern = {"", ".", "..", "..."};
        
        dotRunnable = new Runnable() {
            @Override
            public void run() {
                if (downloadTitleText != null && isDownloading) {
                    downloadTitleText.setText("✦ DOWNLOADING FILES" + dotPattern[dotCount[0]] + " ✦");
                    dotCount[0] = (dotCount[0] + 1) % dotPattern.length;
                    handler.postDelayed(this, 400);
                }
            }
        };
        handler.post(dotRunnable);
    }
    
    private void updateDownloadProgress(int progress, String message, long downloaded, long total) {
        ((Activity) context).runOnUiThread(() -> {
            if (downloadProgressBar != null && isDownloading) {
                downloadProgressBar.setProgress(progress);
                downloadProgressBar.setIndeterminate(false);
                
                String progressText = String.format(Locale.getDefault(), 
                        "%d%% • %.2f MB / %.2f MB", 
                        progress, downloaded / (1024.0 * 1024.0), total / (1024.0 * 1024.0));
                downloadProgressText.setText(progressText);
                
                String timeMessage = String.format(Locale.getDefault(),
                        "⏱️ Time: %d ms", System.currentTimeMillis() - startTime);
                downloadMessageText.setText(timeMessage);
                
                AlphaAnimation fadeAnim = new AlphaAnimation(0.5f, 1f);
                fadeAnim.setDuration(300);
                downloadProgressText.startAnimation(fadeAnim);
            }
        });
    }
    
    private void hideDownloadAnimation(boolean success, String resultMessage) {
        if (!isDownloading) return;
        isDownloading = false;
        
        if (handler != null && dotRunnable != null) {
            handler.removeCallbacks(dotRunnable);
        }
        
        ((Activity) context).runOnUiThread(() -> {
            if (downloadOverlay != null) {
                downloadOverlay.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    if (downloadOverlay.getParent() != null) {
                        ((FrameLayout) downloadOverlay.getParent()).removeView(downloadOverlay);
                    }
                    if (downloadIcon != null) downloadIcon.clearAnimation();
                    if (downloadTitleText != null) downloadTitleText.clearAnimation();
                    downloadOverlay = null;
                    downloadIcon = null;
                    downloadTitleText = null;
                    downloadMessageText = null;
                    downloadProgressBar = null;
                    downloadProgressText = null;
                    
                    // Show result dialog
                    showResultDialog(success, resultMessage);
                }).start();
            }
        });
    }
    
    private void showResultDialog(boolean success, String message) {
        ((Activity) context).runOnUiThread(() -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            LinearLayout dialogLayout = new LinearLayout(context);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(40, 40, 40, 40);
            dialogLayout.setBackgroundColor(Color.parseColor("#1A1A1A"));
            
            android.graphics.drawable.GradientDrawable bgShape = new android.graphics.drawable.GradientDrawable();
            bgShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bgShape.setCornerRadius(16);
            bgShape.setColor(Color.parseColor("#1A1A1A"));
            dialogLayout.setBackground(bgShape);
            
            TextView titleText = new TextView(context);
            titleText.setText(success ? "✓ SUCCESS ✓" : "✗ FAILED ✗");
            titleText.setTextSize(20);
            titleText.setTypeface(getPremiumFont(), Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextColor(success ? Color.parseColor("#FFD700") : Color.parseColor("#FF4444"));
            titleText.setPadding(0, 0, 0, 20);
            
            TextView messageText = new TextView(context);
            messageText.setText(message);
            messageText.setTextSize(14);
            messageText.setTypeface(getPremiumFont());
            messageText.setGravity(Gravity.CENTER);
            messageText.setTextColor(Color.parseColor("#FFFFFF"));
            messageText.setPadding(0, 0, 0, 30);
            
            TextView buttonText = new TextView(context);
            buttonText.setText("OK");
            buttonText.setTextSize(16);
            buttonText.setTypeface(getPremiumFont(), Typeface.BOLD);
            buttonText.setGravity(Gravity.CENTER);
            buttonText.setTextColor(Color.parseColor("#000000"));
            buttonText.setPadding(50, 15, 50, 15);
            buttonText.setClickable(true);
            buttonText.setFocusable(true);
            
            android.graphics.drawable.GradientDrawable buttonShape = new android.graphics.drawable.GradientDrawable();
            buttonShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            buttonShape.setCornerRadius(25);
            buttonShape.setColor(Color.parseColor("#FFD700"));
            buttonText.setBackground(buttonShape);
            
            dialogLayout.addView(titleText);
            dialogLayout.addView(messageText);
            dialogLayout.addView(buttonText);
            
            builder.setView(dialogLayout);
            builder.setCancelable(false);
            
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
            
            buttonText.setOnClickListener(v -> dialog.dismiss());
            
            dialogLayout.setAlpha(0f);
            dialogLayout.animate().alpha(1f).setDuration(300).start();
        });
    }

    public void startDownload(String downloadUrl) {
        startDownload(downloadUrl, null);
    }

    public void startDownload(String downloadUrl, DownloadCallback callback) {
        showDownloadAnimation("Initializing download...");

        if (callback != null) {
            callback.onStart();
        }

        startTime = System.currentTimeMillis();
        downloadedBytes = 0;

        executor.execute(() -> {
            boolean downloaded = downloadFile(downloadUrl, callback);
            if (!downloaded) {
                handler.post(() -> {
                    hideDownloadAnimation(false, "✗ Download failed!\n✗ Check the HTTPS URL or connection");
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
                    hideDownloadAnimation(true,
                            "✓ Download Complete!\n✓ Archive validated and installed successfully!");
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    hideDownloadAnimation(false, "✗ " + extractionResult.message);
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
                        if (downloadProgressBar != null && isDownloading) {
                            updateDownloadProgress(finalProgress, "Downloading...", progressBytes, totalBytes);
                        }
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
