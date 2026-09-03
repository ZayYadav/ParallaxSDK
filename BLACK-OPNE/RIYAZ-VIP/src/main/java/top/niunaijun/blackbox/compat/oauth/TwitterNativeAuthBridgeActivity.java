package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.provider.ProviderCall;

/**
 * Host-main trampoline dedicated to real Twitter/X application OAuth.
 *
 * <p>The original guest Activity result target is retained in the bridge extras,
 * while the provider-owned Activity is launched by Android outside the virtual
 * process. A validated custom-scheme callback is captured by
 * {@link TwitterOAuthCallbackActivity} and relayed to that original guest target.
 * If a provider build returns the callback directly as an Activity result, this
 * bridge accepts the same validated URI without waiting for the exported callback
 * Activity.</p>
 *
 * <p>For the current official X package, a genuine OAuth2 PKCE authorization URL
 * using {@code /i/oauth2/authorize} is dispatched to the manifest-verified
 * {@code com.x.android.deeplink.XUrlInterpreterActivity}. This exact component
 * was verified on-device to accept X authorization links. URL-less legacy Twitter
 * Kit SSO probes are intentionally never rewritten here.</p>
 */
@Obfuscate
public final class TwitterNativeAuthBridgeActivity extends Activity {
    private static final String TAG = "TwitterNativeAuth";
    private static final int REQUEST_TWITTER_APP = 0x5854;
    private static final long CALLBACK_SETTLE_MS = 1_800L;

    private static final String OFFICIAL_TWITTER_PACKAGE = "com.twitter.android";
    private static final String X_URL_INTERPRETER_ACTIVITY =
            "com.x.android.deeplink.XUrlInterpreterActivity";
    private static final String X_OAUTH2_AUTHORIZE_PATH = "/i/oauth2/authorize";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String virtualPackage;
    private int userId = -1;
    private long generation = -1L;
    private boolean providerLaunched;
    private boolean completionPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent bridge = getIntent();
        Intent providerIntent = bridge == null ? null
                : bridge.getParcelableExtra(ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
        String providerPackage = ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
        String authUrl = bridge == null ? null
                : bridge.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL);
        String redirectValue = bridge == null ? null
                : bridge.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI);
        virtualPackage = bridge == null ? null
                : bridge.getStringExtra(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        userId = bridge == null ? -1
                : bridge.getIntExtra(ExternalAuthRouter.EXTRA_USER_ID, -1);

        Uri authUri = safeUri(authUrl);
        Uri expectedRedirect = safeUri(redirectValue);
        if (!validResultTarget(bridge)
                || !ExternalAuthRouter.isTwitterProviderPackage(providerPackage)
                || !ExternalAuthRouter.isTrustedTwitterOAuthUri(authUri)
                || !TwitterOAuthSessionStore.isHostCaptureSupported(expectedRedirect)
                || !redirectResolvesToVirtualPackage(expectedRedirect)
                || providerIntent == null) {
            relayCancellation();
            finish();
            return;
        }

        if (savedInstanceState == null) {
            generation = TwitterOAuthSessionStore.begin(
                    bridge, authUri, expectedRedirect, providerPackage);
            if (generation < 0L) {
                relayCancellation();
                finish();
                return;
            }

            try {
                providerIntent = prepareProviderIntent(
                        providerIntent, providerPackage, authUri);
                providerLaunched = true;
                startActivityForResult(providerIntent, REQUEST_TWITTER_APP);
            } catch (Throwable ignored) {
                TwitterOAuthSessionStore.clear(virtualPackage, userId);
                relayCancellation();
                finish();
            }
        } else {
            providerLaunched = true;
        }
    }

    /**
     * Keep the normal package-only provider route for legacy/alternate builds,
     * but explicitly target the current X deep-link interpreter when Android
     * confirms that the exact component is enabled/exported and accepts this
     * genuine OAuth2 authorization URI.
     */
    private Intent prepareProviderIntent(
            Intent original,
            String providerPackage,
            Uri authUri) {
        Intent prepared = new Intent(original);
        prepared.putExtra(ExternalAuthRouter.EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        if (OFFICIAL_TWITTER_PACKAGE.equals(providerPackage)
                && isModernXOAuth2AuthorizeUri(authUri)) {
            ComponentName exact = new ComponentName(
                    OFFICIAL_TWITTER_PACKAGE, X_URL_INTERPRETER_ACTIVITY);
            if (isLaunchableExactXAuthorizeComponent(exact, authUri)) {
                prepared.setAction(Intent.ACTION_VIEW);
                prepared.setData(authUri);
                prepared.addCategory(Intent.CATEGORY_DEFAULT);
                prepared.addCategory(Intent.CATEGORY_BROWSABLE);
                prepared.setComponent(exact);
                Log.w(TAG, "dispatching OAuth2 authorize URL to verified XUrlInterpreterActivity");
                return prepared;
            }
            Log.w(TAG, "verified XUrlInterpreterActivity unavailable; using package resolver");
        }

        prepared.setComponent(null);
        prepared.setPackage(providerPackage);
        return prepared;
    }

    private boolean isLaunchableExactXAuthorizeComponent(
            ComponentName component,
            Uri authUri) {
        try {
            if (component == null || authUri == null || BlackBoxCore.getContext() == null) {
                return false;
            }
            PackageManager pm = BlackBoxCore.getContext().getPackageManager();
            ActivityInfo info = pm.getActivityInfo(component, 0);
            if (info == null
                    || !OFFICIAL_TWITTER_PACKAGE.equals(info.packageName)
                    || !X_URL_INTERPRETER_ACTIVITY.equals(info.name)
                    || !info.enabled
                    || !info.exported
                    || (info.applicationInfo != null && !info.applicationInfo.enabled)) {
                return false;
            }
            String permission = info.permission;
            if (permission != null && !permission.trim().isEmpty()
                    && pm.checkPermission(permission, getPackageName())
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            Intent probe = new Intent(Intent.ACTION_VIEW, authUri);
            probe.addCategory(Intent.CATEGORY_DEFAULT);
            probe.addCategory(Intent.CATEGORY_BROWSABLE);
            probe.setComponent(component);
            ResolveInfo resolved = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null
                    && resolved.activityInfo != null
                    && OFFICIAL_TWITTER_PACKAGE.equals(resolved.activityInfo.packageName)
                    && X_URL_INTERPRETER_ACTIVITY.equals(resolved.activityInfo.name)
                    && resolved.activityInfo.enabled
                    && resolved.activityInfo.exported;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isModernXOAuth2AuthorizeUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        host = host == null ? "" : host.toLowerCase(java.util.Locale.US);
        if (!("x.com".equals(host)
                || "www.x.com".equals(host)
                || "mobile.x.com".equals(host)
                || "twitter.com".equals(host)
                || "www.twitter.com".equals(host)
                || "mobile.twitter.com".equals(host))) {
            return false;
        }
        String path = uri.getPath();
        return path != null && X_OAUTH2_AUTHORIZE_PATH.equalsIgnoreCase(path);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!providerLaunched || completionPending || virtualPackage == null || userId < 0) {
            return;
        }
        if (TwitterOAuthSessionStore.isCompleted(virtualPackage, userId)) {
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TWITTER_APP || completionPending) {
            return;
        }

        Uri callback = data == null ? null : data.getData();
        if (callback != null) {
            TwitterOAuthSessionStore.Claim claim = TwitterOAuthSessionStore.claim(callback);
            if (claim != null
                    && virtualPackage != null
                    && virtualPackage.equals(claim.virtualPackage)
                    && userId == claim.userId) {
                Intent result = new Intent(data);
                boolean delivered = TwitterOAuthSessionStore.deliver(
                        claim, RESULT_OK, result);
                if (delivered) {
                    TwitterOAuthSessionStore.complete(claim.generation);
                    TwitterOAuthSessionStore.clear(virtualPackage, userId);
                    finish();
                    return;
                }
                TwitterOAuthSessionStore.release(claim.generation);
            }
        }

        // Several Twitter/X builds finish or cancel their provider Activity just
        // before Android dispatches the custom-scheme callback. Give that callback
        // a short bounded window instead of prematurely returning Authorize failed.
        completionPending = true;
        mainHandler.postDelayed(() -> {
            completionPending = false;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (TwitterOAuthSessionStore.isCompleted(virtualPackage, userId)) {
                TwitterOAuthSessionStore.clear(virtualPackage, userId);
                finish();
                return;
            }
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            relayCancellation();
            finish();
        }, CALLBACK_SETTLE_MS);
    }

    private boolean validResultTarget(Intent bridge) {
        if (bridge == null) {
            return false;
        }
        Bundle extras = bridge.getExtras();
        if (extras == null) {
            return false;
        }
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
        return resultTo != null
                && requestCode >= 0
                && bpid >= 0 && bpid <= 24
                && userId >= 0
                && virtualPackage != null
                && !virtualPackage.trim().isEmpty();
    }

    private boolean redirectResolvesToVirtualPackage(Uri redirectUri) {
        if (redirectUri == null || virtualPackage == null || userId < 0) {
            return false;
        }
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

    private void relayCancellation() {
        try {
            Intent bridge = getIntent();
            Bundle extras = bridge == null ? null : bridge.getExtras();
            if (extras == null) {
                return;
            }
            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(
                    ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
            if (resultTo == null || originalRequestCode < 0 || bpid < 0 || bpid > 24
                    || virtualPackage == null || virtualPackage.trim().isEmpty()) {
                return;
            }

            Bundle relay = new Bundle();
            BundleCompat.putBinder(
                    relay, ExternalAuthRouter.EXTRA_RESULT_BINDER, resultTo);
            relay.putString(ExternalAuthRouter.EXTRA_RESULT_WHO, resultWho);
            relay.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, originalRequestCode);
            relay.putInt(ExternalAuthRouter.EXTRA_RESULT_CODE, RESULT_CANCELED);
            relay.putInt(ExternalAuthRouter.EXTRA_BPID, bpid);
            relay.putInt(ExternalAuthRouter.EXTRA_USER_ID, userId);
            relay.putString(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, virtualPackage);
            ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(bpid),
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT,
                    null,
                    relay);
        } catch (Throwable ignored) {
        }
    }

    private static Uri safeUri(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
