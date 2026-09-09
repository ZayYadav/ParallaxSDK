package com.onecore.loader.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.onecore.loader.security.SecurityIncidentDispatcher;
import com.onecore.loader.utils.FLog;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TextureView-based signature cinematic. TextureView keeps video and Canvas HUD in one compositor,
 * so animated security GFX remain visible above the supplied video on devices where VideoView's
 * SurfaceView would otherwise punch through sibling overlays.
 */
@Obfuscate
public final class SecurityCinematicDialogV2 {
    private static final String VIDEO_URL =
            "https://github.com/sk8787914-maker/Zoro-online-mod/releases/download/JANGAM/VID_20260822_235111_206.mp4";
    private static final String CACHE_FILE = "onecore_security_response_v3.mp4";
    private static final long MIN_VIDEO_BYTES = 64L * 1024L;
    private static final long MAX_VIDEO_BYTES = 160L * 1024L * 1024L;
    private static final long EXIT_DELAY_MS = 15_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOAD_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneCore-CinematicFetch");
                thread.setDaemon(true);
                return thread;
            });
    private static final ScheduledExecutorService EXIT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneCore-CinematicExit");
                thread.setDaemon(true);
                return thread;
            });

    private SecurityCinematicDialogV2() {
    }

    public static void show(
            Activity activity,
            SecurityIncidentDispatcher.Reason reason,
            String detail,
            Runnable onFinished) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> show(activity, reason, detail, onFinished));
            return;
        }
        if (!usable(activity)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        new Session(
                activity,
                ThemeManager.current(activity),
                reason == null ? SecurityIncidentDispatcher.Reason.SIGNATURE : reason,
                detail == null ? "" : detail,
                onFinished).start();
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private interface ProgressCallback {
        void onProgress(int progress, String message);
    }

    private static final class Session implements TextureView.SurfaceTextureListener {
        private final Activity activity;
        private final ThemeManager.ThemeSpec theme;
        private final SecurityIncidentDispatcher.Reason reason;
        private final String detail;
        private final Runnable onFinished;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean countdownArmed = new AtomicBoolean(false);

        private Dialog dialog;
        private FrameLayout stage;
        private ProgressBar progressBar;
        private TextView progressText;
        private TextView statusText;
        private TextureView textureView;
        private MediaPlayer mediaPlayer;
        private Surface mediaSurface;
        private TextView countdownText;
        private TextView countdownFooter;
        private long exitDeadlineElapsed;
        private AudioManager audioManager;
        private int previousMusicVolume = -1;
        private boolean volumeRaised;
        private File pendingVideo;

        Session(
                Activity activity,
                ThemeManager.ThemeSpec theme,
                SecurityIncidentDispatcher.Reason reason,
                String detail,
                Runnable onFinished) {
            this.activity = activity;
            this.theme = theme;
            this.reason = reason;
            this.detail = detail;
            this.onFinished = onFinished;
        }

        void start() {
            buildDialog();
            showProcessingStage();
            try {
                dialog.show();
                configureWindow();
            } catch (Throwable error) {
                FLog.error("Security cinematic V2 could not be shown", error);
                finishNow();
                return;
            }

            DOWNLOAD_EXECUTOR.execute(() -> {
                try {
                    File video = downloadVideo(activity, (progress, message) ->
                            MAIN.post(() -> updateDownload(progress, message)));
                    MAIN.post(() -> showVideoStage(video));
                } catch (Throwable error) {
                    FLog.error("Security cinematic V2 download failed", error);
                    MAIN.post(() -> showFallback("SECURE MEDIA UNAVAILABLE"));
                }
            });
        }

        private void buildDialog() {
            dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener((ignored, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK);
            dialog.setOnDismissListener(ignored -> restoreVolume());

            stage = new FrameLayout(activity);
            stage.setPadding(dp(8), dp(8), dp(8), dp(8));
            stage.setBackground(panelBackground(theme));
            dialog.setContentView(stage);
        }

        private void configureWindow() {
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.94f;
            window.setAttributes(attrs);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            window.setLayout((int) (width * 0.96f), (int) (height * 0.88f));
            window.setGravity(Gravity.CENTER);
        }

        private void showProcessingStage() {
            stage.removeAllViews();
            FrameLayout shell = new FrameLayout(activity);
            shell.addView(new ProcessingGfxView(activity, theme), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(22), dp(24), dp(22), dp(22));
            shell.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            TextView eyebrow = text("ONECORE EDGE  •  SECURITY INTERCEPT", 10f,
                    theme.accent, true);
            eyebrow.setGravity(Gravity.CENTER);
            eyebrow.setLetterSpacing(0.14f);
            content.addView(eyebrow, matchWrap(dp(8)));

            TextView title = text(reasonTitle(), 25f, theme.text, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title, matchWrap(dp(8)));

            TextView subtitle = text(
                    "VALIDATING RESPONSE MEDIA  •  SESSION CONTAINED",
                    9.5f, theme.muted, true);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setLetterSpacing(0.08f);
            content.addView(subtitle, matchWrap(dp(20)));

            PulseCoreView core = new PulseCoreView(activity, theme);
            LinearLayout.LayoutParams coreParams = new LinearLayout.LayoutParams(dp(176), dp(176));
            coreParams.bottomMargin = dp(18);
            content.addView(core, coreParams);

            statusText = text("PREPARING SECURE VIDEO CHANNEL", 13f, theme.text, true);
            statusText.setGravity(Gravity.CENTER);
            content.addView(statusText, matchWrap(dp(11)));

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
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            barParams.bottomMargin = dp(9);
            content.addView(progressBar, barParams);

            progressText = text("0%", 16f, theme.accent2, true);
            progressText.setGravity(Gravity.CENTER);
            content.addView(progressText, matchWrap(dp(12)));

            TextView footer = text("SIGNER  •  APK V2  •  NATIVE  •  LOCKED", 9.5f,
                    theme.success, true);
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.09f);
            content.addView(footer, matchWrap(0));
        }

        private void updateDownload(int progress, String message) {
            if (finished.get()) return;
            int safe = Math.max(0, Math.min(100, progress));
            if (progressBar != null) progressBar.setProgress(safe);
            if (progressText != null) {
                progressText.setText(String.format(Locale.US, "%d%%", safe));
            }
            if (statusText != null && message != null && !message.isEmpty()) {
                statusText.setText(message);
            }
        }

        private void showVideoStage(File videoFile) {
            if (finished.get()) return;
            if (!validMp4(videoFile)) {
                showFallback("SECURE MEDIA VALIDATION FAILED");
                return;
            }
            pendingVideo = videoFile;
            stage.removeAllViews();

            FrameLayout shell = new FrameLayout(activity);
            shell.setBackgroundColor(Color.BLACK);
            shell.setClipChildren(false);
            shell.setClipToPadding(false);
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            textureView = new TextureView(activity);
            textureView.setSurfaceTextureListener(this);
            shell.addView(textureView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            // TextureView is a normal composited view; this HUD is guaranteed to render above it.
            SecurityHudView hud = new SecurityHudView(activity, theme);
            shell.addView(hud, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            TextView top = text("ONECORE EDGE // SECURITY INTERCEPT", 10.5f,
                    theme.accent, true);
            top.setGravity(Gravity.CENTER);
            top.setLetterSpacing(0.12f);
            top.setPadding(dp(12), dp(8), dp(12), dp(8));
            top.setBackground(chipBackground(theme, true));
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            topParams.topMargin = dp(14);
            shell.addView(top, topParams);

            LinearLayout bottomPanel = new LinearLayout(activity);
            bottomPanel.setOrientation(LinearLayout.HORIZONTAL);
            bottomPanel.setGravity(Gravity.CENTER_VERTICAL);
            bottomPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
            bottomPanel.setBackground(chipBackground(theme, false));
            FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            bottomParams.leftMargin = dp(14);
            bottomParams.rightMargin = dp(14);
            bottomParams.bottomMargin = dp(14);
            shell.addView(bottomPanel, bottomParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            bottomPanel.addView(copy, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView breach = text("SIGNATURE INTEGRITY BREACH", 13f, theme.error, true);
            breach.setLetterSpacing(0.045f);
            copy.addView(breach, matchWrap(dp(3)));

            countdownFooter = text("SECURE RESPONSE PLAYING  •  EXIT ARMED", 9f,
                    theme.success, true);
            countdownFooter.setLetterSpacing(0.06f);
            copy.addView(countdownFooter, matchWrap(0));

            countdownText = text("15s", 27f, theme.error, true);
            countdownText.setGravity(Gravity.CENTER);
            countdownText.setPadding(dp(12), dp(4), dp(12), dp(4));
            bottomPanel.addView(countdownText, new LinearLayout.LayoutParams(
                    dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));

            if (textureView.isAvailable()) {
                startMedia(textureView.getSurfaceTexture());
            }
        }

        private void startMedia(SurfaceTexture surfaceTexture) {
            if (finished.get() || surfaceTexture == null || pendingVideo == null || mediaPlayer != null) {
                return;
            }
            try {
                mediaSurface = new Surface(surfaceTexture);
                MediaPlayer player = new MediaPlayer();
                mediaPlayer = player;
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.setSurface(mediaSurface);
                player.setDataSource(pendingVideo.getAbsolutePath());
                player.setLooping(true);
                player.setOnPreparedListener(prepared -> {
                    if (finished.get() || mediaPlayer != prepared) return;
                    raiseVolume();
                    try {
                        prepared.setVolume(1f, 1f);
                        prepared.start();
                        armCountdown();
                    } catch (Throwable error) {
                        FLog.error("Unable to start composited security video", error);
                        showFallback("VIDEO PLAYBACK BLOCKED");
                    }
                });
                player.setOnErrorListener((ignored, what, extra) -> {
                    MAIN.post(() -> showFallback("VIDEO PLAYBACK BLOCKED"));
                    return true;
                });
                player.prepareAsync();
            } catch (Throwable error) {
                FLog.error("Unable to prepare composited security video", error);
                showFallback("VIDEO PLAYBACK BLOCKED");
            }
        }

        private void armCountdown() {
            if (!countdownArmed.compareAndSet(false, true)) return;
            exitDeadlineElapsed = SystemClock.elapsedRealtime() + EXIT_DELAY_MS;
            tickCountdown();
            EXIT_EXECUTOR.schedule(() -> MAIN.post(this::finishNow),
                    EXIT_DELAY_MS, TimeUnit.MILLISECONDS);
        }

        private void tickCountdown() {
            if (finished.get() || !countdownArmed.get()) return;
            long remaining = exitDeadlineElapsed - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                finishNow();
                return;
            }
            int seconds = (int) Math.max(1L, (remaining + 999L) / 1000L);
            if (countdownText != null) {
                countdownText.setText(String.format(Locale.US, "%02ds", seconds));
                countdownText.animate().cancel();
                countdownText.setScaleX(0.90f);
                countdownText.setScaleY(0.90f);
                countdownText.animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
            }
            if (countdownFooter != null) {
                countdownFooter.setText("SECURE RESPONSE PLAYING  •  EXIT IN " + seconds + "s");
            }
            MAIN.postDelayed(this::tickCountdown, 200L);
        }

        private void showFallback(String message) {
            if (finished.get()) return;
            releasePlayer();
            restoreVolume();
            stage.removeAllViews();

            FrameLayout shell = new FrameLayout(activity);
            shell.addView(new ProcessingGfxView(activity, theme), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            content.setPadding(dp(24), dp(24), dp(24), dp(24));
            shell.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            PulseCoreView core = new PulseCoreView(activity, theme);
            content.addView(core, new LinearLayout.LayoutParams(dp(160), dp(160)));

            TextView title = text(reasonTitle(), 23f, theme.error, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title, matchWrap(dp(8)));

            TextView body = text(message + "\nSECURE SESSION TERMINATION ARMED", 11.5f,
                    theme.text, true);
            body.setGravity(Gravity.CENTER);
            body.setLineSpacing(0f, 1.18f);
            content.addView(body, matchWrap(dp(8)));

            if (!detail.isEmpty()) {
                TextView detailText = text(detail, 9f, theme.muted, false);
                detailText.setGravity(Gravity.CENTER);
                content.addView(detailText, matchWrap(dp(12)));
            }

            countdownText = text("15s", 30f, theme.error, true);
            countdownText.setGravity(Gravity.CENTER);
            content.addView(countdownText, matchWrap(0));
            countdownFooter = body;
            armCountdown();
        }

        private void raiseVolume() {
            if (volumeRaised) return;
            try {
                audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager == null) return;
                previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (max > 0 && previousMusicVolume < max) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                }
                volumeRaised = true;
            } catch (Throwable error) {
                FLog.error("Unable to raise cinematic volume", error);
            }
        }

        private void restoreVolume() {
            if (!volumeRaised || audioManager == null || previousMusicVolume < 0) return;
            try {
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        Math.max(0, Math.min(max, previousMusicVolume)), 0);
            } catch (Throwable ignored) {
            } finally {
                volumeRaised = false;
            }
        }

        private void releasePlayer() {
            MediaPlayer player = mediaPlayer;
            mediaPlayer = null;
            if (player != null) {
                try {
                    player.setSurface(null);
                } catch (Throwable ignored) {
                }
                try {
                    player.stop();
                } catch (Throwable ignored) {
                }
                try {
                    player.release();
                } catch (Throwable ignored) {
                }
            }
            Surface surface = mediaSurface;
            mediaSurface = null;
            if (surface != null) {
                try {
                    surface.release();
                } catch (Throwable ignored) {
                }
            }
        }

        private void finishNow() {
            if (!finished.compareAndSet(false, true)) return;
            releasePlayer();
            restoreVolume();
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            } catch (Throwable ignored) {
            }
            if (onFinished != null) onFinished.run();
        }

        private String reasonTitle() {
            if (reason == SecurityIncidentDispatcher.Reason.SIGNATURE) {
                return "SIGNATURE INTEGRITY BREACH";
            }
            if (reason == SecurityIncidentDispatcher.Reason.DEBUGGER) {
                return "DEBUGGER / TRACER DETECTED";
            }
            return "RUNTIME INTEGRITY BREACH";
        }

        private TextView text(String value, float sizeSp, int color, boolean bold) {
            TextView view = new TextView(activity);
            view.setText(value);
            view.setTextSize(sizeSp);
            view.setTextColor(color);
            view.setTypeface(Typeface.create(theme.headingFont,
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

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startMedia(surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releasePlayer();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    }

    private static File downloadVideo(Context context, ProgressCallback callback) throws IOException {
        File target = new File(context.getCacheDir(), CACHE_FILE);
        if (validMp4(target)) {
            callback.onProgress(100, "SECURE MEDIA READY");
            return target;
        }
        if (target.exists()) target.delete();
        File partial = new File(context.getCacheDir(), CACHE_FILE + ".part");
        if (partial.exists()) partial.delete();

        HttpURLConnection connection = null;
        long written = 0L;
        try {
            connection = (HttpURLConnection) new URL(VIDEO_URL).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(45_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "video/mp4,application/octet-stream,*/*");
            connection.setRequestProperty("User-Agent", "OneCore-Edge-Security/3.0");
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Unexpected media response " + code);
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_VIDEO_BYTES) throw new IOException("Security media exceeds size limit");

            callback.onProgress(1, "DOWNLOADING SECURE RESPONSE MEDIA");
            try (InputStream input = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(partial), 64 * 1024)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                int last = -1;
                while ((read = input.read(buffer)) != -1) {
                    if (read <= 0) continue;
                    written += read;
                    if (written > MAX_VIDEO_BYTES) {
                        throw new IOException("Security media exceeds size limit");
                    }
                    output.write(buffer, 0, read);
                    int progress = total > 0
                            ? (int) Math.min(99L, written * 100L / total)
                            : (int) Math.min(95L, 5L + written / (512L * 1024L));
                    if (progress != last) {
                        last = progress;
                        callback.onProgress(progress, "DOWNLOADING SECURE RESPONSE MEDIA");
                    }
                }
                output.flush();
            }

            if (!validMp4(partial)) throw new IOException("Downloaded media is not valid MP4");
            if (target.exists() && !target.delete()) {
                throw new IOException("Unable to replace cached media");
            }
            if (!partial.renameTo(target)) {
                copyFile(partial, target);
                partial.delete();
            }
            if (!validMp4(target)) throw new IOException("Cached media validation failed");
            callback.onProgress(100, "SECURE MEDIA VERIFIED");
            return target;
        } finally {
            if (connection != null) connection.disconnect();
            if (partial.exists() && !validMp4(partial)) partial.delete();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
    }

    private static boolean validMp4(File file) {
        if (file == null || !file.isFile()) return false;
        long length = file.length();
        if (length < MIN_VIDEO_BYTES || length > MAX_VIDEO_BYTES) return false;
        byte[] header = new byte[64];
        try (FileInputStream input = new FileInputStream(file)) {
            int count = input.read(header);
            if (count < 12) return false;
            for (int i = 0; i <= count - 4; i++) {
                if (header[i] == 'f' && header[i + 1] == 't'
                        && header[i + 2] == 'y' && header[i + 3] == 'p') {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static GradientDrawable panelBackground(ThemeManager.ThemeSpec theme) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{theme.surfaceAlt, theme.bgBottom});
        drawable.setCornerRadius(theme.cardRadiusDp * 2.2f);
        drawable.setStroke(2, ThemeManager.withAlpha(theme.accent, 220));
        return drawable;
    }

    private static GradientDrawable chipBackground(ThemeManager.ThemeSpec theme, boolean top) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ThemeManager.withAlpha(top ? theme.surfaceAlt : theme.surface, 235),
                        ThemeManager.withAlpha(theme.bgBottom, 238)});
        drawable.setCornerRadius(theme.buttonRadiusDp * 2.1f);
        drawable.setStroke(1, ThemeManager.withAlpha(top ? theme.accent : theme.error, 220));
        return drawable;
    }

    private static final class ProcessingGfxView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        ProcessingGfxView(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 34));
            float step = Math.max(30f, Math.min(w, h) * 0.08f);
            float shift = phase * step;
            for (float x = -step + shift; x < w + step; x += step) {
                canvas.drawLine(x, 0f, x, h, paint);
            }
            for (float y = -step + shift; y < h + step; y += step) {
                canvas.drawLine(0f, y, w, y, paint);
            }
            paint.setStrokeWidth(2f);
            paint.setColor(ThemeManager.withAlpha(theme.accent2, 120));
            float scan = h * phase;
            canvas.drawLine(0f, scan, w, scan, paint);
            phase += 0.007f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    private static final class PulseCoreView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        PulseCoreView(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float min = Math.min(w, h);
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 5; i++) {
                float r = min * (0.16f + i * 0.065f);
                paint.setStrokeWidth(i == 0 ? 3f : 1.5f);
                paint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2,
                        70 + i * 28));
                RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(oval, phase * 360f + i * 72f, 135f - i * 12f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 48));
            canvas.drawCircle(cx, cy, min * 0.12f, paint);
            paint.setColor(theme.text);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(min * 0.105f);
            canvas.drawText("OE", cx, cy + paint.getTextSize() * 0.34f, paint);
            phase += 0.012f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    /** High-contrast transparent HUD drawn above TextureView video. */
    private static final class SecurityHudView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        SecurityHudView(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setClickable(false);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            float min = Math.min(w, h);
            float inset = min * 0.028f;
            float edge = min * 0.15f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(74, 0, 0, 0));
            canvas.drawRect(0f, 0f, w, h * 0.08f, paint);
            canvas.drawRect(0f, h * 0.91f, w, h, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, min * 0.006f));
            paint.setColor(ThemeManager.withAlpha(theme.accent, 225));
            drawCorner(canvas, inset, inset, edge, true, true);
            drawCorner(canvas, w - inset, inset, edge, false, true);
            drawCorner(canvas, inset, h - inset, edge, true, false);
            drawCorner(canvas, w - inset, h - inset, edge, false, false);

            paint.setStrokeWidth(1.5f);
            paint.setColor(ThemeManager.withAlpha(theme.accent2, 160));
            RectF inner = new RectF(inset + 10f, inset + 10f,
                    w - inset - 10f, h - inset - 10f);
            canvas.drawRect(inner, paint);

            float y1 = inset + (h - inset * 2f) * phase;
            float y2 = h - inset - (h - inset * 2f) * ((phase * 0.67f + 0.21f) % 1f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 42));
            canvas.drawRect(inset, y1 - 12f, w - inset, y1 + 12f, paint);
            paint.setColor(ThemeManager.withAlpha(theme.error, 30));
            canvas.drawRect(inset, y2 - 7f, w - inset, y2 + 7f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 235));
            canvas.drawLine(inset, y1, w - inset, y1, paint);
            paint.setStrokeWidth(1.6f);
            paint.setColor(ThemeManager.withAlpha(theme.error, 210));
            canvas.drawLine(inset, y2, w - inset, y2, paint);

            drawRadar(canvas, w - inset - edge * 0.55f,
                    inset + edge * 0.55f, edge * 0.32f, pulse, true);
            drawRadar(canvas, inset + edge * 0.52f,
                    h - inset - edge * 0.52f, edge * 0.25f, pulse, false);
            drawCenterTarget(canvas, w * 0.5f, h * 0.50f, min * 0.082f, pulse);
            drawNodes(canvas, w, h, inset, pulse);
            drawTelemetry(canvas, w, h, inset, min, pulse);

            phase += 0.009f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }

        private void drawCorner(Canvas canvas, float x, float y, float len,
                boolean left, boolean top) {
            float sx = left ? 1f : -1f;
            float sy = top ? 1f : -1f;
            path.reset();
            path.moveTo(x, y + sy * len);
            path.lineTo(x, y);
            path.lineTo(x + sx * len, y);
            canvas.drawPath(path, paint);
        }

        private void drawRadar(Canvas canvas, float cx, float cy, float r,
                float pulse, boolean clockwise) {
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 1; i <= 3; i++) {
                paint.setStrokeWidth(i == 3 ? 2f : 1f);
                paint.setColor(ThemeManager.withAlpha(theme.accent,
                        70 + i * 36));
                canvas.drawCircle(cx, cy, r * i / 3f, paint);
            }
            float angle = (clockwise ? 1f : -1f) * phase * 360f;
            float ex = cx + (float) Math.cos(Math.toRadians(angle)) * r;
            float ey = cy + (float) Math.sin(Math.toRadians(angle)) * r;
            paint.setStrokeWidth(2.5f);
            paint.setColor(ThemeManager.withAlpha(theme.accent2, 230));
            canvas.drawLine(cx, cy, ex, ey, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeManager.withAlpha(theme.accent2, 130 + (int) (80f * pulse)));
            canvas.drawCircle(ex, ey, 4f + 2f * pulse, paint);
        }

        private void drawCenterTarget(Canvas canvas, float cx, float cy, float r, float pulse) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(ThemeManager.withAlpha(theme.error, 190));
            canvas.drawCircle(cx, cy, r * (0.82f + 0.08f * pulse), paint);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 210));
            canvas.drawCircle(cx, cy, r * 0.48f, paint);
            float gap = r * 0.22f;
            canvas.drawLine(cx - r, cy, cx - gap, cy, paint);
            canvas.drawLine(cx + gap, cy, cx + r, cy, paint);
            canvas.drawLine(cx, cy - r, cx, cy - gap, paint);
            canvas.drawLine(cx, cy + gap, cx, cy + r, paint);
        }

        private void drawNodes(Canvas canvas, float w, float h, float inset, float pulse) {
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 13; i++) {
                float x = inset + ((i * 71f + phase * 150f * (i % 2 == 0 ? 1f : -1f))
                        % Math.max(1f, w - inset * 2f));
                if (x < inset) x += w - inset * 2f;
                float y = h * (0.12f + ((i * 37) % 74) / 100f);
                int color = i % 3 == 0 ? theme.error : (i % 2 == 0 ? theme.accent : theme.accent2);
                paint.setColor(ThemeManager.withAlpha(color, 110 + (i * 9) % 110));
                canvas.drawCircle(x, y, 2.4f + (i % 3) + pulse, paint);
            }
        }

        private void drawTelemetry(Canvas canvas, float w, float h, float inset,
                float min, float pulse) {
            paint.setStyle(Paint.Style.FILL);
            float baseY = inset + min * 0.055f;
            float startX = inset + min * 0.02f;
            for (int i = 0; i < 10; i++) {
                float bar = min * (0.008f + 0.018f *
                        (0.5f + 0.5f * (float) Math.sin(phase * 15f + i)));
                paint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.error,
                        150 + (int) (70f * pulse)));
                canvas.drawRect(startX + i * min * 0.017f, baseY - bar,
                        startX + i * min * 0.017f + min * 0.008f, baseY, paint);
            }

            paint.setColor(ThemeManager.withAlpha(theme.accent, 230));
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setTextSize(Math.max(10f, min * 0.026f));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("LIVE / SIGNER_SCAN", w - inset - 8f,
                    h - inset - min * 0.04f, paint);
        }
    }
}
