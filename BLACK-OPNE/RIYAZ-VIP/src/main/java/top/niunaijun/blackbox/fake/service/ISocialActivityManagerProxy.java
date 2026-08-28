package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.compat.auth.SocialPermissionCompat;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.utils.MethodParameterUtils;

/**
 * ActivityManager-side permission compatibility for SDK self-checks.
 * Only normal network permissions actually declared by the guest APK are granted.
 */
public final class ISocialActivityManagerProxy extends IActivityManagerProxy {

    @Override
    public void injectHook() {
        super.injectHook();

        // Runtime subclass scanning does not include parent declared hooks.
        for (Class<?> declaredClass : IActivityManagerProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }
        for (Class<?> declaredClass : ActivityManagerCommonProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }

        addMethodHook("checkPermission", new CheckPermission());
    }

    private static final class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = findPermission(args);
            if (SocialPermissionCompat.guestDeclaresNormalNetworkPermission(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }

            MethodParameterUtils.replaceLastUid(args);
            if (Manifest.permission.ACCOUNT_MANAGER.equals(permission)
                    || Manifest.permission.SEND_SMS.equals(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    private static String findPermission(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof String
                    && ((String) arg).startsWith("android.permission.")) {
                return (String) arg;
            }
        }
        return null;
    }
}
