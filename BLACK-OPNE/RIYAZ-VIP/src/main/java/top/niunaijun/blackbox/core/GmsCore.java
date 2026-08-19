package top.niunaijun.blackbox.core;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import java.util.HashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.utils.auth.Auth;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class GmsCore {
    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    // Firebase/Google Analytics metadata keys documented for Android apps.
    // In a virtual package the real Android UID belongs to the host package,
    // so background measurement can fail strict package/UID validation in
    // Android 16 / recent Play Services. Deactivating measurement avoids that
    // non-essential background call while leaving Google sign-in/auth intact.
    private static final String FIREBASE_ANALYTICS_DEACTIVATED =
            "firebase_analytics_collection_deactivated";
    private static final String FIREBASE_ANALYTICS_ENABLED =
            "firebase_analytics_collection_enabled";
    private static final String GOOGLE_ANALYTICS_ADID_ENABLED =
            "google_analytics_adid_collection_enabled";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }

    /**
     * Adds only compatibility metadata to the currently running virtual app.
     * This does not alter the APK on disk and does not spoof package/signing
     * identity or change metadata for unrelated queried packages.
     */
    public static ApplicationInfo applyVirtualAppGmsSafety(ApplicationInfo info) {
        if (info == null || info.packageName == null || Build.VERSION.SDK_INT < 36) {
            return info;
        }

        String virtualPackage = BActivityThread.getAppPackageName();
        if (virtualPackage == null || !virtualPackage.equals(info.packageName)) {
            return info;
        }
        if (info.packageName.equals(BlackBoxCore.getHostPkg())
                || isGoogleAppOrService(info.packageName)) {
            return info;
        }

        Bundle metaData = info.metaData == null ? new Bundle() : new Bundle(info.metaData);
        metaData.putBoolean(FIREBASE_ANALYTICS_DEACTIVATED, true);
        metaData.putBoolean(FIREBASE_ANALYTICS_ENABLED, false);
        metaData.putBoolean(GOOGLE_ANALYTICS_ADID_ENABLED, false);
        info.metaData = metaData;
        return info;
    }

    public static boolean setGoogleAppOrService(String pkg) {
        if (pkg == null) return false;
        for (String p : Auth.AUTH_PKG_SET) {
            if (pkg.equals(p) || pkg.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGmsIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (action == null) return false;
        return action.startsWith("com.google.android.gms")
                || action.startsWith("com.google.android.gsf")
                || action.contains(".gms.")
                || action.contains(".play.");
    }

    private static InstallResult installPackages(Set<String> list, int userId) {
        BlackBoxCore sBlackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            if (sBlackBoxCore.isInstalled(packageName, userId)) {
                continue;
            }

            try {
                BlackBoxCore.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException ignored) {
                continue;
            }

            InstallResult installResult = sBlackBoxCore.installPackageAsUser(packageName, userId);
            if (!installResult.success) {
                return installResult;
            }
        }
        return new InstallResult();
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        BlackBoxCore sBlackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            sBlackBoxCore.uninstallPackageAsUser(packageName, userId);
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new HashSet<>();
        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);

        InstallResult installResult = installPackages(googleApps, userId);
        if (!installResult.success) {
            uninstallGApps(userId);
            return installResult;
        }
        return installResult;
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }

    public static boolean isSupportGms() {
        try {
            BlackBoxCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static boolean isInstalledGoogleService(int userId) {
        return BlackBoxCore.get().isInstalled(GMS_PKG, userId);
    }
}
