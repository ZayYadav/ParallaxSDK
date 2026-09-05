package com.onecore.loader.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.onecore.loader.R;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/** Persistent visual theme system for all OneCore Edge Loader screens. */
public final class ThemeManager {

    private static final String PREFS = "onecore_edge_ui";
    private static final String KEY_THEME = "theme_index";
    private static final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();

    public static final class ThemeSpec {
        public final String name;
        public final String style;
        public final int bgTop;
        public final int bgBottom;
        public final int surface;
        public final int surfaceAlt;
        public final int accent;
        public final int accent2;
        public final int text;
        public final int muted;
        public final int success;
        public final int error;
        public final float cardRadiusDp;
        public final float buttonRadiusDp;
        public final int strokeDp;
        public final float elevationDp;
        public final String headingFont;
        public final boolean uppercaseHeadings;

        ThemeSpec(
                String name,
                String style,
                String bgTop,
                String bgBottom,
                String surface,
                String surfaceAlt,
                String accent,
                String accent2,
                String text,
                String muted,
                String success,
                String error,
                float cardRadiusDp,
                float buttonRadiusDp,
                int strokeDp,
                float elevationDp,
                String headingFont,
                boolean uppercaseHeadings) {
            this.name = name;
            this.style = style;
            this.bgTop = Color.parseColor(bgTop);
            this.bgBottom = Color.parseColor(bgBottom);
            this.surface = Color.parseColor(surface);
            this.surfaceAlt = Color.parseColor(surfaceAlt);
            this.accent = Color.parseColor(accent);
            this.accent2 = Color.parseColor(accent2);
            this.text = Color.parseColor(text);
            this.muted = Color.parseColor(muted);
            this.success = Color.parseColor(success);
            this.error = Color.parseColor(error);
            this.cardRadiusDp = cardRadiusDp;
            this.buttonRadiusDp = buttonRadiusDp;
            this.strokeDp = strokeDp;
            this.elevationDp = elevationDp;
            this.headingFont = headingFont;
            this.uppercaseHeadings = uppercaseHeadings;
        }
    }

    private static final ThemeSpec[] THEMES = new ThemeSpec[]{
            new ThemeSpec("Edge Cyan", "Neon glass • rounded",
                    "#050A12", "#02050A", "#E80A111B", "#D8111C29",
                    "#21D4FD", "#1976FF", "#F5FAFF", "#8796AA",
                    "#43DBA4", "#FF5D6C", 28f, 26f, 1, 10f,
                    "sans-serif-black", false),
            new ThemeSpec("Obsidian Gold", "Luxury • soft capsule",
                    "#090806", "#020202", "#F1161511", "#EC211C11",
                    "#FFD34E", "#FF9F1A", "#FFF9EA", "#9E9278",
                    "#79E2A8", "#FF6B65", 34f, 30f, 1, 14f,
                    "sans-serif-medium", false),
            new ThemeSpec("Neon Violet", "Cyber violet • floating",
                    "#0D0718", "#04010B", "#E51A0D2E", "#E5281244",
                    "#B76CFF", "#5B8CFF", "#FAF5FF", "#A18DB7",
                    "#5FE6C3", "#FF668E", 24f, 18f, 2, 16f,
                    "sans-serif-black", true),
            new ThemeSpec("Crimson Core", "Sharp tactical • red",
                    "#120506", "#030202", "#F01C0A0D", "#E82A0D12",
                    "#FF4D5A", "#FF8B3D", "#FFF5F5", "#A98B8E",
                    "#66E2A3", "#FF3048", 8f, 6f, 2, 2f,
                    "sans-serif-condensed", true),
            new ThemeSpec("Emerald Matrix", "Technical • green grid",
                    "#03100C", "#010503", "#E80A1B15", "#E7122B20",
                    "#31E69A", "#00BFA6", "#EEFFF8", "#7FA99A",
                    "#6EFFB7", "#FF6574", 14f, 12f, 1, 5f,
                    "monospace", true),
            new ThemeSpec("Arctic Glass", "Frosted • bright blue",
                    "#0B1420", "#05090F", "#D9203141", "#D92B4056",
                    "#8EDBFF", "#C0E7FF", "#FFFFFF", "#A8B8C8",
                    "#75E5C1", "#FF7B88", 32f, 32f, 1, 18f,
                    "sans-serif-medium", false),
            new ThemeSpec("Sakura Pulse", "Soft neon • pink",
                    "#130A12", "#050205", "#ED211421", "#E92D182A",
                    "#FF78C6", "#C76BFF", "#FFF5FC", "#B498AA",
                    "#6CE0B8", "#FF6079", 30f, 22f, 1, 12f,
                    "serif", false),
            new ThemeSpec("Solar Flare", "Warm energy • amber",
                    "#160D03", "#050301", "#F2241608", "#EF34200A",
                    "#FFB323", "#FF6F1D", "#FFF8E8", "#B79A72",
                    "#68DF9F", "#FF5B56", 18f, 9f, 2, 8f,
                    "sans-serif-condensed", true),
            new ThemeSpec("Midnight Mono", "Minimal • monochrome",
                    "#08090B", "#020203", "#F014161A", "#ED1B1E23",
                    "#E7EDF4", "#8B96A3", "#FFFFFF", "#89919B",
                    "#B7F1D5", "#FF727C", 4f, 4f, 1, 0f,
                    "monospace", true),
            new ThemeSpec("Titanium Blue", "Industrial • beveled feel",
                    "#07101A", "#02060B", "#F016222E", "#ED213241",
                    "#45A8FF", "#66D9FF", "#F2F8FF", "#8C9BAB",
                    "#4DDBAE", "#FF6675", 12f, 8f, 2, 6f,
                    "sans-serif-black", false)
    };

    private ThemeManager() {
    }

    public static void attach(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        applyNow(activity);
        View root = activity.getWindow().getDecorView();
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
                suppressTransientOverlays(root);
                applyThemePickerHook(activity);
            };
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LISTENERS.put(activity, listener);
        }
        root.post(() -> {
            suppressTransientOverlays(root);
            applyThemePickerHook(activity);
        });
    }

    public static void detach(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        View root = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        if (listener != null && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    public static ThemeSpec current(Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, 0);
        if (index < 0 || index >= THEMES.length) {
            index = 0;
        }
        return THEMES[index];
    }

    public static int currentIndex(Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, 0);
        return index >= 0 && index < THEMES.length ? index : 0;
    }

    public static int themeCount() {
        return THEMES.length;
    }

    /**
     * Selects a fresh automatic theme for a visible Loader launch.
     * When multiple themes exist, the immediately previous theme is never repeated.
     */
    public static void randomizeForLaunch(Context context) {
        if (context == null || THEMES.length <= 0) {
            return;
        }

        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int previous = preferences.getInt(KEY_THEME, -1);
        Random random = new Random(
                System.nanoTime()
                        ^ android.os.Process.myPid()
                        ^ Thread.currentThread().getId());

        int next;
        if (THEMES.length == 1) {
            next = 0;
        } else if (previous >= 0 && previous < THEMES.length) {
            int offset = 1 + random.nextInt(THEMES.length - 1);
            next = (previous + offset) % THEMES.length;
        } else {
            next = random.nextInt(THEMES.length);
        }

        preferences.edit().putInt(KEY_THEME, next).apply();
    }

    public static void applyNow(Activity activity) {
        if (activity == null) {
            return;
        }
        ThemeSpec spec = current(activity);
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        GradientDrawable windowBackground = gradient(spec.bgTop, spec.bgBottom, 0f, spec.accent, 0);
        content.setBackground(windowBackground);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            ((ViewGroup) content).getChildAt(0).setBackground(
                    gradient(spec.bgTop, spec.bgBottom, 0f, spec.accent, 0));
        }
        styleTree(content, spec);
        applyThemePickerHook(activity);
    }

    private static void styleTree(View view, ThemeSpec spec) {
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setCardBackgroundColor(spec.surface);
            card.setRadius(dp(view.getContext(), spec.cardRadiusDp));
            card.setStrokeColor(withAlpha(spec.accent, 125));
            card.setStrokeWidth(dp(view.getContext(), spec.strokeDp));
            card.setCardElevation(dp(view.getContext(), spec.elevationDp));
        } else if (view instanceof RadioButton) {
            RadioButton radio = (RadioButton) view;
            radio.setTextColor(spec.muted);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                radio.setButtonTintList(new ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{spec.accent, spec.muted}));
            }
        } else if (view instanceof EditText) {
            EditText edit = (EditText) view;
            edit.setTextColor(spec.text);
            edit.setHintTextColor(spec.muted);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                edit.setBackgroundTintList(ColorStateList.valueOf(spec.accent));
            }
        } else if (view instanceof ProgressBar) {
            ProgressBar progress = (ProgressBar) view;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progress.setProgressTintList(ColorStateList.valueOf(spec.accent));
                progress.setIndeterminateTintList(ColorStateList.valueOf(spec.accent));
                progress.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(spec.muted, 55)));
            }
        } else if (view instanceof TextView) {
            styleText((TextView) view, spec);
        } else if (view instanceof ImageView) {
            view.setAlpha(0.98f);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleTree(group.getChildAt(i), spec);
            }
        }
    }

    private static void styleText(TextView textView, ThemeSpec spec) {
        CharSequence raw = textView.getText();
        String text = raw == null ? "" : raw.toString().trim();
        String upper = text.toUpperCase();

        if (isPrimaryButton(upper)) {
            textView.setTextColor(contrastInk(spec.accent));
            textView.setTypeface(Typeface.create(spec.headingFont, Typeface.BOLD));
            textView.setBackground(gradient(spec.accent, spec.accent2,
                    spec.buttonRadiusDp, spec.accent, 0));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textView.setElevation(dp(textView.getContext(), Math.max(2f, spec.elevationDp / 2f)));
            }
            return;
        }

        if (isOutlineButton(upper)) {
            textView.setTextColor(spec.accent);
            textView.setTypeface(Typeface.create(spec.headingFont, Typeface.BOLD));
            textView.setBackground(gradient(withAlpha(spec.surfaceAlt, 245), withAlpha(spec.surface, 245),
                    spec.buttonRadiusDp, spec.accent, Math.max(1, spec.strokeDp)));
            return;
        }

        if (upper.equals("ACTIVE") || upper.equals("SECURE") || upper.contains("READY PROFILE")
                || upper.equals("AUTO SELECTED")) {
            textView.setTextColor(spec.success);
            textView.setBackground(gradient(withAlpha(spec.success, 24), withAlpha(spec.success, 10),
                    Math.max(12f, spec.buttonRadiusDp), withAlpha(spec.success, 90), 1));
            return;
        }

        if (upper.contains("FAILED") || upper.contains("DENIED") || upper.equals("EXPIRED")) {
            textView.setTextColor(spec.error);
            return;
        }

        if (upper.contains("SIGNATURE VERIFIED") || upper.contains("ENCRYPTED VAULT")
                || upper.contains("HTTPS TRANSPORT")) {
            textView.setTextColor(spec.success);
            return;
        }

        if (isHeading(upper, textView)) {
            textView.setTextColor(spec.text);
            textView.setTypeface(Typeface.create(spec.headingFont, Typeface.BOLD));
            if (spec.uppercaseHeadings && text.length() > 0 && text.length() < 40) {
                textView.setAllCaps(true);
            }
            return;
        }

        if (textView.getTextSize() / textView.getResources().getDisplayMetrics().scaledDensity <= 12.5f) {
            textView.setTextColor(spec.muted);
        }
    }

    private static boolean isHeading(String upper, TextView view) {
        return upper.equals("EDGE CONTROL")
                || upper.equals("PROTECTED SESSION")
                || upper.equals("BGMI RUNTIME")
                || upper.equals("EDGE SERVER STATUS")
                || upper.equals("ACCESS CONSOLE")
                || upper.equals("ONECORE EDGE")
                || upper.contains("DOWNLOADING FILES")
                || view.getTextSize() / view.getResources().getDisplayMetrics().scaledDensity >= 18f;
    }

    private static boolean isPrimaryButton(String upper) {
        return upper.equals("START BGMI")
                || upper.equals("CONNECT TO EDGE SERVER")
                || upper.equals("TRY AGAIN")
                || upper.equals("OK")
                || upper.equals("APPLY THEME");
    }

    private static boolean isOutlineButton(String upper) {
        return upper.equals("INSTALL")
                || upper.equals("UNINSTALL")
                || upper.equals("GET ACCESS")
                || upper.equals("GET KEY");
    }

    private static void applyThemePickerHook(Activity activity) {
        // Themes are selected automatically by BoxApplication for every app launch.
        // Keep the top-right brand mark decorative so there is no manual theme picker button.
        View settings = activity.findViewById(R.id.btn_settings);
        if (settings != null) {
            settings.setOnClickListener(null);
            settings.setOnLongClickListener(null);
            settings.setClickable(false);
            settings.setLongClickable(false);
            settings.setFocusable(false);
            settings.setContentDescription("OneCore Edge brand");
        }
    }

    /** Removes modal verification/modifying overlays while the request continues in background. */
    private static void suppressTransientOverlays(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            String text = textView.getText() == null ? "" : textView.getText().toString().toUpperCase();
            if (text.contains("VERIFYING LICENSE") || text.contains("MODIFYING")) {
                View parent = textView.getParent() instanceof View ? (View) textView.getParent() : textView;
                parent.setVisibility(View.GONE);
                parent.setClickable(false);
                parent.setFocusable(false);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                suppressTransientOverlays(group.getChildAt(i));
            }
        }
    }

    public static void showThemePicker(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        ThemeSpec active = current(activity);
        int selectedIndex = currentIndex(activity);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 18));
        shell.setBackground(gradient(active.surface, active.surfaceAlt,
                active.cardRadiusDp, active.accent, Math.max(1, active.strokeDp)));

        TextView title = new TextView(activity);
        title.setText("CHOOSE EDGE THEME");
        title.setTextColor(active.text);
        title.setTextSize(20f);
        title.setTypeface(Typeface.create(active.headingFont, Typeface.BOLD));
        title.setLetterSpacing(0.06f);
        shell.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(activity);
        subtitle.setText("10 complete visual systems • selection is saved automatically");
        subtitle.setTextColor(active.muted);
        subtitle.setTextSize(12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(activity, 5);
        subtitleParams.bottomMargin = dp(activity, 14);
        shell.addView(subtitle, subtitleParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < THEMES.length; i++) {
            final int index = i;
            ThemeSpec spec = THEMES[i];
            TextView row = new TextView(activity);
            row.setText((i == selectedIndex ? "✓  " : "") + spec.name + "\n" + spec.style);
            row.setTextColor(spec.text);
            row.setTextSize(14f);
            row.setTypeface(Typeface.create(spec.headingFont, Typeface.BOLD));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
            row.setBackground(gradient(spec.surfaceAlt, spec.surface,
                    Math.max(7f, spec.cardRadiusDp * 0.62f), spec.accent,
                    Math.max(1, spec.strokeDp)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(activity, 9);
            list.addView(row, rowParams);
            row.setOnClickListener(v -> {
                SharedPreferences preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                preferences.edit().putInt(KEY_THEME, index).apply();
                dialog.dismiss();
                applyNow(activity);
                View decor = activity.getWindow().getDecorView();
                decor.setAlpha(0.76f);
                decor.animate().alpha(1f).setDuration(220L).start();
            });
        }

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 470));
        shell.addView(scrollView, scrollParams);
        dialog.setContentView(shell);
        dialog.setCancelable(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = 0.72f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private static GradientDrawable gradient(int start, int end, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(radiusDp);
        if (strokeDp > 0) {
            drawable.setStroke(strokeDp, strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable themedPanel(Context context, boolean stronger) {
        ThemeSpec spec = current(context);
        return gradient(stronger ? spec.surfaceAlt : spec.surface,
                stronger ? spec.surface : spec.surfaceAlt,
                dp(context, spec.cardRadiusDp),
                withAlpha(spec.accent, 150),
                dp(context, Math.max(1, spec.strokeDp)));
    }

    public static GradientDrawable themedButton(Context context) {
        ThemeSpec spec = current(context);
        return gradient(spec.accent, spec.accent2,
                dp(context, spec.buttonRadiusDp), spec.accent, 0);
    }

    public static int contrastInk(int background) {
        double luminance = (0.299 * Color.red(background)
                + 0.587 * Color.green(background)
                + 0.114 * Color.blue(background)) / 255d;
        return luminance > 0.62d ? Color.parseColor("#071018") : Color.WHITE;
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}