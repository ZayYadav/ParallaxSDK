package com.onecore.loader.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.onecore.loader.security.HostedLicenseClient;

import top.niunaijun.blackbox.BlackBoxCore;

import static com.onecore.loader.Config.GAME_LIST_PKG;

/**
 * Minimal entry point for the hardened distribution variant.
 *
 * This activity intentionally contains no artifact download, OBB copy, package
 * installation, overlay, patching or native-library staging path. It only opens
 * an app that is already present in the virtual workspace after the normal
 * license gate succeeds.
 */
public final class HardenedMainActivity extends Activity {
    private HostedLicenseClient licenseClient;
    private BlackBoxCore blackBoxCore;
    private TextView statusView;
    private TextView startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        licenseClient = new HostedLicenseClient(this);
        if (!licenseClient.hasActiveLicense()) {
            openLogin();
            return;
        }

        blackBoxCore = BlackBoxCore.get();
        blackBoxCore.doCreate();
        setContentView(buildContent());
        refreshState();
    }

    private LinearLayout buildContent() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(12, 12, 14));

        TextView title = text("Hardened Release", 24, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text(
                "R8-optimized safe launcher. Install/setup actions and native patch paths are disabled in this variant.",
                14,
                Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(12), 0, dp(18));
        root.addView(subtitle, matchWrap());

        statusView = text("Checking virtual app…", 14, Color.LTGRAY);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, 0, 0, dp(18));
        root.addView(statusView, matchWrap());

        startButton = text("START", 18, Color.BLACK);
        startButton.setGravity(Gravity.CENTER);
        startButton.setBackgroundColor(Color.rgb(93, 226, 177));
        startButton.setPadding(dp(28), dp(14), dp(28), dp(14));
        startButton.setClickable(true);
        startButton.setFocusable(true);
        startButton.setOnClickListener(v -> launchExistingVirtualApp());
        root.addView(startButton, wrapWrap());

        TextView device = text(
                "Android API " + Build.VERSION.SDK_INT + " • " + TextUtils.join(", ", Build.SUPPORTED_ABIS),
                12,
                Color.GRAY);
        device.setGravity(Gravity.CENTER);
        device.setPadding(0, dp(20), 0, 0);
        root.addView(device, matchWrap());

        return root;
    }

    private void launchExistingVirtualApp() {
        if (licenseClient == null || !licenseClient.hasActiveLicense()) {
            openLogin();
            return;
        }

        String targetPackage = GAME_LIST_PKG.length == 0 ? null : GAME_LIST_PKG[0];
        if (targetPackage == null || targetPackage.trim().isEmpty()) {
            show("No launch target is configured.");
            return;
        }

        boolean installed;
        try {
            installed = blackBoxCore != null && blackBoxCore.isInstalled(targetPackage, 0);
        } catch (Throwable ignored) {
            installed = false;
        }

        if (!installed) {
            statusView.setText("App is not installed in the virtual workspace.");
            show("Use an authorized setup build to install the app first.");
            return;
        }

        try {
            blackBoxCore.launchApk(targetPackage, 0);
        } catch (Throwable failure) {
            statusView.setText("Unable to start the installed app.");
            show("Launch failed.");
        }
    }

    private void refreshState() {
        String targetPackage = GAME_LIST_PKG.length == 0 ? null : GAME_LIST_PKG[0];
        boolean installed = false;
        if (targetPackage != null) {
            try {
                installed = blackBoxCore != null && blackBoxCore.isInstalled(targetPackage, 0);
            } catch (Throwable ignored) {
            }
        }
        statusView.setText(installed
                ? "Ready — installed app can be started."
                : "Setup required — no app is installed in the virtual workspace.");
        startButton.setAlpha(installed ? 1f : 0.65f);
    }

    private void openLogin() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(login);
        finish();
    }

    private void show(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
