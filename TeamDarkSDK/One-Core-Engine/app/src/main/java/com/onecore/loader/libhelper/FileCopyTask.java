package com.onecore.loader.libhelper;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.onecore.loader.ui.ThemeManager;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import top.niunaijun.blackbox.core.env.BEnvironment;

/** Theme-aware OBB copy flow used by the OneCore Edge Loader. */
@Obfuscate
public class FileCopyTask {
    private static final File EXTERNAL_DIRECTORY = Environment.getExternalStorageDirectory();
    private static final AtomicBoolean COPYING = new AtomicBoolean(false);

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OneCore-AssetCopy");
        thread.setDaemon(true);
        return thread;
    });

    private FrameLayout overlay;
    private CopyHudView hudView;
    private ProgressBar progressBar;
    private TextView progressValue;
    private TextView stateText;
    private TextView titleText;

    public interface CopyCallback {
        void onCopyCompleted(boolean success);
    }

    public FileCopyTask(Activity activity) {
        this.activity = activity;
    }

    public static File getExternalStorageDirectory() {
        return new File(EXTERNAL_DIRECTORY, "SdCard");
    }

    public static File getExternalObbDir(String packageName) {
        return new File(getExternalStorageDirectory(),
                String.format(Locale.US, "Android/obb/%s/", packageName));
    }

    public boolean isObbCopied(String packageName) {
        File destDir = getExternalObbDir(packageName);
        File[] children = destDir.listFiles();
        return destDir.isDirectory() && children != null && children.length > 0;
    }

    public void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                activity.startActivity(intent);
            }
            return;
        }
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 101);
    }

    public void copyObbFolderAsync(final String packageName, final CopyCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            requestStoragePermission();
            if (callback != null) callback.onCopyCompleted(false);
            return;
        }
        if (!COPYING.compareAndSet(false, true)) return;

        showCopyUi();
        executor.execute(() -> {
            boolean success = false;
            String result;
            try {
                File source = new File(Environment.getExternalStorageDirectory(),
                        "Android/obb/" + packageName);
                File destination = getExternalObbDir(packageName);
                if (!source.isDirectory() || !source.canRead()) {
                    throw new IOException("Source OBB folder is unavailable");
                }
                long total = directoryBytes(source);
                if (total <= 0L) throw new IOException("No OBB payload was found");
                if (!destination.exists() && !destination.mkdirs()) {
                    throw new IOException("Destination folder could not be created");
                }
                long[] copied = new long[]{0L};
                copyDirectory(source, destination, total, copied);
                success = true;
                result = "Files copied successfully!\nLocation: Android/obb/" + packageName;
            } catch (Throwable error) {
                result = error.getMessage() == null ? "Secure asset copy failed" : error.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalResult = result;
            main.post(() -> {
                finishCopyUi(finalSuccess, finalResult);
                if (callback != null) callback.onCopyCompleted(finalSuccess);
            });
        });
    }

    private void showCopyUi() {
        main.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                COPYING.set(false);
                return;
            }
            ThemeManager.ThemeSpec theme = ThemeManager.current(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                COPYING.set(false);
                return;
            }

            overlay = new FrameLayout(activity);
            overlay.setClickable(true);
            overlay.setFocusable(true);
            overlay.setBackgroundColor(ThemeManager.withAlpha(theme.bgBottom, 226));
            root.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout card = new FrameLayout(activity);
            card.setBackground(cardBackground(theme));
            card.setClipChildren(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                card.setElevation(dp(Math.max(10f, theme.elevationDp)));
            }
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            cardParams.leftMargin = dp(22);
            cardParams.rightMargin = dp(22);
            overlay.addView(card, cardParams);

            hudView = new CopyHudView(activity, theme);
            card.addView(hudView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(22), dp(22), dp(22), dp(20));
            card.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));

            TextView eyebrow = text("ONECORE EDGE  •  SECURE ASSET CHANNEL", 9.5f,
                    theme.success, true);
            eyebrow.setLetterSpacing(0.12f);
            content.addView(eyebrow, matchWrap(dp(8)));

            LinearLayout hero = new LinearLayout(activity);
            hero.setOrientation(LinearLayout.HORIZONTAL);
            hero.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(hero, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            CopyOrbView orb = new CopyOrbView(activity, theme);
            LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(104), dp(104));
            orbParams.rightMargin = dp(18);
            hero.addView(orb, orbParams);

            LinearLayout words = new LinearLayout(activity);
            words.setOrientation(LinearLayout.VERTICAL);
            hero.addView(words, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            titleText = text("COPYING FILES", 23f, theme.accent, true);
            titleText.setLetterSpacing(0.045f);
            words.addView(titleText, matchWrap(dp(5)));

            stateText = text("Preparing secure asset copy", 12f, theme.text, false);
            words.addView(stateText, matchWrap(dp(8)));

            TextView sub = text("Encrypted workspace  •  verified destination", 9.5f,
                    theme.muted, true);
            sub.setLetterSpacing(0.025f);
            words.addView(sub, matchWrap(0));

            progressValue = text("0%", 31f, theme.text, true);
            progressValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            percentParams.topMargin = dp(14);
            content.addView(progressValue, percentParams);

            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setProgressTintList(ColorStateList.valueOf(theme.accent));
                progressBar.setProgressBackgroundTintList(
                        ColorStateList.valueOf(ThemeManager.withAlpha(theme.muted, 45)));
            }
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            progressParams.topMargin = dp(7);
            content.addView(progressBar, progressParams);

            TextView footer = text("SECURE COPY IN PROGRESS  •  DO NOT CLOSE", 9.5f,
                    theme.success, true);
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams footerParams = matchWrap(0);
            footerParams.topMargin = dp(14);
            content.addView(footer, footerParams);

            overlay.setAlpha(0f);
            card.setScaleX(0.965f);
            card.setScaleY(0.965f);
            overlay.animate().alpha(1f).setDuration(180L).start();
            card.animate().scaleX(1f).scaleY(1f).setDuration(280L).start();
        });
    }

    private void updateProgress(int progress) {
        main.post(() -> {
            int safe = Math.max(0, Math.min(100, progress));
            if (progressBar != null) progressBar.setProgress(safe);
            if (progressValue != null) progressValue.setText(String.format(Locale.US, "%d%%", safe));
            if (stateText != null) {
                stateText.setText(safe >= 100
                        ? "Validating copied runtime assets"
                        : "Copying protected runtime assets");
            }
            if (hudView != null) hudView.setProgress(safe);
        });
    }

    private void finishCopyUi(boolean success, String message) {
        COPYING.set(false);
        ThemeManager.ThemeSpec theme = ThemeManager.current(activity);
        if (progressBar != null && success) progressBar.setProgress(100);
        if (progressValue != null && success) progressValue.setText("100%");
        if (titleText != null) {
            titleText.setText(success ? "COPY COMPLETE" : "COPY FAILED");
            titleText.setTextColor(success ? theme.success : theme.error);
        }
        if (stateText != null) {
            stateText.setText(success
                    ? "Secure assets verified and installed"
                    : "Secure asset copy could not complete");
        }
        if (hudView != null) hudView.setProgress(success ? 100 : -1);

        main.postDelayed(() -> {
            FrameLayout current = overlay;
            if (current == null) {
                showResultDialog(success, message);
                return;
            }
            current.animate().alpha(0f).setDuration(190L).withEndAction(() -> {
                if (current.getParent() instanceof ViewGroup) {
                    ((ViewGroup) current.getParent()).removeView(current);
                }
                if (overlay == current) overlay = null;
                hudView = null;
                progressBar = null;
                progressValue = null;
                stateText = null;
                titleText = null;
                showResultDialog(success, message);
            }).start();
        }, success ? 520L : 900L);
    }

    private void showResultDialog(boolean success, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ThemeManager.ThemeSpec theme = ThemeManager.current(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        FrameLayout shell = new FrameLayout(activity);
        shell.setPadding(dp(2), dp(2), dp(2), dp(2));
        shell.setBackground(resultBackground(theme, success));
        dialog.setContentView(shell);

        ResultGfxView gfx = new ResultGfxView(activity, theme, success);
        shell.addView(gfx, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(22), dp(24), dp(22));
        shell.addView(body, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        ResultBadgeView badge = new ResultBadgeView(activity, theme, success);
        body.addView(badge, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView title = text(success ? "SUCCESS" : "FAILED", 24f,
                success ? theme.success : theme.error, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams titleParams = matchWrap(dp(10));
        titleParams.topMargin = dp(8);
        body.addView(title, titleParams);

        TextView status = text(success
                        ? "Secure files copied successfully"
                        : "Secure copy operation failed",
                13f, theme.text, true);
        status.setGravity(Gravity.CENTER);
        body.addView(status, matchWrap(dp(5)));

        TextView detail = text(cleanResultMessage(message), 11f, theme.muted, false);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(0f, 1.18f);
        body.addView(detail, matchWrap(dp(18)));

        TextView button = text("OK", 15f, contrastingText(theme.accent), true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(18), dp(13), dp(18), dp(13));
        button.setBackground(buttonBackground(theme));
        body.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        button.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.78f;
            window.setAttributes(attrs);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (width * 0.86f), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        shell.setAlpha(0f);
        shell.setScaleX(0.96f);
        shell.setScaleY(0.96f);
        shell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L).start();
    }

    private String cleanResultMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "Operation completed";
        return message.replace("✓", "").replace("✗", "").trim();
    }

    private void copyDirectory(File source, File destination, long total, long[] copied)
            throws IOException {
        File[] files = source.listFiles();
        if (files == null) throw new IOException("Unable to read source OBB folder");
        for (File file : files) {
            File out = new File(destination, file.getName());
            if (file.isDirectory()) {
                if (!out.exists() && !out.mkdirs()) {
                    throw new IOException("Unable to create destination directory");
                }
                copyDirectory(file, out, total, copied);
            } else {
                copyFile(file, out, total, copied);
            }
        }
    }

    private void copyFile(File source, File destination, long total, long[] copied)
            throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            int lastProgress = -1;
            while ((read = input.read(buffer)) != -1) {
                if (read <= 0) continue;
                output.write(buffer, 0, read);
                copied[0] += read;
                int progress = (int) Math.min(100L, copied[0] * 100L / Math.max(1L, total));
                if (progress != lastProgress) {
                    lastProgress = progress;
                    updateProgress(progress);
                }
            }
            output.flush();
        }
    }

    private long directoryBytes(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File file : files) {
            total += file.isDirectory() ? directoryBytes(file) : Math.max(0L, file.length());
        }
        return total;
    }

    public static boolean deleteObbFolder(String packageName) {
        return deleteDirectory(BEnvironment.getExternalObbDir(packageName));
    }

    private static boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteDirectory(child)) return false;
                }
            }
        }
        return dir.delete();
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

    private static GradientDrawable cardBackground(ThemeManager.ThemeSpec theme) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ThemeManager.withAlpha(theme.surfaceAlt, 248),
                        ThemeManager.withAlpha(theme.surface, 248)});
        drawable.setCornerRadius(theme.cardRadiusDp * 2.2f);
        drawable.setStroke(Math.max(1, theme.strokeDp * 2),
                ThemeManager.withAlpha(theme.accent, 210));
        return drawable;
    }

    private static GradientDrawable resultBackground(ThemeManager.ThemeSpec theme, boolean success) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{theme.surfaceAlt, theme.surface});
        drawable.setCornerRadius(theme.cardRadiusDp * 2.4f);
        drawable.setStroke(2, ThemeManager.withAlpha(success ? theme.success : theme.error, 225));
        return drawable;
    }

    private static GradientDrawable buttonBackground(ThemeManager.ThemeSpec theme) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{theme.accent, theme.accent2});
        drawable.setCornerRadius(theme.buttonRadiusDp * 2.2f);
        return drawable;
    }

    private static int contrastingText(int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.62 ? Color.rgb(5, 8, 12) : Color.WHITE;
    }

    private static final class CopyHudView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private float phase;
        private int progress;

        CopyHudView(Activity context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setWillNotDraw(false);
        }

        void setProgress(int value) {
            progress = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0f || h <= 0f) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 28));
            float step = Math.max(34f, w / 11f);
            float shift = phase * step;
            for (float x = -step + shift; x < w + step; x += step) {
                canvas.drawLine(x, 0f, x + h * 0.20f, h, paint);
            }

            paint.setColor(ThemeManager.withAlpha(theme.accent2, 80));
            paint.setStrokeWidth(2f);
            float scan = h * (0.12f + 0.76f * phase);
            canvas.drawLine(18f, scan, w - 18f, scan, paint);

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 9; i++) {
                float px = w * (i + 1f) / 10f + phase * 45f * (i % 2 == 0 ? 1f : -1f);
                float py = h * (0.18f + ((i * 29) % 67) / 100f);
                paint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2,
                        70 + (i * 11) % 100));
                canvas.drawCircle(px, py, 2.2f + (i % 3), paint);
            }

            if (progress >= 100) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(ThemeManager.withAlpha(theme.success, 180));
                RectF ring = new RectF(w - 70f, 18f, w - 18f, 70f);
                canvas.drawArc(ring, -90f, 360f, false, paint);
            }

            phase += 0.008f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    private static final class CopyOrbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final ThemeManager.ThemeSpec theme;
        private float phase;

        CopyOrbView(Activity context, ThemeManager.ThemeSpec theme) {
            super(context);
            this.theme = theme;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.38f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            for (int i = 0; i < 3; i++) {
                paint.setColor(ThemeManager.withAlpha(i == 0 ? theme.accent : theme.accent2,
                        75 + i * 35));
                RectF oval = new RectF(cx - r + i * 8f, cy - r + i * 8f,
                        cx + r - i * 8f, cy + r - i * 8f);
                canvas.drawArc(oval, phase * 360f + i * 105f, 105f - i * 15f, false, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 42));
            canvas.drawCircle(cx, cy, r * 0.55f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(theme.accent);
            path.reset();
            path.moveTo(cx - r * 0.42f, cy + r * 0.28f);
            path.lineTo(cx - r * 0.42f, cy - r * 0.22f);
            path.lineTo(cx - r * 0.08f, cy - r * 0.22f);
            path.lineTo(cx + r * 0.02f, cy - r * 0.37f);
            path.lineTo(cx + r * 0.46f, cy - r * 0.37f);
            path.lineTo(cx + r * 0.46f, cy + r * 0.28f);
            path.close();
            canvas.drawPath(path, paint);

            phase += 0.012f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    private static final class ResultBadgeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private final boolean success;
        private float phase;

        ResultBadgeView(Activity context, ThemeManager.ThemeSpec theme, boolean success) {
            super(context);
            this.theme = theme;
            this.success = success;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.38f;
            int color = success ? theme.success : theme.error;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(ThemeManager.withAlpha(color, 210));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(ThemeManager.withAlpha(color, 85));
            canvas.drawCircle(cx, cy,
                    r * (0.68f + 0.05f * (float) Math.sin(phase * Math.PI * 2)), paint);

            paint.setStrokeWidth(4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color);
            if (success) {
                canvas.drawLine(cx - r * 0.42f, cy, cx - r * 0.12f, cy + r * 0.30f, paint);
                canvas.drawLine(cx - r * 0.12f, cy + r * 0.30f,
                        cx + r * 0.46f, cy - r * 0.32f, paint);
            } else {
                canvas.drawLine(cx - r * 0.30f, cy - r * 0.30f,
                        cx + r * 0.30f, cy + r * 0.30f, paint);
                canvas.drawLine(cx + r * 0.30f, cy - r * 0.30f,
                        cx - r * 0.30f, cy + r * 0.30f, paint);
            }
            phase += 0.02f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }

    private static final class ResultGfxView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeManager.ThemeSpec theme;
        private final boolean success;
        private float phase;

        ResultGfxView(Activity context, ThemeManager.ThemeSpec theme, boolean success) {
            super(context);
            this.theme = theme;
            this.success = success;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            int color = success ? theme.success : theme.error;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(ThemeManager.withAlpha(theme.accent, 35));
            for (int i = 0; i < 7; i++) {
                float y = h * (i + 1f) / 8f;
                canvas.drawLine(0f, y, w, y + (phase - 0.5f) * 16f, paint);
            }
            paint.setStrokeWidth(2f);
            paint.setColor(ThemeManager.withAlpha(color, 120));
            float span = Math.min(w, h) * 0.11f;
            canvas.drawLine(12f, 12f, 12f + span, 12f, paint);
            canvas.drawLine(12f, 12f, 12f, 12f + span, paint);
            canvas.drawLine(w - 12f, h - 12f, w - 12f - span, h - 12f, paint);
            canvas.drawLine(w - 12f, h - 12f, w - 12f, h - 12f - span, paint);
            phase += 0.009f;
            if (phase > 1f) phase -= 1f;
            postInvalidateOnAnimation();
        }
    }
}
