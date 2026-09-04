package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
 * Stable host-main trampoline for browser OAuth and trusted native provider auth.
 *
 * Normal startActivityForResult bridges stay attached to Android's original
 * resultTo token and return exactly one result with setResult(). Detached
 * IntentSender bridges have no system result link, so only those use the private
 * process-specific ProxyContentProvider relay.
 *
 * No provider package/signature identity is spoofed and no OAuth/provider result
 * values are logged or persisted.
 */
@Obfuscate
public final class VirtualOAuthBridgeActivity extends Activity {
    private static final String TAG = "KESHAVXOWNEROAuth";
    private static final String AUTH_TAG = "KESHAVXOWNERAuth";
    private static final int REQUEST_AUTH_TAB = 0x5041;
    private static final int REQUEST_EXTERNAL_AUTH = 0x5042;
    private static final long FACEBOOK_FALLBACK_WAIT_MS = 1_500L;
    private static final long FACEBOOK_HANDOFF_SETTLE_MS = 750L;

    private static final String FACEBOOK_CUSTOM_TAB_ACTIVITY =
            "com.facebook.CustomTabActivity";
    private static final String FACEBOOK_CUSTOM_TAB_MAIN_ACTIVITY =
            "com.facebook.CustomTabMainActivity";
    private static final String FACEBOOK_CUSTOM_TAB_REDIRECT_ACTION =
            "CustomTabActivity.action_customTabRedirect";
    private static final String FACEBOOK_CUSTOM_TAB_EXTRA_URL =
            "CustomTabMainActivity.extra_url";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String virtualPackage;
    private Uri expectedRedirectUri;
    private Uri authUri;
    private String authProvider;
    private int userId = -1;
    private int resultBpid = -1;
    private boolean facebookFlow;
    private boolean twitterFlow;
    private boolean legacyTwitterFlow;
    private boolean resultBridgeMode;
    private boolean externalAuthMode;
    private boolean manualResultRelay;
    private boolean facebookResultPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getIntent();
        externalAuthMode = launchIntent != null
                && launchIntent.getBooleanExtra(ExternalAuthRouter.EXTRA_EXTERNAL_AUTH, false);
        resultBridgeMode = launchIntent != null
                && launchIntent.getBooleanExtra(ExternalAuthRouter.EXTRA_BROWSER_AUTH, false);
        manualResultRelay = launchIntent != null
                && launchIntent.getBooleanExtra(
                ExternalAuthRouter.EXTRA_MANUAL_RESULT_RELAY, false);
        resultBpid = launchIntent == null ? -1
                : launchIntent.getIntExtra(ExternalAuthRouter.EXTRA_BPID, -1);

        if (externalAuthMode) {
            virtualPackage = launchIntent.getStringExtra(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            userId = launchIntent.getIntExtra(ExternalAuthRouter.EXTRA_USER_ID, -1);
            if (!validResultTarget(launchIntent)) {
                finish();
                return;
            }
            if (savedInstanceState == null) {
                launchExternalProvider(launchIntent);
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
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
            return;
        }

        expectedRedirectUri = redirectUri;
        facebookFlow = isFacebookHost(authUri);
        twitterFlow = isTwitterHost(authUri);
        legacyTwitterFlow = twitterFlow && hasQueryParameter(authUri, "oauth_token");
        if (!redirectResolvesToVirtualPackage(redirectUri)
                || !AuthTabCompat.isSupportedProvider(this, authProvider, authUri)) {
            diagnostic("setup_rejected", false, false, false, false, false);
            facebookDiagnostic("setup_rejected", RESULT_CANCELED, null, null, false);
            if (resultBridgeMode) {
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
            return;
        }

        if (resultBridgeMode && !validResultTarget(launchIntent)) {
            diagnostic("result_bridge_target_rejected", false, false, false, false, false);
            facebookDiagnostic(
                    "result_bridge_target_rejected", RESULT_CANCELED, null, null, false);
            finish();
            return;
        }

        if (savedInstanceState == null) {
            if (facebookFlow) {
                FacebookOAuthSessionStore.begin(
                        authUri, expectedRedirectUri, virtualPackage, userId);
                facebookDiagnostic("session_started", RESULT_CANCELED, null, null, false);
            }
            launchAuthTab(authUri, lower(expectedRedirectUri.getScheme()), authProvider);
        }
    }

    private void launchExternalProvider(Intent bridgeIntent) {
        try {
            IntentSender providerSender = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT_SENDER);
            if (providerSender != null) {
                if (!ExternalAuthRouter.isTrustedProviderIntentSender(providerSender)) {
                    authDiagnostic("sender_rejected", null, false);
                    completeBridgeResult(RESULT_CANCELED, null);
                    return;
                }

                Intent fillInIntent = bridgeIntent.getParcelableExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FILL_IN_INTENT);
                int flagsMask = bridgeIntent.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_MASK, 0);
                int flagsValues = bridgeIntent.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_VALUES, 0);
                Bundle options = bridgeIntent.getBundleExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_OPTIONS);

                authDiagnostic("sender_launch", providerSender.getCreatorPackage(), false);
                startIntentSenderForResult(
                        providerSender,
                        REQUEST_EXTERNAL_AUTH,
                        fillInIntent,
                        flagsMask,
                        flagsValues,
                        0,
                        options);
                return;
            }

            Intent providerIntent = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
            String providerPackage = ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
            if (providerPackage == null) {
                authDiagnostic("provider_rejected", null, false);
                completeBridgeResult(RESULT_CANCELED, null);
                return;
            }

            providerIntent.setExtrasClassLoader(getClassLoader());
            authDiagnostic("provider_launch", providerPackage, false);
            startActivityForResult(providerIntent, REQUEST_EXTERNAL_AUTH);
        } catch (Throwable error) {
            authDiagnostic("provider_launch_failed", null, false);
            completeBridgeResult(RESULT_CANCELED, null);
        }
    }

    private void launchAuthTab(Uri authUri, String redirectScheme, String provider) {
        try {
            // This is the wire contract produced by AuthTabIntent.Builder().build()
            // followed by launch(..., redirectScheme). Keeping it dependency-free
            // avoids changing the SDK's AndroidX surface while remaining compatible
            // with browsers implementing AndroidX Browser Auth Tab 1.9+.
            Intent authIntent = new Intent(Intent.ACTION_VIEW, authUri);
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
            facebookDiagnostic("launch_failed", RESULT_CANCELED, null, null, false);
            if (facebookFlow) {
                FacebookOAuthSessionStore.clear(virtualPackage, userId);
            }
            if (resultBridgeMode) {
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXTERNAL_AUTH && externalAuthMode) {
            boolean delivered = completeBridgeResult(resultCode, data);
            authDiagnostic("provider_result", providerPackageFromBridge(), delivered);
            return;
        }

        if (requestCode != REQUEST_AUTH_TAB) {
            return;
        }

        // Facebook has an additional exported fbconnect receiver fallback. Handle
        // its result before the generic browser path so a browser-side RESULT_CANCELED
        // cannot race and overwrite a callback already delivered by that receiver.
        if (facebookFlow) {
            handleFacebookAuthResult(resultCode, data);
            return;
        }

        // Keep the established Twitter/X and other-provider result path unchanged.
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            diagnostic("auth_not_completed", false, false, false, false, false);
            if (resultBridgeMode) {
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
            return;
        }

        Uri callbackUri = data.getData();
        if (!matchesExpectedCallback(callbackUri)) {
            diagnostic("callback_mismatch", false, false, false, false, false);
            if (resultBridgeMode) {
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
            return;
        }

        boolean hasToken = hasQueryParameter(callbackUri, "oauth_token");
        boolean hasVerifier = hasQueryParameter(callbackUri, "oauth_verifier");
        boolean hasCode = hasQueryParameter(callbackUri, "code");
        boolean denied = hasQueryParameter(callbackUri, "denied")
                || hasQueryParameter(callbackUri, "error");

        if (legacyTwitterFlow && !denied && (!hasToken || !hasVerifier)) {
            diagnostic("twitter_oauth1_incomplete",
                    hasToken, hasVerifier, hasCode, denied, false);
            if (resultBridgeMode) {
                completeBridgeResult(RESULT_CANCELED, null);
            } else {
                finish();
            }
            return;
        }

        if (resultBridgeMode) {
            boolean delivered = completeBridgeResult(resultCode, data);
            diagnostic(delivered ? "result_bridge_delivered" : "result_bridge_delivery_failed",
                    hasToken, hasVerifier, hasCode, denied, delivered);
            return;
        }

        boolean dispatched = dispatchToVirtualPackage(callbackUri);
        diagnostic(dispatched ? "callback_dispatched" : "callback_unresolved",
                hasToken, hasVerifier, hasCode, denied, dispatched);
        finish();
    }

    private void handleFacebookAuthResult(int resultCode, Intent data) {
        if (facebookResultPending || isFinishing()) {
            return;
        }

        Uri callbackUri = data == null ? null : data.getData();
        // Some Chrome/Auth Tab builds return a valid redirect URI together with
        // RESULT_CANCELED. The validated URI/state/target is authoritative for
        // Facebook only, so accept that shape without weakening other providers.
        if (callbackUri != null && matchesExpectedCallback(callbackUri)) {
            FacebookOAuthSessionStore.Claim claim =
                    FacebookOAuthSessionStore.claim(callbackUri);
            boolean alreadyDelivered = FacebookOAuthSessionStore.isCompleted(
                    virtualPackage, userId);
            boolean delivered = alreadyDelivered;

            if (!delivered && claim != null
                    && virtualPackage.equals(claim.virtualPackage)
                    && userId == claim.userId) {
                delivered = dispatchFacebookCallback(
                        claim.virtualPackage, claim.userId, callbackUri);
                if (delivered) {
                    FacebookOAuthSessionStore.complete(claim.generation);
                } else {
                    FacebookOAuthSessionStore.release(claim.generation);
                }
            }

            facebookDiagnostic(
                    delivered ? "callback_dispatched" : "callback_delivery_failed",
                    resultCode,
                    data,
                    callbackUri,
                    delivered);
            if (delivered) {
                settleFacebookSuccess();
            } else {
                failFacebookResult();
            }
            return;
        }

        if (callbackUri != null) {
            facebookDiagnostic("callback_mismatch", resultCode, data, callbackUri, false);
            failFacebookResult();
            return;
        }

        // AndroidX Auth Tab reports RESULT_CANCELED when the expected redirect was
        // not captured by the browser. Give the host fbconnect receiver a short,
        // bounded window to validate and relay that callback before propagating a
        // cancellation to the virtual Facebook SDK.
        facebookDiagnostic("auth_not_completed", resultCode, data, callbackUri, false);
        facebookResultPending = true;
        mainHandler.postDelayed(() -> {
            facebookResultPending = false;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            boolean delivered = FacebookOAuthSessionStore.isCompleted(
                    virtualPackage, userId);
            facebookDiagnostic(
                    delivered ? "fallback_completed" : "fallback_timeout",
                    resultCode,
                    data,
                    callbackUri,
                    delivered);
            if (delivered) {
                settleFacebookSuccess();
            } else {
                failFacebookResult();
            }
        }, FACEBOOK_FALLBACK_WAIT_MS);
    }

    private void settleFacebookSuccess() {
        facebookResultPending = true;
        mainHandler.postDelayed(() -> {
            facebookResultPending = false;
            FacebookOAuthSessionStore.clear(virtualPackage, userId);
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }, FACEBOOK_HANDOFF_SETTLE_MS);
    }

    private void failFacebookResult() {
        facebookResultPending = false;
        FacebookOAuthSessionStore.clear(virtualPackage, userId);
        if (resultBridgeMode) {
            completeBridgeResult(RESULT_CANCELED, null);
        } else {
            finish();
        }
    }

    /**
     * Normal intercepted startActivityForResult calls retain Android's original
     * resultTo/requestCode on the host bridge. Returning via setResult is therefore
     * the single authoritative path. Detached IntentSender bridges are started
     * with Context.startActivity and must relay explicitly to the guest provider.
     */
    private boolean completeBridgeResult(int resultCode, Intent data) {
        if (!manualResultRelay) {
            try {
                setResult(resultCode, data);
                finish();
                return true;
            } catch (Throwable ignored) {
                finish();
                return false;
            }
        }

        boolean delivered = relayOriginalActivityResult(resultCode, data);
        finish();
        return delivered;
    }

    private boolean validResultTarget(Intent launchIntent) {
        if (launchIntent == null || resultBpid < 0 || resultBpid > 24) {
            return false;
        }
        Bundle extras = launchIntent.getExtras();
        if (extras == null) {
            return false;
        }
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        String targetPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        return resultTo != null
                && requestCode >= 0
                && targetPackage != null
                && !targetPackage.trim().isEmpty();
    }

    private boolean relayOriginalActivityResult(int resultCode, Intent data) {
        try {
            Intent bridgeIntent = getIntent();
            Bundle extras = bridgeIntent == null ? null : bridgeIntent.getExtras();
            if (extras == null || resultBpid < 0 || resultBpid > 24) {
                return false;
            }

            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(
                    ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            String targetPackage = extras.getString(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
            if (resultTo == null || originalRequestCode < 0
                    || targetPackage == null || targetPackage.trim().isEmpty()) {
                return false;
            }

            Bundle relay = new Bundle();
            BundleCompat.putBinder(
                    relay, ExternalAuthRouter.EXTRA_RESULT_BINDER, resultTo);
            relay.putString(ExternalAuthRouter.EXTRA_RESULT_WHO, resultWho);
            relay.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, originalRequestCode);
            relay.putInt(ExternalAuthRouter.EXTRA_RESULT_CODE, resultCode);
            relay.putInt(ExternalAuthRouter.EXTRA_BPID, resultBpid);
            relay.putInt(ExternalAuthRouter.EXTRA_USER_ID, userId);
            relay.putString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, targetPackage);
            if (data != null) {
                relay.putParcelable(
                        ExternalAuthRouter.EXTRA_RESULT_DATA, new Intent(data));
            }

            Bundle response = ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(resultBpid),
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT,
                    null,
                    relay);
            return response != null
                    && response.getBoolean(
                    ExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String providerPackageFromBridge() {
        try {
            Intent bridgeIntent = getIntent();
            if (bridgeIntent == null) return null;
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
        if (isFacebookHost(authUri)
                && dispatchFacebookCallback(virtualPackage, userId, callbackUri)) {
            return true;
        }

        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, callbackUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            callback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback,
                    FileUtils.FileMode.MODE_IWUSR,
                    null,
                    userId);
            if (resolved == null || resolved.activityInfo == null
                    || !virtualPackage.equals(resolved.activityInfo.packageName)) {
                return false;
            }

            callback.setComponent(new ComponentName(
                    resolved.activityInfo.packageName,
                    resolved.activityInfo.name));
            BActivityManager.get().startActivity(callback, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Re-enters Meta's own callback chain. Prefer CustomTabActivity because its
     * implementation is responsible for converting the browser redirect into the
     * CustomTabMainActivity redirect action. If that component is unavailable in
     * an older SDK, reproduce the same stable action/extra directly.
     */
    static boolean dispatchFacebookCallback(
            String targetPackage, int targetUserId, Uri callbackUri) {
        if (callbackUri == null || targetPackage == null
                || targetPackage.trim().isEmpty() || targetUserId < 0) {
            return false;
        }

        if (virtualActivityExists(
                targetPackage, FACEBOOK_CUSTOM_TAB_ACTIVITY, targetUserId)) {
            try {
                Intent callback = new Intent(Intent.ACTION_VIEW, callbackUri);
                callback.setComponent(new ComponentName(
                        targetPackage, FACEBOOK_CUSTOM_TAB_ACTIVITY));
                callback.addCategory(Intent.CATEGORY_DEFAULT);
                callback.addCategory(Intent.CATEGORY_BROWSABLE);
                callback.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                BActivityManager.get().startActivity(callback, targetUserId);
                Log.i(TAG, "facebook stage=custom_tab_activity_handoff delivered=true");
                return true;
            } catch (Throwable ignored) {
                Log.i(TAG, "facebook stage=custom_tab_activity_handoff delivered=false");
            }
        } else {
            Log.i(TAG, "facebook stage=custom_tab_activity_unavailable delivered=false");
        }

        if (!virtualActivityExists(
                targetPackage, FACEBOOK_CUSTOM_TAB_MAIN_ACTIVITY, targetUserId)) {
            Log.i(TAG, "facebook stage=custom_tab_main_unavailable delivered=false");
            return false;
        }

        try {
            Intent callback = new Intent();
            callback.setComponent(new ComponentName(
                    targetPackage, FACEBOOK_CUSTOM_TAB_MAIN_ACTIVITY));
            callback.setAction(FACEBOOK_CUSTOM_TAB_REDIRECT_ACTION);
            callback.putExtra(FACEBOOK_CUSTOM_TAB_EXTRA_URL, callbackUri.toString());
            callback.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            BActivityManager.get().startActivity(callback, targetUserId);
            Log.i(TAG, "facebook stage=custom_tab_main_handoff delivered=true");
            return true;
        } catch (Throwable ignored) {
            Log.i(TAG, "facebook stage=custom_tab_main_handoff delivered=false");
            return false;
        }
    }

    private static boolean virtualActivityExists(
            String packageName, String className, int targetUserId) {
        try {
            ActivityInfo info = BPackageManager.get().getActivityInfo(
                    new ComponentName(packageName, className), 0, targetUserId);
            return info != null
                    && packageName.equals(info.packageName)
                    && className.equals(info.name);
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
                    callback,
                    FileUtils.FileMode.MODE_IWUSR,
                    null,
                    userId);
            return resolved != null
                    && resolved.activityInfo != null
                    && virtualPackage.equals(resolved.activityInfo.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void diagnostic(String stage,
                            boolean hasToken,
                            boolean hasVerifier,
                            boolean hasCode,
                            boolean denied,
                            boolean dispatched) {
        if (!twitterFlow) {
            return;
        }
        Log.i(TAG, "twitter stage=" + stage
                + " oauth1=" + legacyTwitterFlow
                + " token=" + hasToken
                + " verifier=" + hasVerifier
                + " code=" + hasCode
                + " denied=" + denied
                + " dispatched=" + dispatched);
    }

    /** Logs Facebook lifecycle/result shape only; never URL, state, code or token values. */
    private void facebookDiagnostic(String stage,
                                    int resultCode,
                                    Intent data,
                                    Uri callbackUri,
                                    boolean delivered) {
        if (!facebookFlow) {
            return;
        }
        boolean uriPresent = callbackUri != null;
        boolean schemeMatch = uriPresent && expectedRedirectUri != null
                && lower(expectedRedirectUri.getScheme())
                .equals(lower(callbackUri.getScheme()));
        boolean authorityMatch = uriPresent && expectedRedirectUri != null
                && lower(expectedRedirectUri.getEncodedAuthority())
                .equals(lower(callbackUri.getEncodedAuthority()));
        boolean validated = uriPresent
                && OAuthCallbackValidator.matches(authUri, expectedRedirectUri, callbackUri);
        boolean virtualTarget = validated && redirectResolvesToVirtualPackage(callbackUri);
        boolean fallbackCompleted = FacebookOAuthSessionStore.isCompleted(
                virtualPackage, userId);

        Log.i(TAG, "facebook stage=" + stage
                + " result=" + resultCode
                + " data=" + (data != null)
                + " uri=" + uriPresent
                + " scheme_match=" + schemeMatch
                + " authority_match=" + authorityMatch
                + " validated=" + validated
                + " virtual_target=" + virtualTarget
                + " fallback_completed=" + fallbackCompleted
                + " delivered=" + delivered);
    }

    private static void authDiagnostic(String stage, String provider, boolean delivered) {
        String safeProvider = ExternalAuthRouter.isTrustedProviderPackage(provider)
                ? provider : "unknown";
        Log.i(AUTH_TAG, "native stage=" + stage
                + " provider=" + safeProvider
                + " delivered=" + delivered);
    }

    private static boolean hasQueryParameter(Uri uri, String name) {
        if (uri == null || name == null) {
            return false;
        }
        try {
            String value = uri.getQueryParameter(name);
            return value != null && !value.trim().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isFacebookHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = lower(uri.getHost());
        return "facebook.com".equals(host)
                || "fb.com".equals(host)
                || host.endsWith(".facebook.com")
                || host.endsWith(".fb.com");
    }

    private static boolean isTwitterHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = lower(uri.getHost());
        return "twitter.com".equals(host)
                || "x.com".equals(host)
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
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
            String scheme = lower(uri.getScheme());
            if (scheme.isEmpty()
                    || "http".equals(scheme)
                    || "https".equals(scheme)
                    || "file".equals(scheme)
                    || "content".equals(scheme)
                    || "javascript".equals(scheme)
                    || "data".equals(scheme)
                    || "intent".equals(scheme)
                    || !scheme.matches("^[a-z][a-z0-9+.-]{1,127}$")) {
                return null;
            }
            return uri;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
