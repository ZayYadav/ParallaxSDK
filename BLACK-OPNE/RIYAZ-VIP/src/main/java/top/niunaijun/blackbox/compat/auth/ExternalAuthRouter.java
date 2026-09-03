package top.niunaijun.blackbox.compat.auth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.oauth.TwitterNativeAuthBridgeActivity;
import top.niunaijun.blackbox.compat.oauth.TwitterOAuthSessionStore;
import top.niunaijun.blackbox.compat.oauth.VirtualOAuthBridgeActivity;
import top.niunaijun.blackbox.compat.oauth.VirtualOAuthRouter;
import top.niunaijun.blackbox.utils.compat.IntentRedirectCompat;

/**
 * Routes native sign-in activities and provider-owned IntentSenders to the real
 * provider app installed on the phone while keeping the activity-result target
 * inside the virtual process.
 *
 * Twitter/X OAuth is real-app-first. For the official current X package we prefer
 * the manifest-verified exported deep-link entry point
 * com.x.android.deeplink.XUrlInterpreterActivity for trusted twitter.com/x.com
 * authorization URLs. No provider identity, tokens, cookies, passwords, consumer
 * secrets or provider results are fabricated.
 */
@Obfuscate
public final class ExternalAuthRouter {
    public static final String EXTRA_EXTERNAL_AUTH =
            "top.niunaijun.blackbox.auth.EXTERNAL_AUTH";
    public static final String EXTRA_BROWSER_AUTH =
            "top.niunaijun.blackbox.auth.BROWSER_AUTH";
    public static final String EXTRA_PROVIDER_INTENT =
            "top.niunaijun.blackbox.auth.PROVIDER_INTENT";
    public static final String EXTRA_PROVIDER_INTENT_SENDER =
            "top.niunaijun.blackbox.auth.PROVIDER_INTENT_SENDER";
    public static final String EXTRA_PROVIDER_FILL_IN_INTENT =
            "top.niunaijun.blackbox.auth.PROVIDER_FILL_IN_INTENT";
    public static final String EXTRA_PROVIDER_FLAGS_MASK =
            "top.niunaijun.blackbox.auth.PROVIDER_FLAGS_MASK";
    public static final String EXTRA_PROVIDER_FLAGS_VALUES =
            "top.niunaijun.blackbox.auth.PROVIDER_FLAGS_VALUES";
    public static final String EXTRA_PROVIDER_OPTIONS =
            "top.niunaijun.blackbox.auth.PROVIDER_OPTIONS";
    public static final String EXTRA_RESULT_BINDER =
            "top.niunaijun.blackbox.auth.RESULT_BINDER";
    public static final String EXTRA_RESULT_WHO =
            "top.niunaijun.blackbox.auth.RESULT_WHO";
    public static final String EXTRA_REQUEST_CODE =
            "top.niunaijun.blackbox.auth.REQUEST_CODE";
    public static final String EXTRA_VIRTUAL_PACKAGE =
            "top.niunaijun.blackbox.auth.VIRTUAL_PACKAGE";
    public static final String EXTRA_DIRECT_PROVIDER_DISPATCH =
            "top.niunaijun.blackbox.auth.DIRECT_PROVIDER_DISPATCH";
    public static final String EXTRA_BPID =
            "top.niunaijun.blackbox.auth.BPID";
    public static final String EXTRA_USER_ID =
            "top.niunaijun.blackbox.auth.USER_ID";
    public static final String EXTRA_RESULT_CODE =
            "top.niunaijun.blackbox.auth.RESULT_CODE";
    public static final String EXTRA_RESULT_DATA =
            "top.niunaijun.blackbox.auth.RESULT_DATA";
    public static final String EXTRA_RESULT_DELIVERED =
            "top.niunaijun.blackbox.auth.RESULT_DELIVERED";
    public static final String EXTRA_MANUAL_RESULT_RELAY =
            "top.niunaijun.blackbox.auth.MANUAL_RESULT_RELAY";

    public static final String METHOD_DELIVER_ACTIVITY_RESULT =
            "_Black_|_auth_activity_result_";

    private static final String GCLOUD_TWITTER_WEB_ACTIVITY =
            "com.itop.twitterwrapper.TwitterWebActivity";
    private static final String OFFICIAL_TWITTER_PACKAGE = "com.twitter.android";
    private static final String X_URL_INTERPRETER_ACTIVITY =
            "com.x.android.deeplink.XUrlInterpreterActivity";

    private static final Set<String> TRUSTED_PROVIDER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.gms",
            "com.google.android.play.games",
            "com.facebook.katana",
            "com.facebook.wakizashi",
            "com.facebook.lite",
            "com.twitter.android",
            "com.twitter.android.lite",
            "com.x.android"
    ));

    private static final Set<String> TWITTER_PROVIDER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.twitter.android",
            "com.twitter.android.lite",
            "com.x.android"
    ));

    private static final String[] TWITTER_NATIVE_PROVIDER_PACKAGES = new String[]{
            "com.twitter.android",
            "com.x.android",
            "com.twitter.android.lite"
    };

    private ExternalAuthRouter() {
    }

    public static boolean isDirectProviderDispatch(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, false);
    }

    public static void clearDirectProviderDispatch(Intent intent) {
        if (intent != null) intent.removeExtra(EXTRA_DIRECT_PROVIDER_DISPATCH);
    }

    public static boolean isTrustedProviderPackage(String packageName) {
        return packageName != null && TRUSTED_PROVIDER_PACKAGES.contains(packageName);
    }

    public static boolean isTwitterProviderPackage(String packageName) {
        return packageName != null && TWITTER_PROVIDER_PACKAGES.contains(packageName);
    }

    public static boolean isTrustedProviderIntent(Intent intent) {
        return trustedProviderPackage(intent) != null;
    }

    public static String getTrustedProviderPackage(Intent intent) {
        return trustedProviderPackage(intent);
    }

    public static boolean isTrustedProviderIntentSender(IntentSender sender) {
        if (sender == null) return false;
        try {
            return isTrustedProviderPackage(sender.getCreatorPackage());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static IntentSender wrapIntentSender(Object target) {
        if (target == null) return null;
        if (target instanceof IntentSender) return (IntentSender) target;
        try {
            for (Constructor<?> constructor : IntentSender.class.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1
                        && "android.content.IIntentSender".equals(types[0].getName())
                        && types[0].isInstance(target)) {
                    constructor.setAccessible(true);
                    Object wrapped = constructor.newInstance(target);
                    return wrapped instanceof IntentSender ? (IntentSender) wrapped : null;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Intent createResultBridgeIntent(
            Intent source,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        if (source == null || resultTo == null || requestCode < 0
                || virtualPackage == null || virtualPackage.trim().isEmpty()) return null;

        Intent embeddedTwitterAuth = extractGCloudTwitterAuthIntent(source);
        if (embeddedTwitterAuth != null) {
            Intent nativeBridge = createTwitterNativeResultBridgeIntent(
                    embeddedTwitterAuth, resultTo, resultWho, requestCode, virtualPackage);
            if (nativeBridge != null) return nativeBridge;
        }

        if (isTwitterWebAuthIntent(source)) {
            Intent nativeBridge = createTwitterNativeResultBridgeIntent(
                    source, resultTo, resultWho, requestCode, virtualPackage);
            if (nativeBridge != null) return nativeBridge;
            return createTwitterWebResultBridgeIntent(
                    source, resultTo, resultWho, requestCode, virtualPackage);
        }

        String providerPackage = trustedProviderPackage(source);
        if (providerPackage == null) return null;

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) return null;

        Intent providerIntent = new Intent(source);
        if (providerIntent.getComponent() == null && providerIntent.getPackage() == null) {
            providerIntent.setPackage(providerPackage);
        }
        providerIntent.putExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        Intent bridge = createBaseBridge(resultTo, resultWho, requestCode, virtualPackage, bpid);
        bridge.putExtra(EXTRA_PROVIDER_INTENT, providerIntent);
        bridge.addFlags(source.getFlags() & (Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return prepareBridgeForLaunch(bridge);
    }

    public static Intent createIntentSenderBridgeIntent(
            IntentSender sender,
            Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            Bundle options,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        if (!isTrustedProviderIntentSender(sender) || resultTo == null || requestCode < 0
                || virtualPackage == null || virtualPackage.trim().isEmpty()) return null;

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) return null;

        Intent bridge = createBaseBridge(resultTo, resultWho, requestCode, virtualPackage, bpid);
        bridge.putExtra(EXTRA_MANUAL_RESULT_RELAY, true);
        bridge.putExtra(EXTRA_PROVIDER_INTENT_SENDER, sender);
        if (fillInIntent != null) {
            bridge.putExtra(EXTRA_PROVIDER_FILL_IN_INTENT, new Intent(fillInIntent));
        }
        bridge.putExtra(EXTRA_PROVIDER_FLAGS_MASK, flagsMask);
        bridge.putExtra(EXTRA_PROVIDER_FLAGS_VALUES, flagsValues);
        if (options != null) bridge.putExtra(EXTRA_PROVIDER_OPTIONS, new Bundle(options));
        bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return prepareBridgeForLaunch(bridge);
    }

    private static Intent createBaseBridge(
            IBinder resultTo, String resultWho, int requestCode,
            String virtualPackage, int bpid) {
        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(), VirtualOAuthBridgeActivity.class.getName()));
        bridge.putExtra(EXTRA_EXTERNAL_AUTH, true);
        bridge.putExtra(EXTRA_BPID, bpid);
        bridge.putExtra(EXTRA_USER_ID, BActivityThread.getUserId());
        putResultTarget(bridge, resultTo, resultWho, requestCode, virtualPackage);
        return bridge;
    }

    private static Intent createTwitterNativeResultBridgeIntent(
            Intent source,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        Uri authUri = source == null ? null : source.getData();
        if (!isTrustedTwitterOAuthUri(authUri)) return null;

        int userId = BActivityThread.getUserId();
        Uri redirectUri = VirtualOAuthRouter.resolveTwitterRedirectUri(
                authUri, userId, virtualPackage);
        if (redirectUri == null || !TwitterOAuthSessionStore.isHostCaptureSupported(redirectUri)) {
            return null;
        }

        ComponentName providerComponent = resolveNativeTwitterProviderComponent(source);
        if (providerComponent == null) return null;

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) return null;

        Intent providerIntent = new Intent(Intent.ACTION_VIEW, authUri);
        providerIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        providerIntent.addCategory(Intent.CATEGORY_DEFAULT);
        providerIntent.setComponent(providerComponent);
        providerIntent.setFlags(source.getFlags());
        providerIntent.putExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(), TwitterNativeAuthBridgeActivity.class.getName()));
        bridge.putExtra(EXTRA_BPID, bpid);
        bridge.putExtra(EXTRA_USER_ID, userId);
        bridge.putExtra(EXTRA_PROVIDER_INTENT, providerIntent);
        bridge.putExtra(VirtualOAuthRouter.EXTRA_AUTH_URL, authUri.toString());
        bridge.putExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI, redirectUri.toString());
        putResultTarget(bridge, resultTo, resultWho, requestCode, virtualPackage);
        bridge.addFlags(source.getFlags() & (Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return prepareBridgeForLaunch(bridge);
    }

    private static ComponentName resolveNativeTwitterProviderComponent(Intent source) {
        if (source == null || BlackBoxCore.getContext() == null) return null;
        PackageManager pm = BlackBoxCore.getContext().getPackageManager();

        // Prefer the exact current X component verified from the installed manifest
        // and manually launch-tested on the device.
        try {
            ComponentName exact = new ComponentName(
                    OFFICIAL_TWITTER_PACKAGE, X_URL_INTERPRETER_ACTIVITY);
            ActivityInfo info = pm.getActivityInfo(exact, 0);
            if (info != null
                    && OFFICIAL_TWITTER_PACKAGE.equals(info.packageName)
                    && X_URL_INTERPRETER_ACTIVITY.equals(info.name)
                    && info.enabled
                    && info.exported
                    && (info.applicationInfo == null || info.applicationInfo.enabled)) {
                Intent probe = new Intent(Intent.ACTION_VIEW, source.getData());
                probe.addCategory(Intent.CATEGORY_BROWSABLE);
                probe.addCategory(Intent.CATEGORY_DEFAULT);
                probe.setComponent(exact);
                ResolveInfo resolved = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
                if (resolved != null && resolved.activityInfo != null) return exact;
            }
        } catch (Throwable ignored) {
        }

        // Fallback for alternate official package variants/builds.
        try {
            for (String packageName : TWITTER_NATIVE_PROVIDER_PACKAGES) {
                Intent candidate = new Intent(source);
                candidate.setComponent(null);
                candidate.setPackage(packageName);
                ResolveInfo resolved = pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY);
                if (resolved == null || resolved.activityInfo == null) continue;
                ActivityInfo info = resolved.activityInfo;
                if (packageName.equals(info.packageName)
                        && isTwitterProviderPackage(packageName)
                        && info.enabled && info.exported) {
                    return new ComponentName(info.packageName, info.name);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Intent extractGCloudTwitterAuthIntent(Intent source) {
        if (!isGCloudTwitterWebActivity(source)) return null;
        Uri uri = findTwitterOAuthUri(source.getData(), source.getExtras(), 0);
        if (uri == null) return null;
        Intent auth = new Intent(Intent.ACTION_VIEW, uri);
        auth.addCategory(Intent.CATEGORY_BROWSABLE);
        auth.addCategory(Intent.CATEGORY_DEFAULT);
        auth.setFlags(source.getFlags());
        return auth;
    }

    private static boolean isGCloudTwitterWebActivity(Intent source) {
        if (source == null) return false;
        ComponentName component = source.getComponent();
        return component != null && GCLOUD_TWITTER_WEB_ACTIVITY.equals(component.getClassName());
    }

    private static Uri findTwitterOAuthUri(Uri direct, Bundle extras, int depth) {
        if (isTrustedTwitterOAuthUri(direct)) return direct;
        if (extras == null || depth > 3) return null;
        try {
            for (String key : extras.keySet()) {
                Object value;
                try { value = extras.get(key); } catch (Throwable ignored) { continue; }
                Uri found = twitterOAuthUriFromValue(value, depth + 1);
                if (found != null) return found;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Uri twitterOAuthUriFromValue(Object value, int depth) {
        if (value == null || depth > 3) return null;
        if (value instanceof Uri) {
            Uri uri = (Uri) value;
            return isTrustedTwitterOAuthUri(uri) ? uri : null;
        }
        if (value instanceof CharSequence) {
            try {
                Uri uri = Uri.parse(value.toString());
                return isTrustedTwitterOAuthUri(uri) ? uri : null;
            } catch (Throwable ignored) { return null; }
        }
        if (value instanceof Intent) {
            Intent nested = (Intent) value;
            return findTwitterOAuthUri(nested.getData(), nested.getExtras(), depth + 1);
        }
        if (value instanceof Bundle) {
            return findTwitterOAuthUri(null, (Bundle) value, depth + 1);
        }
        return null;
    }

    private static boolean isTwitterWebAuthIntent(Intent source) {
        return source != null && isTrustedTwitterOAuthUri(source.getData());
    }

    private static boolean isTrustedTwitterOAuthUri(Uri uri) {
        if (uri == null) return false;
        String scheme = lower(uri.getScheme());
        if (!"https".equals(scheme) && !"http".equals(scheme)) return false;
        String host = lower(uri.getHost());
        if (!("twitter.com".equals(host) || "www.twitter.com".equals(host)
                || "mobile.twitter.com".equals(host) || "api.twitter.com".equals(host)
                || "x.com".equals(host) || "www.x.com".equals(host)
                || "mobile.x.com".equals(host))) return false;
        String path = lower(uri.getPath());
        return path.contains("oauth") || path.contains("authorize") || path.contains("authenticate");
    }

    private static Intent createTwitterWebResultBridgeIntent(
            Intent source, IBinder resultTo, String resultWho,
            int requestCode, String virtualPackage) {
        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) return null;
        Intent bridge = createBaseBridge(resultTo, resultWho, requestCode, virtualPackage, bpid);
        bridge.putExtra(EXTRA_BROWSER_AUTH, true);
        bridge.putExtra(VirtualOAuthRouter.EXTRA_AUTH_URL, source.getDataString());
        return prepareBridgeForLaunch(bridge);
    }

    private static String trustedProviderPackage(Intent intent) {
        if (intent == null || BlackBoxCore.getContext() == null) return null;
        ComponentName component = intent.getComponent();
        if (component != null && isTrustedProviderPackage(component.getPackageName())) {
            return component.getPackageName();
        }
        String packageName = intent.getPackage();
        if (isTrustedProviderPackage(packageName)) return packageName;
        try {
            ResolveInfo resolved = BlackBoxCore.getContext().getPackageManager().resolveActivity(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && isTrustedProviderPackage(resolved.activityInfo.packageName)) {
                return resolved.activityInfo.packageName;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void putResultTarget(
            Intent bridge, IBinder resultTo, String resultWho,
            int requestCode, String virtualPackage) {
        bridge.putExtra(EXTRA_RESULT_BINDER, resultTo);
        bridge.putExtra(EXTRA_RESULT_WHO, resultWho);
        bridge.putExtra(EXTRA_REQUEST_CODE, requestCode);
        bridge.putExtra(EXTRA_VIRTUAL_PACKAGE, virtualPackage);
    }

    private static Intent prepareBridgeForLaunch(Intent bridge) {
        if (bridge == null) return null;
        bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return IntentRedirectCompat.sanitizeForHost(bridge);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
