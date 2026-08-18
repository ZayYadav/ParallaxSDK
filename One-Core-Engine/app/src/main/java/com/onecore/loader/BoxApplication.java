package com.onecore.loader;

import android.app.Application;
import android.content.Context;

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
            // Do not leave the process in a silent startup loop. The UI can still launch and
            // surface a useful error/log instead of being killed by an initialization exception.
            FLog.error("BlackBox attach failed", error);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        FLog.info("Startup: Application.onCreate begin");

        // Full APK cryptographic verification is expensive. IntegrityEnforcer performs it on a
        // background worker and enforces an invalid result after the first UI frame is available.
        IntegrityEnforcer.install(this);

        try {
            FLog.info("Startup: creating BlackBox services");
            BlackBoxCore.get().doCreate();
            FLog.info("Startup: BlackBox services ready");
        } catch (Throwable error) {
            FLog.error("BlackBox service initialization failed", error);
        }

        // Native key retrieval + SDK activation must never delay Application.onCreate().
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

    public void showToastWithImage(String msg, int type) {
        TastyToast.makeText(BoxApplication.get(), msg, TastyToast.LENGTH_LONG, type).show();
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
