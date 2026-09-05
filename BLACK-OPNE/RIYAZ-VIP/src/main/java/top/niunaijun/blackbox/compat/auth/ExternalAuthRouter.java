package top.niunaijun.blackbox.compat.auth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
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
 * Package/signature identity is never spoofed. Explicit provider intents are
 * accepted directly; implicit intents are resolved with Android's real package
 * manager and accepted only when the resolved app is on the trusted allowlist.
 * IntentSenders are accepted only when Android reports an allow-listed creator
 * package, so a cloned app cannot use this bridge for arbitrary external flows.
 *
 * Twitter/X OAuth is real-app-first. This includes the GCloud/IMSDK
 * `com.itop.twitterwrapper.TwitterWebActivity` observed in stock BGMI: when its
 * launch extras already contain a Twitter/X OAuth URL, the URL is extracted
 * without logging it and offered to the installed official Twitter/X app first.
 * A host callback trampoline preserves the guest's original Activity result for
 * supported legacy custom-scheme redirects. If safe app routing is unavailable,
 * the existing Auth Tab/web flow remains the compatibility fallback.
 *
 * No package identity, signatures, cookies, passwords, or tokens are spoofed.
 *
 * Android 16 compatibility note: provider UI is launched from the host-main
 * bridge rather than from the guest :pN process. A normal startActivityForResult
 * bridge remains attached to Android's original resultTo token, so the host
 * trampoline returns exactly one system-delivered result. Detached IntentSender
 * and Twitter custom-scheme callbacks use the private :pN provider relay.
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
    private static final String TWITTER_KIT_OAUTH_ACTIVITY =
            "com.twitter.sdk.android.core.identity.OAuthActivity";
    private static final String IMSDK_PROXY_ACTIVITY =
            "com.itop.imsdk.android.base.IMSDKProxyActivity";

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
        if (intent != null) {
            intent.removeExtra(EXTRA_DIRECT_PROVIDER_DISPATCH);
        }
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
        if (sender == null) {
            return false;
        }
        try {
            return isTrustedProviderPackage(sender.getCreatorPackage());
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static IntentSender wrapIntentSender(Object target) {
        if (target == null) {
            return null;
        }
        if (target instanceof IntentSender) {
            return (IntentSender) target;
        }
        try {
            for (Constructor<?> constructor : IntentSender.class.getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length != 1
                        || !"android.content.IIntentSender".equals(parameterTypes[0].getName())
                        || !parameterTypes[0].isInstance(target)) {
                    continue;
                }
                constructor.setAccessible(true);
                Object wrapped = constructor.newInstance(target);
                return wrapped instanceof IntentSender ? (IntentSender) wrapped : null;
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
                || virtualPackage == null || virtualPackage.trim().isEmpty()) {
            return null;
        }

        // Stock BGMI/GCloud and Twitter Kit sometimes start an explicit wrapper
        // Activity instead of dispatching the OAuth URL with ACTION_VIEW. If that
        // launch payload already carries a Twitter/X OAuth URL, convert only that
        // one URL to a real-provider ACTION_VIEW. Failure is intentionally
        // non-destructive: returning null below lets the original Activity
        // continue exactly as before.
        Intent embeddedTwitterAuth = extractEmbeddedTwitterAuthIntent(source);
        if (embeddedTwitterAuth != null) {
            Intent nativeBridge = createTwitterNativeResultBridgeIntent(
                    embeddedTwitterAuth, resultTo, resultWho, requestCode, virtualPackage);
            if (nativeBridge != null) {
                return nativeBridge;
            }
        }

        if (isTwitterWebAuthIntent(source)) {
            Intent nativeBridge = createTwitterNativeResultBridgeIntent(
                    source, resultTo, resultWho, requestCode, virtualPackage);
            if (nativeBridge != null) {
                return nativeBridge;
            }
            return createTwitterWebResultBridgeIntent(
                    source, resultTo, resultWho, requestCode, virtualPackage);
        }

        String providerPackage = trustedProviderPackage(source);
        if (providerPackage == null) {
            return null;
        }

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) {
            return null;
        }

        Intent providerIntent = new Intent(source);
        if (providerIntent.getComponent() == null && providerIntent.getPackage() == null) {
            providerIntent.setPackage(providerPackage);
        }
        providerIntent.putExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        Intent bridge = createBaseBridge(
                resultTo, resultWho, requestCode, virtualPackage, bpid);
        bridge.putExtra(EXTRA_PROVIDER_INTENT, providerIntent);
        bridge.addFlags(source.getFlags() & (
                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
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
        if (!isTrustedProviderIntentSender(sender)
                || resultTo == null
                || requestCode < 0
                || virtualPackage == null
                || virtualPackage.trim().isEmpty()) {
            return null;
        }

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) {
            return null;
        }

        Intent bridge = createBaseBridge(
                resultTo, resultWho, requestCode, virtualPackage, bpid);
        bridge.putExtra(EXTRA_MANUAL_RESULT_RELAY, true);
        bridge.putExtra(EXTRA_PROVIDER_INTENT_SENDER, sender);
        if (fillInIntent != null) {
            bridge.putExtra(EXTRA_PROVIDER_FILL_IN_INTENT, new Intent(fillInIntent));
        }
        bridge.putExtra(EXTRA_PROVIDER_FLAGS_MASK, flagsMask);
        bridge.putExtra(EXTRA_PROVIDER_FLAGS_VALUES, flagsValues);
        if (options != null) {
            bridge.putExtra(EXTRA_PROVIDER_OPTIONS, new Bundle(options));
        }
        bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return prepareBridgeForLaunch(bridge);
    }

    private static Intent createBaseBridge(
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage,
            int bpid) {
        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(),
                VirtualOAuthBridgeActivity.class.getName()));
        bridge.putExtra(EXTRA_EXTERNAL_AUTH, true);
        bridge.putExtra(EXTRA_BPID, bpid);
        bridge.putExtra(EXTRA_USER_ID, BActivityThread.getUserId());
        putResultTarget(bridge, resultTo, resultWho, requestCode, virtualPackage);
        return bridge;
    }

    /**
     * Prefer the real Twitter/X application only when Android's real PackageManager
     * confirms that the installed provider advertises support for this exact OAuth
     * URL and the host can safely capture the guest's declared callback scheme.
     */
    private static Intent createTwitterNativeResultBridgeIntent(
            Intent source,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        Uri authUri = source == null ? null : source.getData();
        if (!isTrustedTwitterOAuthUri(authUri)) {
            return null;
        }

        int userId = BActivityThread.getUserId();
        Uri redirectUri = VirtualOAuthRouter.resolveTwitterRedirectUri(
                authUri, userId, virtualPackage);
        if (redirectUri == null
                || !TwitterOAuthSessionStore.isHostCaptureSupported(redirectUri)) {
            return null;
        }

        String providerPackage = resolveNativeTwitterProvider(source);
        if (providerPackage == null) {
            return null;
        }

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) {
            return null;
        }

        Intent providerIntent = new Intent(source);
        // The source may have been explicitly aimed at a browser. Resolve again
        // against the official provider without retaining that browser component.
        providerIntent.setComponent(null);
        providerIntent.setPackage(providerPackage);
        providerIntent.putExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(),
                TwitterNativeAuthBridgeActivity.class.getName()));
        bridge.putExtra(EXTRA_BPID, bpid);
        bridge.putExtra(EXTRA_USER_ID, userId);
        bridge.putExtra(EXTRA_PROVIDER_INTENT, providerIntent);
        bridge.putExtra(VirtualOAuthRouter.EXTRA_AUTH_URL, authUri.toString());
        bridge.putExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI, redirectUri.toString());
        putResultTarget(bridge, resultTo, resultWho, requestCode, virtualPackage);
        bridge.addFlags(source.getFlags() & (
                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return prepareBridgeForLaunch(bridge);
    }

    private static String resolveNativeTwitterProvider(Intent source) {
        if (source == null) return null;
        try {
            PackageManager packageManager = BlackBoxCore.getContext().getPackageManager();
            for (String packageName : TWITTER_NATIVE_PROVIDER_PACKAGES) {
                Intent candidate = new Intent(source);
                candidate.setComponent(null);
                candidate.setPackage(packageName);
                ResolveInfo resolved = packageManager.resolveActivity(
                        candidate, PackageManager.MATCH_DEFAULT_ONLY);
                if (resolved == null || resolved.activityInfo == null) {
                    continue;
                }
                if (packageName.equals(resolved.activityInfo.packageName)
                        && isTwitterProviderPackage(packageName)
                        && resolved.activityInfo.enabled
                        && resolved.activityInfo.exported) {
                    return packageName;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Intent extractEmbeddedTwitterAuthIntent(Intent source) {
        if (!isEmbeddedTwitterAuthActivity(source)) {
            return null;
        }

        Uri uri = findTwitterOAuthUri(source.getData(), source.getExtras(), 0);
        if (uri == null) {
            return null;
        }

        Intent auth = new Intent(Intent.ACTION_VIEW, uri);
        auth.addCategory(Intent.CATEGORY_BROWSABLE);
        auth.addCategory(Intent.CATEGORY_DEFAULT);
        auth.setFlags(source.getFlags());
        return auth;
    }

    private static boolean isEmbeddedTwitterAuthActivity(Intent source) {
        if (source == null) return false;
        ComponentName component = source.getComponent();
        return component != null
                && isEmbeddedTwitterAuthClass(component.getClassName());
    }

    private static boolean isEmbeddedTwitterAuthClass(String className) {
        return GCLOUD_TWITTER_WEB_ACTIVITY.equals(className)
                || TWITTER_KIT_OAUTH_ACTIVITY.equals(className)
                || IMSDK_PROXY_ACTIVITY.equals(className);
    }

    /**
     * Search only known Twitter auth wrapper launch payloads for an already
     * created Twitter/X OAuth URL. The value is never persisted or logged.
     * Recursion is intentionally shallow to avoid walking arbitrary app object
     * graphs.
     */
    private static Uri findTwitterOAuthUri(Uri direct, Bundle extras, int depth) {
        if (isTrustedTwitterOAuthUri(direct)) {
            return direct;
        }
        if (extras == null || depth > 3) {
            return null;
        }

        try {
            for (String key : extras.keySet()) {
                Object value;
                try {
                    value = extras.get(key);
                } catch (Throwable ignored) {
                    continue;
                }

                Uri found = twitterOAuthUriFromValue(value, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Uri twitterOAuthUriFromValue(Object value, int depth) {
        if (value == null || depth > 4) return null;

        if (value instanceof Uri) {
            Uri uri = (Uri) value;
            return isTrustedTwitterOAuthUri(uri) ? uri : null;
        }

        if (value instanceof CharSequence) {
            try {
                Uri uri = Uri.parse(value.toString().trim());
                return isTrustedTwitterOAuthUri(uri) ? uri : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        if (value instanceof Bundle) {
            return findTwitterOAuthUri(null, (Bundle) value, depth);
        }

        if (value instanceof Intent) {
            Intent nested = (Intent) value;
            return findTwitterOAuthUri(nested.getData(), nested.getExtras(), depth);
        }

        if (value instanceof String[]) {
            for (String item : (String[]) value) {
                Uri found = twitterOAuthUriFromValue(item, depth + 1);
                if (found != null) return found;
            }
        }

        if (value instanceof CharSequence[]) {
            for (CharSequence item : (CharSequence[]) value) {
                Uri found = twitterOAuthUriFromValue(item, depth + 1);
                if (found != null) return found;
            }
        }

        return null;
    }

    private static Intent createTwitterWebResultBridgeIntent(
            Intent source,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        try {
            Intent template = VirtualOAuthRouter.createBridgeIntent(
                    source, BActivityThread.getUserId(), virtualPackage);
            if (template == null) {
                return null;
            }

            int bpid = BActivityThread.getAppPid();
            if (bpid < 0 || bpid > 24) {
                return null;
            }

            String authUrl = template.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL);
            String redirectUri = template.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI);
            String authProvider = template.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_PROVIDER);
            int userId = template.getIntExtra(VirtualOAuthRouter.EXTRA_USER_ID, -1);
            if (authUrl == null || redirectUri == null || authProvider == null || userId < 0) {
                return null;
            }

            Intent bridge = new Intent();
            bridge.setComponent(new ComponentName(
                    BlackBoxCore.getHostPkg(),
                    VirtualOAuthBridgeActivity.class.getName()));
            bridge.putExtra(EXTRA_BROWSER_AUTH, true);
            bridge.putExtra(EXTRA_BPID, bpid);
            bridge.putExtra(EXTRA_USER_ID, userId);
            bridge.putExtra(VirtualOAuthRouter.EXTRA_AUTH_URL, authUrl);
            bridge.putExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI, redirectUri);
            bridge.putExtra(VirtualOAuthRouter.EXTRA_AUTH_PROVIDER, authProvider);
            bridge.putExtra(VirtualOAuthRouter.EXTRA_VIRTUAL_PACKAGE, virtualPackage);
            bridge.putExtra(VirtualOAuthRouter.EXTRA_USER_ID, userId);
            putResultTarget(bridge, resultTo, resultWho, requestCode, virtualPackage);
            bridge.addFlags(source.getFlags() & (
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return prepareBridgeForLaunch(bridge);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Intent prepareBridgeForLaunch(Intent bridge) {
        IntentRedirectCompat.collectNestedIntentKeys(bridge);
        return bridge;
    }

    private static void putResultTarget(
            Intent bridge,
            IBinder resultTo,
            String resultWho,
            int requestCode,
            String virtualPackage) {
        bridge.putExtra(EXTRA_RESULT_WHO, resultWho);
        bridge.putExtra(EXTRA_REQUEST_CODE, requestCode);
        bridge.putExtra(EXTRA_VIRTUAL_PACKAGE, virtualPackage);
        Bundle binderBundle = new Bundle();
        binderBundle.putBinder(EXTRA_RESULT_BINDER, resultTo);
        bridge.putExtras(binderBundle);
    }

    private static boolean isTwitterWebAuthIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri uri = intent.getData();
        return isTwitterHttpsUri(uri);
    }

    public static boolean isTrustedTwitterOAuthUri(Uri uri) {
        return isTwitterHttpsUri(uri) && isLikelyTwitterOAuthUri(uri);
    }

    private static boolean isTwitterHttpsUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        host = host == null ? "" : host.toLowerCase(Locale.US);
        return "twitter.com".equals(host)
                || "x.com".equals(host)
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
    }

    private static boolean isLikelyTwitterOAuthUri(Uri uri) {
        if (uri == null) return false;
        String path = uri.getPath();
        String query = uri.getQuery();
        path = path == null ? "" : path.toLowerCase(Locale.US);
        query = query == null ? "" : query.toLowerCase(Locale.US);
        return path.contains("oauth")
                || path.contains("authorize")
                || path.contains("authenticate")
                || query.contains("oauth_token=")
                || query.contains("client_id=");
    }

    private static String trustedProviderPackage(Intent intent) {
        if (intent == null) {
            return null;
        }

        ComponentName component = intent.getComponent();
        String explicitPackage = component != null
                ? component.getPackageName() : intent.getPackage();
        if (explicitPackage != null) {
            return isTrustedProviderPackage(explicitPackage) ? explicitPackage : null;
        }

        try {
            PackageManager packageManager = BlackBoxCore.getContext().getPackageManager();
            ResolveInfo resolved = packageManager.resolveActivity(
                    new Intent(intent), PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null) {
                return null;
            }
            String resolvedPackage = resolved.activityInfo.packageName;
            return isTrustedProviderPackage(resolvedPackage) ? resolvedPackage : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
