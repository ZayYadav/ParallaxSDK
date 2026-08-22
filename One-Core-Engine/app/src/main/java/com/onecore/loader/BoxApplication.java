package com.onecore.loader;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.Jagdish.tastytoast.TastyToast;
import com.google.android.material.color.DynamicColors;
import com.onecore.loader.security.IntegrityEnforcer;
import com.onecore.loader.utils.CrashHandler;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.utils.NetworkConnection;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.system.api.MetaActivationManager;

public class BoxApplication extends Application {
    public static final String STATUS_BY = "online";
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
        allowScreenshotsAcrossLoader();
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

    private void allowScreenshotsAcrossLoader() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private void clearSecureFlag(Activity activity) {
                if (activity != null && activity.getWindow() != null) {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                clearSecureFlag(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                clearSecureFlag(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                clearSecureFlag(activity);
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
