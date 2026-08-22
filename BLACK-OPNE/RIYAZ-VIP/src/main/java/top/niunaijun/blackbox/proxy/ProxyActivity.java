package top.niunaijun.blackbox.proxy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.compat.oauth.AuthTabCompat;
import top.niunaijun.blackbox.compat.oauth.VirtualOAuthRouter;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.service.HCallbackStub;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;

/**
 * ProxyActivity
 * Fixed & Hardened for Android 10–16.
 */
public class ProxyActivity extends Activity {

    public static final String TAG = "ProxyActivity";
    private static final String OAUTH_TAG = "ParallaxOAuth";
    private static final int REQUEST_EXTERNAL_AUTH = 0x5042;
    private static final int REQUEST_BROWSER_AUTH = 0x5043;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getIntent();
        if (isExternalAuthBridge(launchIntent)) {
            HookManager.get().checkEnv(HCallbackStub.class);
            if (savedInstanceState == null) {
                launchExternalProvider(launchIntent);
            }
            return;
        }

        if (isBrowserAuthBridge(launchIntent)) {
            HookManager.get().checkEnv(HCallbackStub.class);
            if (savedInstanceState == null) {
                launchBrowserProvider(launchIntent);
            }
            return;
        }

        Log.d(TAG, "onCreate");
        finish();
        HookManager.get().checkEnv(HCallbackStub.class);
        ProxyActivityRecord record = ProxyActivityRecord.create(launchIntent);
        if (record.mTarget != null) {
            record.mTarget.setExtrasClassLoader(BActivityThread.getApplication().getClassLoader());
            startActivity(record.mTarget);
        }
    }

    private boolean isExternalAuthBridge(Intent intent) {
        return intent != null
                && intent.getBooleanExtra(ExternalAuthRouter.EXTRA_EXTERNAL_AUTH, false);
    }

    private boolean isBrowserAuthBridge(Intent intent) {
        return intent != null
                && intent.getBooleanExtra(ExternalAuthRouter.EXTRA_BROWSER_AUTH, false);
    }

    private void launchExternalProvider(Intent bridgeIntent) {
        try {
            Intent providerIntent = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
            if (!ExternalAuthRouter.isTrustedProviderIntent(providerIntent)) {
                finish();
                return;
            }
            providerIntent.setExtrasClassLoader(getClassLoader());
            startActivityForResult(providerIntent, REQUEST_EXTERNAL_AUTH);
        } catch (Throwable ignored) {
            deliverOriginalActivityResult(RESULT_CANCELED, null);
            finish();
        }
    }

    private void launchBrowserProvider(Intent bridgeIntent) {
        try {
            Uri authUri = safeHttpsUri(bridgeIntent.getStringExtra(
                    VirtualOAuthRouter.EXTRA_AUTH_URL));
            Uri redirectUri = safeCustomRedirectUri(bridgeIntent.getStringExtra(
                    VirtualOAuthRouter.EXTRA_REDIRECT_URI));
            String provider = bridgeIntent.getStringExtra(
                    VirtualOAuthRouter.EXTRA_AUTH_PROVIDER);
            if (authUri == null || redirectUri == null || provider == null
                    || !AuthTabCompat.isSupportedProvider(this, provider, authUri)) {
                oauthDiagnostic("result_bridge_setup_rejected", authUri, null, false);
                deliverOriginalActivityResult(RESULT_CANCELED, null);
                finish();
                return;
            }

            Intent authIntent = new Intent(Intent.ACTION_VIEW, authUri);
            authIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            authIntent.setPackage(provider);
            authIntent.putExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, true);
            authIntent.putExtra(
                    AuthTabCompat.EXTRA_REDIRECT_SCHEME, redirectUri.getScheme());

            Bundle session = new Bundle();
            session.putBinder(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION, null);
            authIntent.putExtras(session);
            startActivityForResult(authIntent, REQUEST_BROWSER_AUTH);
        } catch (Throwable ignored) {
            oauthDiagnostic("result_bridge_launch_failed", null, null, false);
            deliverOriginalActivityResult(RESULT_CANCELED, null);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXTERNAL_AUTH && isExternalAuthBridge(getIntent())) {
            deliverOriginalActivityResult(resultCode, data);
            finish();
            return;
        }

        if (requestCode == REQUEST_BROWSER_AUTH && isBrowserAuthBridge(getIntent())) {
            handleBrowserAuthResult(resultCode, data);
            finish();
        }
    }

    private void handleBrowserAuthResult(int resultCode, Intent data) {
        Intent bridgeIntent = getIntent();
        Uri authUri = safeHttpsUri(bridgeIntent == null ? null
                : bridgeIntent.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL));
        Uri expectedRedirect = safeCustomRedirectUri(bridgeIntent == null ? null
                : bridgeIntent.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI));
        Uri callback = data == null ? null : data.getData();

        if (resultCode != RESULT_OK || callback == null || expectedRedirect == null) {
            oauthDiagnostic("result_bridge_not_completed", authUri, callback, false);
            deliverOriginalActivityResult(RESULT_CANCELED, null);
            return;
        }

        String expectedScheme = expectedRedirect.getScheme();
        String actualScheme = callback.getScheme();
        if (expectedScheme == null || actualScheme == null
                || !expectedScheme.equalsIgnoreCase(actualScheme)) {
            oauthDiagnostic("result_bridge_scheme_mismatch", authUri, callback, false);
            deliverOriginalActivityResult(RESULT_CANCELED, null);
            return;
        }

        boolean legacyTwitter = isTwitterHost(authUri)
                && hasQueryParameter(authUri, "oauth_token");
        if (legacyTwitter
                && (!hasQueryParameter(callback, "oauth_token")
                || !hasQueryParameter(callback, "oauth_verifier"))) {
            oauthDiagnostic("result_bridge_incomplete_oauth1", authUri, callback, false);
            deliverOriginalActivityResult(RESULT_CANCELED, null);
            return;
        }

        oauthDiagnostic("result_bridge_delivered", authUri, callback, true);
        deliverOriginalActivityResult(resultCode, data);
    }

    private void deliverOriginalActivityResult(int resultCode, Intent data) {
        try {
            Intent bridgeIntent = getIntent();
            Bundle extras = bridgeIntent == null ? null : bridgeIntent.getExtras();
            if (extras == null) {
                return;
            }
            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(
                    ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            if (resultTo == null || originalRequestCode < 0) {
                return;
            }

            // This proxy instance runs in the same :pN process as the virtual app,
            // so BActivityManager can deliver the result to the exact original
            // virtual Activity token and preserve its requestCode/resultWho.
            BActivityManager.get().sendActivityResult(
                    resultTo,
                    resultWho,
                    originalRequestCode,
                    data,
                    resultCode);
        } catch (Throwable ignored) {
            // Fail closed. Provider result contents are intentionally not logged.
        }
    }

    private static Uri safeHttpsUri(String value) {
        if (value == null || value.length() > 16_384) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Uri safeCustomRedirectUri(String value) {
        if (value == null || value.length() > 8_192) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            String lower = scheme.toLowerCase(java.util.Locale.US);
            if (lower.isEmpty()
                    || "http".equals(lower)
                    || "https".equals(lower)
                    || "file".equals(lower)
                    || "content".equals(lower)
                    || "javascript".equals(lower)
                    || "data".equals(lower)
                    || "intent".equals(lower)
                    || !lower.matches("^[a-z][a-z0-9+.-]{1,127}$")) {
                return null;
            }
            return uri;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasQueryParameter(Uri uri, String key) {
        if (uri == null || key == null) return false;
        try {
            String value = uri.getQueryParameter(key);
            return value != null && !value.trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTwitterHost(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(java.util.Locale.US);
        return "twitter.com".equals(host)
                || "x.com".equals(host)
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
    }

    private static void oauthDiagnostic(
            String stage, Uri authUri, Uri callback, boolean delivered) {
        try {
            boolean oauth1 = isTwitterHost(authUri)
                    && hasQueryParameter(authUri, "oauth_token");
            boolean token = hasQueryParameter(callback, "oauth_token");
            boolean verifier = hasQueryParameter(callback, "oauth_verifier");
            boolean code = hasQueryParameter(callback, "code");
            boolean denied = hasQueryParameter(callback, "denied")
                    || hasQueryParameter(callback, "error");
            Log.d(OAUTH_TAG,
                    "twitter stage=" + stage
                            + " oauth1=" + oauth1
                            + " token=" + token
                            + " verifier=" + verifier
                            + " code=" + code
                            + " denied=" + denied
                            + " delivered=" + delivered);
        } catch (Throwable ignored) {
        }
    }

    public static class P0 extends ProxyActivity {}
    public static class P1 extends ProxyActivity {}
    public static class P2 extends ProxyActivity {}
    public static class P3 extends ProxyActivity {}
    public static class P4 extends ProxyActivity {}
    public static class P5 extends ProxyActivity {}
    public static class P6 extends ProxyActivity {}
    public static class P7 extends ProxyActivity {}
    public static class P8 extends ProxyActivity {}
    public static class P9 extends ProxyActivity {}
    public static class P10 extends ProxyActivity {}
    public static class P11 extends ProxyActivity {}
    public static class P12 extends ProxyActivity {}
    public static class P13 extends ProxyActivity {}
    public static class P14 extends ProxyActivity {}
    public static class P15 extends ProxyActivity {}
    public static class P16 extends ProxyActivity {}
    public static class P17 extends ProxyActivity {}
    public static class P18 extends ProxyActivity {}
    public static class P19 extends ProxyActivity {}
    public static class P20 extends ProxyActivity {}
    public static class P21 extends ProxyActivity {}
    public static class P22 extends ProxyActivity {}
    public static class P23 extends ProxyActivity {}
    public static class P24 extends ProxyActivity {}
}
