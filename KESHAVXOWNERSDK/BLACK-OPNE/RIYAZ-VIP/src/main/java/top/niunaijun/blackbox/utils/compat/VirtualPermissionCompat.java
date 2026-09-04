package top.niunaijun.blackbox.utils.compat;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;

/**
 * Restores normal manifest permission checks for the active virtual package.
 *
 * Android 16 can evaluate a virtual app's permission request against the Loader
 * host UID. That makes install-time permissions such as INTERNET look denied to
 * SDKs running inside the guest. Only normal network permissions declared by the
 * guest APK are repaired here; runtime/dangerous permissions remain untouched.
 */
public final class VirtualPermissionCompat {
    private VirtualPermissionCompat() {
    }

    public static boolean shouldGrantDeclaredNetworkPermission(String permission) {
        return shouldGrantDeclaredNetworkPermission(permission, null);
    }

    public static boolean shouldGrantDeclaredNetworkPermission(
            String permission, String requestedPackage) {
        String virtualPackage = BActivityThread.getAppPackageName();
        if (!isEligibleRequest(permission, requestedPackage, virtualPackage)) {
            return false;
        }

        try {
            PackageInfo info = BPackageManager.get().getPackageInfo(
                    virtualPackage,
                    PackageManager.GET_PERMISSIONS,
                    BActivityThread.getUserId());
            return declaresPermission(info == null ? null : info.requestedPermissions, permission);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isEligibleRequest(
            String permission, String requestedPackage, String virtualPackage) {
        if (!isNormalNetworkPermission(permission)
                || virtualPackage == null || virtualPackage.trim().isEmpty()) {
            return false;
        }
        return requestedPackage == null || virtualPackage.equals(requestedPackage);
    }

    static boolean declaresPermission(String[] requestedPermissions, String permission) {
        if (requestedPermissions == null || permission == null) {
            return false;
        }
        for (String requested : requestedPermissions) {
            if (permission.equals(requested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNormalNetworkPermission(String permission) {
        return Manifest.permission.INTERNET.equals(permission)
                || Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)
                || Manifest.permission.ACCESS_WIFI_STATE.equals(permission);
    }
}
