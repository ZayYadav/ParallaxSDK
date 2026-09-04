package com.onecore.loader.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.onecore.loader.R;

/** Adds the reusable OneCore Edge visual as a normal inline layout child. */
public final class EdgeVisualInstaller {

    private static final String VISUAL_TAG = "onecore_edge_inline_visual";

    private EdgeVisualInstaller() {
    }

    public static void attach(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        if (content == null || content.findViewWithTag(VISUAL_TAG) != null) {
            return;
        }

        // Main screen: larger theme-native visual between header and protected session.
        if (activity.findViewById(R.id.btn_settings) != null) {
            LinearLayout column = findNestedColumn(content);
            if (column != null) {
                addVisual(column, Math.min(1, column.getChildCount()), 154, 16, 18);
                return;
            }
        }

        // Login: advanced visual directly above Access Console.
        if (activity.findViewById(R.id.btnSignIn) != null) {
            LinearLayout column = findNestedColumn(content);
            if (column != null) {
                int targetIndex = column.getChildCount();
                for (int i = 0; i < column.getChildCount(); i++) {
                    if (column.getChildAt(i) instanceof MaterialCardView) {
                        targetIndex = i;
                        break;
                    }
                }
                addVisual(column, targetIndex, 160, 20, 6);
                return;
            }
        }

        // Splash: compact but still large enough for the rotating wordmark ring.
        View subtitle = activity.findViewById(R.id.brandSubtitle);
        if (subtitle != null && subtitle.getParent() instanceof LinearLayout) {
            LinearLayout column = (LinearLayout) subtitle.getParent();
            int index = column.indexOfChild(subtitle) + 1;
            addVisual(column, index, 128, 18, 5);
        }
    }

    private static LinearLayout findNestedColumn(View root) {
        if (root instanceof NestedScrollView) {
            NestedScrollView scrollView = (NestedScrollView) root;
            if (scrollView.getChildCount() > 0 && scrollView.getChildAt(0) instanceof LinearLayout) {
                return (LinearLayout) scrollView.getChildAt(0);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout result = findNestedColumn(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void addVisual(LinearLayout parent, int index,
                                  int heightDp, int marginTopDp, int marginBottomDp) {
        if (parent.findViewWithTag(VISUAL_TAG) != null) {
            return;
        }
        EdgeVisualView visual = new EdgeVisualView(parent.getContext());
        visual.setTag(VISUAL_TAG);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ThemeManager.dp(parent.getContext(), heightDp));
        params.topMargin = ThemeManager.dp(parent.getContext(), marginTopDp);
        params.bottomMargin = ThemeManager.dp(parent.getContext(), marginBottomDp);
        parent.addView(visual, Math.max(0, Math.min(index, parent.getChildCount())), params);

        visual.setAlpha(0f);
        visual.setScaleX(0.985f);
        visual.setScaleY(0.985f);
        visual.setTranslationY(ThemeManager.dp(parent.getContext(), 10));
        visual.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(420L)
                .start();
    }
}
