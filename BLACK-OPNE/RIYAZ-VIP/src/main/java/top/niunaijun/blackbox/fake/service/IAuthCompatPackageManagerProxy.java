package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import black.android.content.pm.BRParceledListSlice;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>The existing Facebook web-first behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. On top of that, this proxy repairs a
 * narrow legacy Twitter Kit probe: old Twitter Kit checks whether the exact
 * {@code com.twitter.android.SingleSignOnActivity} component is queryable before
 * it attempts native SSO. On newer Android/package-visibility combinations that
 * query can be empty even though the official Twitter/X package is installed and
 * its signing identity is valid.</p>
 *
 * <p>The fallback below never fabricates package identity. It is enabled only
 * after the real installed {@code com.twitter.android} package is read through
 * PackageManager and at least one signer matches Twitter/X's long-lived signing
 * certificate SHA-256. It only returns one synthetic ResolveInfo for Twitter
 * Kit's availability probe. The subsequent explicit startActivityForResult still
 * goes to Android/the real provider; if that activity is genuinely absent, the
 * platform start fails normally and Twitter Kit falls back to its web OAuth
 * handler.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

    // SHA-256 of the real Twitter/X Android signing certificate (Leland Rechis,
    // Twitter, Inc.). Keep this value as a package-identity guard, not a network
    // trust anchor.
    private static final String TWITTER_SIGNING_SHA256 =
            "0fd9a0cfb07b65950997b4eaebdc53931392391aa406538a3b04073bc2ce2fe9";

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

            // First run the already-shipped Facebook/Twitter compatibility logic.
            // This keeps its normal system-query and real ActivityInfo path as the
            // preferred result and leaves Facebook callback filtering untouched.
            Object result = existingCompat.hook(who, method, args);

            if (!isExactTwitterSsoProbe(intent) || containsResolve(result)) {
                return result;
            }

            PackageInfo verifiedPackage = getVerifiedTwitterPackage();
            if (verifiedPackage == null) {
                Log.w(TAG,
                        "native SSO discovery: verified-package fallback rejected");
                return result;
            }

            ResolveInfo resolveInfo = buildTwitterSsoResolveInfo(verifiedPackage);
            if (resolveInfo == null) {
                return result;
            }

            Log.i(TAG,
                    "native SSO discovery: verified-package fallback matched");
            List<ResolveInfo> resolves = Collections.singletonList(resolveInfo);
            if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
                return ParceledListSliceCompat.create(resolves);
            }
            return resolves;
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

    private static boolean containsResolve(Object result) {
        List<?> list = extractList(result);
        return list != null && !list.isEmpty();
    }

    private static List<?> extractList(Object result) {
        if (result instanceof List) {
            return (List<?>) result;
        }
        if (ParceledListSliceCompat.isParceledListSlice(result)) {
            try {
                return BRParceledListSlice.get(result).getList();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static PackageInfo getVerifiedTwitterPackage() {
        try {
            if (BlackBoxCore.getContext() == null) {
                return null;
            }

            PackageManager pm = BlackBoxCore.getContext().getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo packageInfo = pm.getPackageInfo(TWITTER_PACKAGE, flags);
            if (packageInfo == null
                    || !TWITTER_PACKAGE.equals(packageInfo.packageName)
                    || packageInfo.applicationInfo == null
                    || !packageInfo.applicationInfo.enabled) {
                return null;
            }

            Signature[] signatures = getSignatures(packageInfo);
            if (signatures == null || signatures.length == 0) {
                return null;
            }

            for (Signature signature : signatures) {
                if (signature != null
                        && TWITTER_SIGNING_SHA256.equals(sha256Hex(signature.toByteArray()))) {
                    return packageInfo;
                }
            }
        } catch (Throwable error) {
            Log.w(TAG,
                    "native SSO discovery: package certificate verification failed ("
                            + error.getClass().getSimpleName() + ")");
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] getSignatures(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageInfo.signingInfo != null) {
            if (packageInfo.signingInfo.hasMultipleSigners()) {
                return packageInfo.signingInfo.getApkContentsSigners();
            }
            Signature[] history = packageInfo.signingInfo.getSigningCertificateHistory();
            if (history != null && history.length > 0) {
                return history;
            }
            return packageInfo.signingInfo.getApkContentsSigners();
        }
        return packageInfo.signatures;
    }

    private static String sha256Hex(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return out.toString();
    }

    private static ResolveInfo buildTwitterSsoResolveInfo(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return null;
        }

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = TWITTER_PACKAGE;
        activityInfo.name = TWITTER_SSO_ACTIVITY;
        activityInfo.enabled = true;
        activityInfo.exported = true;
        activityInfo.applicationInfo = packageInfo.applicationInfo;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.resolvePackageName = TWITTER_PACKAGE;
        resolveInfo.isDefault = true;
        return resolveInfo;
    }
}
