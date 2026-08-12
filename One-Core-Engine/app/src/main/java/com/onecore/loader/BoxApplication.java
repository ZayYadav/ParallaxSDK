package com.onecore.loader;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import androidx.appcompat.app.AppCompatDelegate;
import com.onecore.loader.utils.CrashHandler;
import com.Jagdish.tastytoast.TastyToast;
import com.onecore.loader.utils.FLog;
import com.onecore.loader.utils.NetworkConnection;
import com.onecore.loader.utils.FPrefs;
import com.onecore.loader.security.SecurityThreatDetector;
import com.onecore.loader.security.IntegrityEnforcer;
import com.google.android.material.color.DynamicColors;
import com.topjohnwu.superuser.Shell;
import java.io.IOException;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
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
        } catch (UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FLog.initialize(base);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(base));
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }
         /*       @Override
                public boolean setHideRoot() {
                    return true;
                } 

                @Override
                public boolean setHideXposed() {
                    return true;
                } */

                @Override
                public boolean isEnableDaemonService(){
                    return true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        if (!IntegrityEnforcer.install(this)) {
            FLog.error("Application signing identity validation failed");
            return;
        }
        if (SecurityThreatDetector.detect(this) != SecurityThreatDetector.Threat.NONE) {
            FLog.error("Application security policy validation failed");
            return;
        }
        BlackBoxCore.get().doCreate();
        try {
            MetaActivationManager.activateSdk(BoxApp());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        NetworkConnection.CheckInternet network = new NetworkConnection.CheckInternet(this);
        network.registerNetworkCallback();
    }
    
    public void showToastWithImage(String msg, int type) {
        TastyToast.makeText(BoxApplication.get(), msg, TastyToast.LENGTH_LONG, type).show();
    }
    
    public static boolean checkRootAccess() {
        if (Shell.rootAccess()){
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
