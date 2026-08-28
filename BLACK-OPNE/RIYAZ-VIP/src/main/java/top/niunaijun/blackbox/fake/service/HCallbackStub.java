package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import androidx.annotation.NonNull;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import black.android.app.ActivityThreadActivityClientRecordContext;
import black.android.app.BRActivityClient;
import black.android.app.BRActivityClientActivityClientControllerSingleton;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import black.android.app.BRActivityThreadCreateServiceData;
import black.android.app.BRActivityThreadH;
import black.android.app.BRIActivityManager;
import black.android.app.servertransaction.BRClientTransaction;
import black.android.app.servertransaction.BRLaunchActivityItem;
import black.android.app.servertransaction.LaunchActivityItemContext;
import black.android.os.BRHandler;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * ActivityThread callback used to replace host stub activities with their
 * virtual targets before Android executes the launch transaction.
 *
 * Android 16 computes ClientTransaction/LaunchActivityItem hash codes before
 * execution and LaunchActivityItem.hashCode() assumes mIntent is non-null. A
 * null target must therefore never be written into a launch item or an
 * ActivityClientRecord.
 */
public class HCallbackStub implements IInjectHook, Handler.Callback {
    public static final String TAG = "HCallbackStub";

    private Handler.Callback mOtherCallback;
    private final AtomicBoolean mBeing = new AtomicBoolean(false);

    private Handler.Callback getHCallback() {
        return BRHandler.get(getH()).mCallback();
    }

    private Handler getH() {
        Object currentActivityThread = BlackBoxCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mH();
    }

    @Override
    public void injectHook() {
        mOtherCallback = getHCallback();
        if (mOtherCallback != null
                && (mOtherCallback == this
                || mOtherCallback.getClass().getName().equals(getClass().getName()))) {
            mOtherCallback = null;
        }
        BRHandler.get(getH())._set_mCallback(this);
    }

    @Override
    public boolean isBadEnv() {
        Handler.Callback hCallback = getHCallback();
        return hCallback != null && hCallback != this;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (mBeing.getAndSet(true)) {
            return false;
        }

        try {
            if (BuildCompat.isPie()) {
                if (msg.what == BRActivityThreadH.get().EXECUTE_TRANSACTION()
                        && handleLaunchActivity(msg.obj)) {
                    getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                    return true;
                }
            } else if (msg.what == BRActivityThreadH.get().LAUNCH_ACTIVITY()
                    && handleLaunchActivity(msg.obj)) {
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return true;
            }

            if (msg.what == BRActivityThreadH.get().CREATE_SERVICE()) {
                return handleCreateService(msg.obj);
            }
            if (mOtherCallback != null) {
                return mOtherCallback.handleMessage(msg);
            }
            return false;
        } finally {
            mBeing.set(false);
        }
    }

    private Object getLaunchActivityItem(Object clientTransaction) {
        if (clientTransaction == null) {
            return null;
        }

        try {
            List<Object> callbacks = BRClientTransaction.get(clientTransaction).mActivityCallbacks();
            if (callbacks == null) {
                return null;
            }

            Class<?> launchClass = BRLaunchActivityItem.getRealClass();
            for (Object item : callbacks) {
                if (item == null) {
                    continue;
                }
                if (launchClass.isInstance(item)
                        || launchClass.getName().equals(item.getClass().getName())) {
                    return item;
                }
            }
        } catch (Throwable error) {
            Slog.e(TAG, "Unable to inspect ClientTransaction launch item: " + error);
        }
        return null;
    }

    private Boolean handleLaunchActivity(Object client) {
        if (client == null) {
            return false;
        }

        Object launchItem;
        if (BuildCompat.isPie()) {
            launchItem = getLaunchActivityItem(client);
            if (launchItem == null) {
                return false;
            }
        } else {
            launchItem = client;
        }

        Intent stubIntent;
        IBinder token;
        if (BuildCompat.isPie()) {
            stubIntent = BRLaunchActivityItem.get(launchItem).mIntent();
            token = BRClientTransaction.get(client).mActivityToken();
        } else {
            ActivityThreadActivityClientRecordContext recordContext =
                    BRActivityThreadActivityClientRecord.get(launchItem);
            stubIntent = recordContext.intent();
            token = recordContext.token();
        }

        // Never hand Android a transaction item whose intent we made null. On
        // Android 16 LaunchActivityItem.hashCode() dereferences mIntent before
        // execute(), so returning after a null write would crash in framework.
        if (stubIntent == null) {
            Slog.e(TAG, "Ignoring launch transaction with an already-null Intent");
            return false;
        }

        ProxyActivityRecord stubRecord = ProxyActivityRecord.create(stubIntent);
        ActivityInfo activityInfo = stubRecord.mActivityInfo;
        if (activityInfo == null) {
            // A real host activity (including our social-auth bridge) is not a
            // virtual stub launch and must pass through untouched.
            return false;
        }

        Intent targetIntent = stubRecord.mTarget;

        if (BActivityThread.getAppConfig() == null) {
            BlackBoxCore.getBActivityManager().restartProcess(
                    activityInfo.packageName,
                    activityInfo.processName,
                    stubRecord.mUserId);

            Intent packageLaunchIntent = BlackBoxCore.getBPackageManager()
                    .getLaunchIntentForPackage(activityInfo.packageName, stubRecord.mUserId);

            // A provider round-trip can resume a virtual process while its
            // package launcher is unavailable or not yet resolved. Preserve the
            // original target instead of replacing it with null.
            Intent restartTarget = packageLaunchIntent != null
                    ? packageLaunchIntent : targetIntent;
            if (restartTarget == null) {
                Slog.e(TAG, "Process restart has no safe virtual target; keeping host stub intact");
                return false;
            }

            stubIntent.setExtrasClassLoader(getClass().getClassLoader());
            ProxyActivityRecord.saveStub(
                    stubIntent,
                    restartTarget,
                    stubRecord.mActivityInfo,
                    stubRecord.mActivityRecord,
                    stubRecord.mUserId);
            writeLaunchRecord(launchItem, stubIntent, activityInfo, null);
            return true;
        }

        if (!BActivityThread.currentActivityThread().isInit()) {
            BActivityThread.currentActivityThread().bindApplication(
                    activityInfo.packageName, activityInfo.processName);
            return true;
        }

        // This is the critical Android 16 guard. Missing/corrupt proxy metadata
        // must never be converted into LaunchActivityItem.mIntent = null.
        if (targetIntent == null) {
            Slog.e(TAG, "Virtual stub is missing target Intent; refusing null launch rewrite");
            return false;
        }

        int taskId = BRIActivityManager.get(BRActivityManagerNative.get().getDefault())
                .getTaskForActivity(token, false);
        BlackBoxCore.getBActivityManager().onActivityCreated(
                taskId, token, stubRecord.mActivityRecord);

        if (BuildCompat.isS()) {
            Object launchingRecord = BRActivityThread.get(BlackBoxCore.mainThread())
                    .getLaunchingActivity(token);

            // Keep both representations consistent. Android 16 transaction
            // diagnostics/hashCode inspect LaunchActivityItem itself even when
            // ActivityThread also has a launching ActivityClientRecord cached.
            writeLaunchRecord(launchItem, targetIntent, activityInfo, launchingRecord);
            checkActivityClient();
        } else if (BuildCompat.isPie()) {
            writeLaunchRecord(launchItem, targetIntent, activityInfo, null);
        } else {
            ActivityThreadActivityClientRecordContext recordContext =
                    BRActivityThreadActivityClientRecord.get(launchItem);
            recordContext._set_intent(targetIntent);
            recordContext._set_activityInfo(activityInfo);
        }
        return false;
    }

    private void writeLaunchRecord(
            Object launchItem,
            Intent targetIntent,
            ActivityInfo activityInfo,
            Object launchingRecord) {
        if (launchItem == null || targetIntent == null || activityInfo == null) {
            return;
        }

        if (BuildCompat.isPie()) {
            LaunchActivityItemContext launchContext = BRLaunchActivityItem.get(launchItem);
            launchContext._set_mIntent(targetIntent);
            launchContext._set_mInfo(activityInfo);
        } else {
            // API 24-27 still launch through ActivityClientRecord directly.
            ActivityThreadActivityClientRecordContext recordContext =
                    BRActivityThreadActivityClientRecord.get(launchItem);
            recordContext._set_intent(targetIntent);
            recordContext._set_activityInfo(activityInfo);
        }

        if (launchingRecord != null) {
            ActivityThreadActivityClientRecordContext recordContext =
                    BRActivityThreadActivityClientRecord.get(launchingRecord);
            recordContext._set_intent(targetIntent);
            recordContext._set_activityInfo(activityInfo);
            recordContext._set_packageInfo(
                    BActivityThread.currentActivityThread().getPackageInfo());
        }
    }

    private boolean handleCreateService(Object data) {
        if (BActivityThread.getAppConfig() != null) {
            String appPackageName = BActivityThread.getAppPackageName();
            if (appPackageName == null || data == null) {
                return false;
            }

            ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
            if (serviceInfo == null || serviceInfo.name == null) {
                return false;
            }

            if (!serviceInfo.name.equals(ProxyManifest.getProxyService(BActivityThread.getAppPid()))
                    && !serviceInfo.name.equals(
                    ProxyManifest.getProxyJobService(BActivityThread.getAppPid()))) {
                Slog.d(TAG, "handleCreateService: " + data);
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
                BlackBoxCore.getBActivityManager().startService(
                        intent, null, false, BActivityThread.getUserId());
                return true;
            }
        }
        return false;
    }

    private void checkActivityClient() {
        try {
            Object activityClientController =
                    BRActivityClient.get().getActivityClientController();
            if (!(activityClientController instanceof Proxy)) {
                IActivityClientProxy proxy = new IActivityClientProxy(activityClientController);
                proxy.onlyProxy(true);
                proxy.injectHook();
                Object instance = BRActivityClient.get().getInstance();
                Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
                BRActivityClientActivityClientControllerSingleton.get(singleton)
                        ._set_mKnownInstance(proxy.getProxyInvocation());
            }
        } catch (Throwable error) {
            Slog.e(TAG, "Unable to refresh ActivityClient hook: " + error);
        }
    }
}
