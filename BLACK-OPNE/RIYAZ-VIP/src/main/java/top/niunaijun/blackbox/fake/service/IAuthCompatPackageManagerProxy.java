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
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>The existing Facebook web-first behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. For legacy Twitter Kit, the exact
 * {@code com.twitter.android.SingleSignOnActivity} availability probe is first
 * resolved against the real installed Twitter/X package.</p>
 *
 * <p>Recent official X builds can remove the old public SingleSignOnActivity while
 * retaining real exported app entry points. Twitter Kit unfortunately hard-codes
 * the removed component. When that exact legacy component is absent, this layer
 * may remap the SAME Intent object to a real, enabled, exported and permission-
 * accessible Activity in the official {@code com.twitter.android} package.</p>
 *
 * <p>No package/signature identity, OAuth token, token secret, consumer credential
 * or provider result is fabricated. If no real accessible successor exists, the
 * proxy returns an empty result and Twitter Kit keeps its normal OAuth fallback.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

    /**
     * First entry is verified from X 12.22.0 manifest and manually launch-tested:
     * exported=true and handles x.com/twitter.com ACTION_VIEW deep links.
     * Remaining entries cover older/alternate official builds.
     */
    private static final String[] TWITTER_SSO_SUCCESSORS = new String[]{
            "com.x.android.deeplink.XUrlInterpreterActivity",
            "com.twitter.android.AuthorizeAppActivity",
            "com.twitter.app.authorizeapp.AppAuthorizationActivity"
    };

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

            Object result = existingCompat.hook(who, method, args);
            if (!legacyTwitterProbe) {
                return result;
            }

            if (containsUsableTwitterSso(result)) {
                Log.w(TAG, "native SSO discovery: verified real legacy activity available"
                        + processSuffix());
                return result;
            }

            Object successor = tryOfficialTwitterSsoSuccessor(who, method, args, intent);
            if (successor != null) {
                return successor;
            }

            Log.w(TAG,
                    "native SSO discovery: no accessible official successor; using OAuth fallback"
                            + processSuffix());
            return emptyResult(method);
        }
    }

    private static Object tryOfficialTwitterSsoSuccessor(
            Object who, Method queryMethod, Object[] queryArgs, Intent originalIntent) {
        if (originalIntent == null || BlackBoxCore.getContext() == null) {
            return null;
        }

        PackageManager pm = BlackBoxCore.getContext().getPackageManager();
        for (String className : TWITTER_SSO_SUCCESSORS) {
            ComponentName component = new ComponentName(TWITTER_PACKAGE, className);
            ActivityInfo activityInfo;
            try {
                activityInfo = pm.getActivityInfo(component, 0);
            } catch (Throwable ignored) {
                continue;
            }

            if (!isUsableOfficialSuccessor(pm, activityInfo, className)) {
                continue;
            }

            originalIntent.setComponent(component);

            Object systemResult = null;
            try {
                systemResult = queryMethod.invoke(who, queryArgs);
                if (containsUsableActivity(systemResult, className)) {
                    Log.w(TAG, "native SSO discovery: mapped legacy entry to official "
                            + className + processSuffix());
                    return systemResult;
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
            Log.w(TAG, "native SSO discovery: mapped legacy entry to official "
                    + className + " via ActivityInfo" + processSuffix());
            if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }
        return null;
    }

    private static boolean isUsableOfficialSuccessor(
            PackageManager pm, ActivityInfo activityInfo, String expectedClassName) {
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
            return pm.checkPermission(permission, BlackBoxCore.getHostPkg())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsUsableActivity(Object result, String expectedClassName) {
        List<?> list = extractList(result);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof ResolveInfo)) {
                continue;
            }
            ActivityInfo info = ((ResolveInfo) item).activityInfo;
            if (info != null
                    && TWITTER_PACKAGE.equals(info.packageName)
                    && expectedClassName.equals(info.name)
                    && info.enabled
                    && info.exported
                    && (info.applicationInfo == null || info.applicationInfo.enabled)) {
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

    private static boolean isExactTwitterSsoProbe(Intent intent) {
        if (intent == null) {
            return false;
        }
        ComponentName component = intent.getComponent();
        return component != null
                && TWITTER_PACKAGE.equals(component.getPackageName())
                && TWITTER_SSO_ACTIVITY.equals(component.getClassName());
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
