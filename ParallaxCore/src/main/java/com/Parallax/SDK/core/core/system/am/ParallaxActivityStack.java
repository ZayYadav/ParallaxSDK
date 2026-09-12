package com.Parallax.SDK.core.core.system.am;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.Parallax.SDK.mirror.android.app.BRActivityManagerNative;
import com.Parallax.SDK.mirror.android.app.BRIActivityManager;
import com.Parallax.SDK.mirror.com.android.internal.BRRstyleable;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.ParallaxProcessManagerService;
import com.Parallax.SDK.core.core.system.ParallaxProcessRecord;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerCompat;
import com.Parallax.SDK.core.proxy.ParallaxProxyActivity;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyActivityRecord;
import com.Parallax.SDK.core.utils.ParallaxArrayUtils;
import com.Parallax.SDK.core.utils.ParallaxComponentUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;

import static android.content.pm.PackageManager.GET_ACTIVITIES;
import com.Parallax.SDK.core.utils.compat.ParallaxActivityManagerCompat;

/**
 * Created by @RIYAZXERO on 4/5/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxActivityStack {
    public static final String TAG = "ParallaxActivityStack";

    private final ActivityManager mAms;
    private final Map<Integer, ParallaxTaskRecord> mTasks = new LinkedHashMap<>();
    private final Set<ParallaxActivityRecord> mLaunchingActivities = new HashSet<>();

    public static final int LAUNCH_TIME_OUT = 0;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_TIME_OUT:
                    ParallaxActivityRecord record = (ParallaxActivityRecord) msg.obj;
                    if (record != null) {
                        mLaunchingActivities.remove(record);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public ParallaxActivityStack() {
        mAms = (ActivityManager) ParallaxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
    }

    public boolean containsFlag(Intent intent, int flag) {
        return (intent.getFlags() & flag) != 0;
    }

    public int startActivitiesLocked(int userId, Intent[] intents, String[] resolvedTypes, IBinder resultTo, Bundle options) {
        if (intents == null) {
            throw new NullPointerException("intents is null");
        }
        if (resolvedTypes == null) {
            throw new NullPointerException("resolvedTypes is null");
        }
        if (intents.length != resolvedTypes.length) {
            throw new IllegalArgumentException("intents are length different than resolvedTypes");
        }
        for (int i = 0; i < intents.length; i++) {
            startActivityLocked(userId, intents[i], resolvedTypes[i], resultTo, null, -1, 0, options);
        }
        return 0;
    }

    public int startActivityLocked(int userId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options) {
        synchronized (mTasks) {
            synchronizeTasks();
        }

        ResolveInfo resolveInfo = ParallaxPackageManagerService.get().resolveActivity(intent, GET_ACTIVITIES, resolvedType, userId);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return 0;
        }
        Log.d(TAG, "startActivityLocked : " + resolveInfo.activityInfo);
        ActivityInfo activityInfo = resolveInfo.activityInfo;

        ParallaxActivityRecord sourceRecord = findActivityRecordByToken(userId, resultTo);
        if (sourceRecord == null) {
            resultTo = null;
        }
        ParallaxTaskRecord sourceTask = null;
        if (sourceRecord != null) {
            sourceTask = sourceRecord.task;
        }

        String taskAffinity = ParallaxComponentUtils.getTaskAffinity(activityInfo);

        int launchModeFlags = 0;
        boolean singleTop = containsFlag(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP) || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP;
        boolean newTask = containsFlag(intent, Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean clearTop = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
        boolean clearTask = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);

        ParallaxTaskRecord taskRecord = null;
        switch (activityInfo.launchMode) {
            case ActivityInfo.LAUNCH_SINGLE_TOP:
            case ActivityInfo.LAUNCH_MULTIPLE:
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
                if (taskRecord == null && !newTask) {
                    taskRecord = sourceTask;
                }
                break;
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
                break;
        }

        // 如果还没有task则新启动一个task
        if (taskRecord == null || taskRecord.needNewTask()) {
            return startActivityInNewTaskLocked(userId, intent, activityInfo, resultTo, launchModeFlags);
        }

        boolean notStartToFront = false;
        if (clearTop || singleTop || clearTask) {
            notStartToFront = true;
        }
        boolean startTaskToFront = !notStartToFront && ParallaxComponentUtils.intentFilterEquals(taskRecord.rootIntent, intent) && taskRecord.rootIntent.getFlags() == intent.getFlags();
        if (startTaskToFront) {
            mAms.moveTaskToFront(taskRecord.id, 0);
            return 0;
        }
        ParallaxActivityRecord topActivityRecord = taskRecord.getTopActivityRecord();
        ParallaxActivityRecord targetActivityRecord = findActivityRecordByComponentName(userId, ParallaxComponentUtils.toComponentName(activityInfo));
        ParallaxActivityRecord newIntentRecord = null;
        boolean ignore = false;

        if (clearTop) {
            if (targetActivityRecord != null) {
                // 目标栈上面所有activity出栈
                synchronized (targetActivityRecord.task.activities) {
                    for (int i = targetActivityRecord.task.activities.size() - 1; i >= 0; i--) {
                        ParallaxActivityRecord next = targetActivityRecord.task.activities.get(i);
                        if (next != targetActivityRecord) {
                            next.finished = true;
                            Log.d(TAG, "makerFinish: " + next.component.toString());
                        } else {
                            if (singleTop) {
                                newIntentRecord = targetActivityRecord;
                            } else {
                                // clearTop并且不是singleTop，目标也finish，重建。
                                targetActivityRecord.finished = true;
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (singleTop && !clearTop) {
            if (ParallaxComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
                newIntentRecord = topActivityRecord;
            } else {
                synchronized (mLaunchingActivities) {
                    for (ParallaxActivityRecord launchingActivity : mLaunchingActivities) {
                        if (!launchingActivity.finished && launchingActivity.component.equals(intent.getComponent())) {
                            // todo update onNewIntent from intent
                            ignore = true;
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK && !clearTop) {
            if (ParallaxComponentUtils.intentFilterEquals(topActivityRecord.intent, intent)) {
                newIntentRecord = topActivityRecord;
            } else {
                ParallaxActivityRecord record = findActivityRecordByComponentName(userId, ParallaxComponentUtils.toComponentName(activityInfo));
                if (record != null) {
                    // 需要调用目标onNewIntent
                    newIntentRecord = record;
                    // 目标栈上面所有activity出栈
                    synchronized (taskRecord.activities) {
                        for (int i = taskRecord.activities.size() - 1; i >= 0; i--) {
                            ParallaxActivityRecord next = taskRecord.activities.get(i);
                            if (next != record) {
                                next.finished = true;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            newIntentRecord = topActivityRecord;
        }

        // clearTask finish All
        if (clearTask && newTask) {
            for (ParallaxActivityRecord activity : taskRecord.activities) {
                activity.finished = true;
            }
        }

        // Queue onNewIntent before any operation that can resume the reused
        // Activity. BActivityThread posts the actual delivery to the guest main
        // looper, so moving the task to front first lets lifecycle-sensitive
        // activities observe onResume() before the redirect intent is queued.
        if (newIntentRecord != null && !(clearTask && newTask)) {
            deliverNewIntentLocked(newIntentRecord, intent);
            finishAllActivity(userId);
            mAms.moveTaskToFront(taskRecord.id, 0);
            return 0;
        }

        finishAllActivity(userId);

        if (ignore) {
            mAms.moveTaskToFront(taskRecord.id, 0);
            return 0;
        }

        if (resultTo == null) {
            ParallaxActivityRecord top = taskRecord.getTopActivityRecord();
            if (top != null) {
                resultTo = top.token;
            }
        } else if (sourceTask != null) {
            ParallaxActivityRecord top = sourceTask.getTopActivityRecord();
            if (top != null) {
                resultTo = top.token;
            }
        }
        mAms.moveTaskToFront(taskRecord.id, 0);
        return startActivityInSourceTask(intent,resolvedType, resultTo, resultWho, requestCode, flags, options, userId, topActivityRecord, activityInfo, launchModeFlags);
    }

    private void deliverNewIntentLocked(ParallaxActivityRecord activityRecord, Intent intent) {
        try {
            activityRecord.processRecord.bActivityThread.handleNewIntent(activityRecord.token, intent);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private Intent startActivityProcess(int userId, Intent intent, ActivityInfo info, ParallaxActivityRecord record) {
		ParallaxProxyActivityRecord stubRecord = new ParallaxProxyActivityRecord(userId, info, intent, record);
		String processName = info.processName;
		if (processName == null || processName.contains(":") || !processName.equals(info.packageName)) {
			processName = info.packageName;
		}
		Log.e(TAG,"StartProcess pkg=" + info.packageName + " activity=" + info.name + " process=" + processName);
		ParallaxProcessRecord targetApp = ParallaxProcessManagerService.get().startProcessLocked(info.packageName,processName,userId,-1,Binder.getCallingPid());
		if (targetApp == null) {
			Log.e(TAG, "Process creation failed for " + processName + ", using default process");
			return getStartStubActivityIntentInner(intent, 0, userId, stubRecord, info);
		}
		return getStartStubActivityIntentInner(intent,targetApp.bpid,userId,stubRecord,info);
	}

    private int startActivityInNewTaskLocked(int userId, Intent intent, ActivityInfo activityInfo, IBinder resultTo, int launchMode) {
		ParallaxActivityRecord record = newActivityRecord(intent, activityInfo, resultTo, userId);
		Intent shadow = startActivityProcess(userId, intent, activityInfo, record);
		if (shadow == null) {
			Log.e(TAG, "Shadow intent is null, cannot start activity");
			return -1;
		}
		shadow.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
		shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		shadow.addFlags(launchMode);
		try {
			ParallaxCore.getContext().startActivity(shadow);
			return 0;
		} catch (Exception e) {
			Log.e(TAG, "Failed to start activity: " + e.getMessage());
			return -1;
		}
	}

    private int startActivityInSourceTask(Intent intent, String resolvedType,IBinder resultTo, String resultWho, int requestCode, int flags,Bundle options,int userId, ParallaxActivityRecord sourceRecord, ActivityInfo activityInfo, int launchMode) {
        ParallaxActivityRecord selfRecord = newActivityRecord(intent, activityInfo, resultTo, userId);
        Intent shadow = startActivityProcess(userId, intent, activityInfo, selfRecord);
        shadow.setAction(UUID.randomUUID().toString());
        shadow.addFlags(launchMode);
        if (resultTo == null) {
            shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return realStartActivityLocked(sourceRecord.processRecord.appThread, shadow, resolvedType, resultTo, resultWho, requestCode, flags, options);
    }

    private int realStartActivityLocked(IInterface appThread, Intent intent, String resolvedType,IBinder resultTo, String resultWho, int requestCode, int flags,Bundle options) {
        try {
            flags &= ~ParallaxActivityManagerCompat.START_FLAG_DEBUG;
            flags &= ~ParallaxActivityManagerCompat.START_FLAG_NATIVE_DEBUGGING;
            flags &= ~ParallaxActivityManagerCompat.START_FLAG_TRACK_ALLOCATION;
            BRIActivityManager.get(BRActivityManagerNative.get().getDefault()).startActivity(appThread, ParallaxCore.getHostPkg(), intent,resolvedType, resultTo, resultWho, requestCode, flags, null, options);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private ParallaxActivityRecord getTopActivityRecord() {
        synchronized (mTasks) {
            synchronizeTasks();
        }
        List<ParallaxTaskRecord> tasks = new LinkedList<>(mTasks.values());
        if (tasks.isEmpty())
            return null;
        return tasks.get(tasks.size() - 1).getTopActivityRecord();
    }

    private Intent getStartStubActivityIntentInner(Intent intent, int vpid,int userId, ParallaxProxyActivityRecord target,ActivityInfo activityInfo) {
        Intent shadow = new Intent();
        TypedArray typedArray = null;
        try {
            Resources resources = ParallaxPackageManagerCompat.getResources(ParallaxCore.getContext(), activityInfo.applicationInfo);
            int id;
            if (activityInfo.theme != 0) {
                id = activityInfo.theme;
            } else {
                id = activityInfo.applicationInfo.theme;
            }
            assert resources != null;
            typedArray = resources.newTheme().obtainStyledAttributes(id, BRRstyleable.get().Window());
            boolean windowIsTranslucent = typedArray.getBoolean(BRRstyleable.get().Window_windowIsTranslucent(), false);
            if (windowIsTranslucent) {
                shadow.setComponent(new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyManifest.ParallaxTransparentProxyActivity(vpid)));
            } else {
                shadow.setComponent(new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyManifest.getProxyActivity(vpid)));
            }
            ParallaxSlog.d(TAG, activityInfo + ", windowIsTranslucent: " + windowIsTranslucent);
        } catch (Throwable e) {
            e.printStackTrace();
            shadow.setComponent(new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyManifest.getProxyActivity(vpid)));
        } finally {
            if (typedArray != null) {
                typedArray.recycle();
            }
        }
        ParallaxProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, target.mActivityRecord, target.mUserId);
        return shadow;
    }

    private void finishAllActivity(int userId) {
        for (ParallaxTaskRecord task : mTasks.values()) {
            for (ParallaxActivityRecord activity : task.activities) {
                if (activity.userId == userId) {
                    if (activity.finished) {
                        try {
                            activity.processRecord.bActivityThread.finishActivity(activity.token);
                        } catch (RemoteException ignored) {
                        }
                    }
                }
            }
        }
    }

    ParallaxActivityRecord newActivityRecord(Intent intent, ActivityInfo info, IBinder resultTo,int userId) {
        ParallaxActivityRecord targetRecord = ParallaxActivityRecord.create(intent, info, resultTo, userId);
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.add(targetRecord);
            Message obtain = Message.obtain(mHandler, LAUNCH_TIME_OUT, targetRecord);
            mHandler.sendMessageDelayed(obtain, 2000);
        }
        return targetRecord;
    }

    private ParallaxActivityRecord findActivityRecordByComponentName(int userId, ComponentName componentName) {
        ParallaxActivityRecord record = null;
        for (ParallaxTaskRecord next : mTasks.values()) {
            if (userId == next.userId) {
                for (ParallaxActivityRecord activity : next.activities) {
                    if (activity.component.equals(componentName)) {
                        record = activity;
                        break;
                    }
                }
            }
        }
        return record;
    }

    private ParallaxActivityRecord findActivityRecordByToken(int userId, IBinder token) {
        ParallaxActivityRecord record = null;
        if (token != null) {
            for (ParallaxTaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ParallaxActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            record = activity;
                            break;
                        }
                    }
                }
            }
        }
        return record;
    }

    private ParallaxTaskRecord findTaskRecordByTaskAffinityLocked(int userId, String taskAffinity) {
        synchronized (mTasks) {
            for (ParallaxTaskRecord next : mTasks.values()) {
                if (userId == next.userId && next.taskAffinity.equals(taskAffinity))
                    return next;
            }
            return null;
        }
    }

    private ParallaxTaskRecord findTaskRecordByTokenLocked(int userId, IBinder token) {
        synchronized (mTasks) {
            for (ParallaxTaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ParallaxActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            return next;
                        }
                    }
                }
            }
            return null;
        }
    }

    public void onActivityCreated(ParallaxProcessRecord processRecord, int taskId, IBinder token, ParallaxActivityRecord record) {
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.remove(record);
            mHandler.removeMessages(LAUNCH_TIME_OUT, record);
        }
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxTaskRecord taskRecord = mTasks.get(taskId);
            if (taskRecord == null) {
                taskRecord = new ParallaxTaskRecord(taskId, record.userId, ParallaxComponentUtils.getTaskAffinity(record.info));
                taskRecord.rootIntent = record.intent;
                mTasks.put(taskId, taskRecord);
            }
            record.token = token;
            record.processRecord = processRecord;
            record.task = taskRecord;
            taskRecord.addTopActivity(record);
            Log.d(TAG, "onActivityCreated : " + record.component.toString());
        }
    }

    public void onActivityResumed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            Log.d(TAG, "onActivityResumed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
            activityRecord.task.addTopActivity(activityRecord);
        }
    }

    public void onActivityDestroyed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            activityRecord.finished = true;
            Log.d(TAG, "onActivityDestroyed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
        }
    }

    public void onFinishActivity(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            activityRecord.finished = true;
            Log.d(TAG, "onFinishActivity : " + activityRecord.component.toString());
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ParallaxActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                if (resultTo != null) {
                    return resultTo.info.packageName;
                }
            }
            return ParallaxCore.getHostPkg();
        }
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ParallaxActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ParallaxActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                if (resultTo != null) {
                    return resultTo.component;
                }
            }
            return new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyActivity.P0.class.getName());
        }
    }

    private void synchronizeTasks() {
        List<ActivityManager.RecentTaskInfo> recentTasks = mAms.getRecentTasks(100, 0);
        Map<Integer, ParallaxTaskRecord> newTacks = new LinkedHashMap<>();
        for (int i = recentTasks.size() - 1; i >= 0; i--) {
            ActivityManager.RecentTaskInfo next = recentTasks.get(i);
            ParallaxTaskRecord taskRecord = mTasks.get(next.id);
            if (taskRecord == null)
                continue;
            newTacks.put(next.id, taskRecord);
        }
        mTasks.clear();
        mTasks.putAll(newTacks);
    }
}
