package top.niunaijun.blackbox.compat.auth;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;

/**
 * Makes normal network-permission self checks coherent for a virtual guest.
 *
 * A permission is reported as granted only when it is a normal network permission
 * and the guest APK itself declared it. The fallback archive parse reads only the
 * guest's own manifest when the virtual PackageInfo path is temporarily incomplete.
 */
public final class SocialPermissionCompat {
    private static final String TAG = "SocialPermission";
    private static final Set<String> LOGGED = new HashSet<>();

    private SocialPermissionCompat() {
    }

    public static boolean isNormalNetworkPermission(String permission) {
        return Manifest.permission.INTERNET.equals(permission)
                || Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)
                || Manifest.permission.ACCESS_WIFI_STATE.equals(permission);
    }

    public static boolean guestDeclaresNormalNetworkPermission(String permission) {
        if (!isNormalNetworkPermission(permission)) {
            return false;
        }

        String pkg = BActivityThread.getAppPackageName();
        if (pkg == null || pkg.trim().isEmpty()) {
            diagnostic(permission, false, "no_guest_package");
            return false;
        }

        try {
            PackageInfo info = BPackageManager.get().getPackageInfo(
                    pkg, PackageManager.GET_PERMISSIONS, BActivityThread.getUserId());
            if (declares(info, permission)) {
                diagnostic(permission, true, "virtual_package_info");
                return true;
            }
        } catch (Throwable ignored) {
        }

        // During early provider/application startup some SDKs check INTERNET before
        // all virtual package-manager surfaces are fully stable. Parse the same
        // guest APK manifest as a fallback instead of granting an undeclared perm.
        try {
            ApplicationInfo appInfo = BPackageManager.get().getApplicationInfo(
                    pkg, 0, BActivityThread.getUserId());
            String sourceDir = appInfo == null ? null : appInfo.sourceDir;
            if (sourceDir != null && !sourceDir.trim().isEmpty()) {
                PackageManager pm = BlackBoxCore.getContext().getPackageManager();
                PackageInfo archive = pm.getPackageArchiveInfo(
                        sourceDir, PackageManager.GET_PERMISSIONS);
                if (declares(archive, permission)) {
                    diagnostic(permission, true, "guest_archive");
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        diagnostic(permission, false, "not_declared");
        return false;
    }

    private static boolean declares(PackageInfo info, String permission) {
        if (info == null || info.requestedPermissions == null) {
            return false;
        }
        for (String requested : info.requestedPermissions) {
            if (permission.equals(requested)) {
                return true;
            }
        }
        return false;
    }

    private static void diagnostic(String permission, boolean declared, String source) {
        try {
            String key = permission + ":" + declared + ":" + source;
            synchronized (LOGGED) {
                if (!LOGGED.add(key)) return;
            }
            // Permission names/source only; no user/provider credentials or tokens.
            Log.i(TAG, "permission=" + permission
                    + " declared=" + declared
                    + " source=" + source);
        } catch (Throwable ignored) {
        }
    }
}
