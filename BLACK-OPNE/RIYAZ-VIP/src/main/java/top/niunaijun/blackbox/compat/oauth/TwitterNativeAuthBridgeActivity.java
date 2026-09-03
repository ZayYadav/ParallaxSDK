package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.lsposed.lsparanoid.Obfuscate;

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
 */
@Obfuscate
public final class TwitterNativeAuthBridgeActivity extends Activity {
    private static final int REQUEST_TWITTER_APP = 0x5854;
    private static final long CALLBACK_SETTLE_MS = 1_800L;

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
                providerIntent = new Intent(providerIntent);
                providerIntent.setComponent(null);
                providerIntent.setPackage(providerPackage);
                providerIntent.putExtra(
                        ExternalAuthRouter.EXTRA_DIRECT_PROVIDER_DISPATCH, true);
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
