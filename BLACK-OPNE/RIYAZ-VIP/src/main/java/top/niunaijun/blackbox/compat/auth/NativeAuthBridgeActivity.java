package top.niunaijun.blackbox.compat.auth;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.compat.BundleCompat;
import top.niunaijun.blackbox.utils.provider.ProviderCall;

/** Google-only host trampoline which keeps Play-services extras opaque. */
@Obfuscate
public final class NativeAuthBridgeActivity extends Activity {
    private static final String TAG = "ParallaxGoogleAuth";
    private static final int REQUEST_PROVIDER = 0x5142;

    private int resultBpid = -1;
    private int userId = -1;
    private boolean manualResultRelay;
    private String validatedProviderPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent bridge = getIntent();
        resultBpid = bridge == null ? -1
                : bridge.getIntExtra(ExternalAuthRouter.EXTRA_BPID, -1);
        userId = bridge == null ? -1
                : bridge.getIntExtra(ExternalAuthRouter.EXTRA_USER_ID, -1);
        manualResultRelay = bridge != null && bridge.getBooleanExtra(
                ExternalAuthRouter.EXTRA_MANUAL_RESULT_RELAY, false);
        validatedProviderPackage = bridge == null ? null
                : bridge.getStringExtra(ExternalAuthRouter.EXTRA_VALIDATED_PROVIDER_PACKAGE);

        if (bridge == null
                || !bridge.getBooleanExtra(ExternalAuthRouter.EXTRA_EXTERNAL_AUTH, false)
                || !validResultTarget(bridge)) {
            finish();
            return;
        }
        if (savedInstanceState == null) launchProvider(bridge);
    }

    private void launchProvider(Intent bridge) {
        try {
            IntentSender sender = bridge.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT_SENDER);
            if (sender != null) {
                boolean internalOpaque = bridge.getBooleanExtra(
                        ExternalAuthRouter.EXTRA_INTERNAL_OPAQUE_PROVIDER_SENDER, false);
                String creator = safeCreatorPackage(sender);
                if (internalOpaque) {
                    if (!isGoogleProvider(validatedProviderPackage)
                            || !BlackBoxCore.getHostPkg().equals(creator)) {
                        diagnostic("opaque_sender_rejected", false);
                        complete(RESULT_CANCELED, null);
                        return;
                    }
                } else if (!ExternalAuthRouter.isTrustedProviderIntentSender(sender)) {
                    diagnostic("provider_sender_rejected", false);
                    complete(RESULT_CANCELED, null);
                    return;
                } else {
                    validatedProviderPackage = creator;
                }

                Intent fillIn = bridge.getParcelableExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FILL_IN_INTENT);
                int flagsMask = bridge.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_MASK, 0);
                int flagsValues = bridge.getIntExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_FLAGS_VALUES, 0);
                Bundle options = bridge.getBundleExtra(
                        ExternalAuthRouter.EXTRA_PROVIDER_OPTIONS);
                diagnostic("opaque_sender_launch", false);
                startIntentSenderForResult(sender, REQUEST_PROVIDER, fillIn,
                        flagsMask, flagsValues, 0, options);
                return;
            }

            // Fallback for devices where PendingIntent creation is unavailable.
            Intent providerIntent = bridge.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
            String provider = ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
            if (!isGoogleProvider(provider)) {
                diagnostic("provider_intent_rejected", false);
                complete(RESULT_CANCELED, null);
                return;
            }
            validatedProviderPackage = provider;
            diagnostic("provider_intent_fallback_launch", false);
            startActivityForResult(providerIntent, REQUEST_PROVIDER);
        } catch (Throwable ignored) {
            diagnostic("provider_launch_failed", false);
            complete(RESULT_CANCELED, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROVIDER) return;
        boolean delivered = complete(resultCode, data);
        diagnostic("provider_result", delivered);
    }

    private boolean complete(int resultCode, Intent data) {
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

    private boolean validResultTarget(Intent bridge) {
        if (resultBpid < 0 || resultBpid > 24 || userId < 0) return false;
        Bundle extras = bridge.getExtras();
        if (extras == null) return false;
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        String targetPackage = extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        return resultTo != null && requestCode >= 0
                && targetPackage != null && !targetPackage.trim().isEmpty();
    }

    private boolean relayOriginalActivityResult(int resultCode, Intent data) {
        try {
            Intent bridge = getIntent();
            Bundle extras = bridge == null ? null : bridge.getExtras();
            if (extras == null || resultBpid < 0 || resultBpid > 24) return false;
            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(
                    ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            String targetPackage = extras.getString(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
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
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT, null, relay);
            return response != null && response.getBoolean(
                    ExternalAuthRouter.EXTRA_RESULT_DELIVERED, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGoogleProvider(String provider) {
        return "com.google.android.gms".equals(provider)
                || "com.google.android.play.games".equals(provider);
    }

    private static String safeCreatorPackage(IntentSender sender) {
        try {
            return sender == null ? null : sender.getCreatorPackage();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void diagnostic(String stage, boolean delivered) {
        String safe = isGoogleProvider(validatedProviderPackage)
                ? validatedProviderPackage : "unknown";
        Log.i(TAG, "stage=" + stage + " provider=" + safe + " delivered=" + delivered);
    }
}
