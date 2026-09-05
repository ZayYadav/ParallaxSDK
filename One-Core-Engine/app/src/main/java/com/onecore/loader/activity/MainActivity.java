package com.onecore.loader.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.WindowManager;
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
import com.onecore.loader.server.ServerInstallManager;
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

    public static MainActivity instance;
    private BlackBoxCore blackBoxCore;
    public static native String FixCrash();
    public String CURRENT_PACKAGE;

    private TextView installIndia;
    private TextView installFromServer;
    private TextView btnStartGame;
    private RadioButton tvHideEsp;

    public static int gameType = 5;
    private String selectedGamePkg;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private HostedLicenseClient licenseClient;
    private ServerInstallManager serverInstallManager;
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
        installFromServer = findViewById(R.id.installFromServer);
        btnStartGame = findViewById(R.id.btn_start_game);
        serverInstallManager = new ServerInstallManager(this);
        tvHideEsp = findViewById(R.id.tv_hide_esp);

        TextView deviceStatus = findViewById(R.id.tv_device_status);
        deviceStatus.setText("Android API " + Build.VERSION.SDK_INT
                + "  •  " + TextUtils.join(", ", Build.SUPPORTED_ABIS));

        updateButtonState(BGMI_INDEX, installIndia);

        installIndia.setOnClickListener(view -> {
            if (ensureLicenseActive()) {
                if (serverInstallManager != null && serverInstallManager.isBusy()) {
                    BoxApplication.get().showToastWithImage(
                            "Server installation is already running.", TastyToast.INFO);
                    return;
                }
                handleInstallUninstall(BGMI_INDEX, installIndia);
            }
        });

        if (installFromServer != null) {
            installFromServer.setOnClickListener(view -> {
                if (!ensureLicenseActive()) {
                    return;
                }
                if (selectedGamePkg == null || selectedGamePkg.isEmpty()) {
                    BoxApplication.get().showToastWithImage(
                            "BGMI profile is unavailable in this build.", TastyToast.ERROR);
                    return;
                }
                if (serverInstallManager == null || serverInstallManager.isBusy()) {
                    BoxApplication.get().showToastWithImage(
                            "Server installation is already running.", TastyToast.INFO);
                    return;
                }

                installFromServer.setEnabled(false);
                installFromServer.setAlpha(0.72f);
                installFromServer.setText("SERVER INSTALL RUNNING");

                serverInstallManager.start(selectedGamePkg, (success, message) -> {
                    if (installFromServer != null) {
                        installFromServer.setEnabled(true);
                        installFromServer.setAlpha(1f);
                        installFromServer.setText("INSTALL FROM SERVER");
                    }

                    if (success) {
                        saveInstallationStatus(selectedGamePkg, true);
                        updateButtonState(BGMI_INDEX, installIndia);
                        BoxApplication.get().showToastWithImage(
                                "BGMI installed from server.", TastyToast.SUCCESS);
                    } else {
                        BoxApplication.get().showToastWithImage(
                                message, TastyToast.ERROR);
                    }
                });
            });
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
        String packageName = GAME_LIST_PKG[gameIndex];
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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
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
