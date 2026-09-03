package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
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
 * retaining their real app-authorization entry point. Twitter Kit unfortunately
 * hard-codes the removed component. When that exact legacy component is absent,
 * this compatibility layer may remap the SAME Intent object to a real, enabled,
 * exported and permission-accessible authorization Activity in the same official
 * {@code com.twitter.android} package. Twitter Kit then adds its original SSO
 * extras and starts that real provider Activity normally. The existing OneCore
 * external-auth bridge keeps the real provider UI outside the virtual process and
 * returns Android's real Activity result to the original :pN guest process.</p>
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
    private static final String TWITTER_AUTHENTICATOR_SERVICE =
            "com.twitter.android.platform.TwitterAuthenticationService";

    /**
     * Known real authorization Activities shipped by official Twitter/X builds.
     * They are never assumed usable: Android must report the exact component as
     * installed, enabled, exported and launch-permission-accessible first.
     */
    private static final String[] TWITTER_SSO_SUCCESSORS = new String[]{
            "com.twitter.android.AuthorizeAppActivity",
            "com.twitter.app.authorizeapp.AppAuthorizationActivity"
    };

    @Override
    public void injectHook() {
        // Preserve every normal IPackageManager hook first, then restore the
        // existing Facebook compatibility hooks exactly as before.
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

            // Run the already-shipped Facebook/Twitter compatibility logic first.
            // It performs the real system query and real ActivityInfo fallbacks.
            Object result = existingCompat.hook(who, method, args);

            if (!legacyTwitterProbe) {
                return result;
            }

            // Prefer the exact legacy provider component when it genuinely exists.
            if (containsUsableTwitterSso(result)) {
                Log.w(TAG, "native SSO discovery: verified real legacy activity available"
                        + processSuffix());
                return result;
            }

            // Newer X releases can keep the actual authorization Activity while
            // removing only SingleSignOnActivity. Remap the original Intent object
            // itself so Twitter Kit subsequently starts the real successor after
            // it appends its own ck/cs extras. Nothing sensitive is read or logged.
            Object successor = tryOfficialTwitterSsoSuccessor(who, method, args, intent);
            if (successor != null) {
                return successor;
            }

            PackageManager pm = BlackBoxCore.getContext() == null
                    ? null : BlackBoxCore.getContext().getPackageManager();
            Log.w(TAG,
                    "native SSO discovery: no compatible exported activity; "
                            + "modernAccountAuthenticator=" + hasModernTwitterAuthenticator(pm)
                            + "; using standards-based OAuth fallback"
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
            } catch (Throwable error) {
                Log.w(TAG, "native SSO successor absent: " + simpleName(className)
                        + " (" + rootType(error) + ")" + processSuffix());
                continue;
            }

            if (!isUsableOfficialSuccessor(pm, activityInfo, className)) {
                Log.w(TAG, "native SSO successor unusable: " + simpleName(className)
                        + processSuffix());
                continue;
            }

            // IntentUtils.isActivityAvailable() receives the caller's Intent by
            // reference before the binder call. Mutating that same object is what
            // makes Twitter Kit's following startActivityForResult() target the
            // real replacement component rather than the removed legacy class.
            originalIntent.setComponent(component);

            Object systemResult = null;
            try {
                systemResult = queryMethod.invoke(who, queryArgs);
                if (containsUsableActivity(systemResult, className)) {
                    Log.w(TAG, "native SSO discovery: mapped legacy entry to official "
                            + simpleName(className) + processSuffix());
                    return systemResult;
                }
            } catch (Throwable error) {
                Log.w(TAG, "native SSO successor query failed ("
                        + rootType(error) + ")" + processSuffix());
            }

            // Some Android releases apply visibility filtering to the raw query
            // even though getActivityInfo() above returned the real component.
            // Repackage that real ActivityInfo without changing package/name.
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = activityInfo;
            resolveInfo.resolvePackageName = activityInfo.packageName;
            resolveInfo.isDefault = true;
            List<ResolveInfo> resolves = Collections.singletonList(resolveInfo);
            Log.w(TAG, "native SSO discovery: mapped legacy entry to official "
                    + simpleName(className) + " via ActivityInfo" + processSuffix());
            if (ParceledListSliceCompat.isReturnParceledListSlice(queryMethod)) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
        }
        return null;
    }

    private static boolean hasModernTwitterAuthenticator(PackageManager pm) {
        if (pm == null) {
            return false;
        }
        try {
            ServiceInfo info = pm.getServiceInfo(
                    new ComponentName(TWITTER_PACKAGE, TWITTER_AUTHENTICATOR_SERVICE), 0);
            return info != null
                    && TWITTER_PACKAGE.equals(info.packageName)
                    && TWITTER_AUTHENTICATOR_SERVICE.equals(info.name)
                    && info.enabled
                    && (info.applicationInfo == null || info.applicationInfo.enabled);
        } catch (Throwable ignored) {
            return false;
        }
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
