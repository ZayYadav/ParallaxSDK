package com.onecore.loader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Theme-aware inline OneCore Edge visual used across Splash, Login and Main.
 * It is intentionally a normal layout child (not an overlay/dialog) so the
 * branding and runtime graphics remain part of the screen composition.
 */
public final class EdgeVisualView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final Path path = new Path();
    private boolean animate;

    public EdgeVisualView(Context context) {
        super(context);
        init();
    }

    public EdgeVisualView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EdgeVisualView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("OneCore Edge secure runtime visual");
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
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        ThemeManager.ThemeSpec theme = ThemeManager.current(getContext());
        float density = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        float phase = (SystemClock.uptimeMillis() % 4200L) / 4200f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);

        drawCard(canvas, theme, density, w, h);
        drawCircuitMesh(canvas, theme, density, w, h, phase);
        drawOrbitalMark(canvas, theme, density, w, h, phase, pulse);
        drawWordmark(canvas, theme, density, w, h);
        drawTelemetry(canvas, theme, density, w, h, phase, pulse);
        drawScan(canvas, theme, density, w, h, phase);

        if (animate) {
            postInvalidateOnAnimation();
        }
    }

    private void drawCard(Canvas canvas, ThemeManager.ThemeSpec theme, float d, float w, float h) {
        float inset = 1.5f * d;
        cardRect.set(inset, inset, w - inset, h - inset);
        fillPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{
                        ThemeManager.withAlpha(theme.surfaceAlt, 248),
                        ThemeManager.withAlpha(theme.surface, 238),
                        ThemeManager.withAlpha(theme.bgBottom, 248)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(cardRect, theme.cardRadiusDp * d * 0.72f,
                theme.cardRadiusDp * d * 0.72f, fillPaint);
        fillPaint.setShader(null);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, theme.strokeDp * d));
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 155));
        canvas.drawRoundRect(cardRect, theme.cardRadiusDp * d * 0.72f,
                theme.cardRadiusDp * d * 0.72f, linePaint);
    }

    private void drawCircuitMesh(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float phase) {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(0.8f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 42));

        float top = h * 0.19f;
        float mid = h * 0.50f;
        float bottom = h * 0.80f;
        float start = w * 0.04f;
        float end = w * 0.96f;
        float step = Math.max(38f * d, w / 8f);

        for (float x = start; x < end; x += step) {
            float bend = ((int) (x / step) % 2 == 0) ? 9f * d : -9f * d;
            path.reset();
            path.moveTo(x, top);
            path.lineTo(x + 12f * d, top);
            path.lineTo(x + 22f * d, mid + bend);
            path.lineTo(x + 42f * d, mid + bend);
            path.lineTo(x + 52f * d, bottom);
            canvas.drawPath(path, linePaint);
        }

        float movingX = start + (end - start) * phase;
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(ThemeManager.withAlpha(theme.accent2, 185));
        glowPaint.setShadowLayer(8f * d, 0f, 0f, ThemeManager.withAlpha(theme.accent, 150));
        canvas.drawCircle(movingX, mid, 2.2f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawOrbitalMark(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float phase, float pulse) {
        float cx = Math.min(w * 0.18f, 78f * d);
        float cy = h * 0.50f;
        float base = Math.min(h * 0.34f, 36f * d);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        linePaint.setStrokeWidth(1.1f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 90));
        canvas.drawCircle(cx, cy, base * 1.22f, linePaint);

        linePaint.setStrokeWidth(2.0f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 190));
        RectF orbit = new RectF(cx - base, cy - base, cx + base, cy + base);
        canvas.drawArc(orbit, -90f + phase * 360f, 118f, false, linePaint);
        canvas.drawArc(orbit, 104f + phase * 360f, 72f, false, linePaint);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(ThemeManager.withAlpha(theme.accent, 42 + (int) (pulse * 35f)));
        glowPaint.setShadowLayer((7f + pulse * 5f) * d, 0f, 0f,
                ThemeManager.withAlpha(theme.accent, 170));
        canvas.drawCircle(cx, cy, base * 0.64f, glowPaint);
        glowPaint.clearShadowLayer();

        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black",
                android.graphics.Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.min(18f * d, h * 0.20f));
        textPaint.setColor(theme.text);
        canvas.drawText("OE", cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);

        float dotAngle = (float) (phase * Math.PI * 2.0);
        float dotX = cx + (float) Math.cos(dotAngle) * base;
        float dotY = cy + (float) Math.sin(dotAngle) * base;
        glowPaint.setColor(theme.accent2);
        glowPaint.setShadowLayer(6f * d, 0f, 0f, theme.accent);
        canvas.drawCircle(dotX, dotY, 2.6f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawWordmark(Canvas canvas, ThemeManager.ThemeSpec theme,
                              float d, float w, float h) {
        float left = Math.min(w * 0.33f, 142f * d);
        float usable = Math.max(110f * d, w - left - 22f * d);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(android.graphics.Typeface.create(theme.headingFont,
                android.graphics.Typeface.BOLD));
        float titleSize = Math.min(20f * d, usable / 10.8f);
        titleSize = Math.max(13f * d, titleSize);
        textPaint.setTextSize(titleSize);
        textPaint.setColor(theme.text);
        canvas.drawText("ONECORE", left, h * 0.42f, textPaint);

        float firstWidth = textPaint.measureText("ONECORE ");
        textPaint.setColor(theme.accent);
        canvas.drawText("EDGE", left + firstWidth, h * 0.42f, textPaint);

        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.BOLD));
        smallTextPaint.setTextSize(Math.max(8.2f * d, Math.min(10f * d, h * 0.085f)));
        smallTextPaint.setLetterSpacing(0.08f);
        smallTextPaint.setColor(theme.muted);
        canvas.drawText("SECURE RUNTIME  //  VERIFIED ACCESS", left, h * 0.61f, smallTextPaint);

        smallTextPaint.setTextSize(Math.max(7.3f * d, Math.min(8.8f * d, h * 0.073f)));
        smallTextPaint.setColor(ThemeManager.withAlpha(theme.success, 230));
        canvas.drawText("● EDGE NODE ONLINE", left, h * 0.76f, smallTextPaint);
    }

    private void drawTelemetry(Canvas canvas, ThemeManager.ThemeSpec theme,
                               float d, float w, float h, float phase, float pulse) {
        float right = w - 16f * d;
        float y = h * 0.25f;
        float barW = Math.min(42f * d, w * 0.09f);

        for (int i = 0; i < 4; i++) {
            float yy = y + i * 11f * d;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(1.4f * d);
            linePaint.setColor(ThemeManager.withAlpha(theme.muted, 70));
            canvas.drawLine(right - barW, yy, right, yy, linePaint);

            float active = barW * (0.34f + 0.58f * (float) Math.abs(
                    Math.sin((phase + i * 0.17f) * Math.PI * 2.0)));
            linePaint.setColor(ThemeManager.withAlpha(i == 3 ? theme.success : theme.accent,
                    150 + (int) (pulse * 80f)));
            canvas.drawLine(right - barW, yy, right - barW + active, yy, linePaint);
        }

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(theme.success);
        glowPaint.setShadowLayer(5f * d, 0f, 0f, ThemeManager.withAlpha(theme.success, 150));
        canvas.drawCircle(right - 3f * d, h * 0.76f, 2.4f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawScan(Canvas canvas, ThemeManager.ThemeSpec theme,
                          float d, float w, float h, float phase) {
        float x = 8f * d + (w - 16f * d) * phase;
        fillPaint.setShader(new LinearGradient(
                x - 22f * d, 0f, x + 22f * d, 0f,
                new int[]{Color.TRANSPARENT, ThemeManager.withAlpha(theme.accent, 42), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(Math.max(2f * d, x - 22f * d), 5f * d,
                Math.min(w - 2f * d, x + 22f * d), h - 5f * d, fillPaint);
        fillPaint.setShader(null);
    }
}
