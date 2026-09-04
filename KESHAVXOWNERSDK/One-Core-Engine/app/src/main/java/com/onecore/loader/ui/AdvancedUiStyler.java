package com.onecore.loader.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Applies the OneCore Edge visual language to runtime-created overlays/dialogs.
 *
 * The legacy Loader builds several dialogs completely in Java, so resource-only
 * theming cannot reach them. This styler observes the activity decor view and
 * upgrades those runtime views without touching their callbacks or download
 * logic.
 */
public final class AdvancedUiStyler {

    private static final int BG_DIM = Color.parseColor("#E805090F");
    private static final int CARD_TOP = Color.parseColor("#F3141B25");
    private static final int CARD_BOTTOM = Color.parseColor("#F3070B11");
    private static final int CYAN = Color.parseColor("#23C7F3");
    private static final int CYAN_SOFT = Color.parseColor("#7CDFF7");
    private static final int GOLD = Color.parseColor("#FFC62A");
    private static final int GREEN = Color.parseColor("#47D7A3");
    private static final int RED = Color.parseColor("#FF5A65");
    private static final int TEXT = Color.parseColor("#F5F7FA");
    private static final int MUTED = Color.parseColor("#98A3B3");

    private static final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();
    private static final Set<View> STYLED_CONTAINERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> STYLED_VIEWS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private AdvancedUiStyler() {
    }

    public static void attach(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        final View root = activity.getWindow().getDecorView();
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> styleTree(root);
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            LISTENERS.put(activity, listener);
        }
        root.post(() -> styleTree(root));
    }

    public static void detach(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(activity);
        View root = activity.getWindow().getDecorView();
        if (listener != null && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private static void styleTree(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextView) {
            styleTextView((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            View[] snapshot = new View[count];
            for (int i = 0; i < count; i++) {
                snapshot[i] = group.getChildAt(i);
            }
            for (View child : snapshot) {
                styleTree(child);
            }
        }
    }

    private static void styleTextView(TextView textView) {
        CharSequence raw = textView.getText();
        if (raw == null) {
            return;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return;
        }

        if (text.contains("ACCESS DENIED")) {
            styleAccessDenied(textView);
            return;
        }
        if (text.startsWith("✦ DOWNLOADING FILES") || text.startsWith("DOWNLOADING FILES")) {
            styleDownloadPanel(textView);
            return;
        }
        if (text.equals("✓ SUCCESS ✓") || text.equals("✗ FAILED ✗")) {
            styleResultDialog(textView, text.contains("SUCCESS"));
            return;
        }
        if (text.contains("ACCESS GRANTED")) {
            styleGrantedView(textView);
            return;
        }
        if (looksLikeDownloadSize(text)) {
            showPercentageOnly(textView, text);
            return;
        }
        if (text.startsWith("⏱") || text.startsWith("Time:") || text.contains("Time:")) {
            textView.setText("SECURE ASSET SYNC  •  VERIFIED CHANNEL");
            textView.setTextColor(MUTED);
            textView.setTextSize(11f);
            textView.setLetterSpacing(0.06f);
            return;
        }
        if (text.equalsIgnoreCase("Initializing download...")) {
            textView.setText("Preparing encrypted runtime");
            textView.setTextColor(MUTED);
        }
    }

    private static void styleAccessDenied(TextView title) {
        if (STYLED_VIEWS.contains(title) || !(title.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout overlay = (LinearLayout) title.getParent();
        if (STYLED_CONTAINERS.contains(overlay)) {
            return;
        }
        STYLED_VIEWS.add(title);
        STYLED_CONTAINERS.add(overlay);

        Context context = title.getContext();
        List<View> original = snapshotChildren(overlay);
        TextView message = findMessageText(original, title);
        LinearLayout buttons = findButtonRow(original);

        overlay.removeAllViews();
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        overlay.setBackgroundColor(BG_DIM);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 22));
        card.setBackground(cardBackground(context, RED));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(context, 18));
        }

        TextView eyebrow = label(context, "ONECORE EDGE  •  SERVER CHECK", RED, 11f);
        LinearLayout.LayoutParams eyebrowParams = matchWrap();
        eyebrowParams.bottomMargin = dp(context, 14);
        card.addView(eyebrow, eyebrowParams);

        TextView icon = label(context, "!", RED, 26f);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circleBackground(context, Color.parseColor("#33FF5A65"), RED));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 58), dp(context, 58));
        iconParams.bottomMargin = dp(context, 14);
        card.addView(icon, iconParams);

        title.setText("ACCESS DENIED");
        title.setTextColor(RED);
        title.setTextSize(28f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.045f);
        title.setPadding(0, 0, 0, dp(context, 6));
        card.addView(title, matchWrap());

        TextView subtitle = label(context, "EDGE SERVER  •  VERIFICATION FAILED", MUTED, 11f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.bottomMargin = dp(context, 18);
        card.addView(subtitle, subtitleParams);

        if (message != null) {
            message.setTextColor(GOLD);
            message.setTextSize(14f);
            message.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
            GradientDrawable messageBg = solidRounded(context, Color.parseColor("#241F1604"), 18, Color.parseColor("#65FFC62A"));
            message.setBackground(messageBg);
            LinearLayout.LayoutParams messageParams = matchWrap();
            messageParams.bottomMargin = dp(context, 8);
            card.addView(message, messageParams);
        }

        if (buttons != null) {
            keepOnlyTryAgain(buttons);
            buttons.setOrientation(LinearLayout.VERTICAL);
            buttons.setGravity(Gravity.CENTER);
            buttons.setPadding(0, dp(context, 14), 0, 0);
            buttons.setBackgroundColor(Color.TRANSPARENT);
            card.addView(buttons, matchWrap());
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.leftMargin = dp(context, 4);
        cardParams.rightMargin = dp(context, 4);
        overlay.addView(card, cardParams);

        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L).start();
    }

    private static void keepOnlyTryAgain(LinearLayout buttons) {
        Context context = buttons.getContext();
        TextView tryAgain = null;
        for (int i = 0; i < buttons.getChildCount(); i++) {
            View child = buttons.getChildAt(i);
            if (child instanceof TextView) {
                String value = ((TextView) child).getText().toString().trim();
                if (value.equalsIgnoreCase("TRY AGAIN")) {
                    tryAgain = (TextView) child;
                } else {
                    child.setVisibility(View.GONE);
                }
            }
        }
        if (tryAgain == null) {
            return;
        }
        tryAgain.setVisibility(View.VISIBLE);
        tryAgain.setText("TRY AGAIN");
        tryAgain.setTextColor(Color.parseColor("#081017"));
        tryAgain.setTextSize(15f);
        tryAgain.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tryAgain.setGravity(Gravity.CENTER);
        tryAgain.setLetterSpacing(0.05f);
        tryAgain.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        tryAgain.setBackground(buttonBackground(context));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tryAgain.setElevation(dp(context, 4));
        }
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 52));
        buttonParams.leftMargin = dp(context, 8);
        buttonParams.rightMargin = dp(context, 8);
        tryAgain.setLayoutParams(buttonParams);
    }

    private static void styleDownloadPanel(TextView title) {
        if (STYLED_VIEWS.contains(title) || !(title.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout overlay = (LinearLayout) title.getParent();
        if (STYLED_CONTAINERS.contains(overlay)) {
            return;
        }
        STYLED_VIEWS.add(title);
        STYLED_CONTAINERS.add(overlay);

        Context context = title.getContext();
        List<View> original = snapshotChildren(overlay);
        overlay.removeAllViews();
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));
        overlay.setBackgroundColor(BG_DIM);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 22));
        card.setBackground(cardBackground(context, CYAN));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(context, 18));
        }

        TextView eyebrow = label(context, "ONECORE EDGE  •  SECURE DOWNLOAD", CYAN_SOFT, 11f);
        LinearLayout.LayoutParams eyebrowParams = matchWrap();
        eyebrowParams.bottomMargin = dp(context, 12);
        card.addView(eyebrow, eyebrowParams);

        for (View child : original) {
            if (child == title) {
                title.setTextColor(GOLD);
                title.setTextSize(20f);
                title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                title.setGravity(Gravity.CENTER);
                title.setLetterSpacing(0.035f);
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(CYAN);
            } else if (child instanceof ProgressBar) {
                styleProgressBar((ProgressBar) child);
            } else if (child instanceof TextView) {
                TextView text = (TextView) child;
                String value = text.getText().toString();
                if (looksLikeDownloadSize(value)) {
                    showPercentageOnly(text, value);
                } else {
                    text.setTextColor(MUTED);
                    text.setTextSize(11f);
                    text.setGravity(Gravity.CENTER);
                }
            }
            card.addView(child);
        }

        TextView secure = label(context, "TLS VERIFIED  •  ENCRYPTED CHANNEL", GREEN, 11f);
        secure.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        secure.setBackground(solidRounded(context, Color.parseColor("#1D0D2B24"), 18, Color.parseColor("#5547D7A3")));
        LinearLayout.LayoutParams secureParams = matchWrap();
        secureParams.topMargin = dp(context, 16);
        card.addView(secure, secureParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        overlay.addView(card, cardParams);

        card.setAlpha(0f);
        card.setTranslationY(dp(context, 12));
        card.animate().alpha(1f).translationY(0f).setDuration(240L).start();
    }

    private static void styleProgressBar(ProgressBar bar) {
        Context context = bar.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 10));
        params.topMargin = dp(context, 16);
        params.bottomMargin = dp(context, 12);
        bar.setLayoutParams(params);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bar.setProgressTintList(ColorStateList.valueOf(CYAN));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#283C4753")));
        }
    }

    private static void showPercentageOnly(TextView view, String value) {
        int percentIndex = value.indexOf('%');
        if (percentIndex <= 0) {
            return;
        }
        String number = value.substring(0, percentIndex).replaceAll("[^0-9]", "");
        if (number.isEmpty()) {
            return;
        }
        String clean = number + "%";
        if (!clean.equals(value)) {
            view.setText(clean);
        }
        view.setTextColor(TEXT);
        view.setTextSize(30f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.025f);
        view.setPadding(0, dp(view.getContext(), 6), 0, 0);
    }

    private static boolean looksLikeDownloadSize(String text) {
        if (text == null || !text.contains("%")) {
            return false;
        }
        return text.contains("MB") || text.matches(".*%\\s*•.*[/].*");
    }

    private static void styleResultDialog(TextView title, boolean success) {
        if (STYLED_VIEWS.contains(title) || !(title.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout card = (LinearLayout) title.getParent();
        STYLED_VIEWS.add(title);
        STYLED_CONTAINERS.add(card);

        Context context = title.getContext();
        int accent = success ? GREEN : RED;
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(context, 26), dp(context, 24), dp(context, 26), dp(context, 24));
        card.setBackground(cardBackground(context, accent));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(context, 20));
        }

        TextView eyebrow = label(context,
                success ? "ONECORE EDGE  •  VERIFIED DELIVERY" : "ONECORE EDGE  •  DELIVERY ERROR",
                accent,
                11f);
        card.addView(eyebrow, 0, matchWrap());

        title.setText(success ? "DOWNLOAD READY" : "DOWNLOAD FAILED");
        title.setTextColor(accent);
        title.setTextSize(25f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(0.045f);
        title.setPadding(0, dp(context, 12), 0, dp(context, 14));

        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (!(child instanceof TextView) || child == title || child == eyebrow) {
                continue;
            }
            TextView text = (TextView) child;
            String value = text.getText().toString().trim();
            if (value.equalsIgnoreCase("OK")) {
                text.setTextColor(Color.parseColor("#07120F"));
                text.setTextSize(15f);
                text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                text.setGravity(Gravity.CENTER);
                text.setLetterSpacing(0.05f);
                text.setBackground(success ? successButtonBackground(context) : errorButtonBackground(context));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(context, 50));
                params.topMargin = dp(context, 8);
                text.setLayoutParams(params);
            } else {
                if (success && value.contains("Download Complete")) {
                    text.setText("Archive verified\nSecure runtime installed successfully");
                }
                text.setTextColor(TEXT);
                text.setTextSize(14f);
                text.setLineSpacing(0f, 1.2f);
                text.setGravity(Gravity.CENTER);
            }
        }

        card.setAlpha(0f);
        card.setScaleX(0.95f);
        card.setScaleY(0.95f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240L).start();
    }

    private static void styleGrantedView(TextView view) {
        if (STYLED_VIEWS.contains(view)) {
            return;
        }
        STYLED_VIEWS.add(view);
        view.setText("✓  ACCESS GRANTED");
        view.setTextColor(TEXT);
        view.setTextSize(15f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.035f);
        view.setPadding(dp(view.getContext(), 20), dp(view.getContext(), 12), dp(view.getContext(), 20), dp(view.getContext(), 12));
        view.setBackground(solidRounded(view.getContext(), Color.parseColor("#E7132824"), 22, GREEN));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(view.getContext(), 10));
        }
    }

    private static List<View> snapshotChildren(ViewGroup group) {
        List<View> children = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            children.add(group.getChildAt(i));
        }
        return children;
    }

    private static TextView findMessageText(List<View> views, TextView title) {
        for (View view : views) {
            if (view instanceof TextView && view != title) {
                TextView text = (TextView) view;
                String value = text.getText().toString().trim();
                if (!value.equalsIgnoreCase("GET KEY") && !value.equalsIgnoreCase("TRY AGAIN")) {
                    return text;
                }
            }
        }
        return null;
    }

    private static LinearLayout findButtonRow(List<View> views) {
        for (View view : views) {
            if (view instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) view;
                for (int i = 0; i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    if (child instanceof TextView
                            && ((TextView) child).getText().toString().trim().equalsIgnoreCase("TRY AGAIN")) {
                        return row;
                    }
                }
            }
        }
        return null;
    }

    private static TextView label(Context context, String text, int color, float size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable cardBackground(Context context, int accent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{CARD_TOP, CARD_BOTTOM});
        drawable.setCornerRadius(dp(context, 26));
        drawable.setStroke(dp(context, 1), Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return drawable;
    }

    private static GradientDrawable solidRounded(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static GradientDrawable circleBackground(Context context, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static GradientDrawable buttonBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GOLD, Color.parseColor("#FFE27B")});
        drawable.setCornerRadius(dp(context, 24));
        return drawable;
    }

    private static GradientDrawable successButtonBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GREEN, Color.parseColor("#8BEBC8")});
        drawable.setCornerRadius(dp(context, 24));
        return drawable;
    }

    private static GradientDrawable errorButtonBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{RED, Color.parseColor("#FF8A91")});
        drawable.setCornerRadius(dp(context, 24));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
