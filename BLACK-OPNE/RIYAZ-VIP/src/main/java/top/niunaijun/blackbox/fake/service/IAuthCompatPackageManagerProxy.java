package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.content.pm.BRParceledListSlice;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>Facebook compatibility behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. For legacy Twitter Kit, the exact
 * {@code com.twitter.android.SingleSignOnActivity} probe is resolved against
 * the real installed X package.</p>
 *
 * <p>Some current X builds remove only the legacy subclass while retaining its
 * exported parent, {@code com.twitter.android.AuthorizeAppActivity}. That parent
 * implements the same {@code ck}/{@code cs} input and
 * {@code tk}/{@code ts}/{@code screen_name}/{@code user_id} result contract. If
 * the exact legacy component is absent, the original probe Intent is therefore
 * retargeted only to that verified, enabled, exported provider Activity. The X
 * URL interpreter is deliberately not used for this URL-less OAuth1 contract.</p>
 *
 * <p>No provider result, account, OAuth token, cookie, consumer credential or
 * signature identity is fabricated.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String BGMI_PACKAGE = "com.pubg.imobile";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";
    private static final String TWITTER_AUTHORIZE_ACTIVITY =
            "com.twitter.android.AuthorizeAppActivity";

    @Override
    public void injectHook() {
        super.injectHook();
        addMethodHook("resolveIntent",
                new IFacebookWebPackageManagerProxy.ResolveIntentFacebookWebFirst());
        addMethodHook("resolveService",
                new IFacebookWebPackageManagerProxy.ResolveServiceFacebookWebFirst());
        addMethodHook("queryIntentActivities", new QueryExternalAuthActivities());
    }

    @ProxyMethod("queryIntentActivities")
    public static final class QueryExternalAuthActivities extends MethodHook {
        private final IFacebookWebPackageManagerProxy.QueryFacebookCallbackActivities
                existingCompat =
                new IFacebookWebPackageManagerProxy.QueryFacebookCallbackActivities();

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = findIntent(args);
            final boolean legacyTwitterProbe = isExactTwitterSsoProbe(intent);

            // BGMI already contains a bundled Twitter OAuth WebView fallback.
            // Do not expose or synthesize a native X SSO activity for its legacy
            // Twitter Kit probe; an empty result makes Twitter Kit continue through
            // its own in-app OAuth activity without changing any provider result.
            if (legacyTwitterProbe && isBgmiGuest()) {
                Log.i(TAG, "BGMI native Twitter SSO suppressed; using bundled WebView fallback"
                        + processSuffix());
                return emptyResult(method);
            }

            // Preserve all already-shipped Facebook/Twitter compatibility behavior.
            Object result = existingCompat.hook(who, method, args);

            if (!legacyTwitterProbe) {
                return result;
            }

            // Prefer the exact legacy provider component when it still exists.
            if (containsUsableTwitterSso(result)) {
                Log.i(TAG, "legacy SingleSignOnActivity genuinely available"
                        + processSuffix());
                return result;
            }

            Object successor = tryOfficialTwitterAuthorizeActivity(
                    who, method, args, intent);
            if (successor != null) {
                return successor;
            }

            Log.w(TAG,
                    "native SSO unavailable; no wire-compatible X authorization activity"
                            + processSuffix());
            return emptyResult(method);
        }
    }

    private static Object tryOfficialTwitterAuthorizeActivity(
            Object who, Method queryMethod, Object[] queryArgs, Intent originalIntent) {
        if (originalIntent == null) {
            return null;
        }

        ComponentName component = new ComponentName(
                TWITTER_PACKAGE, TWITTER_AUTHORIZE_ACTIVITY);
        ActivityInfo activityInfo = resolveRealActivityInfo(who, component);
        if (!isUsableOfficialActivity(activityInfo, TWITTER_AUTHORIZE_ACTIVITY)) {
            return null;
        }

        // Twitter Kit reuses this same Intent after its availability query and
        // then appends ck/cs before startActivityForResult(). Retargeting this
        // object preserves those inputs and lets the real X Activity return its
        // normal signed-in account result through the existing external bridge.
        originalIntent.setComponent(component);

        try {
            Object queried = queryMethod.invoke(who, queryArgs);
            if (containsUsableActivity(queried, TWITTER_AUTHORIZE_ACTIVITY)) {
                Log.i(TAG, "native SSO mapped to X AuthorizeAppActivity"
                        + processSuffix());
                return queried;
            }
        } catch (Throwable error) {
            Log.w(TAG, "native SSO successor query failed ("
                    + rootType(error) + ")" + processSuffix());
        }

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.resolvePackageName = activityInfo.packageName;
        resolveInfo.isDefault = true;
        List<ResolveInfo> resolves = Collections.singletonList(resolveInfo);
        Log.i(TAG, "native SSO mapped to X AuthorizeAppActivity via ActivityInfo"
                + processSuffix());
        if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
            return ParceledListSliceCompat.create(resolves);
        }
        return resolves;
    }

    private static ActivityInfo resolveRealActivityInfo(
            Object rawPackageManager, ComponentName component) {
        ActivityInfo direct = getActivityInfoFromRawSystemPm(rawPackageManager, component);
        if (direct != null) {
            return direct;
        }

        try {
            if (BlackBoxCore.getContext() != null) {
                return BlackBoxCore.getContext().getPackageManager()
                        .getActivityInfo(component, 0);
            }
        } catch (Throwable error) {
            Log.w(TAG, "X AuthorizeAppActivity lookup failed ("
                    + rootType(error) + ")" + processSuffix());
        }
        return null;
    }

    private static ActivityInfo getActivityInfoFromRawSystemPm(
            Object who, ComponentName component) {
        if (who == null || component == null) {
            return null;
        }
        try {
            for (Method candidate : who.getClass().getMethods()) {
                if (!"getActivityInfo".equals(candidate.getName())) {
                    continue;
                }
                Class<?>[] types = candidate.getParameterTypes();
                if (types.length != 3
                        || !ComponentName.class.isAssignableFrom(types[0])
                        || !isIntOrLong(types[1])
                        || !isInt(types[2])) {
                    continue;
                }

                Object flags = types[1] == long.class || types[1] == Long.class
                        ? Long.valueOf(0L) : Integer.valueOf(0);
                Object result = candidate.invoke(
                        who, component, flags, Integer.valueOf(hostUserId()));
                if (result instanceof ActivityInfo) {
                    return (ActivityInfo) result;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "raw X activity lookup failed ("
                    + rootType(error) + ")" + processSuffix());
        }
        return null;
    }

    private static int hostUserId() {
        try {
            if (BlackBoxCore.getContext() != null
                    && BlackBoxCore.getContext().getApplicationInfo() != null) {
                return BUserHandle.getUserId(
                        BlackBoxCore.getContext().getApplicationInfo().uid);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean isIntOrLong(Class<?> type) {
        return isInt(type) || type == long.class || type == Long.class;
    }

    private static boolean isInt(Class<?> type) {
        return type == int.class || type == Integer.class;
    }

    private static boolean isUsableOfficialActivity(
            ActivityInfo activityInfo, String expectedClassName) {
        if (activityInfo == null
                || !TWITTER_PACKAGE.equals(activityInfo.packageName)
                || !expectedClassName.equals(activityInfo.name)
                || !activityInfo.enabled
                || !activityInfo.exported
                || (activityInfo.applicationInfo != null
                && !activityInfo.applicationInfo.enabled)) {
            return false;
        }

        String permission = activityInfo.permission;
        if (permission == null || permission.trim().isEmpty()) {
            return true;
        }
        try {
            PackageManager pm = BlackBoxCore.getContext().getPackageManager();
            return pm.checkPermission(permission, BlackBoxCore.getHostPkg())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsUsableActivity(
            Object result, String expectedClassName) {
        List<?> list = extractList(result);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof ResolveInfo
                    && isUsableOfficialActivity(
                    ((ResolveInfo) item).activityInfo, expectedClassName)) {
                return true;
            }
        }
        return false;
    }

    private static Intent findIntent(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        return null;
    }

    private static boolean isBgmiGuest() {
        try {
            return BGMI_PACKAGE.equals(BActivityThread.getAppPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isExactTwitterSsoProbe(Intent intent) {
        if (intent == null) {
            return false;
        }
        ComponentName component = intent.getComponent();
        return component != null
                && TWITTER_PACKAGE.equals(component.getPackageName())
                && TWITTER_SSO_ACTIVITY.equals(component.getClassName());
    }

    static boolean isWireCompatibleTwitterSsoClass(String className) {
        return TWITTER_SSO_ACTIVITY.equals(className)
                || TWITTER_AUTHORIZE_ACTIVITY.equals(className);
    }

    private static boolean containsUsableTwitterSso(Object result) {
        List<?> list = extractList(result);
        if (list == null || list.isEmpty()) {
            return false;
        }

        for (Object item : list) {
            if (!(item instanceof ResolveInfo)) {
                continue;
            }
            ActivityInfo activityInfo = ((ResolveInfo) item).activityInfo;
            if (activityInfo != null
                    && TWITTER_PACKAGE.equals(activityInfo.packageName)
                    && TWITTER_SSO_ACTIVITY.equals(activityInfo.name)
                    && activityInfo.enabled
                    && activityInfo.exported
                    && (activityInfo.applicationInfo == null
                    || activityInfo.applicationInfo.enabled)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> extractList(Object result) {
        if (result instanceof List) {
            return (List<?>) result;
        }
        if (ParceledListSliceCompat.isParceledListSlice(result)) {
            try {
                return BRParceledListSlice.get(result).getList();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object emptyResult(Method method) {
        List<ResolveInfo> empty = Collections.emptyList();
        if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
            return ParceledListSliceCompat.create(empty);
        }
        return empty;
    }

    private static String processSuffix() {
        try {
            return " [bpid=" + BActivityThread.getAppPid() + "]";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String rootType(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? "unknown" : current.getClass().getSimpleName();
    }

}
