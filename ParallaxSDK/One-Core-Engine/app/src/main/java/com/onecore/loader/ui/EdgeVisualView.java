package com.onecore.loader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Advanced theme-native inline visual for OneCore Edge.
 *
 * Each of the ten themes gets a different procedural GFX language while the
 * OneCore Edge wordmark remains animated on an orbital path. Everything is
 * Canvas-rendered so no heavy bitmap artwork is added to the APK.
 */
public final class EdgeVisualView extends View {

    private static final String ORBIT_TEXT = "ONECORE EDGE  •  ONECORE EDGE  •  ";

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF tempRect = new RectF();
    private final Path path = new Path();
    private final Path textOrbit = new Path();
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
        setContentDescription("Animated OneCore Edge theme visual");
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
        int themeIndex = ThemeManager.currentIndex(getContext());
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth();
        float h = getHeight();
        long now = SystemClock.uptimeMillis();
        float phase = (now % 5600L) / 5600f;
        float fastPhase = (now % 2600L) / 2600f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);

        drawCard(canvas, theme, d, w, h, themeIndex);
        drawAmbientGlow(canvas, theme, d, w, h, pulse);
        drawThemeSignature(canvas, theme, themeIndex, d, w, h, phase, fastPhase, pulse);
        drawCircuitMesh(canvas, theme, themeIndex, d, w, h, phase);
        drawOrbitalCore(canvas, theme, themeIndex, d, w, h, phase, pulse);
        drawOrbitingWordmark(canvas, theme, themeIndex, d, w, h, phase);
        drawMainWordmark(canvas, theme, themeIndex, d, w, h, phase);
        drawTelemetry(canvas, theme, themeIndex, d, w, h, phase, pulse);
        drawScan(canvas, theme, themeIndex, d, w, h, fastPhase);

        if (animate) {
            postInvalidateOnAnimation();
        }
    }

    private void drawCard(Canvas canvas, ThemeManager.ThemeSpec theme,
                          float d, float w, float h, int themeIndex) {
        float inset = 1.5f * d;
        cardRect.set(inset, inset, w - inset, h - inset);

        int middle = theme.surface;
        if (themeIndex == 5) {
            middle = ThemeManager.withAlpha(theme.accent2, 35);
        } else if (themeIndex == 8) {
            middle = ThemeManager.withAlpha(theme.surfaceAlt, 255);
        }

        fillPaint.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{
                        ThemeManager.withAlpha(theme.surfaceAlt, 250),
                        middle,
                        ThemeManager.withAlpha(theme.bgBottom, 252)
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP));
        float radius = Math.max(8f, theme.cardRadiusDp * 0.72f) * d;
        canvas.drawRoundRect(cardRect, radius, radius, fillPaint);
        fillPaint.setShader(null);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, theme.strokeDp * d));
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, themeIndex == 8 ? 100 : 165));
        canvas.drawRoundRect(cardRect, radius, radius, linePaint);
    }

    private void drawAmbientGlow(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float pulse) {
        float cx = Math.min(w * 0.18f, 82f * d);
        float cy = h * 0.50f;
        float radius = Math.min(h * 0.58f, 74f * d);
        fillPaint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{
                        ThemeManager.withAlpha(theme.accent, 38 + (int) (pulse * 25f)),
                        ThemeManager.withAlpha(theme.accent2, 13),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, fillPaint);
        fillPaint.setShader(null);
    }

    private void drawThemeSignature(Canvas canvas, ThemeManager.ThemeSpec theme, int index,
                                    float d, float w, float h,
                                    float phase, float fastPhase, float pulse) {
        switch (index) {
            case 0:
                drawCyanRadar(canvas, theme, d, w, h, fastPhase);
                break;
            case 1:
                drawGoldLuxuryRings(canvas, theme, d, w, h, phase);
                break;
            case 2:
                drawVioletPortal(canvas, theme, d, w, h, phase, pulse);
                break;
            case 3:
                drawCrimsonBrackets(canvas, theme, d, w, h, fastPhase);
                break;
            case 4:
                drawEmeraldMatrix(canvas, theme, d, w, h, phase);
                break;
            case 5:
                drawArcticPrism(canvas, theme, d, w, h, phase);
                break;
            case 6:
                drawSakuraParticles(canvas, theme, d, w, h, phase);
                break;
            case 7:
                drawSolarSpokes(canvas, theme, d, w, h, phase, pulse);
                break;
            case 8:
                drawMidnightGrid(canvas, theme, d, w, h, fastPhase);
                break;
            case 9:
            default:
                drawTitaniumHud(canvas, theme, d, w, h, phase);
                break;
        }
    }

    private void drawCyanRadar(Canvas canvas, ThemeManager.ThemeSpec theme,
                               float d, float w, float h, float phase) {
        float cx = w * 0.80f;
        float cy = h * 0.50f;
        float r = Math.min(44f * d, h * 0.32f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1f * d);
        for (int i = 1; i <= 3; i++) {
            linePaint.setColor(ThemeManager.withAlpha(theme.accent, 48 + i * 15));
            canvas.drawCircle(cx, cy, r * i / 3f, linePaint);
        }
        float a = phase * 360f - 90f;
        float x = cx + (float) Math.cos(Math.toRadians(a)) * r;
        float y = cy + (float) Math.sin(Math.toRadians(a)) * r;
        linePaint.setColor(ThemeManager.withAlpha(theme.accent2, 205));
        linePaint.setStrokeWidth(1.6f * d);
        canvas.drawLine(cx, cy, x, y, linePaint);
    }

    private void drawGoldLuxuryRings(Canvas canvas, ThemeManager.ThemeSpec theme,
                                     float d, float w, float h, float phase) {
        float cx = w * 0.82f;
        float cy = h * 0.50f;
        float base = Math.min(h * 0.28f, 36f * d);
        linePaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            linePaint.setStrokeWidth((0.8f + i * 0.45f) * d);
            linePaint.setColor(ThemeManager.withAlpha(i == 1 ? theme.accent2 : theme.accent, 75 + i * 35));
            tempRect.set(cx - base - i * 8f * d, cy - base - i * 5f * d,
                    cx + base + i * 8f * d, cy + base + i * 5f * d);
            canvas.drawArc(tempRect, phase * 360f + i * 75f, 160f - i * 18f, false, linePaint);
        }
    }

    private void drawVioletPortal(Canvas canvas, ThemeManager.ThemeSpec theme,
                                  float d, float w, float h, float phase, float pulse) {
        float cx = w * 0.81f;
        float cy = h * 0.50f;
        for (int i = 0; i < 4; i++) {
            float r = (18f + i * 10f + pulse * 3f) * d;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth((2.4f - i * 0.35f) * d);
            linePaint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2, 155 - i * 22));
            tempRect.set(cx - r, cy - r * 0.58f, cx + r, cy + r * 0.58f);
            canvas.save();
            canvas.rotate(phase * 90f + i * 18f, cx, cy);
            canvas.drawOval(tempRect, linePaint);
            canvas.restore();
        }
    }

    private void drawCrimsonBrackets(Canvas canvas, ThemeManager.ThemeSpec theme,
                                     float d, float w, float h, float phase) {
        float left = w * 0.69f;
        float right = w * 0.94f;
        float top = h * 0.22f;
        float bottom = h * 0.78f;
        float cut = 14f * d;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 180));
        path.reset();
        path.moveTo(left + cut, top); path.lineTo(left, top); path.lineTo(left, top + cut);
        path.moveTo(right - cut, top); path.lineTo(right, top); path.lineTo(right, top + cut);
        path.moveTo(left, bottom - cut); path.lineTo(left, bottom); path.lineTo(left + cut, bottom);
        path.moveTo(right, bottom - cut); path.lineTo(right, bottom); path.lineTo(right - cut, bottom);
        canvas.drawPath(path, linePaint);
        float sweepX = left + (right - left) * phase;
        linePaint.setColor(ThemeManager.withAlpha(theme.accent2, 120));
        linePaint.setStrokeWidth(1f * d);
        canvas.drawLine(sweepX, top + 5f * d, sweepX, bottom - 5f * d, linePaint);
    }

    private void drawEmeraldMatrix(Canvas canvas, ThemeManager.ThemeSpec theme,
                                   float d, float w, float h, float phase) {
        float left = w * 0.68f;
        float right = w * 0.96f;
        float top = h * 0.16f;
        float bottom = h * 0.84f;
        linePaint.setStrokeWidth(0.7f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 46));
        for (int i = 0; i < 7; i++) {
            float x = left + (right - left) * i / 6f;
            canvas.drawLine(x, top, x, bottom, linePaint);
        }
        for (int i = 0; i < 5; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, linePaint);
        }
        smallTextPaint.setTypeface(Typeface.MONOSPACE);
        smallTextPaint.setTextSize(7.5f * d);
        smallTextPaint.setColor(ThemeManager.withAlpha(theme.success, 180));
        for (int i = 0; i < 4; i++) {
            int value = (int) ((phase * 1000f + i * 173f) % 999f);
            canvas.drawText(String.format(java.util.Locale.US, "%03d", value), left + i * 22f * d, bottom - 5f * d, smallTextPaint);
        }
    }

    private void drawArcticPrism(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float phase) {
        float cx = w * 0.82f;
        float cy = h * 0.49f;
        float r = Math.min(42f * d, h * 0.31f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.25f * d);
        for (int k = 0; k < 3; k++) {
            path.reset();
            for (int i = 0; i <= 6; i++) {
                double a = Math.toRadians(i * 60f - 90f + phase * 25f + k * 12f);
                float rr = r - k * 8f * d;
                float x = cx + (float) Math.cos(a) * rr;
                float y = cy + (float) Math.sin(a) * rr;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            linePaint.setColor(ThemeManager.withAlpha(k == 1 ? theme.accent2 : theme.accent, 150 - k * 26));
            canvas.drawPath(path, linePaint);
        }
    }

    private void drawSakuraParticles(Canvas canvas, ThemeManager.ThemeSpec theme,
                                     float d, float w, float h, float phase) {
        glowPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            float local = (phase + i * 0.083f) % 1f;
            float x = w * 0.67f + (i % 4) * 24f * d + (float) Math.sin(local * 6.28f + i) * 7f * d;
            float y = h * (0.12f + local * 0.76f);
            float r = (1.6f + (i % 3) * 0.6f) * d;
            glowPaint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2, 105 + (i % 4) * 25));
            canvas.save();
            canvas.rotate(local * 220f, x, y);
            canvas.drawOval(x - r * 1.7f, y - r, x + r * 1.7f, y + r, glowPaint);
            canvas.restore();
        }
    }

    private void drawSolarSpokes(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float phase, float pulse) {
        float cx = w * 0.82f;
        float cy = h * 0.50f;
        float inner = 18f * d;
        float outer = (35f + pulse * 6f) * d;
        linePaint.setStrokeWidth(1.6f * d);
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5f + phase * 360f);
            float x1 = cx + (float) Math.cos(a) * inner;
            float y1 = cy + (float) Math.sin(a) * inner;
            float x2 = cx + (float) Math.cos(a) * outer;
            float y2 = cy + (float) Math.sin(a) * outer;
            linePaint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2, 125));
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }
    }

    private void drawMidnightGrid(Canvas canvas, ThemeManager.ThemeSpec theme,
                                  float d, float w, float h, float phase) {
        float left = w * 0.68f;
        float right = w * 0.96f;
        float top = h * 0.18f;
        float bottom = h * 0.82f;
        linePaint.setStrokeWidth(0.65f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.muted, 45));
        for (int i = 0; i <= 8; i++) {
            float x = left + (right - left) * i / 8f;
            canvas.drawLine(x, top, x, bottom, linePaint);
        }
        float scanY = top + (bottom - top) * phase;
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 185));
        linePaint.setStrokeWidth(1.2f * d);
        canvas.drawLine(left, scanY, right, scanY, linePaint);
    }

    private void drawTitaniumHud(Canvas canvas, ThemeManager.ThemeSpec theme,
                                 float d, float w, float h, float phase) {
        float cx = w * 0.82f;
        float cy = h * 0.50f;
        float r = Math.min(40f * d, h * 0.30f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * d);
        for (int i = 0; i < 8; i++) {
            float start = i * 45f + phase * 42f;
            linePaint.setColor(ThemeManager.withAlpha(i % 2 == 0 ? theme.accent : theme.accent2, 145));
            tempRect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(tempRect, start, 25f, false, linePaint);
        }
        linePaint.setStrokeWidth(1f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.muted, 90));
        canvas.drawLine(cx - r * 0.72f, cy, cx + r * 0.72f, cy, linePaint);
        canvas.drawLine(cx, cy - r * 0.72f, cx, cy + r * 0.72f, linePaint);
    }

    private void drawCircuitMesh(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                                 float d, float w, float h, float phase) {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(themeIndex == 3 ? 1.15f * d : 0.8f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, themeIndex == 8 ? 24 : 38));

        float top = h * 0.18f;
        float mid = h * 0.50f;
        float bottom = h * 0.82f;
        float start = w * 0.04f;
        float end = w * 0.64f;
        float step = Math.max(38f * d, w / 9f);

        for (float x = start; x < end; x += step) {
            float bend = ((int) (x / step) % 2 == 0) ? 8f * d : -8f * d;
            path.reset();
            path.moveTo(x, top);
            path.lineTo(x + 10f * d, top);
            path.lineTo(x + 20f * d, mid + bend);
            path.lineTo(x + 36f * d, mid + bend);
            path.lineTo(x + 47f * d, bottom);
            canvas.drawPath(path, linePaint);
        }

        float movingX = start + (end - start) * phase;
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(ThemeManager.withAlpha(theme.accent2, 190));
        glowPaint.setShadowLayer(7f * d, 0f, 0f, ThemeManager.withAlpha(theme.accent, 145));
        canvas.drawCircle(movingX, mid, 2.1f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawOrbitalCore(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                                 float d, float w, float h, float phase, float pulse) {
        float cx = Math.min(w * 0.18f, 82f * d);
        float cy = h * 0.50f;
        float base = Math.min(h * 0.28f, 35f * d);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(1f * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 82));
        canvas.drawCircle(cx, cy, base * 1.28f, linePaint);

        linePaint.setStrokeWidth((themeIndex == 7 ? 2.7f : 2f) * d);
        linePaint.setColor(ThemeManager.withAlpha(theme.accent, 200));
        tempRect.set(cx - base, cy - base, cx + base, cy + base);
        canvas.drawArc(tempRect, -90f + phase * 360f, 112f, false, linePaint);
        canvas.drawArc(tempRect, 100f + phase * 360f, 76f, false, linePaint);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(ThemeManager.withAlpha(theme.accent, 34 + (int) (pulse * 42f)));
        glowPaint.setShadowLayer((7f + pulse * 5f) * d, 0f, 0f,
                ThemeManager.withAlpha(theme.accent, 165));
        canvas.drawCircle(cx, cy, base * 0.64f, glowPaint);
        glowPaint.clearShadowLayer();

        textPaint.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.min(18f * d, h * 0.17f));
        textPaint.setColor(theme.text);
        canvas.drawText("OE", cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);

        float dotAngle = (float) (phase * Math.PI * 2.0);
        float dotX = cx + (float) Math.cos(dotAngle) * base;
        float dotY = cy + (float) Math.sin(dotAngle) * base;
        glowPaint.setColor(theme.accent2);
        glowPaint.setShadowLayer(6f * d, 0f, 0f, theme.accent);
        canvas.drawCircle(dotX, dotY, 2.5f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawOrbitingWordmark(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                                      float d, float w, float h, float phase) {
        float cx = Math.min(w * 0.18f, 82f * d);
        float cy = h * 0.50f;
        float radius = Math.min(h * 0.405f, 50f * d);

        textOrbit.reset();
        textOrbit.addCircle(cx, cy, radius, Path.Direction.CW);
        smallTextPaint.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        smallTextPaint.setTextSize(Math.max(6.2f * d, Math.min(8.1f * d, h * 0.056f)));
        smallTextPaint.setColor(ThemeManager.withAlpha(themeIndex == 8 ? theme.text : theme.accent, 215));
        smallTextPaint.setStyle(Paint.Style.FILL);
        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setLetterSpacing(themeIndex == 4 || themeIndex == 8 ? 0.15f : 0.10f);

        canvas.save();
        float direction = (themeIndex == 1 || themeIndex == 6) ? -1f : 1f;
        canvas.rotate(direction * phase * 360f, cx, cy);
        canvas.drawTextOnPath(ORBIT_TEXT, textOrbit, 0f, 0f, smallTextPaint);
        canvas.restore();
    }

    private void drawMainWordmark(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                                  float d, float w, float h, float phase) {
        float left = Math.min(w * 0.34f, 150f * d);
        float usable = Math.max(120f * d, w - left - 80f * d);
        float drift = (float) Math.sin(phase * Math.PI * 2.0) * 4f * d;

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        float titleSize = Math.min(21f * d, usable / 10.3f);
        titleSize = Math.max(13.5f * d, titleSize);
        textPaint.setTextSize(titleSize);
        textPaint.setColor(theme.text);
        if (themeIndex != 8) {
            textPaint.setShadowLayer(8f * d, 0f, 0f, ThemeManager.withAlpha(theme.accent, 95));
        }
        canvas.drawText("ONECORE", left + drift, h * 0.39f, textPaint);
        textPaint.clearShadowLayer();

        float firstWidth = textPaint.measureText("ONECORE ");
        textPaint.setColor(theme.accent);
        canvas.drawText("EDGE", left + drift + firstWidth, h * 0.39f, textPaint);

        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setTypeface(Typeface.create(themeIndex == 4 || themeIndex == 8 ? "monospace" : "sans-serif-medium", Typeface.BOLD));
        smallTextPaint.setTextSize(Math.max(7.7f * d, Math.min(9.6f * d, h * 0.070f)));
        smallTextPaint.setLetterSpacing(themeIndex == 3 ? 0.12f : 0.075f);
        smallTextPaint.setColor(theme.muted);
        canvas.drawText(theme.style.toUpperCase(java.util.Locale.US), left, h * 0.57f, smallTextPaint);

        smallTextPaint.setTextSize(Math.max(7.2f * d, Math.min(8.7f * d, h * 0.062f)));
        smallTextPaint.setColor(ThemeManager.withAlpha(theme.success, 235));
        canvas.drawText("● SECURE RUNTIME  //  EDGE NODE ONLINE", left, h * 0.73f, smallTextPaint);
    }

    private void drawTelemetry(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                               float d, float w, float h, float phase, float pulse) {
        float right = w - 13f * d;
        float y = h * 0.20f;
        float barW = Math.min(39f * d, w * 0.085f);

        for (int i = 0; i < 4; i++) {
            float yy = y + i * 10f * d;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth((themeIndex == 3 ? 1.7f : 1.3f) * d);
            linePaint.setColor(ThemeManager.withAlpha(theme.muted, 60));
            canvas.drawLine(right - barW, yy, right, yy, linePaint);

            float active = barW * (0.28f + 0.64f * (float) Math.abs(
                    Math.sin((phase + i * 0.17f) * Math.PI * 2.0)));
            linePaint.setColor(ThemeManager.withAlpha(i == 3 ? theme.success : theme.accent,
                    145 + (int) (pulse * 85f)));
            canvas.drawLine(right - barW, yy, right - barW + active, yy, linePaint);
        }

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(theme.success);
        glowPaint.setShadowLayer(5f * d, 0f, 0f, ThemeManager.withAlpha(theme.success, 145));
        canvas.drawCircle(right - 3f * d, h * 0.78f, 2.3f * d, glowPaint);
        glowPaint.clearShadowLayer();
    }

    private void drawScan(Canvas canvas, ThemeManager.ThemeSpec theme, int themeIndex,
                          float d, float w, float h, float phase) {
        if (themeIndex == 8) {
            return;
        }
        float x = 8f * d + (w - 16f * d) * phase;
        int alpha = themeIndex == 7 ? 58 : 38;
        fillPaint.setShader(new LinearGradient(
                x - 24f * d, 0f, x + 24f * d, 0f,
                new int[]{Color.TRANSPARENT, ThemeManager.withAlpha(theme.accent, alpha), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(Math.max(2f * d, x - 24f * d), 5f * d,
                Math.min(w - 2f * d, x + 24f * d), h - 5f * d, fillPaint);
        fillPaint.setShader(null);
    }
}
