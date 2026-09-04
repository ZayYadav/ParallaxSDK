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

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>Facebook compatibility behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. Legacy Twitter Kit probes the exact
 * {@code com.twitter.android.SingleSignOnActivity}. That probe is allowed to see
 * only the real installed provider component when Android reports it as enabled
 * and exported.</p>
 *
 * <p>Current X releases may remove the legacy Twitter Kit activity entirely.
 * Modern URL handlers such as
 * {@code com.x.android.deeplink.XUrlInterpreterActivity} are intentionally not
 * substituted for the URL-less Twitter Kit SSO contract: they do not implement
 * the legacy {@code ck}/{@code cs} input and {@code tk}/{@code ts} result wire
 * format. Returning a fake match would merely push Twitter Kit into a provider
 * activity that cannot satisfy the request.</p>
 *
 * <p>No provider result, account, OAuth token, cookie, consumer credential or
 * signature identity is fabricated.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

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

            // Preserve all already-shipped Facebook/Twitter compatibility behavior.
            Object result = existingCompat.hook(who, method, args);

            if (!legacyTwitterProbe) {
                return result;
            }

            if (containsUsableTwitterSso(result)) {
                Log.i(TAG, "legacy SingleSignOnActivity genuinely available"
                        + processSuffix());
                return result;
            }

            // Do not rewrite a legacy Twitter Kit SSO probe to XUrlInterpreterActivity
            // or another unrelated modern X activity. Those components consume URLs,
            // while this probe is a URL-less ck/cs -> tk/ts Activity result contract.
            Log.w(TAG,
                    "legacy Twitter Kit native SSO unavailable in installed X build; "
                            + "allowing OAuth fallback" + processSuffix());
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

    static boolean isWireCompatibleTwitterSsoClass(String className) {
        return TWITTER_SSO_ACTIVITY.equals(className);
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
}
