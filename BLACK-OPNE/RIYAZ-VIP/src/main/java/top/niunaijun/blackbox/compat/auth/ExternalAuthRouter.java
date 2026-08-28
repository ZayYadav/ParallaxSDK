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
 * Android 16 compatibility note: provider UI is launched from the host-main
 * bridge rather than from the guest :pN process. Some provider transitions can
 * tear down an embedded guest task when the real provider is launched directly
 * by a TransparentProxyActivity. Keeping the trampoline in the stable host
 * process preserves the virtual app process while the provider is foreground.
 * The original Activity result is relayed back to the exact :pN process through
 * its private ProxyContentProvider; no provider identity or result payload is
 * rewritten.
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

    public static final String METHOD_DELIVER_ACTIVITY_RESULT =
            "_Black_|_auth_activity_result_";

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
            return false;
        }
    }

    /**
     * Converts the hidden IIntentSender argument used by ActivityManager into the
     * public IntentSender wrapper. The target and its creator identity remain
     * owned by Android and are never modified.
     */
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
            // OEM hidden API layouts can differ. Fail closed and let Android
            // handle the original sender when it cannot be verified.
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

        if (isTwitterWebAuthIntent(source)) {
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

    /**
     * Creates a result bridge for provider-owned PendingIntent/IntentSender auth
     * resolutions. The provider executes the sender unchanged; the host proxy
     * only forwards its result to the original virtual Activity token.
     */
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
