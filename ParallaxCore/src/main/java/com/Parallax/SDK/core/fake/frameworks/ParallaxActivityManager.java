package com.Parallax.SDK.core.fake.frameworks;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.Parallax.SDK.mirror.android.app.ActivityThread;
import com.Parallax.SDK.mirror.android.app.ActivityThreadContext;
import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadActivityClientRecord;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.am.IParallaxActivityManagerService;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.entity.ParallaxUnbindRecord;
import com.Parallax.SDK.core.entity.am.ParallaxPendingResultData;
import com.Parallax.SDK.core.entity.am.ParallaxRunningAppProcessInfo;
import com.Parallax.SDK.core.entity.am.ParallaxRunningServiceInfo;

/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxActivityManager extends ParallaxBlackManager<IParallaxActivityManagerService> {
    private static final ParallaxActivityManager sActivityManager = new ParallaxActivityManager();

    public static ParallaxActivityManager get() {
        return sActivityManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.ACTIVITY_MANAGER;
    }

    public ParallaxAppConfig initProcess(String packageName, String processName, int userId) {
        try {
            return getService().initProcess(packageName, processName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void restartProcess(String packageName, String processName, int userId) {
        try {
            getService().restartProcess(packageName, processName, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void startActivity(Intent intent, int userId) {
        try {
            getService().startActivity(intent, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int startActivityAms(int userId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options) {
        try {
            return getService().startActivityAms(userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public int startActivities(int userId, Intent[] intent, String[] resolvedType, IBinder resultTo, Bundle options) {
        try {
            return getService().startActivities(userId, intent, resolvedType, resultTo, options);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public ComponentName startService(Intent intent, String resolvedType, boolean requireForeground, int userId) {
        try {
            return getService().startService(intent, resolvedType, requireForeground, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int stopService(Intent intent, String resolvedType, int userId) {
        try {
            return getService().stopService(intent, resolvedType, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public Intent bindService(Intent service, IBinder binder, String resolvedType, int userId) {
        try {
            return getService().bindService(service, binder, resolvedType, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void unbindService(IBinder binder, int userId) {
        try {
            getService().unbindService(binder, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void stopServiceToken(ComponentName componentName, IBinder token, int userId) {
        try {
            getService().stopServiceToken(componentName, token, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onStartCommand(Intent proxyIntent, int userId) {
        try {
            getService().onStartCommand(proxyIntent, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public ParallaxUnbindRecord onServiceUnbind(Intent proxyIntent, int userId) {
        try {
            return getService().onServiceUnbind(proxyIntent, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int checkPermission(String permission, int pid, int uid, String packageName) {
        try {
            return getService().checkPermission(permission, pid, uid, packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void onServiceDestroy(Intent proxyIntent, int userId) {
        try {
            getService().onServiceDestroy(proxyIntent, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public Activity findActivityByToken(IBinder token) {
        if (token == null) {
            return null;
        }
        try {
            Object r = BRActivityThread.get(ParallaxActivityThread.currentActivityThread())
                    .mActivities().get(token);
            if (r != null) {
                // BRActivityThreadActivityClientRecord wraps the ActivityClientRecord,
                // not the IBinder key used to find it.
                return BRActivityThreadActivityClientRecord.get(r).activity();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void sendCancelActivityResult(IBinder resultTo, String resultWho, int requestCode) {
        sendActivityResult(resultTo, resultWho, requestCode, null, 0);
    }

    public void sendActivityResult(IBinder resultTo, String resultWho, int requestCode, Intent data, int resultCode) {
        if (resultTo == null || requestCode < 0) {
            return;
        }

        try {
            // Android 16 ActivityThread.sendActivityResult() schedules an
            // ActivityResultItem transaction for the supplied token. Do not gate
            // that scheduling on a brittle local Activity lookup: the target may
            // be transitioning back from Google/Facebook/X when the result arrives.
            Object mainThread = ParallaxActivityThread.currentActivityThread();
            BRActivityThread.get(mainThread).sendActivityResult(
                    resultTo, resultWho, requestCode, resultCode, data);
        } catch (Throwable ignored) {
            // Provider result contents are intentionally not logged.
        }
    }

    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
        try {
            return getService().acquireContentProviderClient(providerInfo);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Intent sendBroadcast(Intent intent, String resolvedType, int userId) {
        try {
            return getService().sendBroadcast(intent, resolvedType, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public IBinder peekService(Intent intent, String resolvedType, int userId) {
        try {
            return getService().peekService(intent, resolvedType, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void onActivityCreated(int taskId, IBinder token, IBinder activityRecord) {
        try {
            getService().onActivityCreated(taskId, token, activityRecord);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onActivityResumed(IBinder token) {
        try {
            // Fix https://github.com/FBlackBox/BlackBox/issues/28
            if ("com.tencent.mm".equals(ParallaxActivityThread.getAppPackageName())) {
                Activity activityByToken = ParallaxActivityThread.getActivityByToken(token);
                if (activityByToken != null) {
                    activityByToken.getWindow().getDecorView().clearFocus();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            getService().onActivityResumed(token);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onActivityDestroyed(IBinder token) {
        try {
            getService().onActivityDestroyed(token);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onFinishActivity(IBinder token) {
        try {
            getService().onFinishActivity(token);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public ParallaxRunningAppProcessInfo getRunningAppProcesses(String callerPackage, int userId) throws RemoteException {
        try {
            return getService().getRunningAppProcesses(callerPackage, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ParallaxRunningServiceInfo getRunningServices(String callerPackage, int userId) throws RemoteException {
        try {
            return getService().getRunningServices(callerPackage, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void scheduleBroadcastReceiver(Intent intent, ParallaxPendingResultData pendingResultData, int userId) throws RemoteException {
        getService().scheduleBroadcastReceiver(intent, pendingResultData, userId);
    }

    public void finishBroadcast(ParallaxPendingResultData data) {
        try {
            getService().finishBroadcast(data);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        try {
            return getService().getCallingPackage(token, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        try {
            return getService().getCallingActivity(token, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void getIntentSender(IBinder target, String packageName, int uid) {
        try {
            getService().getIntentSender(target, packageName, uid, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public String getPackageForIntentSender(IBinder target) {
        try {
            return getService().getPackageForIntentSender(target, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getUidForIntentSender(IBinder target) {
        try {
            return getService().getUidForIntentSender(target, ParallaxActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
