package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.content.pm.BRParceledListSlice;

import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>The existing Facebook web-first behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. For legacy Twitter Kit, the exact
 * {@code com.twitter.android.SingleSignOnActivity} availability probe is treated
 * conservatively: native SSO is advertised only when Android actually returns a
 * real, enabled, exported ActivityInfo for that exact component.</p>
 *
 * <p>Modern Twitter/X builds may keep the package installed while removing the
 * old Twitter Kit SingleSignOnActivity. Returning any non-empty query result in
 * that situation is unsafe because Twitter Kit's IntentUtils only checks whether
 * the list is empty. A false-positive result makes Twitter Kit start a missing
 * SSO component and later report a bare {@code Authorize failed.} cancellation.
 * When the real activity is absent this proxy therefore returns a correctly
 * shaped empty result so Twitter Kit naturally continues to its OAuth/WebView
 * handler. No package, activity, signature, token, or provider identity is
 * fabricated.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

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

            // Run the already-shipped Facebook/Twitter compatibility logic first.
            // It performs the real system query and real ActivityInfo fallbacks.
            Object result = existingCompat.hook(who, method, args);

            if (!isExactTwitterSsoProbe(intent)) {
                return result;
            }

            // Twitter Kit's IntentUtils considers ANY non-empty list to mean SSO
            // exists. Filter out stale/disabled/wrong-component results instead of
            // allowing a false positive to force the legacy SSO handler.
            if (containsUsableTwitterSso(result)) {
                Log.i(TAG, "native SSO discovery: verified real activity available");
                return result;
            }

            Log.i(TAG,
                    "native SSO discovery: real activity unavailable; using OAuth fallback");
            return emptyResult(method);
        }
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
}
