package com.onecore.loader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Animated, theme-aware inline key verification panel. */
public final class KeyVerificationView extends View {
    public static final int STATE_VERIFYING = 0;
    public static final int STATE_SUCCESS = 1;
    public static final int STATE_FAILED = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean animate;
    private int state = STATE_VERIFYING;
    private String detail = "NATIVE TRUST • TLS PINNED • RESPONSE BOUND";

    public KeyVerificationView(Context context) {
        super(context);
        init();
    }

    public KeyVerificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("OneCore Edge key verification status");
    }

    public void setState(int state, String detail) {
        this.state = state;
        if (detail != null && !detail.trim().isEmpty()) {
            this.detail = detail.trim();
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animate = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        animate = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        ThemeManager.ThemeSpec theme = ThemeManager.current(getContext());
        int themeIndex = ThemeManager.currentIndex(getContext());
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float phase = (SystemClock.uptimeMillis() % 3600L) / 3600f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        int statusColor = state == STATE_SUCCESS
                ? theme.success
                : state == STATE_FAILED ? theme.error : theme.accent;

        drawPanel(canvas, theme, statusColor, d, w, h);
        drawThemeMesh(canvas, theme, themeIndex, statusColor, d, w, h, phase);
        drawCore(canvas, theme, statusColor, d, h, phase, pulse);
        drawStatus(canvas, theme, statusColor, d, w, h, pulse);
        drawScan(canvas, theme, statusColor, d, w, h, phase);

        if (animate && state == STATE_VERIFYING) postInvalidateOnAnimation();
    }

    private void drawPanel(Canvas canvas, ThemeManager.ThemeSpec theme, int statusColor,
                           float d, float w, float h) {
        rect.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d);
        fill.setShader(new LinearGradient(0f, 0f, w, h,
                new int[]{
                        ThemeManager.withAlpha(theme.surfaceAlt, 250),
                        ThemeManager.withAlpha(theme.surface, 244),
                        ThemeManager.withAlpha(theme.bgBottom, 250)
                }, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, theme.cardRadiusDp * d * 0.72f,
                theme.cardRadiusDp * d * 0.72f, fill);
        fill.setShader(null);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, theme.strokeDp * d));
        line.setColor(ThemeManager.withAlpha(statusColor, 190));
        canvas.drawRoundRect(rect, theme.cardRadiusDp * d * 0.72f,
                theme.cardRadiusDp * d * 0.72f, line);
    }

    private void drawCore(Canvas canvas, ThemeManager.ThemeSpec theme, int statusColor,
                          float d, float h, float phase, float pulse) {
        float cx = 57f * d;
        float cy = h * 0.5f;
        float radius = Math.min(34f * d, h * 0.30f);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            float r = radius + i * 6f * d;
            line.setStrokeWidth((i == 1 ? 2.2f : 1.1f) * d);
            line.setColor(ThemeManager.withAlpha(i == 2 ? theme.accent2 : statusColor,
                    90 + i * 35));
            RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
            float direction = i == 1 ? -1f : 1f;
            canvas.drawArc(ring, phase * 360f * direction + i * 67f,
                    74f + i * 24f, false, line);
            canvas.drawArc(ring, phase * 360f * direction + 180f + i * 41f,
                    45f + i * 17f, false, line);
        }

        glow.setStyle(Paint.Style.FILL);
        glow.setColor(ThemeManager.withAlpha(statusColor, 38 + (int) (pulse * 36f)));
        glow.setShadowLayer((8f + 5f * pulse) * d, 0f, 0f,
                ThemeManager.withAlpha(statusColor, 180));
        canvas.drawCircle(cx, cy, radius * 0.68f, glow);
        glow.clearShadowLayer();

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        text.setTextSize(15f * d);
        text.setColor(theme.text);
        canvas.drawText(state == STATE_SUCCESS ? "✓" : state == STATE_FAILED ? "!" : "KEY",
                cx, cy - (text.ascent() + text.descent()) / 2f, text);

        if (state == STATE_VERIFYING) {
            float angle = phase * (float) Math.PI * 2f;
            float dotX = cx + (float) Math.cos(angle) * (radius + 6f * d);
            float dotY = cy + (float) Math.sin(angle) * (radius + 6f * d);
            glow.setColor(theme.accent2);
            glow.setShadowLayer(7f * d, 0f, 0f, statusColor);
            canvas.drawCircle(dotX, dotY, 2.6f * d, glow);
            glow.clearShadowLayer();
        }
    }

    private void drawStatus(Canvas canvas, ThemeManager.ThemeSpec theme, int statusColor,
                            float d, float w, float h, float pulse) {
        float left = 112f * d;
        float right = w - 16f * d;

        small.setTextAlign(Paint.Align.LEFT);
        small.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        small.setTextSize(8.5f * d);
        small.setColor(ThemeManager.withAlpha(statusColor, 235));
        canvas.drawText("ONECORE EDGE  •  SECURE KEY CHANNEL", left, h * 0.24f, small);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        text.setTextSize(Math.max(14f * d, Math.min(18f * d, (right - left) / 13f)));
        text.setColor(theme.text);
        String headline = state == STATE_SUCCESS
                ? "KEY VERIFIED"
                : state == STATE_FAILED ? "VERIFICATION BLOCKED" : "VERIFYING LICENSE";
        text.setShadowLayer((state == STATE_VERIFYING ? 4f + pulse * 4f : 3f) * d,
                0f, 0f, ThemeManager.withAlpha(statusColor, 150));
        canvas.drawText(headline, left, h * 0.49f, text);
        text.clearShadowLayer();

        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setTextSize(8.2f * d);
        small.setColor(theme.muted);
        String safeDetail = detail.length() > 48 ? detail.substring(0, 48) : detail;
        canvas.drawText(safeDetail.toUpperCase(), left, h * 0.67f, small);

        String[] labels = {"NATIVE", "TLS", "BOUND"};
        float chipY = h * 0.84f;
        float chipWidth = Math.max(36f * d, (right - left - 12f * d) / 3f);
        for (int i = 0; i < labels.length; i++) {
            float x = left + i * (chipWidth + 5f * d);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1f * d);
            line.setColor(ThemeManager.withAlpha(statusColor, 115 + i * 20));
            RectF chip = new RectF(x, chipY - 12f * d, x + chipWidth, chipY + 3f * d);
            canvas.drawRoundRect(chip, 7f * d, 7f * d, line);
            small.setTextAlign(Paint.Align.CENTER);
            small.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            small.setTextSize(6.8f * d);
            small.setColor(ThemeManager.withAlpha(statusColor, 220));
            canvas.drawText(labels[i], x + chipWidth / 2f, chipY - 1f * d, small);
        }
    }

    private void drawThemeMesh(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                               int statusColor, float d, float w, float h, float phase) {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(0.8f * d);
        line.setColor(ThemeManager.withAlpha(theme.accent2, 36));
        int mode = themeIndex % 5;
        if (mode == 0) {
            for (int i = 0; i < 6; i++) {
                float y = (i + 1) * h / 7f;
                canvas.drawLine(w * 0.72f, y, w - 8f * d, y, line);
            }
        } else if (mode == 1) {
            for (int i = 0; i < 5; i++) {
                float x = w * 0.70f + i * 12f * d;
                canvas.drawCircle(x, h * 0.50f, (8f + i * 5f) * d, line);
            }
        } else if (mode == 2) {
            for (int i = 0; i < 7; i++) {
                float x = w * 0.70f + i * 9f * d;
                canvas.drawLine(x, h * 0.18f, x - 18f * d, h * 0.82f, line);
            }
        } else if (mode == 3) {
            float cx = w * 0.86f;
            float cy = h * 0.50f;
            for (int i = 0; i < 4; i++) {
                RectF r = new RectF(cx - (14 + i * 8) * d, cy - (14 + i * 8) * d,
                        cx + (14 + i * 8) * d, cy + (14 + i * 8) * d);
                canvas.drawArc(r, phase * 360f + i * 40f, 105f, false, line);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                float y = h * (0.20f + i * 0.11f);
                float length = (18f + (i % 3) * 13f) * d;
                canvas.drawLine(w - 10f * d - length, y, w - 10f * d, y, line);
            }
        }
    }

    private void drawScan(Canvas canvas, ThemeManager.ThemeSpec theme, int statusColor,
                          float d, float w, float h, float phase) {
        if (state != STATE_VERIFYING) return;
        float x = 7f * d + (w - 14f * d) * phase;
        fill.setShader(new LinearGradient(x - 18f * d, 0f, x + 18f * d, 0f,
                new int[]{0x00000000, ThemeManager.withAlpha(statusColor, 36), 0x00000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(Math.max(3f * d, x - 18f * d), 4f * d,
                Math.min(w - 3f * d, x + 18f * d), h - 4f * d, fill);
        fill.setShader(null);
    }
}
