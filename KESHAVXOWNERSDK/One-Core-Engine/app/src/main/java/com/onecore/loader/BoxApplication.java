package com.onecore.loader;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.Jagdish.tastytoast.TastyToast;
import com.google.android.material.color.DynamicColors;
import com.onecore.loader.security.IntegrityEnforcer;
import com.onecore.loader.security.SecurityIncidentDispatcher;
import com.onecore.loader.ui.AdvancedUiStyler;
import com.onecore.loader.ui.EdgeVisualInstaller;
import com.onecore.loader.ui.InteractionGlowInstaller;
import com.onecore.loader.ui.PremiumButtonStyler;
import com.onecore.loader.ui.ThemeManager;
import com.onecore.loader.utils.CrashHandler;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.utils.NetworkConnection;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.Random;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.system.api.MetaActivationManager;

public class BoxApplication extends Application {
    public static final String STATUS_BY = "online";
    private static final String UI_PREFS = "onecore_edge_ui";
    private static final String UI_THEME_KEY = "theme_index";

    private native String BoxApp();
    public static BoxApplication gApp;

    private boolean isNetworkConnected = false;

    public static BoxApplication get() {
        return gApp;
    }

    public boolean isInternetAvailable() {
        return isNetworkConnected;
    }

    public void setInternetAvailable(boolean b) {
        isNetworkConnected = b;
    }

    static {
        try {
            System.loadLibrary("ParallaxLoader");
        } catch (UnsatisfiedLinkError error) {
            FLog.error("ParallaxLoader native library could not be loaded: " + error.getMessage());
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FLog.initialize(base);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(base));

        try {
            FLog.info("Startup: attaching BlackBox core");
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }

                @Override
                public boolean isEnableDaemonService() {
                    return true;
                }
            });
            FLog.info("Startup: BlackBox attach complete");
        } catch (Throwable error) {
            FLog.error("BlackBox attach failed", error);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        selectRandomThemeForLaunch();
        configureLoaderActivities();
        FLog.info("Startup: Application.onCreate begin");

        IntegrityEnforcer.install(this);

        try {
            FLog.info("Startup: creating BlackBox services");
            BlackBoxCore.get().doCreate();
            FLog.info("Startup: BlackBox services ready");
        } catch (Throwable error) {
            FLog.error("BlackBox service initialization failed", error);
        }

        new Thread(() -> {
            try {
                MetaActivationManager.activateSdk(BoxApp());
            } catch (Throwable error) {
                FLog.error("Background SDK activation failed", error);
            }
        }, "OneCore-SdkActivation").start();

        try {
            DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable error) {
            FLog.error("Dynamic color initialization failed", error);
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        try {
            NetworkConnection.CheckInternet network = new NetworkConnection.CheckInternet(this);
            network.registerNetworkCallback();
        } catch (Throwable error) {
            FLog.error("Network callback registration failed", error);
        }

        FLog.info("Startup: Application.onCreate complete");
    }

    private void selectRandomThemeForLaunch() {
        int themeCount = ThemeManager.themeCount();
        if (themeCount <= 0) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
        int previousTheme = preferences.getInt(UI_THEME_KEY, -1);
        Random random = new Random(System.nanoTime() ^ android.os.Process.myPid());

        int nextTheme;
        if (themeCount == 1) {
            nextTheme = 0;
        } else if (previousTheme >= 0 && previousTheme < themeCount) {
            int offset = 1 + random.nextInt(themeCount - 1);
            nextTheme = (previousTheme + offset) % themeCount;
        } else {
            nextTheme = random.nextInt(themeCount);
        }

        preferences.edit().putInt(UI_THEME_KEY, nextTheme).apply();
        FLog.info("UI: selected random launch theme " + nextTheme + " of " + themeCount);
    }

    private void configureLoaderActivities() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private void configure(Activity activity) {
                if (activity == null || activity.getWindow() == null) {
                    return;
                }
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                AdvancedUiStyler.attach(activity);
                ThemeManager.attach(activity);
                PremiumButtonStyler.attach(activity);
                EdgeVisualInstaller.attach(activity);
                InteractionGlowInstaller.attach(activity);
                SecurityIncidentDispatcher.attach(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                configure(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                configure(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                configure(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                AdvancedUiStyler.detach(activity);
                ThemeManager.detach(activity);
                PremiumButtonStyler.detach(activity);
                InteractionGlowInstaller.detach(activity);
                SecurityIncidentDispatcher.detach(activity);
            }
        });
    }

    public void showToastWithImage(String msg, int type) {
        TastyToast.makeText(this, msg, TastyToast.LENGTH_LONG, type);
    }

    public static boolean checkRootAccess() {
        if (Shell.rootAccess()) {
            FLog.info("Root granted");
            return true;
        } else {
            FLog.info("Root not granted");
            return false;
        }
    }

    public static void doExe(String shell) {
        if (checkRootAccess()) {
            Shell.su(shell).exec();
        } else {
            try {
                Runtime.getRuntime().exec(shell);
                FLog.info("Shell: " + shell);
            } catch (IOException e) {
                FLog.error(e.getMessage());
            }
        }
    }

    public void doExecute(String shell) {
        doChmod(shell, 777);
        doExe(shell);
    }

    public static void doChmod(String shell, int mask) {
        doExe("chmod " + mask + " " + shell);
    }
}
