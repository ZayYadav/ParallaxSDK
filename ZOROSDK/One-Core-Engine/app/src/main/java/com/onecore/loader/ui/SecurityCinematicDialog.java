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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.onecore.loader.security.SecurityIncidentDispatcher;
import com.onecore.loader.utils.FLog;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-cancelable themed security response surface used for confirmed signature incidents.
 *
 * <p>The first stage displays a themed processing HUD while the cinematic is downloaded into the
 * private cache. Once validated, the same surface transitions into video playback with a strong,
 * transparent real-time HUD layer so the supplied video remains visible underneath the effects.</p>
 */
public final class SecurityCinematicDialog {
    private static final String VIDEO_URL =
            "https://github.com/sk8787914-maker/Zoro-online-mod/releases/download/JANGAM/VID_20260822_235111_206.mp4";
    private static final String CACHE_FILE = "onecore_security_response_v2.mp4";
    private static final long MIN_VIDEO_BYTES = 64L * 1024L;
    private static final long MAX_VIDEO_BYTES = 160L * 1024L * 1024L;
    private static final long HARD_WATCHDOG_MS = 125_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOAD_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneCore-SecurityMedia");
                thread.setDaemon(true);
                return thread;
            });

    private SecurityCinematicDialog() {
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

        ThemeManager.ThemeSpec theme = ThemeManager.current(activity);
        new Session(activity, theme, reason, detail, onFinished).start();
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private interface ProgressCallback {
        void onProgress(int percent, String message);
    }

    private static final class Session {
        private final Activity activity;
        private final ThemeManager.ThemeSpec theme;
        private final SecurityIncidentDispatcher.Reason reason;
        private final String detail;
        private final Runnable onFinished;
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private Dialog dialog;
        private FrameLayout stage;
        private ProgressBar progressBar;
        private TextView progressText;
        private TextView statusText;
        private VideoView videoView;
        private SecurityVideoHud hud;
        private AudioManager audioManager;
        private int previousMusicVolume = -1;
        private boolean volumeRaised;

        Session(
                Activity activity,
                ThemeManager.ThemeSpec theme,
                SecurityIncidentDispatcher.Reason reason,
                String detail,
                Runnable onFinished) {
            this.activity = activity;
            this.theme = theme;
            this.reason = reason == null ? SecurityIncidentDispatcher.Reason.SIGNATURE : reason;
            this.detail = detail == null ? "" : detail;
            this.onFinished = onFinished;
        }

        void start() {
            buildDialog();
            showProcessingStage();
            try {
                dialog.show();
                configureWindow();
            } catch (Throwable error) {
                FLog.error("Security cinematic dialog could not be shown", error);
                finishNow();
                return;
            }

            MAIN.postDelayed(this::finishNow, HARD_WATCHDOG_MS);
            DOWNLOAD_EXECUTOR.execute(() -> {
                try {
                    File video = downloadVideo(activity, (percent, message) ->
                            MAIN.post(() -> updateDownloadProgress(percent, message)));
                    MAIN.post(() -> showVideoStage(video));
                } catch (Throwable error) {
                    FLog.error("Security cinematic download failed", error);
                    MAIN.post(() -> showFallbackStage("SECURE MEDIA UNAVAILABLE"));
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
            stage.setPadding(dp(10), dp(10), dp(10), dp(10));
            stage.setBackground(panelBackground(theme));
            dialog.setContentView(stage);
        }

        private void configureWindow() {
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.92f;
            window.setAttributes(attributes);

            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            window.setLayout((int) (width * 0.96f), (int) (height * 0.86f));
            window.setGravity(Gravity.CENTER);
        }

        private void showProcessingStage() {
            stage.removeAllViews();

            FrameLayout shell = new FrameLayout(activity);
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            SecurityProcessingBackground processingBackground =
                    new SecurityProcessingBackground(activity, theme);
            shell.addView(processingBackground, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            content.setPadding(dp(20), dp(24), dp(20), dp(20));
            shell.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            TextView eyebrow = text("ONECORE EDGE // SIGNATURE RESPONSE", 11f, theme.accent, true);
            eyebrow.setLetterSpacing(0.16f);
            content.addView(eyebrow, lpMatchWrap(dp(5)));

            TextView title = text(reasonTitle(), 25f, theme.text, true);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
            content.addView(title, lpMatchWrap(dp(8)));

            TextView subtitle = text(
                    "HOST SIGNER REJECTED • SECURITY CINEMATIC PREPARING",
                    10f,
                    theme.muted,
                    true);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setLetterSpacing(0.075f);
            content.addView(subtitle, lpMatchWrap(dp(20)));

            SecurityPulseView pulse = new SecurityPulseView(activity, theme);
            LinearLayout.LayoutParams pulseParams = new LinearLayout.LayoutParams(dp(170), dp(170));
            pulseParams.bottomMargin = dp(20);
            content.addView(pulse, pulseParams);

            statusText = text("PREPARING SECURE RESPONSE MEDIA", 13f, theme.text, true);
            statusText.setGravity(Gravity.CENTER);
            content.addView(statusText, lpMatchWrap(dp(10)));

            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(theme.accent));
                progressBar.setProgressBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(withAlpha(theme.muted, 45)));
            }
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            progressParams.bottomMargin = dp(10);
            content.addView(progressBar, progressParams);

            progressText = text("0%", 14f, theme.accent2, true);
            progressText.setGravity(Gravity.CENTER);
            content.addView(progressText, lpMatchWrap(dp(13)));

            TextView chips = text(
                    "SIGNER • APK V2 • NATIVE • SESSION LOCKED",
                    10f,
                    theme.success,
                    true);
            chips.setGravity(Gravity.CENTER);
            chips.setLetterSpacing(0.09f);
            content.addView(chips, lpMatchWrap(0));
        }

        private void updateDownloadProgress(int percent, String message) {
            if (finished.get() || progressBar == null) return;
            int safe = Math.max(0, Math.min(100, percent));
            progressBar.setProgress(safe);
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
                showFallbackStage("SECURE MEDIA VALIDATION FAILED");
                return;
            }

            stage.removeAllViews();

            FrameLayout videoShell = new FrameLayout(activity);
            videoShell.setBackgroundColor(Color.BLACK);
            videoShell.setClipChildren(false);
            videoShell.setClipToPadding(false);
            stage.addView(videoShell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            videoView = new VideoView(activity);
            videoView.setBackgroundColor(Color.BLACK);
            videoShell.addView(videoView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));

            // This view is intentionally added after VideoView so all GFX remain visible over media.
            hud = new SecurityVideoHud(activity, theme);
            videoShell.addView(hud, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            TextView topBadge = text("ONECORE EDGE // SECURITY INTERCEPT", 11f, theme.accent, true);
            topBadge.setLetterSpacing(0.14f);
            topBadge.setPadding(dp(13), dp(8), dp(13), dp(8));
            topBadge.setBackground(labelBackground(theme, true));
            topBadge.setElevation(dp(8));
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            topParams.topMargin = dp(16);
            videoShell.addView(topBadge, topParams);

            TextView reasonBadge = text("SIGNATURE BREACH // UNTRUSTED BUILD", 12f, Color.WHITE, true);
            reasonBadge.setLetterSpacing(0.08f);
            reasonBadge.setPadding(dp(13), dp(8), dp(13), dp(8));
            reasonBadge.setBackground(labelBackground(theme, false));
            reasonBadge.setElevation(dp(8));
            FrameLayout.LayoutParams reasonParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            reasonParams.bottomMargin = dp(52);
            videoShell.addView(reasonBadge, reasonParams);

            TextView footer = text(
                    "NATIVE ATTESTATION • SIGNER MISMATCH • TERMINATION ARMED",
                    9f,
                    theme.success,
                    true);
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.075f);
            footer.setShadowLayer(6f, 0f, 0f, Color.BLACK);
            FrameLayout.LayoutParams footerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            footerParams.leftMargin = dp(12);
            footerParams.rightMargin = dp(12);
            footerParams.bottomMargin = dp(16);
            videoShell.addView(footer, footerParams);

            try {
                videoView.setVideoPath(videoFile.getAbsolutePath());
                videoView.setOnPreparedListener(player -> onVideoPrepared(player, footer));
                videoView.setOnCompletionListener(player -> {
                    footer.setText("SECURITY RESPONSE COMPLETE • CLOSING SESSION");
                    MAIN.postDelayed(this::finishNow, 900L);
                });
                videoView.setOnErrorListener((player, what, extra) -> {
                    showFallbackStage("VIDEO PLAYBACK BLOCKED");
                    return true;
                });
                videoView.requestFocus();
                videoView.start();
            } catch (Throwable error) {
                FLog.error("Security cinematic playback failed", error);
                showFallbackStage("VIDEO PLAYBACK BLOCKED");
            }
        }

        private void onVideoPrepared(MediaPlayer player, TextView footer) {
            if (finished.get()) return;
            raiseVolume();
            try {
                player.setVolume(1.0f, 1.0f);
                player.setLooping(false);
                if (!player.isPlaying()) player.start();
                footer.setText("AUDIO CHANNEL MAX • CINEMATIC + LIVE HUD ACTIVE");
            } catch (Throwable error) {
                FLog.error("Unable to start security cinematic media", error);
                showFallbackStage("VIDEO PLAYBACK BLOCKED");
            }
        }

        private void showFallbackStage(String message) {
            if (finished.get()) return;
            restoreVolume();
            try {
                if (videoView != null) {
                    videoView.stopPlayback();
                    videoView = null;
                }
            } catch (Throwable ignored) {
            }

            stage.removeAllViews();
            FrameLayout shell = new FrameLayout(activity);
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            shell.addView(new SecurityProcessingBackground(activity, theme),
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            content.setPadding(dp(24), dp(24), dp(24), dp(24));
            shell.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            SecurityPulseView pulse = new SecurityPulseView(activity, theme);
            content.addView(pulse, new LinearLayout.LayoutParams(dp(154), dp(154)));

            TextView title = text(reasonTitle(), 24f, theme.error, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title, lpMatchWrap(dp(10)));

            TextView body = text(message + "\nSESSION WILL CLOSE SECURELY", 12f, theme.text, true);
            body.setGravity(Gravity.CENTER);
            body.setLineSpacing(0f, 1.18f);
            content.addView(body, lpMatchWrap(dp(12)));

            if (!detail.isEmpty()) {
                TextView detailView = text(detail, 9f, theme.muted, true);
                detailView.setGravity(Gravity.CENTER);
                content.addView(detailView, lpMatchWrap(0));
            }
            MAIN.postDelayed(this::finishNow, 3200L);
        }

        private void raiseVolume() {
            if (volumeRaised) return;
            try {
                audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager == null) return;
                previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                if (maximum > 0 && previousMusicVolume < maximum) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maximum, 0);
                }
                volumeRaised = true;
            } catch (Throwable error) {
                FLog.error("Unable to raise security cinematic media volume", error);
            }
        }

        private void restoreVolume() {
            if (!volumeRaised || audioManager == null || previousMusicVolume < 0) return;
            try {
                int maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int safe = Math.max(0, Math.min(maximum, previousMusicVolume));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safe, 0);
            } catch (Throwable ignored) {
            } finally {
                volumeRaised = false;
            }
        }

        private void finishNow() {
            if (!finished.compareAndSet(false, true)) return;
            restoreVolume();
            try {
                if (videoView != null) videoView.stopPlayback();
            } catch (Throwable ignored) {
            }
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
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setTypeface(Typeface.create(
                    theme.headingFont,
                    bold ? Typeface.BOLD : Typeface.NORMAL));
            return view;
        }

        private LinearLayout.LayoutParams lpMatchWrap(int bottomMargin) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = bottomMargin;
            return params;
        }

        private int dp(float value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
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
            URL url = new URL(VIDEO_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(45_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "video/mp4,application/octet-stream,*/*");
            connection.setRequestProperty("User-Agent", "OneCore-Edge-Security/2.0");
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Unexpected media response code " + code);
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_VIDEO_BYTES) {
                throw new IOException("Security media exceeds size limit");
            }

            callback.onProgress(1, "DOWNLOADING SECURE RESPONSE MEDIA");
            try (InputStream input = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(partial), 64 * 1024)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                int lastPercent = -1;
                while ((read = input.read(buffer)) != -1) {
                    if (read <= 0) continue;
                    written += read;
                    if (written > MAX_VIDEO_BYTES) {
                        throw new IOException("Security media exceeds size limit");
                    }
                    output.write(buffer, 0, read);
                    int percent;
                    if (total > 0L) {
                        percent = (int) Math.min(99L, (written * 100L) / total);
                    } else {
                        percent = (int) Math.min(95L, 5L + (written / (512L * 1024L)));
                    }
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        callback.onProgress(percent, "DOWNLOADING SECURE RESPONSE MEDIA");
                    }
                }
                output.flush();
            }

            if (!validMp4(partial)) {
                throw new IOException("Downloaded security media is not a valid MP4");
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Unable to replace cached security media");
            }
            if (!partial.renameTo(target)) {
                copyFile(partial, target);
                partial.delete();
            }
            if (!validMp4(target)) {
                target.delete();
                throw new IOException("Cached security media validation failed");
            }
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
            for (int index = 0; index <= count - 4; index++) {
                if (header[index] == 'f'
                        && header[index + 1] == 't'
                        && header[index + 2] == 'y'
                        && header[index + 3] == 'p') {
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
                new int[]{theme.surface, theme.bgBottom});
        drawable.setCornerRadius(theme.cardRadiusDp * 2f);
        drawable.setStroke(2, withAlpha(theme.accent, 190));
        return drawable;
    }

    private static GradientDrawable labelBackground(ThemeManager.ThemeSpec theme, boolean accent) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(withAlpha(accent ? theme.surfaceAlt : theme.error, accent ? 225 : 205));
        drawable.setCornerRadius(theme.buttonRadiusDp * 2f);
        drawable.setStroke(1, withAlpha(accent ? theme.accent : theme.error, 235));
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static final class SecurityProcessingBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        SecurityProcessingBackground(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(withAlpha(theme.accent, 32));
            float step = Math.max(28f, Math.min(w, h) * 0.085f);
            float offset = (phase * step * 2f) % step;
            for (float x = -step + offset; x < w + step; x += step) {
                canvas.drawLine(x, 0f, x, h, paint);
            }
            for (float y = -step + offset; y < h + step; y += step) {
                canvas.drawLine(0f, y, w, y, paint);
            }

            float scan = phase * h;
            paint.setColor(withAlpha(theme.accent2, 85));
            paint.setStrokeWidth(Math.max(1.5f, h * 0.002f));
            canvas.drawLine(0f, scan, w, scan, paint);

            phase += 0.0065f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    private static final class SecurityPulseView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        SecurityPulseView(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float cx = width / 2f;
            float cy = height / 2f;
            float min = Math.min(width, height);

            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, min * 0.016f));
            paint.setColor(withAlpha(theme.accent, 70));
            for (int i = 0; i < 4; i++) {
                float radius = min * (0.17f + i * 0.075f);
                canvas.drawCircle(cx, cy, radius, paint);
            }

            paint.setShadowLayer(min * 0.045f, 0f, 0f, withAlpha(theme.accent, 200));
            paint.setStrokeWidth(Math.max(3f, min * 0.026f));
            RectF ring = new RectF(cx - min * 0.35f, cy - min * 0.35f,
                    cx + min * 0.35f, cy + min * 0.35f);
            for (int i = 0; i < 6; i++) {
                paint.setColor(i % 2 == 0 ? theme.accent : theme.accent2);
                float start = phase * 360f + i * 60f;
                canvas.drawArc(ring, start, 30f, false, paint);
            }

            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(theme.accent, 42));
            canvas.drawCircle(cx, cy,
                    min * (0.13f + 0.018f * (float) Math.sin(phase * Math.PI * 8f)), paint);
            paint.setColor(theme.accent);
            canvas.drawCircle(cx, cy, min * 0.055f, paint);

            paint.setColor(theme.text);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(min * 0.11f);
            canvas.drawText("OE", cx, cy + paint.getTextSize() * 0.34f, paint);

            phase += 0.0125f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    /** Strong transparent HUD rendered above the video surface. */
    private static final class SecurityVideoHud extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        SecurityVideoHud(Context context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float h = getHeight();
            if (w <= 0f || h <= 0f) return;

            final float min = Math.min(w, h);
            final float inset = min * 0.035f;
            final float edge = min * 0.14f;
            final float inner = inset + min * 0.032f;
            final float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2f);

            drawVignetteBands(canvas, w, h, min);
            drawOuterFrame(canvas, w, h, inset, edge, pulse);
            drawInnerFrame(canvas, w, h, inner, pulse);
            drawScanBeams(canvas, w, h, inset, pulse);
            drawTicks(canvas, w, h, inner);
            drawRadar(canvas, w - inset - edge * 0.58f, inset + edge * 0.58f, edge * 0.34f, true);
            drawRadar(canvas, inset + edge * 0.54f, h - inset - edge * 0.54f, edge * 0.27f, false);
            drawCenterTarget(canvas, w * 0.5f, h * 0.52f, min * 0.085f, pulse);
            drawCircuitNodes(canvas, w, h, inset, pulse);
            drawTelemetry(canvas, w, h, inset, min, pulse);
            drawHudText(canvas, w, h, inset, min, pulse);

            phase += 0.0075f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }

        private void drawVignetteBands(Canvas canvas, float w, float h, float min) {
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(92, 0, 0, 0));
            canvas.drawRect(0f, 0f, w, Math.max(min * 0.10f, h * 0.08f), paint);
            canvas.drawRect(0f, h - Math.max(min * 0.11f, h * 0.09f), w, h, paint);

            paint.setColor(withAlpha(theme.accent, 22));
            canvas.drawRect(0f, 0f, w, min * 0.016f, paint);
            paint.setColor(withAlpha(theme.accent2, 22));
            canvas.drawRect(0f, h - min * 0.016f, w, h, paint);
        }

        private void drawOuterFrame(
                Canvas canvas, float w, float h, float inset, float edge, float pulse) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2.5f, Math.min(w, h) * 0.005f));
            paint.setColor(withAlpha(theme.accent, 205 + (int) (45f * pulse)));
            paint.setShadowLayer(Math.min(w, h) * 0.028f, 0f, 0f, withAlpha(theme.accent, 190));

            drawCorner(canvas, inset, inset, edge, true, true);
            drawCorner(canvas, w - inset, inset, edge, false, true);
            drawCorner(canvas, inset, h - inset, edge, true, false);
            drawCorner(canvas, w - inset, h - inset, edge, false, false);

            paint.clearShadowLayer();
            paint.setStrokeWidth(Math.max(1.2f, Math.min(w, h) * 0.0022f));
            paint.setColor(withAlpha(theme.accent2, 125));
            float shortEdge = edge * 0.48f;
            drawCorner(canvas, inset + 7f, inset + 7f, shortEdge, true, true);
            drawCorner(canvas, w - inset - 7f, inset + 7f, shortEdge, false, true);
            drawCorner(canvas, inset + 7f, h - inset - 7f, shortEdge, true, false);
            drawCorner(canvas, w - inset - 7f, h - inset - 7f, shortEdge, false, false);
        }

        private void drawInnerFrame(Canvas canvas, float w, float h, float inner, float pulse) {
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(withAlpha(theme.accent, 58 + (int) (28f * pulse)));
            RectF rect = new RectF(inner, inner, w - inner, h - inner);
            canvas.drawRect(rect, paint);
        }

        private void drawScanBeams(
                Canvas canvas, float w, float h, float inset, float pulse) {
            float y1 = inset + (h - inset * 2f) * phase;
            float y2 = h - inset - (h - inset * 2f) * ((phase * 0.73f + 0.16f) % 1f);

            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(theme.accent, 22 + (int) (20f * pulse)));
            canvas.drawRect(inset, y1 - 10f, w - inset, y1 + 10f, paint);
            paint.setColor(withAlpha(theme.accent2, 16 + (int) (18f * pulse)));
            canvas.drawRect(inset, y2 - 6f, w - inset, y2 + 6f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            paint.setColor(withAlpha(theme.accent, 190));
            paint.setShadowLayer(10f, 0f, 0f, withAlpha(theme.accent, 160));
            canvas.drawLine(inset, y1, w - inset, y1, paint);
            paint.setStrokeWidth(1.2f);
            paint.setColor(withAlpha(theme.accent2, 130));
            canvas.drawLine(inset, y2, w - inset, y2, paint);
            paint.clearShadowLayer();
        }

        private void drawTicks(Canvas canvas, float w, float h, float inner) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(withAlpha(theme.accent, 110));
            final int ticks = 18;
            float available = w - inner * 2f;
            for (int i = 0; i <= ticks; i++) {
                float x = inner + available * i / ticks;
                float len = (i % 3 == 0) ? 10f : 5f;
                canvas.drawLine(x, inner, x, inner + len, paint);
                canvas.drawLine(x, h - inner, x, h - inner - len, paint);
            }
        }

        private void drawRadar(Canvas canvas, float cx, float cy, float radius, boolean clockwise) {
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(withAlpha(theme.success, 150));
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.drawCircle(cx, cy, radius * 0.62f, paint);
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);

            float angle = (clockwise ? 1f : -1f) * phase * 360f;
            paint.setStrokeWidth(2.4f);
            paint.setColor(withAlpha(theme.success, 230));
            RectF ring = new RectF(cx - radius * 1.18f, cy - radius * 1.18f,
                    cx + radius * 1.18f, cy + radius * 1.18f);
            canvas.drawArc(ring, angle, 76f, false, paint);
        }

        private void drawCenterTarget(
                Canvas canvas, float cx, float cy, float radius, float pulse) {
            float dynamic = radius * (0.92f + pulse * 0.13f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(withAlpha(theme.accent2, 118));
            canvas.drawCircle(cx, cy, dynamic, paint);
            canvas.drawCircle(cx, cy, dynamic * 0.58f, paint);

            paint.setStrokeWidth(2f);
            paint.setColor(withAlpha(theme.accent, 155));
            float arm = dynamic * 1.45f;
            float gap = dynamic * 0.70f;
            canvas.drawLine(cx - arm, cy, cx - gap, cy, paint);
            canvas.drawLine(cx + gap, cy, cx + arm, cy, paint);
            canvas.drawLine(cx, cy - arm, cx, cy - gap, paint);
            canvas.drawLine(cx, cy + gap, cx, cy + arm, paint);

            canvas.save();
            canvas.rotate(phase * 180f, cx, cy);
            RectF arc = new RectF(cx - dynamic * 1.2f, cy - dynamic * 1.2f,
                    cx + dynamic * 1.2f, cy + dynamic * 1.2f);
            canvas.drawArc(arc, 8f, 42f, false, paint);
            canvas.drawArc(arc, 188f, 42f, false, paint);
            canvas.restore();
        }

        private void drawCircuitNodes(
                Canvas canvas, float w, float h, float inset, float pulse) {
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(withAlpha(theme.accent, 92));

            for (int i = 0; i < 8; i++) {
                float t = (phase + i * 0.137f) % 1f;
                float x1 = inset + (w - inset * 2f) * t;
                float y1 = inset + (h - inset * 2f) * ((t * 1.71f + i * 0.091f) % 1f);
                float x2 = inset + (w - inset * 2f) * ((t + 0.08f) % 1f);
                float y2 = inset + (h - inset * 2f) * ((t * 1.27f + i * 0.163f) % 1f);
                canvas.drawLine(x1, y1, x2, y2, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(i % 2 == 0 ? theme.accent : theme.accent2,
                        185 + (int) (55f * pulse)));
                float r = 2.5f + (i % 3) * 0.8f;
                canvas.drawCircle(x1, y1, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(withAlpha(theme.accent, 92));
            }
        }

        private void drawTelemetry(
                Canvas canvas, float w, float h, float inset, float min, float pulse) {
            float barWidth = min * 0.18f;
            float x = inset + min * 0.025f;
            float y = h - inset - min * 0.16f;
            float barH = Math.max(3f, min * 0.008f);

            for (int i = 0; i < 5; i++) {
                float value = 0.26f + 0.68f * Math.abs((float) Math.sin(
                        phase * Math.PI * 2f + i * 0.82f));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(theme.muted, 60));
                canvas.drawRect(x, y + i * (barH + 5f), x + barWidth, y + i * (barH + 5f) + barH, paint);
                paint.setColor(withAlpha(i % 2 == 0 ? theme.accent : theme.accent2,
                        155 + (int) (65f * pulse)));
                canvas.drawRect(x, y + i * (barH + 5f),
                        x + barWidth * value, y + i * (barH + 5f) + barH, paint);
            }
        }

        private void drawHudText(
                Canvas canvas, float w, float h, float inset, float min, float pulse) {
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setTextSize(Math.max(10f, min * 0.026f));
            paint.setColor(withAlpha(theme.accent, 215));
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
            canvas.drawText("OE // SIGNATURE BREACH", inset + 8f, inset + min * 0.055f, paint);

            paint.setTextSize(Math.max(8f, min * 0.020f));
            paint.setColor(withAlpha(theme.success, 205));
            canvas.drawText("NATIVE: SEALED", inset + 8f, inset + min * 0.090f, paint);
            canvas.drawText("APK V2: REJECTED", inset + 8f, inset + min * 0.122f, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(withAlpha(theme.accent2, 195 + (int) (45f * pulse)));
            String sweep = String.format(Locale.US, "SCAN %03d", (int) (phase * 999f));
            canvas.drawText(sweep, w - inset - 8f, h - inset - min * 0.035f, paint);
            paint.clearShadowLayer();
        }

        private void drawCorner(
                Canvas canvas,
                float x,
                float y,
                float edge,
                boolean left,
                boolean top) {
            path.reset();
            path.moveTo(x, y + (top ? edge : -edge));
            path.lineTo(x, y);
            path.lineTo(x + (left ? edge : -edge), y);
            canvas.drawPath(path, paint);
        }
    }
}
