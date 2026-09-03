package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
 * External-auth PackageManager compatibility layer.
 *
 * <p>BGMI's legacy Twitter Kit hard-codes
 * {@code com.twitter.android.SingleSignOnActivity}. Modern official X builds may
 * remove that component while still exposing a real exported authorization UI.
 * For the exact legacy probe only, this class rewrites the same Intent object to
 * a real, enabled, exported, permission-accessible Activity from the installed
 * official X/Twitter package.</p>
 *
 * <p>The resolver first tries known historical successor names, then scans the
 * installed official package for exported auth/authorize/oauth/sso/login
 * Activities and selects the strongest candidate. No account, token, cookie,
 * consumer secret, signature, or provider result is fabricated or extracted.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

    private static final String[] TWITTER_SSO_SUCCESSORS = new String[]{
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
                Log.w(TAG, "legacy SingleSignOnActivity is genuinely available"
                        + processSuffix());
                return result;
            }

            Object forced = forceRealTwitterAuthorizationActivity(
                    who, method, args, intent);
            if (forced != null) {
                return forced;
            }

            Log.w(TAG, "no real exported X/Twitter auth Activity found; OAuth fallback"
                    + processSuffix());
            return emptyResult(method);
        }
    }

    private static Object forceRealTwitterAuthorizationActivity(
            Object who, Method queryMethod, Object[] queryArgs, Intent originalIntent) {
        if (originalIntent == null || BlackBoxCore.getContext() == null) {
            return null;
        }

        PackageManager pm = BlackBoxCore.getContext().getPackageManager();
        ActivityInfo candidate = findBestRealTwitterAuthActivity(pm);
        if (candidate == null) {
            return null;
        }

        ComponentName component = new ComponentName(
                candidate.packageName, candidate.name);

        // Twitter Kit passes the same Intent instance into the availability check
        // and later startActivityForResult(). Rewriting this exact object is what
        // redirects the old hard-coded SingleSignOnActivity launch to the real X
        // authorization Activity without inventing a provider result.
        originalIntent.setComponent(component);

        try {
            Object systemResult = queryMethod.invoke(who, queryArgs);
            if (containsUsableActivity(systemResult, candidate.name)) {
                Log.w(TAG, "forced legacy SSO probe -> real official X Activity: "
                        + simpleName(candidate.name) + processSuffix());
                return systemResult;
            }
        } catch (Throwable error) {
            Log.w(TAG, "forced X Activity system query failed ("
                    + rootType(error) + ")" + processSuffix());
        }

        // Package visibility can filter the binder query even after host PM has
        // verified the real component. Return the real ActivityInfo unchanged.
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = candidate;
        resolveInfo.resolvePackageName = candidate.packageName;
        resolveInfo.isDefault = true;

        List<ResolveInfo> resolves = Collections.singletonList(resolveInfo);
        Log.w(TAG, "forced legacy SSO probe -> real official X Activity via ActivityInfo: "
                + simpleName(candidate.name) + processSuffix());
        if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
            return ParceledListSliceCompat.create(resolves);
        }
        return resolves;
    }

    private static ActivityInfo findBestRealTwitterAuthActivity(PackageManager pm) {
        if (pm == null) {
            return null;
        }

        // First try known real successor names.
        for (String className : TWITTER_SSO_SUCCESSORS) {
            try {
                ActivityInfo info = pm.getActivityInfo(
                        new ComponentName(TWITTER_PACKAGE, className), 0);
                if (isUsableOfficialActivity(pm, info)) {
                    Log.w(TAG, "hard-coded real X auth candidate available: "
                            + simpleName(className) + processSuffix());
                    return info;
                }
            } catch (Throwable ignored) {
                // Continue into package scan.
            }
        }

        // Modern builds change internal class names. Scan only the installed
        // official package and only consider real exported Activities.
        try {
            PackageInfo packageInfo = pm.getPackageInfo(
                    TWITTER_PACKAGE, PackageManager.GET_ACTIVITIES);
            ActivityInfo[] activities = packageInfo == null ? null : packageInfo.activities;
            if (activities == null || activities.length == 0) {
                return null;
            }

            ActivityInfo best = null;
            int bestScore = 0;
            for (ActivityInfo info : activities) {
                if (!isUsableOfficialActivity(pm, info)) {
                    continue;
                }
                int score = authActivityScore(info.name);
                if (score > bestScore) {
                    best = info;
                    bestScore = score;
                }
            }

            if (best != null && bestScore >= 60) {
                Log.w(TAG, "dynamic real X auth candidate selected: "
                        + simpleName(best.name) + " score=" + bestScore
                        + processSuffix());
                return best;
            }
        } catch (Throwable error) {
            Log.w(TAG, "official X Activity scan failed ("
                    + rootType(error) + ")" + processSuffix());
        }

        return null;
    }

    private static int authActivityScore(String className) {
        if (className == null) {
            return 0;
        }
        String n = className.toLowerCase(Locale.US);

        // Avoid obviously unrelated exported surfaces.
        if (n.contains("settings")
                || n.contains("compose")
                || n.contains("tweet")
                || n.contains("profile")
                || n.contains("camera")
                || n.contains("share")
                || n.contains("notification")) {
            return 0;
        }

        if (n.contains("authorizeapp")) return 100;
        if (n.contains("appauthorization")) return 98;
        if (n.contains("authorization")) return 95;
        if (n.contains("authorize")) return 90;
        if (n.contains("oauth")) return 85;
        if (n.contains("singlesignon")) return 82;
        if (n.contains("sso")) return 80;
        if (n.contains("signin")) return 72;
        if (n.contains("login")) return 68;
        if (n.contains("auth")) return 60;
        return 0;
    }

    private static boolean isUsableOfficialActivity(
            PackageManager pm, ActivityInfo activityInfo) {
        if (activityInfo == null
                || !TWITTER_PACKAGE.equals(activityInfo.packageName)
                || activityInfo.name == null
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

    private static boolean containsUsableActivity(
            Object result, String expectedClassName) {
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
                    && (info.applicationInfo == null
                    || info.applicationInfo.enabled)) {
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
            ActivityInfo info = ((ResolveInfo) item).activityInfo;
            if (info != null
                    && TWITTER_PACKAGE.equals(info.packageName)
                    && TWITTER_SSO_ACTIVITY.equals(info.name)
                    && info.enabled
                    && info.exported
                    && (info.applicationInfo == null
                    || info.applicationInfo.enabled)) {
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

    private static String simpleName(String className) {
        if (className == null) return "unknown";
        int dot = className.lastIndexOf('.');
        return dot >= 0 && dot + 1 < className.length()
                ? className.substring(dot + 1) : className;
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
