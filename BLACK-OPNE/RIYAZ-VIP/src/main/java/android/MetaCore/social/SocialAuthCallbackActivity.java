package android.MetaCore.social;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Receives the package-scoped deep link after the SDK panel completes OAuth. */
public final class SocialAuthCallbackActivity extends Activity {
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderProgress();
        handleIntentData();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handled = false;
        handleIntentData();
    }

    private void handleIntentData() {
        if (handled) {
            return;
        }
        handled = true;
        Uri data = getIntent() == null ? null : getIntent().getData();
        SocialAuthManager.handleCallback(this, data);
    }

    private void renderProgress() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(10, 14, 24));

        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(this);
        text.setText("Completing secure sign in…");
        text.setTextColor(Color.WHITE);
        text.setTextSize(16f);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = 24;
        root.addView(text, textParams);

        setContentView(root);
    }
}
