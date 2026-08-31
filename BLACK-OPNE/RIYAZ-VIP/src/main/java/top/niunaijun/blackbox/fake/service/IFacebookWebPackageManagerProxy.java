package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.lang.reflect.Method;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;

/**
 * Facebook-login compatibility layer that keeps the existing package-manager
 * virtualization intact while making Facebook app-switch login unavailable to
 * the guest. Facebook SDKs treat an unresolved native login Activity as "not
 * tried" and continue to their Custom Tab/web handler, which is then routed by
 * AuthTabCompat to Chrome when supported.
 *
 * This intentionally targets Facebook login Activities only. Facebook package
 * metadata/services remain visible, and Twitter/X package-manager behavior is
 * not changed.
 */
@ScanClass({IPackageManagerProxy.class})
public final class IFacebookWebPackageManagerProxy extends IPackageManagerProxy {

    @Override
    public void injectHook() {
        // Let IPackageManagerProxy install every one of its normal hooks first.
        super.injectHook();
        // @ScanClass installs the base resolveIntent hook after our declared
        // classes are scanned, so overwrite only resolveIntent at the very end.
        addMethodHook("resolveIntent", new ResolveIntentFacebookWebFirst());
    }

    @ProxyMethod("resolveIntent")
    public static final class ResolveIntentFacebookWebFirst extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = args != null && args.length > 0 && args[0] instanceof Intent
                    ? (Intent) args[0] : null;

            if (isFacebookNativeLoginIntent(intent)) {
                // Returning null matches PackageManager.resolveActivity() when the
                // Facebook native login Activity is unavailable. The Facebook SDK
                // can then continue naturally to CustomTabLoginMethodHandler.
                return null;
            }

            // Preserve IPackageManagerProxy.ResolveIntent behavior exactly for
            // every non-Facebook-login request.
            String resolvedType = args != null && args.length > 1 && args[1] instanceof String
                    ? (String) args[1] : null;
            int flags = args != null && args.length > 2
                    ? Integer.parseInt(args[2] + "") : 0;
            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveIntent(
                    intent, resolvedType, flags, BActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    private static boolean isFacebookNativeLoginIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        ComponentName component = intent.getComponent();
        String packageName = component != null
                ? component.getPackageName() : intent.getPackage();
        if (!isFacebookLoginPackage(packageName)) {
            return false;
        }

        String className = component == null ? "" : lower(component.getClassName());
        String action = lower(intent.getAction());

        // Current and older Facebook SDK native app-switch login entry points.
        if (className.endsWith(".proxyauth")
                || className.endsWith(".fbloginssoactivity")
                || className.contains("login")
                || className.contains("auth")) {
            return true;
        }

        // Defensive support for protocol-based login Activities used by older
        // Facebook family apps. Non-login share/dialog actions are left alone.
        return "com.facebook.platform.platform_activity".equals(action)
                && (hasLoginExtra(intent, "client_id")
                || hasLoginExtra(intent, "scope")
                || hasLoginExtra(intent, "e2e"));
    }

    private static boolean hasLoginExtra(Intent intent, String key) {
        try {
            return intent.hasExtra(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFacebookLoginPackage(String packageName) {
        String value = lower(packageName);
        return "com.facebook.katana".equals(value)
                || "com.facebook.wakizashi".equals(value)
                || "com.facebook.lite".equals(value);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
