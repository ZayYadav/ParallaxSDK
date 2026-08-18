package top.niunaijun.blackbox.compat.auth;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.proxy.ProxyManifest;

/**
 * Routes native sign-in activities to the real provider app installed on the
 * phone, while keeping the activity-result target inside the virtual process.
 *
 * This deliberately does not spoof package names, signatures, certificates or
 * provider responses. The external provider still sees the real host caller and
 * applies its normal security checks.
 */
public final class ExternalAuthRouter {
    public static final String EXTRA_EXTERNAL_AUTH =
            "top.niunaijun.blackbox.auth.EXTERNAL_AUTH";
    public static final String EXTRA_PROVIDER_INTENT =
            "top.niunaijun.blackbox.auth.PROVIDER_INTENT";
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

    private static final Set<String> TRUSTED_PROVIDER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.gms",
            "com.google.android.play.games",
            "com.facebook.katana",
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

    public static boolean isTrustedProviderIntent(Intent intent) {
        return trustedProviderPackage(intent) != null;
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
        String providerPackage = trustedProviderPackage(source);
        if (providerPackage == null) {
            return null;
        }

        int bpid = BActivityThread.getAppPid();
        if (bpid < 0 || bpid > 24) {
            return null;
        }

        Intent providerIntent = new Intent(source);
        providerIntent.putExtra(EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(),
                ProxyManifest.TransparentProxyActivity(bpid)));
        bridge.putExtra(EXTRA_EXTERNAL_AUTH, true);
        bridge.putExtra(EXTRA_PROVIDER_INTENT, providerIntent);
        bridge.putExtra(EXTRA_RESULT_WHO, resultWho);
        bridge.putExtra(EXTRA_REQUEST_CODE, requestCode);
        bridge.putExtra(EXTRA_VIRTUAL_PACKAGE, virtualPackage);

        Bundle binderBundle = new Bundle();
        binderBundle.putBinder(EXTRA_RESULT_BINDER, resultTo);
        bridge.putExtras(binderBundle);

        bridge.addFlags(source.getFlags() & (
                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return bridge;
    }

    private static String trustedProviderPackage(Intent intent) {
        if (intent == null) {
            return null;
        }
        ComponentName component = intent.getComponent();
        String pkg = component != null ? component.getPackageName() : intent.getPackage();
        if (pkg == null || !TRUSTED_PROVIDER_PACKAGES.contains(pkg)) {
            return null;
        }
        return pkg;
    }
}
