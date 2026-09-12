package com.Parallax.SDK.core.fake.service;

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

import com.Parallax.SDK.mirror.android.app.ActivityThreadActivityClientRecordContext;
import com.Parallax.SDK.mirror.android.app.BRActivityClient;
import com.Parallax.SDK.mirror.android.app.BRActivityClientActivityClientControllerSingleton;
import com.Parallax.SDK.mirror.android.app.BRActivityManagerNative;
import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadActivityClientRecord;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadCreateServiceData;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadH;
import com.Parallax.SDK.mirror.android.app.BRIActivityManager;
import com.Parallax.SDK.mirror.android.app.servertransaction.BRClientTransaction;
import com.Parallax.SDK.mirror.android.app.servertransaction.BRLaunchActivityItem;
import com.Parallax.SDK.mirror.android.app.servertransaction.LaunchActivityItemContext;
import com.Parallax.SDK.mirror.android.os.BRHandler;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.IParallaxInjectHook;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyActivityRecord;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * ActivityThread callback used to replace host stub activities with their
 * virtual targets before Android executes the launch transaction.
 *
 * Android 16 computes ClientTransaction/LaunchActivityItem hash codes before
 * execution and LaunchActivityItem.hashCode() assumes mIntent is non-null. A
 * null target must therefore never be written into a launch item or an
 * ActivityClientRecord.
 */
public class ParallaxHCallbackStub implements IParallaxInjectHook, Handler.Callback {
    public static final String TAG = "ParallaxHCallbackStub";

    private Handler.Callback mOtherCallback;
    private final AtomicBoolean mBeing = new AtomicBoolean(false);

    private Handler.Callback getHCallback() {
        return BRHandler.get(getH()).mCallback();
    }

    private Handler getH() {
        Object currentActivityThread = ParallaxCore.mainThread();
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
            if (ParallaxBuildCompat.isPie()) {
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
            ParallaxSlog.e(TAG, "Unable to inspect ClientTransaction launch item: " + error);
        }
        return null;
    }

    private Boolean handleLaunchActivity(Object client) {
        if (client == null) {
            return false;
        }

        Object launchItem;
        if (ParallaxBuildCompat.isPie()) {
            launchItem = getLaunchActivityItem(client);
            if (launchItem == null) {
                return false;
            }
        } else {
            launchItem = client;
        }

        Intent stubIntent;
        IBinder token;
        if (ParallaxBuildCompat.isPie()) {
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
            ParallaxSlog.e(TAG, "Ignoring launch transaction with an already-null Intent");
            return false;
        }

        ParallaxProxyActivityRecord stubRecord = ParallaxProxyActivityRecord.create(stubIntent);
        ActivityInfo activityInfo = stubRecord.mActivityInfo;
        if (activityInfo == null) {
            // A real host activity (including our social-auth bridge) is not a
            // virtual stub launch and must pass through untouched.
            return false;
        }

        Intent targetIntent = stubRecord.mTarget;

        if (ParallaxActivityThread.getAppConfig() == null) {
            ParallaxCore.getBActivityManager().restartProcess(
                    activityInfo.packageName,
                    activityInfo.processName,
                    stubRecord.mUserId);

            Intent packageLaunchIntent = ParallaxCore.getBPackageManager()
                    .getLaunchIntentForPackage(activityInfo.packageName, stubRecord.mUserId);

            // A provider round-trip can resume a virtual process while its
            // package launcher is unavailable or not yet resolved. Preserve the
            // original target instead of replacing it with null.
            Intent restartTarget = packageLaunchIntent != null
                    ? packageLaunchIntent : targetIntent;
            if (restartTarget == null) {
                ParallaxSlog.e(TAG, "Process restart has no safe virtual target; keeping host stub intact");
                return false;
            }

            stubIntent.setExtrasClassLoader(getClass().getClassLoader());
            ParallaxProxyActivityRecord.saveStub(
                    stubIntent,
                    restartTarget,
                    stubRecord.mActivityInfo,
                    stubRecord.mActivityRecord,
                    stubRecord.mUserId);
            writeLaunchRecord(launchItem, stubIntent, activityInfo, null);
            return true;
        }

        if (!ParallaxActivityThread.currentActivityThread().isInit()) {
            ParallaxActivityThread.currentActivityThread().bindApplication(
                    activityInfo.packageName, activityInfo.processName);
            return true;
        }

        // This is the critical Android 16 guard. Missing/corrupt proxy metadata
        // must never be converted into LaunchActivityItem.mIntent = null.
        if (targetIntent == null) {
            ParallaxSlog.e(TAG, "Virtual stub is missing target Intent; refusing null launch rewrite");
            return false;
        }

        int taskId = BRIActivityManager.get(BRActivityManagerNative.get().getDefault())
                .getTaskForActivity(token, false);
        ParallaxCore.getBActivityManager().onActivityCreated(
                taskId, token, stubRecord.mActivityRecord);

        if (ParallaxBuildCompat.isS()) {
            Object launchingRecord = BRActivityThread.get(ParallaxCore.mainThread())
                    .getLaunchingActivity(token);

            // Keep both representations consistent. Android 16 transaction
            // diagnostics/hashCode inspect LaunchActivityItem itself even when
            // ActivityThread also has a launching ActivityClientRecord cached.
            writeLaunchRecord(launchItem, targetIntent, activityInfo, launchingRecord);
            checkActivityClient();
        } else if (ParallaxBuildCompat.isPie()) {
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

        if (ParallaxBuildCompat.isPie()) {
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
                    ParallaxActivityThread.currentActivityThread().getPackageInfo());
        }
    }

    private boolean handleCreateService(Object data) {
        if (ParallaxActivityThread.getAppConfig() != null) {
            String appPackageName = ParallaxActivityThread.getAppPackageName();
            if (appPackageName == null || data == null) {
                return false;
            }

            ServiceInfo serviceInfo = BRActivityThreadCreateServiceData.get(data).info();
            if (serviceInfo == null || serviceInfo.name == null) {
                return false;
            }

            if (!serviceInfo.name.equals(ParallaxProxyManifest.getProxyService(ParallaxActivityThread.getAppPid()))
                    && !serviceInfo.name.equals(
                    ParallaxProxyManifest.getProxyJobService(ParallaxActivityThread.getAppPid()))) {
                ParallaxSlog.d(TAG, "handleCreateService: " + data);
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(appPackageName, serviceInfo.name));
                ParallaxCore.getBActivityManager().startService(
                        intent, null, false, ParallaxActivityThread.getUserId());
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
                IParallaxActivityClientProxy proxy = new IParallaxActivityClientProxy(activityClientController);
                proxy.onlyProxy(true);
                proxy.injectHook();
                Object instance = BRActivityClient.get().getInstance();
                Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
                BRActivityClientActivityClientControllerSingleton.get(singleton)
                        ._set_mKnownInstance(proxy.getProxyInvocation());
            }
        } catch (Throwable error) {
            ParallaxSlog.e(TAG, "Unable to refresh ActivityClient hook: " + error);
        }
    }
}
