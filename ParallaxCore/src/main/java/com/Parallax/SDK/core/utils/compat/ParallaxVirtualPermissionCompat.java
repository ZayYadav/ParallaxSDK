package com.Parallax.SDK.core.utils.compat;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.frameworks.ParallaxPackageManager;

/**
 * Restores normal manifest permission checks for the active virtual package.
 *
 * Android 16 can evaluate a virtual app's permission request against the Loader
 * host UID. That makes install-time permissions such as INTERNET look denied to
 * SDKs running inside the guest. Only normal network permissions declared by the
 * guest APK are repaired here; runtime/dangerous permissions remain untouched.
 */
public final class ParallaxVirtualPermissionCompat {
    private ParallaxVirtualPermissionCompat() {
    }

    public static boolean shouldGrantDeclaredNetworkPermission(String permission) {
        return shouldGrantDeclaredNetworkPermission(permission, null);
    }

    public static boolean shouldGrantDeclaredNetworkPermission(
            String permission, String requestedPackage) {
        String virtualPackage = ParallaxActivityThread.getAppPackageName();
        if (!isEligibleRequest(permission, requestedPackage, virtualPackage)) {
            return false;
        }

        try {
            PackageInfo info = ParallaxPackageManager.get().getPackageInfo(
                    virtualPackage,
                    PackageManager.GET_PERMISSIONS,
                    ParallaxActivityThread.getUserId());
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
