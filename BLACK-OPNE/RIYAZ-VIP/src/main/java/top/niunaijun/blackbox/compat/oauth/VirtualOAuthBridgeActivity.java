package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.Locale;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.provider.ProviderCall;

/**
 * Stable host-main trampoline for browser OAuth.
 *
 * Native Google/Facebook/Twitter provider UI now uses NativeAuthBridgeActivity so
 * browser lifecycle rules cannot alter native provider routing. This activity is
 * retained for validated Auth-Tab/browser OAuth callbacks and for compatibility
 * with older bridge intents.
 */
@Obfuscate
public final class VirtualOAuthBridgeActivity extends Activity {
    private static final String TAG = "ParallaxOAuth";
    private static final String AUTH_TAG = "ParallaxAuth";
    private static final int REQUEST_AUTH_TAB = 0x5041;
    private static final int REQUEST_EXTERNAL_AUTH = 0x5042;

    private static final String FACEBOOK_CUSTOM_TAB_ACTIVITY =
            "com.facebook.CustomTabActivity";
    private static final String FACEBOOK_CUSTOM_TAB_MAIN_ACTIVITY =
            "com.facebook.CustomTabMainActivity";
    private static final String FACEBOOK_REDIRECT_ACTION =
            "CustomTabActivity.action_customTabRedirect";
    private static final String FACEBOOK_REFRESH_ACTION =
            "CustomTabMainActivity.action_refresh";
    private static final String FACEBOOK_EXTRA_URL =
            "CustomTabMainActivity.extra_url";
    private static final long FACEBOOK_BRIDGE_FINISH_DELAY_MS = 650L;

    private String virtualPackage;
    private Uri expectedRedirectUri;
    private Uri authUri;
    private String authProvider;
    private int userId = -1;
    private int resultBpid = -1;
    private boolean twitterFlow;
    private boolean facebookFlow;
    private boolean legacyTwitterFlow;
    private boolean resultBridgeMode;
    private boolean externalAuthMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getIntent();
        externalAuthMode = launchIntent != null
                && launchIntent.getBooleanExtra(ExternalAuthRouter.EXTRA_EXTERNAL_AUTH, false);
        resultBridgeMode = launchIntent != null
                && launchIntent.getBooleanExtra(ExternalAuthRouter.EXTRA_BROWSER_AUTH, false);
        resultBpid = launchIntent == null ? -1
                : launchIntent.getIntExtra(ExternalAuthRouter.EXTRA_BPID, -1);

        // Backward compatibility only. New native provider routes use the dedicated
        // NativeAuthBridgeActivity and never share browser callback state.
        if (externalAuthMode) {
            virtualPackage = launchIntent.getStringExtra(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            userId = launchIntent.getIntExtra(ExternalAuthRouter.EXTRA_USER_ID, -1);
            if (!validResultTarget(launchIntent)) {
                finish();
                return;
            }
            if (savedInstanceState == null) {
                launchLegacyExternalProvider(launchIntent);
            }
            return;
        }

        String authUrl = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL);
        String redirectUriValue = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI);
        virtualPackage = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        userId = launchIntent == null ? -1
                : launchIntent.getIntExtra(VirtualOAuthRouter.EXTRA_USER_ID, -1);
        authProvider = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_PROVIDER);

        authUri = safeHttpsUri(authUrl);
        Uri redirectUri = safeCustomRedirectUri(redirectUriValue);
        if (authUri == null || !VirtualOAuthRouter.isTrustedAuthUri(authUri)
                || redirectUri == null || virtualPackage == null
                || virtualPackage.trim().isEmpty() || userId < 0
                || authProvider == null || authProvider.trim().isEmpty()) {
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
            return;
        }

        expectedRedirectUri = redirectUri;
        twitterFlow = isTwitterHost(authUri);
        facebookFlow = isFacebookHost(authUri);
        legacyTwitterFlow = twitterFlow && hasQueryParameter(authUri, "oauth_token");

        if (!redirectResolvesToVirtualPackage(redirectUri)
                || !AuthTabCompat.isSupportedProvider(this, authProvider, authUri)) {
            diagnostic("setup_rejected", false, false, false, false, false);
            facebookDiagnostic("setup_rejected", false);
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
            return;
        }

        if (resultBridgeMode && !validResultTarget(launchIntent)) {
            diagnostic("result_bridge_target_rejected", false, false, false, false, false);
            finish();
            return;
        }

        if (savedInstanceState == null) {
            launchAuthTab(authUri, lower(expectedRedirectUri.getScheme()), authProvider);
        }
    }

    private void launchLegacyExternalProvider(Intent bridgeIntent) {
        try {
            IntentSender sender = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT_SENDER);
            if (sender != null) {
                if (!ExternalAuthRouter.isTrustedProviderIntentSender(sender)) {
                    authDiagnostic("legacy_sender_rejected", null, false);
                    relayOriginalActivityResult(RESULT_CANCELED, null);
                    finish();
                    return;
                }
                Intent fillIn = bridgeIntent.getParcelableExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FILL_IN_INTENT);
                int flagsMask = bridgeIntent.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_MASK, 0);
                int flagsValues = bridgeIntent.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_VALUES, 0);
                Bundle options = bridgeIntent.getBundleExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_OPTIONS);
                startIntentSenderForResult(sender, REQUEST_EXTERNAL_AUTH, fillIn,
                        flagsMask, flagsValues, 0, options);
                return;
            }

            Intent providerIntent = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
            String provider = ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
            if (provider == null) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
                finish();
                return;
            }
            startActivityForResult(providerIntent, REQUEST_EXTERNAL_AUTH);
        } catch (Throwable ignored) {
            relayOriginalActivityResult(RESULT_CANCELED, null);
            finish();
        }
    }

    private void launchAuthTab(Uri uri, String redirectScheme, String provider) {
        try {
            Intent authIntent = new Intent(Intent.ACTION_VIEW, uri);
            authIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            authIntent.setPackage(provider);
            authIntent.putExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, true);
            authIntent.putExtra(AuthTabCompat.EXTRA_REDIRECT_SCHEME, redirectScheme);
            Bundle session = new Bundle();
            session.putBinder(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION, null);
            authIntent.putExtras(session);
            startActivityForResult(authIntent, REQUEST_AUTH_TAB);
        } catch (Throwable ignored) {
            diagnostic("launch_failed", false, false, false, false, false);
            facebookDiagnostic("launch_failed", false);
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXTERNAL_AUTH && externalAuthMode) {
            boolean delivered = relayOriginalActivityResult(resultCode, data);
            authDiagnostic("legacy_provider_result", providerPackageFromBridge(), delivered);
            finish();
            return;
        }

        if (requestCode != REQUEST_AUTH_TAB) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            diagnostic("auth_not_completed", false, false, false, false, false);
            facebookDiagnostic("auth_not_completed", false);
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
            return;
        }

        Uri callbackUri = data.getData();
        if (!matchesExpectedCallback(callbackUri)) {
            diagnostic("callback_mismatch", false, false, false, false, false);
            facebookDiagnostic("callback_mismatch", false);
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
            return;
        }

        boolean hasToken = hasQueryParameter(callbackUri, "oauth_token");
        boolean hasVerifier = hasQueryParameter(callbackUri, "oauth_verifier");
        boolean hasCode = hasQueryParameter(callbackUri, "code");
        boolean denied = hasQueryParameter(callbackUri, "denied")
                || hasQueryParameter(callbackUri, "error");

        if (legacyTwitterFlow && !denied && (!hasToken || !hasVerifier)) {
            diagnostic("twitter_oauth1_incomplete", hasToken, hasVerifier, hasCode, denied, false);
            if (resultBridgeMode) {
                relayOriginalActivityResult(RESULT_CANCELED, null);
            }
            finish();
            return;
        }

        if (facebookFlow) {
            boolean dispatched = dispatchFacebookCustomTabResult(callbackUri);
            facebookDiagnostic(dispatched ? "redirect_dispatched" : "redirect_unresolved", dispatched);
            if (dispatched) {
                finishFacebookBridgeAfterDispatch();
            } else {
                if (resultBridgeMode) {
                    relayOriginalActivityResult(RESULT_CANCELED, null);
                }
                finish();
            }
            return;
        }

        if (resultBridgeMode) {
            boolean delivered = relayOriginalActivityResult(resultCode, data);
            diagnostic(delivered ? "result_bridge_delivered" : "result_bridge_delivery_failed",
                    hasToken, hasVerifier, hasCode, denied, delivered);
            finish();
            return;
        }

        boolean dispatched = dispatchToVirtualPackage(callbackUri);
        diagnostic(dispatched ? "callback_dispatched" : "callback_unresolved",
                hasToken, hasVerifier, hasCode, denied, dispatched);
        finish();
    }

    /**
     * Meta's public Custom Tab contract is two-stage:
     * 1) CustomTabActivity receives action_customTabRedirect with callback data.
     * 2) it refreshes the existing CustomTabMainActivity with action_refresh and
     *    extra_url. The previous bridge skipped stage 1 but still used stage-1's
     *    action on CustomTabMainActivity, which made the SDK resume into Cancelled.
     */
    private boolean dispatchFacebookCustomTabResult(Uri callbackUri) {
        if (callbackUri == null || virtualPackage == null
                || virtualPackage.trim().isEmpty() || userId < 0) {
            return false;
        }

        if (dispatchFacebookRedirectActivity(callbackUri)) {
            return true;
        }
        return dispatchFacebookRefreshFallback(callbackUri);
    }

    private boolean dispatchFacebookRedirectActivity(Uri callbackUri) {
        try {
            ComponentName component = new ComponentName(
                    virtualPackage, FACEBOOK_CUSTOM_TAB_ACTIVITY);
            ActivityInfo info = BPackageManager.get().getActivityInfo(component, 0, userId);
            if (info == null || !virtualPackage.equals(info.packageName)) return false;

            Intent redirect = new Intent(FACEBOOK_REDIRECT_ACTION, callbackUri);
            redirect.setComponent(component);
            redirect.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            BActivityManager.get().startActivity(redirect, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean dispatchFacebookRefreshFallback(Uri callbackUri) {
        try {
            ComponentName component = new ComponentName(
                    virtualPackage, FACEBOOK_CUSTOM_TAB_MAIN_ACTIVITY);
            ActivityInfo info = BPackageManager.get().getActivityInfo(component, 0, userId);
            if (info == null || !virtualPackage.equals(info.packageName)) return false;

            Intent refresh = new Intent();
            refresh.setComponent(component);
            refresh.setAction(FACEBOOK_REFRESH_ACTION);
            refresh.putExtra(FACEBOOK_EXTRA_URL, callbackUri.toString());
            refresh.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            BActivityManager.get().startActivity(refresh, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void finishFacebookBridgeAfterDispatch() {
        try {
            getWindow().getDecorView().postDelayed(
                    this::finish, FACEBOOK_BRIDGE_FINISH_DELAY_MS);
        } catch (Throwable ignored) {
            finish();
        }
    }

    private boolean validResultTarget(Intent launchIntent) {
        if (launchIntent == null || resultBpid < 0 || resultBpid > 24) return false;
        Bundle extras = launchIntent.getExtras();
        if (extras == null) return false;
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        String targetPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        return resultTo != null && requestCode >= 0
                && targetPackage != null && !targetPackage.trim().isEmpty();
    }

    private boolean relayOriginalActivityResult(int resultCode, Intent data) {
        try {
            Intent bridgeIntent = getIntent();
            Bundle extras = bridgeIntent == null ? null : bridgeIntent.getExtras();
            if (extras == null || resultBpid < 0 || resultBpid > 24) return false;

            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            String targetPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            if (resultTo == null || originalRequestCode < 0
                    || targetPackage == null || targetPackage.trim().isEmpty()) return false;

            Bundle relay = new Bundle();
            BundleCompat.putBinder(relay, ExternalAuthRouter.EXTRA_RESULT_BINDER, resultTo);
            relay.putString(ExternalAuthRouter.EXTRA_RESULT_WHO, resultWho);
            relay.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, originalRequestCode);
            relay.putInt(ExternalAuthRouter.EXTRA_RESULT_CODE, resultCode);
            relay.putInt(ExternalAuthRouter.EXTRA_BPID, resultBpid);
            relay.putInt(ExternalAuthRouter.EXTRA_USER_ID, userId);
            relay.putString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, targetPackage);
            if (data != null) relay.putParcelable(
                    ExternalAuthRouter.EXTRA_RESULT_DATA, new Intent(data));

            Bundle response = ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(resultBpid),
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT,
                    null,
                    relay);
            return response != null && response.getBoolean(
                    ExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String providerPackageFromBridge() {
        try {
            Intent bridgeIntent = getIntent();
            if (bridgeIntent == null) return null;
            String validated = bridgeIntent.getStringExtra(
                    ExternalAuthRouter.EXTRA_VALIDATED_PROVIDER_PACKAGE);
            if (ExternalAuthRouter.isTrustedProviderPackage(validated)) return validated;
            Intent providerIntent = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
            if (providerIntent != null) {
                return ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
            }
            IntentSender sender = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT_SENDER);
            return sender == null ? null : sender.getCreatorPackage();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean matchesExpectedCallback(Uri callbackUri) {
        return OAuthCallbackValidator.matches(authUri, expectedRedirectUri, callbackUri)
                && redirectResolvesToVirtualPackage(callbackUri);
    }

    private boolean dispatchToVirtualPackage(Uri callbackUri) {
        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, callbackUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            callback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback, FileUtils.FileMode.MODE_IWUSR, null, userId);
            if (resolved == null || resolved.activityInfo == null
                    || !virtualPackage.equals(resolved.activityInfo.packageName)) return false;
            callback.setComponent(new ComponentName(
                    resolved.activityInfo.packageName, resolved.activityInfo.name));
            BActivityManager.get().startActivity(callback, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean redirectResolvesToVirtualPackage(Uri redirectUri) {
        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, redirectUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback, FileUtils.FileMode.MODE_IWUSR, null, userId);
            return resolved != null && resolved.activityInfo != null
                    && virtualPackage.equals(resolved.activityInfo.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void diagnostic(String stage, boolean hasToken, boolean hasVerifier,
                            boolean hasCode, boolean denied, boolean dispatched) {
        if (!twitterFlow) return;
        Log.i(TAG, "twitter stage=" + stage
                + " oauth1=" + legacyTwitterFlow
                + " token=" + hasToken
                + " verifier=" + hasVerifier
                + " code=" + hasCode
                + " denied=" + denied
                + " dispatched=" + dispatched);
    }

    private void facebookDiagnostic(String stage, boolean dispatched) {
        if (facebookFlow) Log.i(TAG,
                "facebook stage=" + stage + " dispatched=" + dispatched);
    }

    private static void authDiagnostic(String stage, String provider, boolean delivered) {
        String safeProvider = ExternalAuthRouter.isTrustedProviderPackage(provider)
                ? provider : "unknown";
        Log.i(AUTH_TAG, "native stage=" + stage
                + " provider=" + safeProvider + " delivered=" + delivered);
    }

    private static boolean hasQueryParameter(Uri uri, String name) {
        if (uri == null || name == null) return false;
        try {
            String value = uri.getQueryParameter(name);
            return value != null && !value.trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTwitterHost(Uri uri) {
        if (uri == null) return false;
        String host = lower(uri.getHost());
        return "twitter.com".equals(host) || "x.com".equals(host)
                || host.endsWith(".twitter.com") || host.endsWith(".x.com");
    }

    private static boolean isFacebookHost(Uri uri) {
        if (uri == null) return false;
        String host = lower(uri.getHost());
        return "facebook.com".equals(host) || host.endsWith(".facebook.com");
    }

    private static Uri safeHttpsUri(String value) {
        if (value == null || value.length() > 16_384) return null;
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Uri safeCustomRedirectUri(String value) {
        if (value == null || value.length() > 8_192) return null;
        try {
            Uri uri = Uri.parse(value);
            String scheme = lower(uri.getScheme());
            if (scheme.isEmpty() || "http".equals(scheme) || "https".equals(scheme)
                    || "file".equals(scheme) || "content".equals(scheme)
                    || "javascript".equals(scheme) || "data".equals(scheme)
                    || "intent".equals(scheme)
                    || !scheme.matches("^[a-z][a-z0-9+.-]{1,127}$")) return null;
            return uri;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
