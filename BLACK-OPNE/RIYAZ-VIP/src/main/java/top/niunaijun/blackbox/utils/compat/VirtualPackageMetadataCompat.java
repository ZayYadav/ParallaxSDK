package top.niunaijun.blackbox.utils.compat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Keeps Android 16 compatibility metadata and normal manifest permission state
 * visible through the same PackageManager instance used by code inside a virtual
 * application.
 *
 * This wrapper delegates package-manager operations to the existing BlackBox
 * IPackageManager proxy and only repairs data that would otherwise be evaluated
 * against the Loader host UID. It never grants dangerous/runtime permissions and
 * never changes package names, signatures, OAuth credentials or provider results.
 */
public final class VirtualPackageMetadataCompat {
    private VirtualPackageMetadataCompat() {
    }

    public static void install(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 36) {
            return;
        }
        try {
            // Cover direct Context.getApplicationInfo() reads before providers
            // initialize.
            GmsCore.applyVirtualAppGmsSafety(context.getApplicationInfo());

            PackageManager packageManager = context.getPackageManager();
            if (packageManager == null) {
                return;
            }

            Reflector mPmField = Reflector.on("android.app.ApplicationPackageManager")
                    .field("mPM");
            Object current = mPmField.get(packageManager);
            if (current == null || isOurProxy(current)) {
                return;
            }

            Class<?>[] interfaces = MethodParameterUtils.getAllInterface(current.getClass());
            if (interfaces.length == 0) {
                return;
            }

            ClassLoader loader = current.getClass().getClassLoader();
            if (loader == null) {
                loader = VirtualPackageMetadataCompat.class.getClassLoader();
            }
            Object proxy = Proxy.newProxyInstance(
                    loader,
                    interfaces,
                    new MetadataInvocationHandler(current));
            mPmField.set(packageManager, proxy);
        } catch (Throwable ignored) {
            // OEM/private API differences must not stop app startup.
        }
    }

    private static boolean isOurProxy(Object candidate) {
        if (candidate == null || !Proxy.isProxyClass(candidate.getClass())) {
            return false;
        }
        try {
            return Proxy.getInvocationHandler(candidate) instanceof MetadataInvocationHandler;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MetadataInvocationHandler implements InvocationHandler {
        private final Object delegate;

        private MetadataInvocationHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Integer permissionOverride = getNormalPermissionOverride(method, args);
            if (permissionOverride != null) {
                return permissionOverride;
            }

            final Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException invocationFailure) {
                Throwable cause = invocationFailure.getTargetException();
                throw cause != null ? cause : invocationFailure;
            }

            if (result instanceof ApplicationInfo) {
                return GmsCore.applyVirtualAppGmsSafety((ApplicationInfo) result);
            }
            if (result instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) result;
                GmsCore.applyVirtualAppGmsSafety(packageInfo.applicationInfo);
                return packageInfo;
            }
            return result;
        }
    }

    /**
     * ApplicationPackageManager.checkPermission() ultimately talks to the real
     * package service. A virtual package such as com.pubg.imobile is not installed
     * for the Loader UID, so normal manifest permissions can incorrectly look
     * denied. Repair only the three normal network permissions commonly checked
     * during social-login SDK bootstrap, and only when the virtual APK declared
     * the permission itself.
     */
    private static Integer getNormalPermissionOverride(Method method, Object[] args) {
        if (method == null || !"checkPermission".equals(method.getName()) || args == null) {
            return null;
        }

        String permission = null;
        String packageName = null;
        String virtualPackage = BActivityThread.getAppPackageName();
        for (Object arg : args) {
            if (!(arg instanceof String)) {
                continue;
            }
            String value = (String) arg;
            if (value.startsWith("android.permission.")) {
                permission = value;
            } else if (virtualPackage != null && virtualPackage.equals(value)) {
                packageName = value;
            }
        }

        if (packageName == null || !isNormalNetworkPermission(permission)) {
            return null;
        }
        return virtualPackageDeclares(packageName, permission)
                ? PackageManager.PERMISSION_GRANTED : null;
    }

    private static boolean isNormalNetworkPermission(String permission) {
        return Manifest.permission.INTERNET.equals(permission)
                || Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)
                || Manifest.permission.ACCESS_WIFI_STATE.equals(permission);
    }

    private static boolean virtualPackageDeclares(String packageName, String permission) {
        if (packageName == null || permission == null) {
            return false;
        }
        try {
            PackageInfo info = BPackageManager.get().getPackageInfo(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                    BActivityThread.getUserId());
            if (info == null || info.requestedPermissions == null) {
                return false;
            }
            for (String requested : info.requestedPermissions) {
                if (permission.equals(requested)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
