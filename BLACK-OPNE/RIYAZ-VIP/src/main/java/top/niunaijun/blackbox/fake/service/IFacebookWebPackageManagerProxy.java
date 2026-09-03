package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import black.android.content.pm.BRParceledListSlice;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Facebook-login compatibility layer that keeps the existing package-manager
 * virtualization intact while making Facebook native login shortcuts unavailable
 * to the guest. Facebook SDKs treat these unavailable native handlers as "not
 * tried" and continue to their Custom Tab/web handler, which AuthTabCompat then
 * routes to stable Chrome when supported.
 *
 * Scope is intentionally narrow: only Facebook's platform token service and
 * Facebook login Activities are hidden. Other Facebook features/package metadata
 * stay available. The one Twitter/X exception below restores discovery of the
 * official exported SingleSignOnActivity that legacy Twitter Kit probes before
 * deciding whether to fall back to its obsolete web OAuth path.
 */
@ScanClass({IPackageManagerProxy.class})
public final class IFacebookWebPackageManagerProxy extends IPackageManagerProxy {

    private static final String FACEBOOK_PLATFORM_SERVICE_ACTION =
            "com.facebook.platform.PLATFORM_SERVICE";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";
    private static final String TWITTER_SSO_TAG = "TwitterSSOCompat";

    @Override
    public void injectHook() {
        // Install all original package-manager hooks first. @ScanClass also scans
        // the base class, so overwrite only the narrow compatibility hooks after
        // the base registrations are complete.
        super.injectHook();
        addMethodHook("resolveIntent", new ResolveIntentFacebookWebFirst());
        addMethodHook("resolveService", new ResolveServiceFacebookWebFirst());
        addMethodHook("queryIntentActivities", new QueryFacebookCallbackActivities());
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

    /**
     * Meta refuses Custom Tab login if Android reports any callback handler other
     * than the app's own CustomTabActivity. The real host-side fbconnect fallback
     * must therefore stay invisible to the guest query while the virtual
     * CustomTabActivity remains visible.
     *
     * Legacy Twitter Kit uses this same PackageManager method to probe the exact
     * official SingleSignOnActivity. For that one explicit provider-owned probe,
     * always ask Android's original system PackageManager first. If Android's
     * query result gets filtered on an OS/version-specific path, fall back to the
     * original raw IPackageManager getActivityInfo() and finally the host PM.
     *
     * No package/signature identity is fabricated. Only a real, enabled, exported
     * ActivityInfo for com.twitter.android.SingleSignOnActivity is accepted, and
     * Twitter Kit's own certificate check still runs before this query.
     */
    @ProxyMethod("queryIntentActivities")
    public static final class QueryFacebookCallbackActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = args != null && args.length > 0 && args[0] instanceof Intent
                    ? (Intent) args[0] : null;

            if (isTwitterSsoDiscoveryIntent(intent)) {
                return queryOfficialTwitterSsoActivity(who, method, args, intent);
            }

            if (!isFacebookCustomTabCallbackIntent(intent)) {
                return method.invoke(who, args);
            }

            String resolvedType = findFirstStringAfterIntent(args);
            int flags = findQueryFlags(args);
            List<ResolveInfo> resolves = BlackBoxCore.getBPackageManager()
                    .queryIntentActivities(
                            intent, flags, resolvedType, BActivityThread.getUserId());
            if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }
    }

    private static Object queryOfficialTwitterSsoActivity(
            Object who, Method queryMethod, Object[] queryArgs, Intent intent) {
        Object systemResult = null;
        try {
            // This is the original, unwrapped IPackageManager method represented by
            // `who`, not BlackBox's virtual package manager.
            systemResult = queryMethod.invoke(who, queryArgs);
            if (containsUsableTwitterSso(systemResult)) {
                Log.i(TWITTER_SSO_TAG, "native SSO discovery: system query matched");
                return systemResult;
            }
            Log.w(TWITTER_SSO_TAG,
                    "native SSO discovery: system query returned no usable activity");
        } catch (Throwable error) {
            logSsoLookupFailure("system query", error);
        }

        ActivityInfo activityInfo = resolveOfficialTwitterSsoActivity(who, intent);
        if (isUsableTwitterSsoActivity(activityInfo)) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = activityInfo;
            resolveInfo.resolvePackageName = activityInfo.packageName;
            resolveInfo.isDefault = true;
            List<ResolveInfo> resolves = Collections.singletonList(resolveInfo);
            Log.i(TWITTER_SSO_TAG, "native SSO discovery: ActivityInfo fallback matched");
            if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }

        Log.e(TWITTER_SSO_TAG,
                "native SSO discovery: official SingleSignOnActivity unavailable");

        // Preserve the platform's original empty result shape when possible.
        if (systemResult != null) {
            return systemResult;
        }
        List<ResolveInfo> empty = Collections.emptyList();
        if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
            return ParceledListSliceCompat.create(empty);
        }
        return empty;
    }

    private static boolean containsUsableTwitterSso(Object result) {
        List<?> list = extractResolveList(result);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof ResolveInfo)) {
                continue;
            }
            ResolveInfo resolveInfo = (ResolveInfo) item;
            if (isUsableTwitterSsoActivity(resolveInfo.activityInfo)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> extractResolveList(Object result) {
        if (result instanceof List) {
            return (List<?>) result;
        }
        if (ParceledListSliceCompat.isParceledListSlice(result)) {
            try {
                return BRParceledListSlice.get(result).getList();
            } catch (Throwable error) {
                logSsoLookupFailure("ParceledListSlice decode", error);
            }
        }
        return null;
    }

    private static ActivityInfo resolveOfficialTwitterSsoActivity(Object who, Intent intent) {
        if (intent == null || intent.getComponent() == null) {
            return null;
        }
        ComponentName component = intent.getComponent();

        // Prefer the original system IPackageManager object. This bypasses both
        // virtual package state and ApplicationPackageManager's hooked mPM field.
        ActivityInfo direct = getActivityInfoFromRawSystemPm(who, component);
        if (isUsableTwitterSsoActivity(direct)) {
            return direct;
        }

        // Compatibility fallback for platform variants where the hidden binder
        // signature cannot be reflected safely. AppSystemEnv keeps Twitter open,
        // so the normal host PM still resolves to the real installed package.
        try {
            if (BlackBoxCore.getContext() == null) {
                return null;
            }
            ActivityInfo activityInfo = BlackBoxCore.getContext()
                    .getPackageManager()
                    .getActivityInfo(component, 0);
            if (isUsableTwitterSsoActivity(activityInfo)) {
                return activityInfo;
            }
        } catch (Throwable error) {
            logSsoLookupFailure("host PackageManager getActivityInfo", error);
        }
        return null;
    }

    private static ActivityInfo getActivityInfoFromRawSystemPm(
            Object who, ComponentName component) {
        if (who == null || component == null) {
            return null;
        }
        try {
            Method[] methods = who.getClass().getMethods();
            for (Method candidate : methods) {
                if (!"getActivityInfo".equals(candidate.getName())) {
                    continue;
                }
                Class<?>[] types = candidate.getParameterTypes();
                if (types.length != 3
                        || !ComponentName.class.isAssignableFrom(types[0])
                        || !(types[1] == int.class || types[1] == Integer.class
                        || types[1] == long.class || types[1] == Long.class)
                        || !(types[2] == int.class || types[2] == Integer.class)) {
                    continue;
                }

                Object flags = (types[1] == long.class || types[1] == Long.class)
                        ? 0L : 0;
                Object result = candidate.invoke(
                        who, component, flags, UserHandle.myUserId());
                if (result instanceof ActivityInfo) {
                    return (ActivityInfo) result;
                }
            }
        } catch (Throwable error) {
            logSsoLookupFailure("raw IPackageManager getActivityInfo", error);
        }
        return null;
    }

    private static boolean isUsableTwitterSsoActivity(ActivityInfo activityInfo) {
        return activityInfo != null
                && TWITTER_PACKAGE.equals(activityInfo.packageName)
                && TWITTER_SSO_ACTIVITY.equals(activityInfo.name)
                && activityInfo.enabled
                && activityInfo.exported
                && (activityInfo.applicationInfo == null
                || activityInfo.applicationInfo.enabled);
    }

    private static void logSsoLookupFailure(String stage, Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String type = cause == null ? "unknown" : cause.getClass().getSimpleName();
        Log.w(TWITTER_SSO_TAG, "native SSO discovery: " + stage + " failed (" + type + ")");
    }

    private static boolean isTwitterSsoDiscoveryIntent(Intent intent) {
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        return component != null
                && TWITTER_PACKAGE.equals(component.getPackageName())
                && TWITTER_SSO_ACTIVITY.equals(component.getClassName());
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

    private static boolean isFacebookCustomTabCallbackIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri uri = intent.getData();
        String virtualPackage = BActivityThread.getAppPackageName();
        if (uri == null || virtualPackage == null || virtualPackage.trim().isEmpty()) {
            return false;
        }
        return "fbconnect".equals(lower(uri.getScheme()))
                && ("cct." + lower(virtualPackage)).equals(lower(uri.getHost()));
    }

    private static String findFirstStringAfterIntent(Object[] args) {
        if (args == null) return null;
        boolean sawIntent = false;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                sawIntent = true;
            } else if (sawIntent && arg instanceof String) {
                return (String) arg;
            }
        }
        return null;
    }

    private static int findQueryFlags(Object[] args) {
        if (args == null) return 0;
        for (Object arg : args) {
            if (arg instanceof Long) {
                return ((Long) arg).intValue();
            }
            if (arg instanceof Integer) {
                return (Integer) arg;
            }
        }
        return 0;
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
