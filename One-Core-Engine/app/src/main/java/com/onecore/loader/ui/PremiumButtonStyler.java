package com.onecore.loader.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.onecore.loader.R;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Applies the polished OneCore Edge button language to the actual Login/Main controls.
 *
 * <p>The existing ThemeManager remains the source of truth for colors. This class only adds
 * depth, gloss, neon edge light, ripple feedback and consistent typography to the important
 * clickable controls without introducing bitmap assets.</p>
 */
public final class PremiumButtonStyler {
    private static final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();
    private static final Set<View> ENTRANCE_ANIMATED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private PremiumButtonStyler() {
    }

    public static void attach(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView();
        apply(activity);
        root.post(() -> apply(activity));
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> apply(activity);
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LISTENERS.put(activity, listener);
        }
    }

    public static void detach(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        if (listener != null && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void apply(Activity activity) {
        ThemeManager.ThemeSpec theme = ThemeManager.current(activity);
        int themeIndex = ThemeManager.currentIndex(activity);

        // Login controls.
        stylePrimary(activity.findViewById(R.id.btnSignIn), theme, themeIndex, 40L);
        styleConnectCard(activity.findViewById(R.id.init), theme);
        styleOutline(activity.findViewById(R.id.telegram), theme, themeIndex, 90L);
        styleIcon(activity.findViewById(R.id.paste), theme, themeIndex, true, 120L);
        styleIcon(activity.findViewById(R.id.vis_pwd), theme, themeIndex, true, 135L);
        styleIcon(activity.findViewById(R.id.show_pwd), theme, themeIndex, true, 135L);

        // Main controls.
        stylePrimary(activity.findViewById(R.id.btn_start_game), theme, themeIndex, 70L);
        styleOutline(activity.findViewById(R.id.installIndia), theme, themeIndex, 110L);
        styleDanger(activity.findViewById(R.id.btn_clear_bgmi_data), theme, themeIndex, 145L);
        styleIcon(activity.findViewById(R.id.btn_settings), theme, themeIndex, false, 35L);
    }

    private static void styleConnectCard(View view, ThemeManager.ThemeSpec theme) {
        if (!(view instanceof MaterialCardView)) return;
        MaterialCardView card = (MaterialCardView) view;
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setStrokeWidth(0);
        card.setRadius(dp(view.getContext(), theme.buttonRadiusDp));
        card.setCardElevation(dp(view.getContext(), Math.max(8f, theme.elevationDp)));
        card.setPreventCornerOverlap(false);
    }

    private static void stylePrimary(
            View view,
            ThemeManager.ThemeSpec theme,
            int themeIndex,
            long entranceDelay) {
        if (!(view instanceof TextView)) return;
        TextView text = (TextView) view;
        Context context = text.getContext();

        text.setBackground(primaryBackground(context, theme, themeIndex));
        text.setTextColor(contrastInk(theme.accent));
        text.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        text.setGravity(Gravity.CENTER);
        text.setLetterSpacing(themeIndex == 4 || themeIndex == 8 ? 0.12f : 0.085f);
        text.setIncludeFontPadding(false);
        text.setShadowLayer(
                dp(context, 1.4f),
                0f,
                dp(context, 0.6f),
                ThemeManager.withAlpha(Color.BLACK, 105));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            text.setElevation(dp(context, Math.max(8f, theme.elevationDp * 0.72f)));
        }
        animateEntrance(text, entranceDelay);
    }

    private static void styleOutline(
            View view,
            ThemeManager.ThemeSpec theme,
            int themeIndex,
            long entranceDelay) {
        if (!(view instanceof TextView)) return;
        TextView text = (TextView) view;
        Context context = text.getContext();

        text.setBackground(outlineBackground(context, theme, themeIndex));
        text.setTextColor(theme.accent);
        text.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        text.setGravity(Gravity.CENTER);
        text.setLetterSpacing(themeIndex == 4 || themeIndex == 8 ? 0.10f : 0.055f);
        text.setIncludeFontPadding(false);
        text.setShadowLayer(
                dp(context, 2.7f),
                0f,
                0f,
                ThemeManager.withAlpha(theme.accent, 165));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            text.setElevation(dp(context, Math.max(4f, theme.elevationDp * 0.45f)));
        }
        animateEntrance(text, entranceDelay);
    }

    private static void styleDanger(
            View view,
            ThemeManager.ThemeSpec theme,
            int themeIndex,
            long entranceDelay) {
        if (!(view instanceof TextView)) return;
        TextView text = (TextView) view;
        Context context = text.getContext();

        float radius = dp(context, Math.max(4f, theme.buttonRadiusDp));
        GradientDrawable base = new GradientDrawable(
                orientation(themeIndex),
                new int[]{
                        ThemeManager.withAlpha(mix(theme.surfaceAlt, theme.error, 0.10f), 247),
                        ThemeManager.withAlpha(theme.surface, 247)});
        base.setCornerRadius(radius);
        base.setStroke(
                dp(context, Math.max(1f, theme.strokeDp)),
                ThemeManager.withAlpha(theme.error, 220));

        Drawable background = base;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            background = new RippleDrawable(
                    ColorStateList.valueOf(ThemeManager.withAlpha(theme.error, 72)),
                    base,
                    roundedMask(radius));
        }

        text.setBackground(background);
        text.setTextColor(theme.error);
        text.setTypeface(Typeface.create(theme.headingFont, Typeface.BOLD));
        text.setGravity(Gravity.CENTER);
        text.setLetterSpacing(themeIndex == 4 || themeIndex == 8 ? 0.11f : 0.07f);
        text.setIncludeFontPadding(false);
        text.setShadowLayer(
                dp(context, 2.2f),
                0f,
                0f,
                ThemeManager.withAlpha(theme.error, 120));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            text.setElevation(dp(context, Math.max(4f, theme.elevationDp * 0.42f)));
        }
        animateEntrance(text, entranceDelay);
    }

    private static void styleIcon(
            View view,
            ThemeManager.ThemeSpec theme,
            int themeIndex,
            boolean tint,
            long entranceDelay) {
        if (!(view instanceof ImageView)) return;
        ImageView image = (ImageView) view;
        Context context = image.getContext();
        float radius = view.getId() == R.id.btn_settings
                ? Math.max(14f, theme.buttonRadiusDp * 0.72f)
                : Math.max(10f, theme.buttonRadiusDp * 0.55f);

        image.setBackground(iconBackground(context, theme, themeIndex, radius));
        if (tint) {
            image.setColorFilter(theme.accent);
        }
        image.setAlpha(1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            image.setElevation(dp(context, Math.max(4f, theme.elevationDp * 0.55f)));
        }
        animateEntrance(image, entranceDelay);
    }

    private static Drawable primaryBackground(
            Context context,
            ThemeManager.ThemeSpec theme,
            int themeIndex) {
        float radius = dp(context, Math.max(4f, theme.buttonRadiusDp));
        int bright = mix(theme.accent, Color.WHITE, themeIndex == 5 ? 0.23f : 0.14f);
        int deep = mix(theme.accent2, Color.BLACK, themeIndex == 8 ? 0.08f : 0.19f);

        GradientDrawable base = new GradientDrawable(
                orientation(themeIndex),
                new int[]{bright, theme.accent, theme.accent2, deep});
        base.setCornerRadius(radius);
        base.setStroke(
                dp(context, themeIndex == 3 || themeIndex == 9 ? 2f : 1f),
                ThemeManager.withAlpha(mix(theme.accent, Color.WHITE, 0.34f), 235));

        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(72, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.TRANSPARENT});
        gloss.setCornerRadius(radius);

        LayerDrawable enabled = new LayerDrawable(new Drawable[]{base, gloss});

        GradientDrawable disabled = new GradientDrawable(
                orientation(themeIndex),
                new int[]{mix(theme.surfaceAlt, theme.muted, 0.16f), theme.surface});
        disabled.setCornerRadius(radius);
        disabled.setStroke(dp(context, 1f), ThemeManager.withAlpha(theme.muted, 70));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        states.addState(new int[]{}, enabled);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(Color.argb(86, 255, 255, 255)),
                    states,
                    roundedMask(radius));
        }
        return states;
    }

    private static Drawable outlineBackground(
            Context context,
            ThemeManager.ThemeSpec theme,
            int themeIndex) {
        float radius = dp(context, Math.max(4f, theme.buttonRadiusDp));
        int glassA = ThemeManager.withAlpha(mix(theme.surfaceAlt, theme.accent, 0.08f), 247);
        int glassB = ThemeManager.withAlpha(mix(theme.surface, theme.accent2, 0.05f), 247);

        GradientDrawable base = new GradientDrawable(
                orientation(themeIndex),
                new int[]{glassA, glassB});
        base.setCornerRadius(radius);
        base.setStroke(
                dp(context, Math.max(1f, theme.strokeDp)),
                ThemeManager.withAlpha(theme.accent, themeIndex == 8 ? 150 : 225));

        GradientDrawable shine = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ThemeManager.withAlpha(theme.accent, 22), Color.TRANSPARENT});
        shine.setCornerRadius(radius);
        LayerDrawable enabled = new LayerDrawable(new Drawable[]{base, shine});

        GradientDrawable disabled = new GradientDrawable();
        disabled.setColor(ThemeManager.withAlpha(theme.surface, 220));
        disabled.setCornerRadius(radius);
        disabled.setStroke(dp(context, 1f), ThemeManager.withAlpha(theme.muted, 55));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        states.addState(new int[]{}, enabled);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(ThemeManager.withAlpha(theme.accent, 68)),
                    states,
                    roundedMask(radius));
        }
        return states;
    }

    private static Drawable iconBackground(
            Context context,
            ThemeManager.ThemeSpec theme,
            int themeIndex,
            float radiusDp) {
        float radius = dp(context, radiusDp);
        GradientDrawable glass = new GradientDrawable(
                orientation(themeIndex),
                new int[]{
                        ThemeManager.withAlpha(mix(theme.surfaceAlt, theme.accent, 0.10f), 250),
                        ThemeManager.withAlpha(theme.surface, 245)});
        glass.setCornerRadius(radius);
        glass.setStroke(dp(context, 1f), ThemeManager.withAlpha(theme.accent, 175));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(
                    ColorStateList.valueOf(ThemeManager.withAlpha(theme.accent, 75)),
                    glass,
                    roundedMask(radius));
        }
        return glass;
    }

    private static GradientDrawable roundedMask(float radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return mask;
    }

    private static GradientDrawable.Orientation orientation(int themeIndex) {
        switch (themeIndex) {
            case 1:
            case 7:
                return GradientDrawable.Orientation.LEFT_RIGHT;
            case 2:
            case 6:
                return GradientDrawable.Orientation.TR_BL;
            case 3:
            case 8:
                return GradientDrawable.Orientation.TOP_BOTTOM;
            case 4:
                return GradientDrawable.Orientation.BL_TR;
            case 9:
                return GradientDrawable.Orientation.BR_TL;
            case 0:
            case 5:
            default:
                return GradientDrawable.Orientation.TL_BR;
        }
    }

    private static void animateEntrance(View view, long delay) {
        if (view == null || ENTRANCE_ANIMATED.contains(view)) return;
        ENTRANCE_ANIMATED.add(view);
        view.setAlpha(0f);
        view.setScaleX(0.985f);
        view.setScaleY(0.985f);
        view.setTranslationY(dp(view.getContext(), 6f));
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(280L)
                .start();
    }

    private static int mix(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int contrastInk(int color) {
        double luminance = (0.299d * Color.red(color)
                + 0.587d * Color.green(color)
                + 0.114d * Color.blue(color)) / 255d;
        return luminance > 0.62d ? Color.rgb(3, 8, 14) : Color.WHITE;
    }

    private static int dp(Context context, float value) {
        return Math.max(1, Math.round(value * context.getResources().getDisplayMetrics().density));
    }
}
