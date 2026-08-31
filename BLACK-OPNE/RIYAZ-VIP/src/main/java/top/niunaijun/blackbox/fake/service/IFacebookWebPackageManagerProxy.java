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
 * virtualization intact while making Facebook native login shortcuts unavailable
 * to the guest. Facebook SDKs treat these unavailable native handlers as "not
 * tried" and continue to their Custom Tab/web handler, which AuthTabCompat then
 * routes to stable Chrome when supported.
 *
 * Scope is intentionally narrow: only Facebook's platform token service and
 * Facebook login Activities are hidden. Other Facebook features/package metadata
 * stay available, and Twitter/X package-manager behavior is untouched.
 */
@ScanClass({IPackageManagerProxy.class})
public final class IFacebookWebPackageManagerProxy extends IPackageManagerProxy {

    private static final String FACEBOOK_PLATFORM_SERVICE_ACTION =
            "com.facebook.platform.PLATFORM_SERVICE";

    @Override
    public void injectHook() {
        // Install all original package-manager hooks first. @ScanClass also scans
        // the base class, so overwrite only the two Facebook-login discovery hooks
        // after the base registrations are complete.
        super.injectHook();
        addMethodHook("resolveIntent", new ResolveIntentFacebookWebFirst());
        addMethodHook("resolveService", new ResolveServiceFacebookWebFirst());
    }

    @ProxyMethod("resolveIntent")
    public static final class ResolveIntentFacebookWebFirst extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = args != null && args.length > 0 && args[0] instanceof Intent
                    ? (Intent) args[0] : null;

            if (isFacebookNativeLoginIntent(intent)) {
                // PackageManager.resolveActivity() returning null makes Facebook's
                // Katana/SSO handler report "not tried", allowing Custom Tab next.
                return null;
            }

            return resolveIntentLikeBase(who, method, args, intent);
        }
    }

    @ProxyMethod("resolveService")
    public static final class ResolveServiceFacebookWebFirst extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = args != null && args.length > 0 && args[0] instanceof Intent
                    ? (Intent) args[0] : null;

            if (isFacebookPlatformTokenService(intent)) {
                // Meta's GetTokenLoginMethodHandler calls PlatformServiceClient.start().
                // If this PLATFORM_SERVICE cannot resolve, start() returns false and
                // LoginClient advances to its next handler instead of consuming a
                // Facebook-app token behind the web flow.
                return null;
            }

            return resolveServiceLikeBase(who, method, args, intent);
        }
    }

    private static Object resolveIntentLikeBase(
            Object who, Method method, Object[] args, Intent intent) throws Throwable {
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

    private static Object resolveServiceLikeBase(
            Object who, Method method, Object[] args, Intent intent) throws Throwable {
        String resolvedType = args != null && args.length > 1 && args[1] instanceof String
                ? (String) args[1] : null;
        int flags = args != null && args.length > 2
                ? Integer.parseInt(args[2] + "") : 0;
        ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveService(
                intent, flags, resolvedType, BActivityThread.getUserId());
        if (resolveInfo != null) {
            return resolveInfo;
        }
        return method.invoke(who, args);
    }

    private static boolean isFacebookPlatformTokenService(Intent intent) {
        if (intent == null
                || !FACEBOOK_PLATFORM_SERVICE_ACTION.equals(intent.getAction())) {
            return false;
        }

        ComponentName component = intent.getComponent();
        String packageName = component != null
                ? component.getPackageName() : intent.getPackage();
        String value = lower(packageName);

        // NativeProtocol.createPlatformServiceIntent() currently probes Katana
        // and Wakizashi. Restrict the block to those official login providers.
        return "com.facebook.katana".equals(value)
                || "com.facebook.wakizashi".equals(value);
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
