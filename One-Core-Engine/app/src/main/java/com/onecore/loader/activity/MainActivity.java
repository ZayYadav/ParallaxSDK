package com.onecore.loader.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.view.View;
import android.view.WindowManager;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.onecore.loader.floating.FloatAim;
import com.onecore.loader.floating.FloatLogo;
import com.onecore.loader.floating.Overlay;
import com.onecore.loader.libhelper.DownloadZip;
import com.onecore.loader.utils.CrashHandler;
import com.Jagdish.tastytoast.TastyToast;
import com.onecore.loader.BoxApplication;
import com.onecore.loader.libhelper.ApkEnv;
import com.onecore.loader.libhelper.FileCopyTask;
import com.onecore.loader.utils.Constants;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.security.HostedLicenseClient;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import static com.onecore.loader.Config.GAME_LIST_PKG;
import com.onecore.loader.R;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class MainActivity extends Activity {

    private static final long ONLINE_REVALIDATION_INTERVAL_MS = 5L * 60L * 1000L;

    public static MainActivity instance;
    private BlackBoxCore blackBoxCore;
    private InstallResult installResult;
    private SharedPreferences sharedPreferences;
    public static native String FixCrash();
    public String CURRENT_PACKAGE;
    private TextView installIndia, btnStartGame;
    private RadioGroup gameSelection;
    private RadioButton radioIndia, tvHideEsp;
    
    public static int gameType = 0;
    private boolean isGameLaunched = false;
    private String selectedGamePkg = "";
    private boolean isIndiaSelected = false;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private HostedLicenseClient licenseClient;
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
        sharedPreferences = getSharedPreferences(getPackageName(), Activity.MODE_PRIVATE);
        
        selectedGamePkg = "";
        gameType = 0;
        isIndiaSelected = false;
        
        // Find Views
        installIndia = findViewById(R.id.installIndia);
        btnStartGame = findViewById(R.id.btn_start_game);
        gameSelection = findViewById(R.id.radio_group_games);
        radioIndia = findViewById(R.id.radio_india);
        tvHideEsp = findViewById(R.id.tv_hide_esp);
        TextView deviceStatus = findViewById(R.id.tv_device_status);
        deviceStatus.setText("Android API " + Build.VERSION.SDK_INT
                + "  •  " + TextUtils.join(", ", Build.SUPPORTED_ABIS));

        // Make sure radio button is unchecked initially
        if (radioIndia != null) {
            radioIndia.setChecked(false);
        }
        
        // Set RadioButton click listener
        if (radioIndia != null) {
            radioIndia.setOnClickListener(v -> {
                boolean isChecked = radioIndia.isChecked();
                
                if (isChecked) {
                    selectedGamePkg = GAME_LIST_PKG[0];
                    gameType = 5;
                    isIndiaSelected = true;
                    BoxApplication.get().showToastWithImage("✓ India Game Selected ✓", TastyToast.SUCCESS);
                    
                    radioIndia.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            radioIndia.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start();
                        })
                        .start();
                } else {
                    selectedGamePkg = "";
                    gameType = 0;
                    isIndiaSelected = false;
                    BoxApplication.get().showToastWithImage("Game deselected", TastyToast.INFO);
                }
            });
        }
        
        // RadioGroup listener
        if (gameSelection != null) {
            gameSelection.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radio_india) {
                    selectedGamePkg = GAME_LIST_PKG[0];
                    gameType = 5;
                    isIndiaSelected = true;
                    BoxApplication.get().showToastWithImage("✓ India Game Selected ✓", TastyToast.SUCCESS);
                }
            });
        }
        
        // Update Install Button State
        updateButtonState(0, installIndia);
        
        // Install button click listener
        installIndia.setOnClickListener(view -> {
            if (ensureLicenseActive()) {
                handleInstallUninstall(0, installIndia);
            }
        });

        // Start Game button click listener
        btnStartGame.setOnClickListener(v -> {
            if (!ensureLicenseActive()) {
                return;
            }
            if (!isIndiaSelected || selectedGamePkg == null || selectedGamePkg.isEmpty()) {
                BoxApplication.get().showToastWithImage("⚠ Please select India game first! ⚠", TastyToast.WARNING);
                if (radioIndia != null) {
                    radioIndia.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            radioIndia.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .start();
                        })
                        .start();
                }
                return;
            }

            if (!ApkEnv.getInstance().isInstalled(selectedGamePkg)) {
                BoxApplication.get().showToastWithImage(Constants.GAME_NOT_INSTALL, TastyToast.ERROR);
                return;
            }

            ApkEnv.getInstance().LaunchApplication(selectedGamePkg);
            startPatcher();
        });
        
        // Hide ESP option click listener
        if (tvHideEsp != null) {
            tvHideEsp.setOnClickListener(v -> {
                if (tvHideEsp.isChecked()) {
                    BoxApplication.get().showToastWithImage("🔒 ESP Hidden Mode Activated", TastyToast.SUCCESS);
                } else {
                    BoxApplication.get().showToastWithImage("👁️ ESP Visible Mode", TastyToast.INFO);
                }
            });
        }
        
        // Start download - DownloadZip will show its own animation and dialog
        // No need to show any toast here as DownloadZip handles it
        new DownloadZip(MainActivity.get()).startDownload(FixCrash(), new DownloadZip.DownloadCallback() {
            @Override
            public void onStart() {
                // DownloadZip shows its own animation
            }
            @Override
            public void onProgress(int progress) {
                // Progress is handled in DownloadZip animation
            }
            @Override
            public void onSuccess() {
                // Don't show toast - DownloadZip already shows success dialog
                // You can add any additional logic here if needed
            }
            @Override
            public void onError(String error) {
                // Don't show toast - DownloadZip already shows error dialog
                // You can add any additional logic here if needed
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
        } else {
            // FileCopyTask will show its own animation and dialog
            if (fileCopyTask.isObbCopied(packageName)) {
                if (ApkEnv.getInstance().installByPackage(packageName)) {
                    installButton.setText("UNINSTALL");
                    saveInstallationStatus(packageName, true);
                    BoxApplication.get().showToastWithImage(Constants.INSTALL_SUCCESS, TastyToast.SUCCESS);
                } else {
                    BoxApplication.get().showToastWithImage(Constants.MSG_ERROR, TastyToast.WARNING);
                }
            } else {
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
        }
    }
    
    private void saveInstallationStatus(String packageName, boolean installed) {
        SharedPreferences preferences = MainActivity.get().getSharedPreferences("install_status", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(packageName, installed).apply();
    }

    private boolean getInstallationStatus(String packageName) {
        SharedPreferences preferences = MainActivity.get().getSharedPreferences("install_status", Context.MODE_PRIVATE);
        return preferences.getBoolean(packageName, false);
    }
    
    private void updateButtonState(int gameIndex, TextView installButton) {
        String packageName = GAME_LIST_PKG[gameIndex];
        boolean installed = getInstallationStatus(packageName);
        if(installed) {
            installButton.setText("UNINSTALL");
        } else {
            installButton.setText("INSTALL");
        }
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
                    if (licenseClient.needsOnlineRevalidation(
                            ONLINE_REVALIDATION_INTERVAL_MS)) {
                        revalidateLicenseAsync();
                    }
                } catch (Exception e) {
                    FLog.warning("Unable to update subscription countdown");
                    renderLicenseUnavailable();
                }
                countdownHandler.postDelayed(this, 1000);
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
            if (indiaName != null) {
                indiaName.setText(games.getJSONObject(1).getString("name"));
            }
            if (indiaVersion != null) {
                indiaVersion.setText("Version: " + games.getJSONObject(1).getString("version"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String loadJSONFromAssets() {
        try {
            InputStream is = getAssets().open("games.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void CheckFloatViewPermission() {
        if (!Settings.canDrawOverlays(MainActivity.get())) {
            BoxApplication.get().showToastWithImage(Constants.MSG_FLOATING, TastyToast.INFO);
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
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
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
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
            if (licenseClient.needsOnlineRevalidation(
                    ONLINE_REVALIDATION_INTERVAL_MS)) {
                revalidateLicenseAsync();
            }
        } else if (licenseClient != null) {
            closeExpiredAccess();
        }
    }

    @Override
    protected void onPause() {
        // Keep the monotonic expiry gate alive while the game/overlay is foregrounded.
        super.onPause();
    }
    
    @Override
    public void onDestroy() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        stopService(new Intent(MainActivity.get(), FloatLogo.class));
        stopService(new Intent(MainActivity.get(), Overlay.class));
        stopService(new Intent(MainActivity.get(), FloatAim.class));
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
