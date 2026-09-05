package com.onecore.loader.activity;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.Jagdish.tastytoast.TastyToast;
import com.onecore.loader.BoxApplication;
import com.onecore.loader.R;
import com.onecore.loader.floating.FloatAim;
import com.onecore.loader.floating.FloatLogo;
import com.onecore.loader.floating.Overlay;
import com.onecore.loader.libhelper.ApkEnv;
import com.onecore.loader.libhelper.DownloadZip;
import com.onecore.loader.libhelper.FileCopyTask;
import com.onecore.loader.security.HostedLicenseClient;
import com.onecore.loader.server.ServerInstallStrings;
import com.onecore.loader.server.ServerInstallWorker;
import com.onecore.loader.ui.ThemeManager;
import com.onecore.loader.utils.Constants;
import com.onecore.loader.utils.CrashHandler;
import com.onecore.loader.utils.FLog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;

import static com.onecore.loader.Config.GAME_LIST_PKG;

@Obfuscate
public class MainActivity extends Activity {

    private static final long ONLINE_REVALIDATION_INTERVAL_MS = 5L * 60L * 1000L;
    private static final int BGMI_INDEX = 0;
    private static final int REQUEST_SERVER_NOTIFICATIONS = 9104;

    public static MainActivity instance;
    private BlackBoxCore blackBoxCore;
    public static native String FixCrash();
    public String CURRENT_PACKAGE;

    private TextView installIndia;
    private TextView btnStartGame;
    private TextView btnClearBgmiData;
    private RadioButton tvHideEsp;

    private Dialog serverDownloadDialog;
    private ProgressBar serverDownloadProgress;
    private TextView serverDownloadState;
    private TextView serverDownloadDetail;
    private TextView serverDownloadPercent;

    public static int gameType = 5;
    private String selectedGamePkg;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Handler serverStateHandler = new Handler(Looper.getMainLooper());
    private final Runnable serverStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (installIndia != null) {
                updateButtonState(BGMI_INDEX, installIndia);
            }
            refreshServerDownloadDialog();
            serverStateHandler.postDelayed(this, 500L);
        }
    };
    private Runnable countdownRunnable;
    private HostedLicenseClient licenseClient;
    private boolean pendingServerInstall;
    private boolean notificationPermissionRequestInFlight;
    private boolean accessClosed;
    private boolean revalidationInProgress;

    public static MainActivity get() {
        return instance;
    }

    public static void goMain(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        instance = this;

        licenseClient = new HostedLicenseClient(this);
        if (!licenseClient.hasActiveLicense()) {
            closeExpiredAccess();
            return;
        }

        blackBoxCore = BlackBoxCore.get();
        blackBoxCore.doCreate();
        GameJsonMods();

        // BGMI is the only exposed runtime profile. Keep it ready from the first frame so the
        // user never has to select a game before pressing Start.
        selectedGamePkg = GAME_LIST_PKG.length > BGMI_INDEX ? GAME_LIST_PKG[BGMI_INDEX] : "";
        gameType = 5;

        installIndia = findViewById(R.id.installIndia);
        btnStartGame = findViewById(R.id.btn_start_game);
        btnClearBgmiData = findViewById(R.id.btn_clear_bgmi_data);
        tvHideEsp = findViewById(R.id.tv_hide_esp);

        if (btnClearBgmiData != null) {
            btnClearBgmiData.setText(ServerInstallStrings.CLEAR_BGMI_DATA);
        }

        TextView deviceStatus = findViewById(R.id.tv_device_status);
        deviceStatus.setText("Android API " + Build.VERSION.SDK_INT
                + "  •  " + TextUtils.join(", ", Build.SUPPORTED_ABIS));

        updateButtonState(BGMI_INDEX, installIndia);

        installIndia.setOnClickListener(view -> {
            if (!ensureLicenseActive()) {
                return;
            }
            if (ServerInstallWorker.isRunning(MainActivity.this)) {
                showServerDownloadDialog();
                return;
            }
            showInstallSourceDialog();
        });

        if (btnClearBgmiData != null) {
            btnClearBgmiData.setOnClickListener(v -> showClearBgmiDataDialog());
        }

        btnStartGame.setOnClickListener(v -> {
            if (!ensureLicenseActive()) {
                return;
            }
            if (selectedGamePkg == null || selectedGamePkg.isEmpty()) {
                BoxApplication.get().showToastWithImage(
                        "BGMI profile is unavailable in this build.", TastyToast.ERROR);
                return;
            }
            if (!ApkEnv.getInstance().isInstalled(selectedGamePkg)) {
                BoxApplication.get().showToastWithImage(Constants.GAME_NOT_INSTALL, TastyToast.ERROR);
                return;
            }

            runAfterSdkActivation(() -> {
                BoxApplication.get().showToastWithImage(
                        "BGMI profile ready • Starting secure session", TastyToast.SUCCESS);
                ApkEnv.getInstance().LaunchApplication(selectedGamePkg);
                startPatcher();
            });
        });

        if (tvHideEsp != null) {
            tvHideEsp.setOnClickListener(v -> {
                if (tvHideEsp.isChecked()) {
                    BoxApplication.get().showToastWithImage(
                            "Privacy mode enabled for screen recording", TastyToast.SUCCESS);
                } else {
                    BoxApplication.get().showToastWithImage(
                            "Privacy mode disabled", TastyToast.INFO);
                }
            });
        }

        new DownloadZip(MainActivity.get()).startDownload(FixCrash(), new DownloadZip.DownloadCallback() {
            @Override
            public void onStart() {
            }

            @Override
            public void onProgress(int progress) {
            }

            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    public void do_Lib_And_Run(String packageName) {
        if (!ensureLicenseActive()) {
            return;
        }
        CURRENT_PACKAGE = packageName;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (ApkEnv.getInstance().tryAddLoader(packageName)) {
                ApkEnv.getInstance().LaunchApplication(packageName);
            }
        });
    }

    private void showInstallSourceDialog() {
        if (!ensureLicenseActive()) {
            return;
        }

        if (selectedGamePkg == null || selectedGamePkg.isEmpty()) {
            BoxApplication.get().showToastWithImage(
                    "BGMI profile is unavailable in this build.", TastyToast.ERROR);
            return;
        }

        // Once installed, the same button remains an UNINSTALL action, but keep
        // the SDK activation gate so BlackBox package operations cannot race startup.
        if (getInstallationStatus(selectedGamePkg)) {
            runAfterSdkActivation(() ->
                    handleInstallUninstall(BGMI_INDEX, installIndia));
            return;
        }

        ThemeManager.ThemeSpec theme = ThemeManager.current(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));

        GradientDrawable shell = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeManager.withAlpha(theme.surfaceAlt, 252),
                        ThemeManager.withAlpha(theme.surface, 252)});
        shell.setCornerRadius(dp(Math.max(22f, theme.cardRadiusDp)));
        shell.setStroke(
                dp(Math.max(1f, theme.strokeDp)),
                ThemeManager.withAlpha(theme.accent, 175));
        root.setBackground(shell);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("ONECORE • GAME INSTALLER");
        eyebrow.setTextColor(theme.accent);
        eyebrow.setTextSize(9f);
        eyebrow.setLetterSpacing(0.13f);
        eyebrow.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(eyebrow, matchWrapParams(0));

        TextView title = new TextView(this);
        title.setText(ServerInstallStrings.CHOOSER_TITLE);
        title.setTextColor(theme.text);
        title.setTextSize(23f);
        title.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        title.setLetterSpacing(0.035f);
        LinearLayout.LayoutParams titleParams = matchWrapParams(0);
        titleParams.topMargin = dp(6);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(ServerInstallStrings.CHOOSER_SUBTITLE);
        subtitle.setTextColor(theme.muted);
        subtitle.setTextSize(12f);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subtitleParams = matchWrapParams(0);
        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(18);
        root.addView(subtitle, subtitleParams);

        TextView section = new TextView(this);
        section.setText("SELECT INSTALL SOURCE");
        section.setTextColor(ThemeManager.withAlpha(theme.text, 190));
        section.setTextSize(10f);
        section.setLetterSpacing(0.10f);
        section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrapParams(0);
        sectionParams.bottomMargin = dp(9);
        root.addView(section, sectionParams);

        LinearLayout installedGame = makeModernInstallChoiceCard(
                "DEVICE",
                ServerInstallStrings.INSTALL_FROM_DEVICE,
                ServerInstallStrings.INSTALL_FROM_DEVICE_SUBTITLE,
                "Uses the BGMI files already present on this phone",
                theme,
                false);
        root.addView(installedGame, matchWrapParams(11));

        LinearLayout oneCoreServer = makeModernInstallChoiceCard(
                "ONECORE CDN",
                ServerInstallStrings.INSTALL_FROM_SERVER,
                ServerInstallStrings.INSTALL_FROM_SERVER_SUBTITLE,
                "Background download • resumable • notification progress",
                theme,
                true);
        root.addView(oneCoreServer, matchWrapParams(0));

        TextView footer = new TextView(this);
        footer.setText(ServerInstallStrings.TAP_OUTSIDE_TO_CANCEL);
        footer.setTextColor(ThemeManager.withAlpha(theme.muted, 210));
        footer.setTextSize(10f);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = matchWrapParams(0);
        footerParams.topMargin = dp(14);
        root.addView(footer, footerParams);

        installedGame.setOnClickListener(v -> {
            dialog.dismiss();
            runAfterSdkActivation(() ->
                    handleInstallUninstall(BGMI_INDEX, installIndia));
        });

        oneCoreServer.setOnClickListener(v -> {
            dialog.dismiss();
            beginServerInstall();
        });

        dialog.setContentView(root);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.82f;
            window.setAttributes(attrs);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(
                    (int) (width * 0.92f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        root.setAlpha(0f);
        root.setScaleX(0.96f);
        root.setScaleY(0.96f);
        root.setTranslationY(dp(8));
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();
    }

    private LinearLayout makeModernInstallChoiceCard(
            String badge,
            String title,
            String subtitle,
            String hint,
            ThemeManager.ThemeSpec theme,
            boolean primary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable background;
        if (primary) {
            background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ThemeManager.withAlpha(theme.accent, 242),
                            ThemeManager.withAlpha(theme.accent2, 242)});
            background.setStroke(
                    dp(1),
                    ThemeManager.withAlpha(theme.text, 75));
        } else {
            background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ThemeManager.withAlpha(theme.surfaceAlt, 252),
                            ThemeManager.withAlpha(theme.surface, 252)});
            background.setStroke(
                    dp(Math.max(1f, theme.strokeDp)),
                    ThemeManager.withAlpha(theme.accent, 135));
        }
        background.setCornerRadius(dp(Math.max(16f, theme.buttonRadiusDp)));
        card.setBackground(background);

        TextView badgeView = new TextView(this);
        badgeView.setText(badge);
        badgeView.setTextColor(primary
                ? ThemeManager.contrastInk(theme.accent)
                : theme.accent);
        badgeView.setTextSize(9f);
        badgeView.setLetterSpacing(0.12f);
        badgeView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(badgeView, matchWrapParams(0));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(primary
                ? ThemeManager.contrastInk(theme.accent)
                : theme.text);
        titleView.setTextSize(14f);
        titleView.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        LinearLayout.LayoutParams titleParams = matchWrapParams(0);
        titleParams.topMargin = dp(5);
        card.addView(titleView, titleParams);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(primary
                ? ThemeManager.withAlpha(
                        ThemeManager.contrastInk(theme.accent), 205)
                : theme.muted);
        subtitleView.setTextSize(11f);
        subtitleView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subtitleParams = matchWrapParams(0);
        subtitleParams.topMargin = dp(5);
        card.addView(subtitleView, subtitleParams);

        TextView hintView = new TextView(this);
        hintView.setText(hint + "   →");
        hintView.setTextColor(primary
                ? ThemeManager.withAlpha(
                        ThemeManager.contrastInk(theme.accent), 230)
                : ThemeManager.withAlpha(theme.accent, 230));
        hintView.setTextSize(9.5f);
        hintView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hintParams = matchWrapParams(0);
        hintParams.topMargin = dp(9);
        card.addView(hintView, hintParams);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(primary
                    ? Math.max(8f, theme.elevationDp * 0.75f)
                    : Math.max(3f, theme.elevationDp * 0.35f)));
        }
        return card;
    }

    private TextView makeInstallChoice(
            String title,
            String subtitle,
            ThemeManager.ThemeSpec theme,
            boolean primary) {
        TextView option = new TextView(this);
        option.setText(title + "\n" + subtitle);
        option.setTextColor(primary ? ThemeManager.contrastInk(theme.accent) : theme.text);
        option.setTextSize(13f);
        option.setLineSpacing(dp(3), 1f);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(16), dp(14), dp(16), dp(14));
        option.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));

        GradientDrawable background;
        if (primary) {
            background = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{theme.accent, theme.accent2});
            background.setStroke(0, Color.TRANSPARENT);
        } else {
            background = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            ThemeManager.withAlpha(theme.surfaceAlt, 250),
                            ThemeManager.withAlpha(theme.surface, 250)});
            background.setStroke(
                    dp(Math.max(1f, theme.strokeDp)),
                    ThemeManager.withAlpha(theme.accent, 150));
        }
        background.setCornerRadius(dp(theme.buttonRadiusDp));
        option.setBackground(background);
        option.setClickable(true);
        option.setFocusable(true);
        return option;
    }

    private void showClearBgmiDataDialog() {
        if (!ensureLicenseActive()) {
            return;
        }
        if (selectedGamePkg == null || selectedGamePkg.isEmpty()) {
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.CLEAR_DATA_NOT_INSTALLED,
                    TastyToast.WARNING);
            return;
        }
        if (ServerInstallWorker.isRunning(this)) {
            BoxApplication.get().showToastWithImage(
                    "Finish or cancel the current BGMI download first.",
                    TastyToast.WARNING);
            showServerDownloadDialog();
            return;
        }

        boolean installed;
        try {
            installed = ApkEnv.getInstance().isInstalled(selectedGamePkg);
        } catch (Throwable error) {
            installed = getInstallationStatus(selectedGamePkg);
        }
        if (!installed) {
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.CLEAR_DATA_NOT_INSTALLED,
                    TastyToast.WARNING);
            return;
        }

        ThemeManager.ThemeSpec theme = ThemeManager.current(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));

        GradientDrawable shell = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        ThemeManager.withAlpha(theme.surfaceAlt, 252),
                        ThemeManager.withAlpha(theme.surface, 252)});
        shell.setCornerRadius(dp(Math.max(22f, theme.cardRadiusDp)));
        shell.setStroke(
                dp(Math.max(1f, theme.strokeDp)),
                ThemeManager.withAlpha(theme.error, 180));
        root.setBackground(shell);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("BGMI • ONECORE STORAGE");
        eyebrow.setTextColor(theme.error);
        eyebrow.setTextSize(9f);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(eyebrow, matchWrapParams(0));

        TextView title = new TextView(this);
        title.setText(ServerInstallStrings.CLEAR_DATA_DIALOG_TITLE);
        title.setTextColor(theme.text);
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        LinearLayout.LayoutParams titleParams = matchWrapParams(0);
        titleParams.topMargin = dp(6);
        root.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(ServerInstallStrings.CLEAR_DATA_DIALOG_MESSAGE);
        message.setTextColor(theme.muted);
        message.setTextSize(12f);
        message.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams messageParams = matchWrapParams(0);
        messageParams.topMargin = dp(8);
        messageParams.bottomMargin = dp(17);
        root.addView(message, messageParams);

        TextView clear = makeDialogActionButton(
                ServerInstallStrings.CLEAR_DATA_CONFIRM,
                theme,
                true);
        root.addView(clear, matchWrapParams(10));

        TextView keep = makeDialogActionButton(
                ServerInstallStrings.CLEAR_DATA_CANCEL,
                theme,
                false);
        root.addView(keep, matchWrapParams(0));

        clear.setOnClickListener(v -> {
            dialog.dismiss();
            runAfterSdkActivation(this::clearBgmiDataAsync);
        });
        keep.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.82f;
            window.setAttributes(attrs);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(
                    (int) (width * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        root.setAlpha(0f);
        root.setScaleX(0.97f);
        root.setScaleY(0.97f);
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .start();
    }

    private TextView makeDialogActionButton(
            String text,
            ThemeManager.ThemeSpec theme,
            boolean destructive) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        button.setLetterSpacing(0.07f);
        button.setClickable(true);
        button.setFocusable(true);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                destructive
                        ? new int[]{
                                ThemeManager.withAlpha(theme.error, 235),
                                ThemeManager.withAlpha(theme.error, 185)}
                        : new int[]{
                                ThemeManager.withAlpha(theme.surfaceAlt, 250),
                                ThemeManager.withAlpha(theme.surface, 250)});
        background.setCornerRadius(dp(Math.max(14f, theme.buttonRadiusDp)));
        background.setStroke(
                dp(Math.max(1f, theme.strokeDp)),
                destructive
                        ? ThemeManager.withAlpha(theme.error, 235)
                        : ThemeManager.withAlpha(theme.accent, 145));
        button.setBackground(background);
        button.setTextColor(destructive
                ? ThemeManager.contrastInk(theme.error)
                : theme.accent);
        return button;
    }

    private void clearBgmiDataAsync() {
        final String packageName = selectedGamePkg;
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        if (btnClearBgmiData != null) {
            btnClearBgmiData.setEnabled(false);
            btnClearBgmiData.setText("CLEARING BGMI DATA…");
        }

        new Thread(() -> {
            boolean cleared = ApkEnv.getInstance().clearAppData(packageName);
            runOnUiThread(() -> {
                if (btnClearBgmiData != null) {
                    btnClearBgmiData.setEnabled(true);
                    btnClearBgmiData.setText(ServerInstallStrings.CLEAR_BGMI_DATA);
                }
                if (cleared) {
                    BoxApplication.get().showToastWithImage(
                            ServerInstallStrings.CLEAR_DATA_SUCCESS,
                            TastyToast.SUCCESS);
                } else {
                    BoxApplication.get().showToastWithImage(
                            ServerInstallStrings.CLEAR_DATA_FAILED,
                            TastyToast.ERROR);
                }
            });
        }, "OneCore-ClearBgmiData").start();
    }

    private void showServerDownloadDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (serverDownloadDialog != null && serverDownloadDialog.isShowing()) {
            refreshServerDownloadDialog();
            return;
        }

        ThemeManager.ThemeSpec theme = ThemeManager.current(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));

        GradientDrawable shell = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{theme.surfaceAlt, theme.surface});
        shell.setCornerRadius(dp(theme.cardRadiusDp));
        shell.setStroke(
                dp(Math.max(1f, theme.strokeDp)),
                ThemeManager.withAlpha(theme.accent, 190));
        root.setBackground(shell);

        TextView title = new TextView(this);
        title.setText(ServerInstallStrings.MANAGER_TITLE);
        title.setTextColor(theme.text);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        root.addView(title, matchWrapParams(0));

        serverDownloadState = new TextView(this);
        serverDownloadState.setTextColor(theme.accent);
        serverDownloadState.setTextSize(12f);
        serverDownloadState.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams stateParams = matchWrapParams(0);
        stateParams.topMargin = dp(10);
        root.addView(serverDownloadState, stateParams);

        serverDownloadDetail = new TextView(this);
        serverDownloadDetail.setTextColor(theme.muted);
        serverDownloadDetail.setTextSize(12f);
        LinearLayout.LayoutParams detailParams = matchWrapParams(0);
        detailParams.topMargin = dp(6);
        root.addView(serverDownloadDetail, detailParams);

        serverDownloadProgress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        serverDownloadProgress.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serverDownloadProgress.setProgressTintList(
                    ColorStateList.valueOf(theme.accent));
            serverDownloadProgress.setProgressBackgroundTintList(
                    ColorStateList.valueOf(
                            ThemeManager.withAlpha(theme.muted, 50)));
        }
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.topMargin = dp(18);
        root.addView(serverDownloadProgress, progressParams);

        serverDownloadPercent = new TextView(this);
        serverDownloadPercent.setTextColor(theme.text);
        serverDownloadPercent.setTextSize(12f);
        serverDownloadPercent.setGravity(Gravity.END);
        LinearLayout.LayoutParams percentParams = matchWrapParams(0);
        percentParams.topMargin = dp(7);
        root.addView(serverDownloadPercent, percentParams);

        TextView cancel = makeInstallChoice(
                ServerInstallStrings.CANCEL_DOWNLOAD,
                ServerInstallStrings.CANCEL_CONFIRM_MESSAGE,
                theme,
                false);
        LinearLayout.LayoutParams cancelParams = matchWrapParams(10);
        cancelParams.topMargin = dp(16);
        root.addView(cancel, cancelParams);

        TextView keep = makeInstallChoice(
                ServerInstallStrings.KEEP_DOWNLOADING,
                "Close this panel and keep the background download running",
                theme,
                true);
        root.addView(keep, matchWrapParams(0));

        cancel.setOnClickListener(v -> {
            ServerInstallWorker.cancel(MainActivity.this);
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.CANCELLED,
                    TastyToast.INFO);
            dialog.dismiss();
            updateButtonState(BGMI_INDEX, installIndia);
        });
        keep.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(ignored -> {
            serverDownloadDialog = null;
            serverDownloadProgress = null;
            serverDownloadState = null;
            serverDownloadDetail = null;
            serverDownloadPercent = null;
        });

        dialog.setContentView(root);
        dialog.show();
        serverDownloadDialog = dialog;

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.78f;
            window.setAttributes(attrs);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(
                    (int) (width * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        root.setAlpha(0f);
        root.setScaleX(0.96f);
        root.setScaleY(0.96f);
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start();

        refreshServerDownloadDialog();
    }

    private void refreshServerDownloadDialog() {
        if (serverDownloadDialog == null || !serverDownloadDialog.isShowing()) {
            return;
        }

        ServerInstallWorker.ProgressSnapshot snapshot =
                ServerInstallWorker.getProgressSnapshot(this);
        if (serverDownloadState != null) {
            serverDownloadState.setText(
                    snapshot.state.isEmpty() ? "PREPARING" : snapshot.state);
        }
        if (serverDownloadDetail != null) {
            serverDownloadDetail.setText(
                    snapshot.detail.isEmpty()
                            ? "Preparing background download"
                            : snapshot.detail);
        }
        if (serverDownloadProgress != null) {
            serverDownloadProgress.setProgress(snapshot.percent);
        }
        if (serverDownloadPercent != null) {
            serverDownloadPercent.setText(snapshot.percent + "%");
        }
    }

    private void runAfterSdkActivation(Runnable action) {
        if (action == null || isFinishing()) {
            return;
        }

        BoxApplication.get().showToastWithImage(
                "Activating OneCore SDK…", TastyToast.INFO);
        new Thread(() -> {
            boolean activated = false;
            try {
                BoxApplication application = BoxApplication.get();
                activated = application != null
                        && application.activateSdkWithFallback(
                                licenseClient == null
                                        ? ""
                                        : licenseClient.getStoredActivationKey());
            } catch (Throwable error) {
                FLog.error("SDK activation before action failed", error);
            }

            final boolean result = activated;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!result) {
                    BoxApplication.get().showToastWithImage(
                            "OneCore SDK activation failed. Verify the SDK panel key.",
                            TastyToast.ERROR);
                    return;
                }
                action.run();
            });
        }, "OneCore-ActionActivation").start();
    }

    private void beginServerInstall() {
        if (!ensureLicenseActive()) {
            pendingServerInstall = false;
            return;
        }

        if (selectedGamePkg == null || selectedGamePkg.isEmpty()) {
            pendingServerInstall = false;
            BoxApplication.get().showToastWithImage(
                    "BGMI profile is unavailable in this build.", TastyToast.ERROR);
            return;
        }

        if (ServerInstallWorker.isRunning(this)) {
            pendingServerInstall = false;
            showServerDownloadDialog();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            pendingServerInstall = true;
            new FileCopyTask(this).requestStoragePermission();
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.STORAGE_PERMISSION_TOAST,
                    TastyToast.INFO);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingServerInstall = true;
            if (!notificationPermissionRequestInFlight) {
                notificationPermissionRequestInFlight = true;
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_SERVER_NOTIFICATIONS);
            }
            return;
        }

        pendingServerInstall = false;
        boolean queued = ServerInstallWorker.enqueue(this, selectedGamePkg);
        if (queued) {
            updateButtonState(BGMI_INDEX, installIndia);
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.STARTED_TOAST,
                    TastyToast.SUCCESS);
            showServerDownloadDialog();
        } else {
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.START_FAILED_TOAST, TastyToast.ERROR);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_SERVER_NOTIFICATIONS) {
            return;
        }
        notificationPermissionRequestInFlight = false;
        if (!pendingServerInstall) {
            return;
        }

        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            beginServerInstall();
        } else {
            pendingServerInstall = false;
            BoxApplication.get().showToastWithImage(
                    ServerInstallStrings.NOTIFICATION_PERMISSION_TOAST,
                    TastyToast.INFO);
        }
    }

    private LinearLayout.LayoutParams matchWrapParams(int bottomMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomMarginDp);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void handleInstallUninstall(final int gameIndex, final TextView installButton) {
        if (!ensureLicenseActive()) {
            return;
        }
        final String packageName = GAME_LIST_PKG[gameIndex];
        final FileCopyTask fileCopyTask = new FileCopyTask(MainActivity.get());

        boolean isInstalled = getInstallationStatus(packageName);
        if (isInstalled) {
            ApkEnv.getInstance().unInstallApp(packageName);
            installButton.setText("INSTALL");
            saveInstallationStatus(packageName, false);
            BoxApplication.get().showToastWithImage(Constants.UNINSTALL_SUCCESS, TastyToast.SUCCESS);
            return;
        }

        if (fileCopyTask.isObbCopied(packageName)) {
            if (ApkEnv.getInstance().installByPackage(packageName)) {
                installButton.setText("UNINSTALL");
                saveInstallationStatus(packageName, true);
                BoxApplication.get().showToastWithImage(Constants.INSTALL_SUCCESS, TastyToast.SUCCESS);
            } else {
                BoxApplication.get().showToastWithImage(Constants.MSG_ERROR, TastyToast.WARNING);
            }
            return;
        }

        fileCopyTask.copyObbFolderAsync(packageName, new FileCopyTask.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean copySuccess) {
                if (!ensureLicenseActive()) {
                    return;
                }
                if (copySuccess) {
                    if (ApkEnv.getInstance().installByPackage(packageName)) {
                        installButton.setText("UNINSTALL");
                        saveInstallationStatus(packageName, true);
                        BoxApplication.get().showToastWithImage(Constants.INSTALL_SUCCESS, TastyToast.SUCCESS);
                    } else {
                        BoxApplication.get().showToastWithImage(Constants.MSG_ERROR, TastyToast.WARNING);
                    }
                } else {
                    BoxApplication.get().showToastWithImage(Constants.COPY_FAILED, TastyToast.ERROR);
                }
            }
        });
    }

    private void saveInstallationStatus(String packageName, boolean installed) {
        SharedPreferences preferences = getSharedPreferences("install_status", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(packageName, installed).apply();
    }

    private boolean getInstallationStatus(String packageName) {
        SharedPreferences preferences = getSharedPreferences("install_status", Context.MODE_PRIVATE);
        return preferences.getBoolean(packageName, false);
    }

    private void updateButtonState(int gameIndex, TextView installButton) {
        if (installButton == null || GAME_LIST_PKG.length <= gameIndex) {
            return;
        }

        ServerInstallWorker.ProgressSnapshot snapshot =
                ServerInstallWorker.getProgressSnapshot(this);
        if (snapshot.running) {
            installButton.setText(
                    ServerInstallStrings.DOWNLOAD_BUTTON_PREFIX
                            + " "
                            + snapshot.percent
                            + "%");
            installButton.setEnabled(true);
            installButton.setAlpha(1f);
            return;
        }

        String packageName = GAME_LIST_PKG[gameIndex];
        installButton.setEnabled(true);
        installButton.setAlpha(1f);
        installButton.setText(
                getInstallationStatus(packageName)
                        ? ServerInstallStrings.UNINSTALL_BUTTON
                        : ServerInstallStrings.INSTALL_BUTTON);
    }

    private void countDownStart() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    long expiryMillis = licenseClient.expiresAtEpochSeconds() * 1000L;
                    long distance = licenseClient.remainingMillis();
                    long days = distance / (24 * 60 * 60 * 1000L);
                    long hours = distance / (60 * 60 * 1000L) % 24;
                    long minutes = distance / (60 * 1000L) % 60;
                    long seconds = distance / 1000L % 60;

                    TextView dayView = findViewById(R.id.tv_d);
                    TextView hourView = findViewById(R.id.tv_h);
                    TextView minuteView = findViewById(R.id.tv_m);
                    TextView secondView = findViewById(R.id.tv_s);

                    dayView.setText(String.format(Locale.US, "%02d", days));
                    hourView.setText(String.format(Locale.US, "%02d", hours));
                    minuteView.setText(String.format(Locale.US, "%02d", minutes));
                    secondView.setText(String.format(Locale.US, "%02d", seconds));
                    secondView.animate().cancel();
                    secondView.setScaleX(0.92f);
                    secondView.setScaleY(0.92f);
                    secondView.animate().scaleX(1f).scaleY(1f).setDuration(180L).start();

                    renderLicenseState(expiryMillis, distance);
                    if (distance <= 0L) {
                        closeExpiredAccess();
                        return;
                    }
                    if (licenseClient.needsOnlineRevalidation(ONLINE_REVALIDATION_INTERVAL_MS)) {
                        revalidateLicenseAsync();
                    }
                } catch (Exception e) {
                    FLog.warning("Unable to update subscription countdown");
                    renderLicenseUnavailable();
                }
                countdownHandler.postDelayed(this, 1000L);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private void renderLicenseState(long expiryMillis, long rawDistance) {
        TextView title = findViewById(R.id.PremiumFileManager);
        TextView subtitle = findViewById(R.id.license_status_subtitle);
        TextView badge = findViewById(R.id.license_status_badge);
        TextView expiryDate = findViewById(R.id.license_expiry_date);
        ProgressBar progressBar = findViewById(R.id.license_progress);

        if (rawDistance <= 0L) {
            title.setText("Access expired");
            subtitle.setText("Renew your key to unlock secure sessions.");
            badge.setText("EXPIRED");
            badge.setTextColor(Color.parseColor("#FFFFDDE2"));
            badge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#B84C1822")));
            expiryDate.setText("License renewal required");
            expiryDate.setTextColor(Color.parseColor("#FFFF667A"));
            progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FFFF667A")));
            progressBar.setProgress(0, true);
            progressBar.setContentDescription("License expired");
            return;
        }

        long warningWindow = 24L * 60L * 60L * 1000L;
        boolean expiringSoon = rawDistance <= warningWindow;
        title.setText(expiringSoon ? "Renew soon" : "Protected session");
        subtitle.setText(expiringSoon
                ? "Less than 24 hours remain on this key."
                : "Live verified access is active.");
        badge.setText(expiringSoon ? "EXPIRING" : "ACTIVE");

        int accent = Color.parseColor(expiringSoon ? "#FFF4BE5E" : "#FF5DE2B1");
        int badgeBackground = Color.parseColor(expiringSoon ? "#8F5B3A10" : "#71325647");
        badge.setTextColor(accent);
        badge.setBackgroundTintList(ColorStateList.valueOf(badgeBackground));
        expiryDate.setText(DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()).format(new Date(expiryMillis)));
        expiryDate.setTextColor(Color.WHITE);

        double thirtyDays = 30d * 24d * 60d * 60d * 1000d;
        int remainingPercent = (int) Math.max(1d, Math.min(100d, (rawDistance / thirtyDays) * 100d));
        progressBar.setProgressTintList(ColorStateList.valueOf(accent));
        progressBar.setProgress(remainingPercent, true);
        progressBar.setContentDescription(remainingPercent + "% of the 30 day license window remains");
    }

    private void renderLicenseUnavailable() {
        TextView title = findViewById(R.id.PremiumFileManager);
        TextView subtitle = findViewById(R.id.license_status_subtitle);
        TextView badge = findViewById(R.id.license_status_badge);
        TextView expiryDate = findViewById(R.id.license_expiry_date);
        ProgressBar progressBar = findViewById(R.id.license_progress);
        title.setText("License unavailable");
        subtitle.setText("Sign in again to refresh the secure session.");
        badge.setText("CHECK");
        badge.setTextColor(Color.parseColor("#FFF4BE5E"));
        badge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8F5B3A10")));
        expiryDate.setText("Verification required");
        progressBar.setProgress(0, true);
    }

    private void GameJsonMods() {
        try {
            JSONArray games = new JSONObject(loadJSONFromAssets()).getJSONArray("games");
            TextView indiaName = findViewById(R.id.IndiaName);
            TextView indiaVersion = findViewById(R.id.IndiaVersion);
            if (indiaName != null && games.length() > 1) {
                indiaName.setText(games.getJSONObject(1).getString("name"));
            }
            if (indiaVersion != null && games.length() > 1) {
                indiaVersion.setText("Version: " + games.getJSONObject(1).getString("version"));
            }
        } catch (Exception e) {
            FLog.warning("Unable to load BGMI profile metadata");
        }
    }

    private String loadJSONFromAssets() {
        try {
            InputStream is = getAssets().open("games.json");
            byte[] buffer = new byte[is.available()];
            int ignored = is.read(buffer);
            is.close();
            return new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            FLog.warning("Unable to read games.json");
            return "{\"games\":[]}";
        }
    }

    private void CheckFloatViewPermission() {
        if (!Settings.canDrawOverlays(MainActivity.get())) {
            BoxApplication.get().showToastWithImage(Constants.MSG_FLOATING, TastyToast.INFO);
            startActivityForResult(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), 0);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (FloatLogo.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startPatcher() {
        if (!ensureLicenseActive()) {
            return;
        }
        if (!Settings.canDrawOverlays(MainActivity.get())) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 123);
        } else {
            startFloater();
        }
    }

    private void startFloater() {
        if (!isServiceRunning()) {
            startService(new Intent(MainActivity.get(), FloatLogo.class));
        } else {
            BoxApplication.get().showToastWithImage(Constants.MSG_RUNNING, TastyToast.WARNING);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (licenseClient != null && licenseClient.hasActiveLicense()) {
            countDownStart();
            if (licenseClient.needsOnlineRevalidation(ONLINE_REVALIDATION_INTERVAL_MS)) {
                revalidateLicenseAsync();
            }
        } else if (licenseClient != null) {
            closeExpiredAccess();
        }

        serverStateHandler.removeCallbacks(serverStateRunnable);
        serverStateHandler.post(serverStateRunnable);

        if (pendingServerInstall
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager())) {
            beginServerInstall();
        }
    }

    @Override
    protected void onPause() {
        serverStateHandler.removeCallbacks(serverStateRunnable);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        serverStateHandler.removeCallbacks(serverStateRunnable);
        stopService(new Intent(this, FloatLogo.class));
        stopService(new Intent(this, Overlay.class));
        stopService(new Intent(this, FloatAim.class));
        super.onDestroy();
    }

    private boolean ensureLicenseActive() {
        if (accessClosed || licenseClient == null || !licenseClient.hasActiveLicense()) {
            closeExpiredAccess();
            return false;
        }
        return true;
    }

    private void revalidateLicenseAsync() {
        if (revalidationInProgress || accessClosed || licenseClient == null) {
            return;
        }
        revalidationInProgress = true;
        new Thread(() -> {
            String result = licenseClient.revalidateStoredLicense();
            runOnUiThread(() -> {
                revalidationInProgress = false;
                if (!"OK".equals(result)) {
                    closeExpiredAccess();
                }
            });
        }, "LicenseRevalidation").start();
    }

    private void closeExpiredAccess() {
        if (accessClosed || isFinishing()) {
            return;
        }
        accessClosed = true;
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        if (licenseClient != null) {
            licenseClient.clearLicense();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
