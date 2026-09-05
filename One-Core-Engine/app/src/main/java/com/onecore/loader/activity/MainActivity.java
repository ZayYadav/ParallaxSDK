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
    private RadioButton tvHideEsp;

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
            serverStateHandler.postDelayed(this, 1000L);
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
        tvHideEsp = findViewById(R.id.tv_hide_esp);

        TextView deviceStatus = findViewById(R.id.tv_device_status);
        deviceStatus.setText("Android API " + Build.VERSION.SDK_INT
                + "  •  " + TextUtils.join(", ", Build.SUPPORTED_ABIS));

        updateButtonState(BGMI_INDEX, installIndia);

        installIndia.setOnClickListener(view -> {
            if (!ensureLicenseActive()) {
                return;
            }
            if (ServerInstallWorker.isRunning(MainActivity.this)) {
                BoxApplication.get().showToastWithImage(
                        "BGMI server download is already running in background.",
                        TastyToast.INFO);
                return;
            }
            showInstallSourceDialog();
        });

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

            BoxApplication.get().showToastWithImage(
                    "BGMI profile ready • Starting secure session", TastyToast.SUCCESS);
            ApkEnv.getInstance().LaunchApplication(selectedGamePkg);
            startPatcher();
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

        // Once installed, the same button remains a direct UNINSTALL action.
        if (getInstallationStatus(selectedGamePkg)) {
            handleInstallUninstall(BGMI_INDEX, installIndia);
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
        title.setText("INSTALL BGMI");
        title.setTextColor(theme.text);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.create(
                theme.headingFont, android.graphics.Typeface.BOLD));
        title.setLetterSpacing(0.05f);
        root.addView(title, matchWrapParams(0));

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose where OneCore should get the game files");
        subtitle.setTextColor(theme.muted);
        subtitle.setTextSize(12f);
        LinearLayout.LayoutParams subtitleParams = matchWrapParams(0);
        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(15);
        root.addView(subtitle, subtitleParams);

        TextView installedGame = makeInstallChoice(
                "INSTALL FROM YOUR INSTALLED GAME",
                "Copy the BGMI APK + OBB already available on this device",
                theme,
                false);
        root.addView(installedGame, matchWrapParams(10));

        TextView oneCoreServer = makeInstallChoice(
                "INSTALL BGMI FROM ONECORE SERVER",
                "Fast resumable CDN download • runs in background with notification progress",
                theme,
                true);
        root.addView(oneCoreServer, matchWrapParams(0));

        TextView footer = new TextView(this);
        footer.setText("Tap outside to cancel");
        footer.setTextColor(theme.muted);
        footer.setTextSize(10f);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = matchWrapParams(0);
        footerParams.topMargin = dp(13);
        root.addView(footer, footerParams);

        installedGame.setOnClickListener(v -> {
            dialog.dismiss();
            handleInstallUninstall(BGMI_INDEX, installIndia);
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
            attrs.dimAmount = 0.78f;
            window.setAttributes(attrs);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(
                    (int) (width * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
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
            BoxApplication.get().showToastWithImage(
                    "BGMI server download is already running in background.",
                    TastyToast.INFO);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            pendingServerInstall = true;
            new FileCopyTask(this).requestStoragePermission();
            BoxApplication.get().showToastWithImage(
                    "Allow file access once. Download will start when you return.",
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
                    "BGMI download started • you can leave the loader open or put it in background.",
                    TastyToast.SUCCESS);
        } else {
            BoxApplication.get().showToastWithImage(
                    "Unable to start background download.", TastyToast.ERROR);
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
                    "Notification permission is needed to show background download progress.",
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

        if (ServerInstallWorker.isRunning(this)) {
            installButton.setText("DOWNLOADING…");
            installButton.setEnabled(false);
            installButton.setAlpha(0.72f);
            return;
        }

        String packageName = GAME_LIST_PKG[gameIndex];
        installButton.setEnabled(true);
        installButton.setAlpha(1f);
        installButton.setText(getInstallationStatus(packageName) ? "UNINSTALL" : "INSTALL");
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
