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
 * {@link IFacebookWebPackageManagerProxy}. For legacy Twitter Kit, the exact
 * {@code com.twitter.android.SingleSignOnActivity} probe is allowed only when
 * that real legacy component actually exists.</p>
 *
 * <p>Current X builds expose {@code com.x.android.deeplink.XUrlInterpreterActivity}
 * for ACTION_VIEW URL routing, but that component is not a replacement for the
 * old Twitter Kit SSO contract. The legacy SSO probe carries no OAuth URL, so
 * remapping it to any modern X Activity can open an unrelated provider surface.
 * App-to-app handoff therefore happens later, only after the guest has created a
 * genuine Twitter/X authorization URL.</p>
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

            // Advertise native legacy SSO only if that exact provider Activity is
            // genuinely present. Current X builds no longer ship the component.
            if (containsUsableTwitterSso(result)) {
                Log.i(TAG, "legacy SingleSignOnActivity genuinely available"
                        + processSuffix());
                return result;
            }

            // XUrlInterpreterActivity consumes ACTION_VIEW URLs. This probe has no
            // URL, so mapping it would launch the wrong X surface and cannot return
            // a valid Twitter Kit SSO result.
            Log.w(TAG,
                    "legacy SingleSignOnActivity absent; URL-less probe not remapped; "
                            + "waiting for a real OAuth authorization URL"
                            + processSuffix());
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

    private static String processSuffix() {
        try {
            return " [bpid=" + BActivityThread.getAppPid() + "]";
        } catch (Throwable ignored) {
            return "";
        }
    }

}
