package android.MetaCore.social;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** SDK-owned sign-in screen with Google, Facebook and X buttons. */
public final class SocialLoginActivity extends Activity {
    private Button googleButton;
    private Button facebookButton;
    private Button xButton;
    private TextView statusText;
    private boolean attempted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SocialAuthManager.isLoggedIn(this) && attempted) {
            finish();
            return;
        }
        String error = SocialAuthManager.consumeLastError(this);
        if (!error.isEmpty()) {
            setBusy(false);
            statusText.setText(error);
            statusText.setTextColor(Color.rgb(255, 120, 130));
        }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7, 10, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(52), dp(28), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Sign in to Parallax SDK");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0));

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose a trusted provider. Authentication opens in your system browser and returns securely to this SDK.");
        subtitle.setTextColor(Color.rgb(180, 190, 210));
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap(dp(12));
        subtitleParams.bottomMargin = dp(28);
        root.addView(subtitle, subtitleParams);

        googleButton = providerButton("Continue with Google", Color.WHITE, Color.rgb(30, 30, 30));
        facebookButton = providerButton("Continue with Facebook", Color.rgb(24, 119, 242), Color.WHITE);
        xButton = providerButton("Continue with X (Twitter)", Color.BLACK, Color.WHITE);

        root.addView(googleButton, buttonParams());
        root.addView(facebookButton, buttonParams());
        root.addView(xButton, buttonParams());

        statusText = new TextView(this);
        statusText.setText("No password is shared with Parallax.");
        statusText.setTextColor(Color.rgb(150, 160, 180));
        statusText.setTextSize(13f);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap(dp(20));
        statusParams.bottomMargin = dp(18);
        root.addView(statusText, statusParams);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> {
            SocialAuthManager.Listener listener = null;
            finish();
        });
        root.addView(cancel, buttonParams());

        googleButton.setOnClickListener(v -> begin(SocialAuthManager.Provider.GOOGLE, "Opening Google…"));
        facebookButton.setOnClickListener(v -> begin(SocialAuthManager.Provider.FACEBOOK, "Opening Facebook…"));
        xButton.setOnClickListener(v -> begin(SocialAuthManager.Provider.X, "Opening X…"));

        setContentView(scroll);
    }

    private void begin(SocialAuthManager.Provider provider, String status) {
        attempted = true;
        setBusy(true);
        statusText.setText(status);
        statusText.setTextColor(Color.rgb(210, 220, 240));
        SocialAuthManager.login(this, provider);
    }

    private void setBusy(boolean busy) {
        googleButton.setEnabled(!busy);
        facebookButton.setEnabled(!busy);
        xButton.setEnabled(!busy);
        googleButton.setAlpha(busy ? 0.55f : 1f);
        facebookButton.setAlpha(busy ? 0.55f : 1f);
        xButton.setAlpha(busy ? 0.55f : 1f);
    }

    private Button providerButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15f);
        button.setTextColor(foreground);
        button.setBackgroundColor(background);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
