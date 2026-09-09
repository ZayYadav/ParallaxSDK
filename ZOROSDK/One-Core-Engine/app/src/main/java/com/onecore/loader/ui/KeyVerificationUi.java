package com.onecore.loader.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.onecore.loader.R;

/** Installs and controls the inline key verification panel on LoginActivity. */
public final class KeyVerificationUi {
    private static final String TAG = "onecore_edge_key_verification";

    private KeyVerificationUi() {
    }

    public static void show(Activity activity) {
        run(activity, () -> {
            KeyVerificationView view = ensureView(activity);
            if (view == null) return;
            view.animate().cancel();
            view.setState(KeyVerificationView.STATE_VERIFYING,
                    "NATIVE TRUST • TLS PINNED • RESPONSE BOUND");
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(ThemeManager.dp(activity, 8));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .start();

            // Safety watchdog: never leave an orphaned verification panel on screen.
            view.postDelayed(() -> {
                if (view.getParent() != null) hide(activity);
            }, 25_000L);
        });
    }

    public static void success(Activity activity) {
        run(activity, () -> {
            KeyVerificationView view = ensureView(activity);
            if (view != null) {
                view.setState(KeyVerificationView.STATE_SUCCESS,
                        "NATIVE TRUST • SERVER VERIFIED • ACCESS BOUND");
                pulse(view);
            }
        });
    }

    public static void failed(Activity activity) {
        run(activity, () -> {
            KeyVerificationView view = ensureView(activity);
            if (view != null) {
                view.setState(KeyVerificationView.STATE_FAILED,
                        "SECURE CHANNEL REJECTED • ACCESS NOT GRANTED");
                pulse(view);
            }
        });
    }

    public static boolean isShowing(Activity activity) {
        return find(activity) != null;
    }

    public static void hide(Activity activity) {
        run(activity, () -> {
            KeyVerificationView view = find(activity);
            if (view == null) return;
            view.animate().cancel();
            view.animate()
                    .alpha(0f)
                    .translationY(ThemeManager.dp(activity, -5))
                    .setDuration(190L)
                    .withEndAction(() -> {
                        if (view.getParent() instanceof ViewGroup) {
                            ((ViewGroup) view.getParent()).removeView(view);
                        }
                    })
                    .start();
        });
    }

    private static KeyVerificationView ensureView(Activity activity) {
        KeyVerificationView existing = find(activity);
        if (existing != null) return existing;

        View anchor = activity.findViewById(R.id.init);
        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) return null;
        LinearLayout parent = (LinearLayout) anchor.getParent();

        KeyVerificationView view = new KeyVerificationView(activity);
        view.setTag(TAG);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ThemeManager.dp(activity, 154));
        params.topMargin = ThemeManager.dp(activity, 14);
        params.bottomMargin = ThemeManager.dp(activity, 2);
        int index = Math.min(parent.indexOfChild(anchor) + 1, parent.getChildCount());
        parent.addView(view, index, params);
        return view;
    }

    private static KeyVerificationView find(Activity activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return null;
        View found = root.findViewWithTag(TAG);
        return found instanceof KeyVerificationView ? (KeyVerificationView) found : null;
    }

    private static void pulse(View view) {
        view.animate().cancel();
        view.setScaleX(0.985f);
        view.setScaleY(0.985f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(220L)
                .start();
    }

    private static void run(Activity activity, Runnable action) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(action);
    }
}
