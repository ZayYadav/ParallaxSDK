package com.Parallax.SDK.core.fake.service;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRContextImpl;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.permission.BRIPermissionManagerStub;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.frameworks.ParallaxPackageManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxReflector;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Fixed for Android 10–16.
 * Keeps normal manifest permissions visible to SDKs running in a virtual app.
 */
public class IParallaxPermissionManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IParallaxPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");

        // ActivityThread.sPermissionManager was removed/reshaped on Android 16
        // builds. Failure to set this optional cache used to abort injectHook()
        // before checkPermission was registered at all.
        try {
            BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
        } catch (Throwable ignored) {
        }

        try {
            Object systemContext = BRActivityThread.get(
                    ParallaxCore.mainThread()).getSystemContext();
            PackageManager packageManager = BRContextImpl.get(systemContext).mPackageManager();
            if (packageManager != null) {
                ParallaxReflector.on("android.app.ApplicationPackageManager").field("mPermissionManager").set(packageManager, proxyInvocation);
            }
        } catch (Throwable ignored) {
            // OEM frameworks may not expose this cache. The ServiceManager proxy
            // installed above and the package/ActivityManager fallbacks remain active.
        }
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ParallaxValueMethodProxy("addPermission", true));
        addMethodHook(new ParallaxValueMethodProxy("performDexOpt", true));
        addMethodHook(new ParallaxValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ParallaxValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ParallaxValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ParallaxValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ParallaxValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new ParallaxPkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (ParallaxBuildCompat.isOreo()) {
            addMethodHook(new ParallaxValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ParallaxValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ParallaxValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ParallaxValueMethodProxy("isInstantApp", false));
        }
    }

    /**
     * Facebook/Meta and several sign-in SDKs perform an early INTERNET permission
     * self-check. On Android 16 the real PermissionManager sees the Loader UID,
     * while the SDK asks about the virtual package, so the normal permission can
     * incorrectly look denied. Only grant normal network permissions that the
     * virtual APK actually declared; dangerous/runtime permissions are untouched.
     */
    @ParallaxProxyMethod("checkPermission")
    public static class CheckPermission extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = findPermission(args);
            if (isNetworkPermission(permission)
                    && virtualPackageDeclares(permission, args)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    private static String findPermission(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (!(arg instanceof String)) continue;
            String value = (String) arg;
            if (value.startsWith("android.permission.")) {
                return value;
            }
        }
        return null;
    }

    private static boolean isNetworkPermission(String permission) {
        return Manifest.permission.INTERNET.equals(permission)
                || Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)
                || Manifest.permission.ACCESS_WIFI_STATE.equals(permission);
    }

    private static boolean virtualPackageDeclares(String permission, Object[] args) {
        if (permission == null) return false;

        // Normal path once the guest ActivityThread is bound.
        String currentPackage = null;
        try {
            currentPackage = ParallaxActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
        }
        if (packageDeclares(currentPackage, permission)) {
            return true;
        }

        // Android 16 can issue an early PermissionManager check before
        // getAppPackageName() is fully available. In that case use only a String
        // argument that resolves to an installed virtual package and declares the
        // exact requested normal permission. No package/permission is fabricated.
        if (args != null) {
            for (Object arg : args) {
                if (!(arg instanceof String)) continue;
                String candidate = (String) arg;
                if (candidate.equals(permission)
                        || candidate.startsWith("android.permission.")) {
                    continue;
                }
                if (packageDeclares(candidate, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean packageDeclares(String packageName, String permission) {
        if (packageName == null || packageName.trim().isEmpty() || permission == null) {
            return false;
        }
        int userId = 0;
        try {
            int currentUserId = ParallaxActivityThread.getUserId();
            if (currentUserId >= 0) {
                userId = currentUserId;
            }
        } catch (Throwable ignored) {
            // Early process bootstrap: virtual user 0 is the established default.
        }
        try {
            PackageInfo info = ParallaxPackageManager.get().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS, userId);
            if (info == null || info.requestedPermissions == null) return false;
            for (String requested : info.requestedPermissions) {
                if (permission.equals(requested)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

}
