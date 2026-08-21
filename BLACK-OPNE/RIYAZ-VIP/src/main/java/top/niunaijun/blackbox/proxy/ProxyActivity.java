package top.niunaijun.blackbox.proxy;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
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
    private static final int REQUEST_EXTERNAL_AUTH = 0x5042;

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

    private void launchExternalProvider(Intent bridgeIntent) {
        try {
            IntentSender providerSender = bridgeIntent.getParcelableExtra(
                    ExternalAuthRouter.EXTRA_PROVIDER_INTENT_SENDER);
            if (providerSender != null) {
                if (!ExternalAuthRouter.isTrustedProviderIntentSender(providerSender)) {
                    deliverExternalAuthResult(RESULT_CANCELED, null);
                    finish();
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
            if (!ExternalAuthRouter.isTrustedProviderIntent(providerIntent)) {
                deliverExternalAuthResult(RESULT_CANCELED, null);
                finish();
                return;
            }
            providerIntent.setExtrasClassLoader(getClassLoader());
            startActivityForResult(providerIntent, REQUEST_EXTERNAL_AUTH);
        } catch (Throwable ignored) {
            deliverExternalAuthResult(RESULT_CANCELED, null);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXTERNAL_AUTH || !isExternalAuthBridge(getIntent())) {
            return;
        }
        deliverExternalAuthResult(resultCode, data);
        finish();
    }

    private void deliverExternalAuthResult(int resultCode, Intent data) {
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
            // so BActivityManager can deliver the provider-controlled result to
            // the original virtual Activity token. Result contents are untouched.
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
