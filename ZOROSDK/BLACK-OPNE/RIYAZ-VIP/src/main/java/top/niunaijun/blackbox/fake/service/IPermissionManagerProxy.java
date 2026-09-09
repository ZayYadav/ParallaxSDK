package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.service.base.PkgMethodProxy;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Fixed for Android 10–16.
 * Keeps normal manifest permissions visible to SDKs running in a virtual app.
 */
public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
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
                    BlackBoxCore.mainThread()).getSystemContext();
            PackageManager packageManager = BRContextImpl.get(systemContext).mPackageManager();
            if (packageManager != null) {
                Reflector.on("android.app.ApplicationPackageManager").field("mPermissionManager").set(packageManager, proxyInvocation);
            }
        } catch (Throwable ignored) {
            // OEM frameworks may not expose this cache. The ServiceManager proxy
            // installed above and the package/ActivityManager fallbacks remain active.
        }
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    /**
     * Facebook/Meta and several sign-in SDKs perform an early INTERNET permission
     * self-check. On Android 16 the real PermissionManager sees the Loader UID,
     * while the SDK asks about the virtual package, so the normal permission can
     * incorrectly look denied. Only grant normal network permissions that the
     * virtual APK actually declared; dangerous/runtime permissions are untouched.
     */
    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
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
            currentPackage = BActivityThread.getAppPackageName();
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
            int currentUserId = BActivityThread.getUserId();
            if (currentUserId >= 0) {
                userId = currentUserId;
            }
        } catch (Throwable ignored) {
            // Early process bootstrap: virtual user 0 is the established default.
        }
        try {
            PackageInfo info = BPackageManager.get().getPackageInfo(
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
