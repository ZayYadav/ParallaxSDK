package com.onecore.loader.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.onecore.loader.security.HostedLicenseClient;
import com.onecore.loader.security.SecurePreferences;
import com.onecore.loader.security.SecurityThreatDetector;

/** Minimal license-only entry point for the hardened distribution. */
public final class HardenedLoginActivity extends Activity {
    private static final String USER = "USER";

    private HostedLicenseClient licenseClient;
    private SecurePreferences securePreferences;
    private EditText keyInput;
    private TextView signInButton;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        SecurityThreatDetector.Threat threat = SecurityThreatDetector.detect(this);
        if (threat != SecurityThreatDetector.Threat.NONE) {
            Toast.makeText(this, "Security verification failed.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        licenseClient = new HostedLicenseClient(this);
        if (licenseClient.hasActiveLicense()) {
            openHardenedMain();
            return;
        }

        securePreferences = new SecurePreferences(this);
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(12, 12, 14));

        TextView title = text("Parallax", 26, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text("Enter your license key to continue.", 14, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(18));
        root.addView(subtitle, matchWrap());

        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint("License key");
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.GRAY);
        keyInput.setFilterTouchesWhenObscured(true);
        String saved = securePreferences.getString(USER, "");
        if (saved != null) {
            keyInput.setText(saved);
            keyInput.setSelection(keyInput.length());
        }
        root.addView(keyInput, matchWrap());

        TextView paste = text("PASTE", 13, Color.rgb(93, 226, 177));
        paste.setGravity(Gravity.CENTER);
        paste.setPadding(dp(12), dp(10), dp(12), dp(10));
        paste.setClickable(true);
        paste.setOnClickListener(v -> pasteKey());
        root.addView(paste, wrapWrap());

        progress = new ProgressBar(this);
        progress.setVisibility(ProgressBar.GONE);
        root.addView(progress, wrapWrap());

        signInButton = text("SIGN IN", 17, Color.BLACK);
        signInButton.setGravity(Gravity.CENTER);
        signInButton.setBackgroundColor(Color.rgb(93, 226, 177));
        signInButton.setPadding(dp(28), dp(14), dp(28), dp(14));
        signInButton.setClickable(true);
        signInButton.setFocusable(true);
        signInButton.setOnClickListener(v -> activate());
        root.addView(signInButton, wrapWrap());

        return root;
    }

    private void pasteKey() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager != null && manager.hasPrimaryClip() ? manager.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value != null) {
            keyInput.setText(value.toString().trim());
            keyInput.setSelection(keyInput.length());
        }
    }

    private void activate() {
        String raw = keyInput.getText().toString().trim();
        if (!HostedLicenseClient.isSupportedActivationKey(raw)) {
            keyInput.setError("Enter a valid Parallax key");
            return;
        }

        String key = HostedLicenseClient.normalizeActivationKey(raw);
        try {
            securePreferences.putString(USER, key);
        } catch (IllegalStateException error) {
            Toast.makeText(this, "Secure storage is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        new Thread(() -> {
            String result;
            try {
                result = licenseClient.activate(key);
            } catch (Throwable failure) {
                result = "Verification unavailable";
            }
            String finalResult = result;
            runOnUiThread(() -> {
                setBusy(false);
                if ("OK".equals(finalResult)) {
                    openHardenedMain();
                } else {
                    Toast.makeText(this,
                            finalResult == null || finalResult.isEmpty() ? "Access denied" : finalResult,
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "HardenedLicenseActivation").start();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? ProgressBar.VISIBLE : ProgressBar.GONE);
        signInButton.setEnabled(!busy);
        signInButton.setAlpha(busy ? 0.6f : 1f);
    }

    private void openHardenedMain() {
        Intent intent = new Intent(this, HardenedMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
