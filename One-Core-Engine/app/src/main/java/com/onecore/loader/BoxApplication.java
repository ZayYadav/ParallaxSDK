package com.onecore.loader;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.Jagdish.tastytoast.TastyToast;
import com.google.android.material.color.DynamicColors;
import com.onecore.loader.security.HostedLicenseClient;
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

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.system.api.MetaActivationManager;

public class BoxApplication extends Application {
    public static final String STATUS_BY = "online";
    private native String BoxApp();
    public static BoxApplication gApp;

    private final Object sdkActivationLock = new Object();
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
                String storedKey = new HostedLicenseClient(this).getStoredActivationKey();
                boolean activated = activateSdkWithFallback(storedKey);
                FLog.info("Startup SDK activation result: " + activated);
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

    /**
     * Ensures the embedded SDK is actually activated before OneCore package operations run.
     *
     * The native bootstrap key remains the primary SDK-panel credential. If that key is no
     * longer provisioned, the securely verified Loader key is tried as a compatibility fallback.
     * Calls are serialized so startup/login/install cannot race each other.
     */
    public boolean activateSdkWithFallback(String loaderKey) {
        synchronized (sdkActivationLock) {
            try {
                if (MetaActivationManager.getActivatedStatus()) {
                    return true;
                }

                String bootstrapKey = "";
                try {
                    String value = BoxApp();
                    bootstrapKey = value == null ? "" : value.trim();
                } catch (Throwable error) {
                    FLog.warning("Native SDK bootstrap key is unavailable");
                }

                if (!bootstrapKey.isEmpty()) {
                    boolean ok = activateSdkAndWaitCompat(bootstrapKey, 45_000L);
                    if (ok) {
                        return true;
                    }
                    if (isSdkActivationInProgressCompat()) {
                        return false;
                    }
                }

                String fallback = loaderKey == null ? "" : loaderKey.trim();
                if (!fallback.isEmpty() && !fallback.equalsIgnoreCase(bootstrapKey)) {
                    boolean ok = activateSdkAndWaitCompat(fallback, 45_000L);
                    if (ok) {
                        return true;
                    }
                }

                return MetaActivationManager.getActivatedStatus();
            } catch (Throwable error) {
                FLog.error("SDK activation bridge failed", error);
                return false;
            }
        }
    }

    /**
     * Uses the newer blocking activation API when the freshly built SDK AAR is present.
     * Loader-only CI may still compile against an older bundled AAR, so reflection keeps
     * that build compatible and falls back to bounded status polling.
     */
    private boolean activateSdkAndWaitCompat(String key, long timeoutMillis) {
        try {
            java.lang.reflect.Method method = MetaActivationManager.class.getMethod(
                    "activateSdkAndWait", String.class, long.class);
            Object value = method.invoke(null, key, timeoutMillis);
            return Boolean.TRUE.equals(value);
        } catch (NoSuchMethodException ignored) {
            MetaActivationManager.activateSdk(key);
            long deadline = android.os.SystemClock.elapsedRealtime()
                    + Math.max(1000L, Math.min(120000L, timeoutMillis));
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (MetaActivationManager.getActivatedStatus()) {
                    return true;
                }
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return MetaActivationManager.getActivatedStatus();
        } catch (Throwable error) {
            FLog.error("Blocking SDK activation call failed", error);
            return false;
        }
    }

    private boolean isSdkActivationInProgressCompat() {
        try {
            java.lang.reflect.Method method = MetaActivationManager.class.getMethod(
                    "isActivationInProgress");
            Object value = method.invoke(null);
            return Boolean.TRUE.equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void selectRandomThemeForLaunch() {
        ThemeManager.randomizeForLaunch(this);
        FLog.info("UI: selected automatic launch theme "
                + ThemeManager.currentIndex(this)
                + " of "
                + ThemeManager.themeCount());
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
