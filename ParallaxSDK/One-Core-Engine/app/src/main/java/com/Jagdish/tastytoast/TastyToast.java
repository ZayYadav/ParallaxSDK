package com.Jagdish.tastytoast;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * In-house OneCore Edge toast renderer.
 *
 * This class intentionally keeps the old TastyToast API surface so existing call sites and
 * packaged modules continue to compile while the third-party toast.aar is no longer required.
 */
public final class TastyToast {
    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    public static final int DEFAULT = 0;
    public static final int SUCCESS = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;
    public static final int INFO = 4;
    public static final int CONFUSING = 5;

    private TastyToast() {
    }

    public static Toast makeText(Context context, String message, int duration, int type) {
        return makeText(context, (CharSequence) message, duration, type);
    }

    public static Toast makeText(Context context, CharSequence message, int duration, int type) {
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        int accent = accentFor(type);
        String icon = iconFor(type);
        String label = labelFor(type);

        LinearLayout root = new LinearLayout(appContext);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(appContext, 14), dp(appContext, 11), dp(appContext, 16), dp(appContext, 11));
        root.setElevation(dp(appContext, 10));

        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setCornerRadius(dp(appContext, 18));
        card.setColor(Color.parseColor("#F0181B22"));
        card.setStroke(dp(appContext, 1), accent);
        root.setBackground(card);

        TextView iconView = new TextView(appContext);
        iconView.setGravity(Gravity.CENTER);
        iconView.setText(icon);
        iconView.setTextColor(Color.WHITE);
        iconView.setTextSize(17f);
        iconView.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(withAlpha(accent, 0x38));
        iconBg.setStroke(dp(appContext, 1), withAlpha(accent, 0xA8));
        iconView.setBackground(iconBg);
        root.addView(iconView, new LinearLayout.LayoutParams(dp(appContext, 38), dp(appContext, 38)));

        LinearLayout copy = new LinearLayout(appContext);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(appContext, 12));

        TextView title = new TextView(appContext);
        title.setText("ONECORE EDGE  •  " + label);
        title.setTextColor(accent);
        title.setTextSize(10f);
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(title);

        TextView body = new TextView(appContext);
        body.setText(message == null ? "" : message);
        body.setTextColor(Color.parseColor("#FFF4F5F7"));
        body.setTextSize(13.5f);
        body.setMaxLines(3);
        body.setPadding(0, dp(appContext, 2), 0, 0);
        copy.addView(body);

        root.addView(copy, copyParams);

        Toast toast = new Toast(appContext);
        toast.setDuration(duration == LENGTH_LONG ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(appContext, 64));
        toast.setView(root);
        toast.show();
        return toast;
    }

    private static int accentFor(int type) {
        switch (type) {
            case SUCCESS:
                return Color.parseColor("#5DE2B1");
            case WARNING:
                return Color.parseColor("#F4BE5E");
            case ERROR:
                return Color.parseColor("#FF667A");
            case INFO:
                return Color.parseColor("#72B7FF");
            case CONFUSING:
                return Color.parseColor("#C59BFF");
            default:
                return Color.parseColor("#D5A94F");
        }
    }

    private static String iconFor(int type) {
        switch (type) {
            case SUCCESS:
                return "✓";
            case WARNING:
                return "!";
            case ERROR:
                return "×";
            case INFO:
                return "i";
            case CONFUSING:
                return "?";
            default:
                return "•";
        }
    }

    private static String labelFor(int type) {
        switch (type) {
            case SUCCESS:
                return "SUCCESS";
            case WARNING:
                return "ATTENTION";
            case ERROR:
                return "ERROR";
            case INFO:
                return "INFO";
            case CONFUSING:
                return "NOTICE";
            default:
                return "EDGE SERVER";
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
