package com.onecore.loader.ui;

import android.app.Activity;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.onecore.loader.R;
import com.onecore.loader.security.HostedLicenseClient;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Adds lightweight theme-aware press/glow feedback to clickable Loader controls. */
public final class InteractionGlowInstaller {
    private static final Set<View> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();
    private static final Set<Activity> FAILURE_HANDLED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private InteractionGlowInstaller() {
    }

    public static void attach(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView();
        installTree(root);
        syncVerificationFailure(activity, root);
        if (!LISTENERS.containsKey(activity)) {
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
                installTree(root);
                syncVerificationFailure(activity, root);
            };
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
        FAILURE_HANDLED.remove(activity);
    }

    private static void installTree(View view) {
        if (view == null) return;
        if (isInteractive(view) && !INSTALLED.contains(view)) {
            install(view);
            INSTALLED.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                installTree(group.getChildAt(i));
            }
        }
    }

    private static boolean isInteractive(View view) {
        if (!view.isClickable() || !view.isEnabled()) return false;
        return view instanceof TextView
                || view instanceof ImageView
                || view instanceof MaterialCardView;
    }

    private static void install(View view) {
        view.setOnTouchListener((target, event) -> {
            ThemeManager.ThemeSpec theme = ThemeManager.current(target.getContext());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    target.animate().cancel();
                    target.animate()
                            .scaleX(0.975f)
                            .scaleY(0.975f)
                            .alpha(0.90f)
                            .setDuration(75L)
                            .start();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        target.setTranslationZ(ThemeManager.dp(target.getContext(), 4));
                    }
                    setTextGlow(target, theme.accent, ThemeManager.dp(target.getContext(), 7));
                    if (target instanceof ImageView) {
                        target.animate().rotation(2.2f).setDuration(75L).start();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    release(target, theme);
                    maybeShowKeyVerification(target);
                    target.animate()
                            .scaleX(1.018f)
                            .scaleY(1.018f)
                            .setDuration(85L)
                            .withEndAction(() -> target.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .alpha(1f)
                                    .rotation(0f)
                                    .setDuration(145L)
                                    .start())
                            .start();
                    break;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_OUTSIDE:
                    release(target, theme);
                    target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .rotation(0f)
                            .setDuration(130L)
                            .start();
                    break;

                default:
                    break;
            }
            return false;
        });
    }

    private static void maybeShowKeyVerification(View target) {
        if (!(target.getContext() instanceof Activity) || !(target instanceof TextView)) return;
        TextView textView = (TextView) target;
        String label = textView.getText() == null ? "" : textView.getText().toString().trim();
        if (!"CONNECT TO EDGE SERVER".equalsIgnoreCase(label)) return;

        Activity activity = (Activity) target.getContext();
        EditText input = activity.findViewById(R.id.textUsername);
        if (input == null) return;
        String key = input.getText() == null ? "" : input.getText().toString();
        if (!HostedLicenseClient.isSupportedActivationKey(key)) return;

        FAILURE_HANDLED.remove(activity);
        KeyVerificationUi.show(activity);
    }

    private static void syncVerificationFailure(Activity activity, View root) {
        if (!KeyVerificationUi.isShowing(activity) || FAILURE_HANDLED.contains(activity)) return;
        if (!containsText(root, "ACCESS DENIED")) return;

        FAILURE_HANDLED.add(activity);
        KeyVerificationUi.failed(activity);
        root.postDelayed(() -> KeyVerificationUi.hide(activity), 620L);
    }

    private static boolean containsText(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence raw = ((TextView) view).getText();
            if (raw != null && raw.toString().toUpperCase().contains(needle)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), needle)) return true;
            }
        }
        return false;
    }

    private static void release(View target, ThemeManager.ThemeSpec theme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            target.setTranslationZ(0f);
        }
        setTextGlow(target, ThemeManager.withAlpha(theme.accent, 0), 0);
    }

    private static void setTextGlow(View view, int color, int radiusPx) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (radiusPx <= 0) {
                text.setShadowLayer(0f, 0f, 0f, 0);
            } else {
                text.setShadowLayer(radiusPx, 0f, 0f, color);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setTextGlow(group.getChildAt(i), color, radiusPx);
            }
        }
    }
}
