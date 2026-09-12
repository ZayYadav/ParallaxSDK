package com.Parallax.SDK.core.core.system;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.IParallaxActivityThread;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.notification.ParallaxNotificationManagerService;
import com.Parallax.SDK.core.core.system.pm.ParallaxPackageManagerService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxPermissionUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxApplicationThreadCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;
import com.Parallax.SDK.core.utils.provider.ParallaxProviderCall;
import com.Parallax.SDK.core.core.system.api.ParallaxActivationManager;

/**
 * Created by @RIYAZXERO on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxProcessManagerService implements IParallaxSystemService {
    public static final String TAG = "BProcessManager";

    public static ParallaxProcessManagerService sBProcessManagerService = new ParallaxProcessManagerService();
    private final Map<Integer, Map<String, ParallaxProcessRecord>> mProcessMap = new HashMap<>();
    private final List<ParallaxProcessRecord> mPidsSelfLocked = new ArrayList<>();
    private final Object mProcessLock = new Object();

    public static ParallaxProcessManagerService get() {
        return sBProcessManagerService;
    }

    public ParallaxProcessRecord startProcessLocked(String packageName, String processName, int userId, int bpid, int callingPid) {
        ApplicationInfo info = ParallaxPackageManagerService.get().getApplicationInfo(packageName, 0, userId);
        if (info == null)
            return null;
        ParallaxProcessRecord app;
        int buid = ParallaxUserHandle.getUid(userId, ParallaxPackageManagerService.get().getAppId(packageName));
        synchronized (mProcessLock) {
            Map<String, ParallaxProcessRecord> bProcess = mProcessMap.get(buid);

            if (bProcess == null) {
                bProcess = new HashMap<>();
            }
            if (bpid == -1) {
                app = bProcess.get(processName);
                if (app != null) {
                    if (app.initLock != null) {
                        app.initLock.block();
                    }
                    if (app.bActivityThread != null) {
                        return app;
                    }
                }
                bpid = getUsingBPidL();
                ParallaxSlog.d(TAG, "init bUid = " + buid + ", bPid = " + bpid);
            }
            if (bpid == -1) {
                throw new RuntimeException("No processes available");
            }
            app = new ParallaxProcessRecord(info, processName);
            app.uid = Process.myUid();
            app.bpid = bpid;
            app.buid = ParallaxPackageManagerService.get().getAppId(packageName);
            app.callingBUid = getBUidByPidOrPackageName(callingPid, packageName);
            app.userId = userId;

            bProcess.put(processName, app);
            mPidsSelfLocked.add(app);

            mProcessMap.put(buid, bProcess);
            if (!initAppProcessL(app)) {
                //init process fail
                bProcess.remove(processName);
                mPidsSelfLocked.remove(app);
                app = null;
            } else {
                app.pid = getPid(ParallaxCore.getContext(), ParallaxProxyManifest.getProcessName(app.bpid));
            }
        }
        return app;
    }

    // 20240801 add request permission add start 0
    private void requestPermissionIfNeed(ParallaxProcessRecord app) {
        if (ParallaxPermissionUtils.isCheckPermissionRequired(app.info)) {
            String[] permissions = ParallaxPackageManagerService.get().getDangerousPermissions(app.info.packageName);
            new Thread(() -> {
				if (!ParallaxPermissionUtils.checkPermissions(permissions)) {
					ConditionVariable permissionLock = new ConditionVariable();
					startRequestPermission(permissions, permissionLock);
					permissionLock.block();
				}
			}).start();
        }
    }

    private void startRequestPermission(String[] permissions, final ConditionVariable permissionLock) {
	   if (permissions == null || permissions.length == 0) {
		   if (permissionLock != null) {
			   permissionLock.open();
		   }
		   return;
	   }
	   if (ParallaxCore.getContext() == null || permissionLock == null) {
		   return;
	   }
	   ParallaxPermissionUtils.startRequestPermissions(ParallaxCore.getContext(), permissions, new ParallaxPermissionUtils.CallBack() {
	   @Override
	   public boolean onResult(int requestCode, String[] permissions, int[] grantResults) {
		 try {
		     return ParallaxPermissionUtils.isRequestGranted(grantResults);
			 } finally {
			 permissionLock.open();
		     }
		  }
	   });
	}

    // 20240801 add request permission add end 0

    private int getUsingBPidL() {
        ActivityManager manager = (ActivityManager) ParallaxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
        Set<Integer> usingPs = new HashSet<>();
        for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
            int i = parseBPid(runningAppProcess.processName);
            usingPs.add(i);
        }
        for (int i = 0; i < ParallaxProxyManifest.FREE_COUNT; i++) {
            if (usingPs.contains(i)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    public void restartAppProcess(String packageName, String processName, int userId) {
        synchronized (mProcessLock) {
            int callingUid = Binder.getCallingUid();
            int callingPid = Binder.getCallingPid();
            ParallaxProcessRecord app;
            synchronized (mProcessLock) {
                app = findProcessByPid(callingPid);
            }
            if (app == null) {
                String stubProcessName = getProcessName(ParallaxCore.getContext(), callingPid);
                int bpid = parseBPid(stubProcessName);
                startProcessLocked(packageName, processName, userId, bpid, callingPid);
            }
        }
    }

    private int parseBPid(String stubProcessName) {
        String prefix;
        if (stubProcessName == null) {
            return -1;
        } else {
            prefix = ParallaxCore.getHostPkg() + ":p";
        }
        if (stubProcessName.startsWith(prefix)) {
            try {
                return Integer.parseInt(stubProcessName.substring(prefix.length()));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1;
    }


    //这里初始化了userinfo
    private boolean initAppProcessL(ParallaxProcessRecord record) {
		Log.d(TAG, "initProcess: " + record.processName);
		requestPermissionIfNeed(record);
		ParallaxAppConfig appConfig = record.getClientConfig();
		Bundle bundle = new Bundle();
		bundle.putParcelable(ParallaxAppConfig.KEY, appConfig);
		// 🔥 CRASH FIX: Line 209
		Bundle result;
		try {
			result = ParallaxProviderCall.callSafely(record.getProviderAuthority(), "_Black_|_init_process_", (String) null, bundle);
		} catch (Exception e) {
			Log.e(TAG, "Provider error: " + e.getMessage());
			result = new Bundle();
		}
		IBinder appThread = ParallaxBundleCompat.getBinder(result, "_Black_|_client_");
		if (appThread == null || !appThread.isBinderAlive()) {
			return false;
		}
		attachClientL(record, appThread);
		createProc(record);
		return true;
	}

    private void attachClientL(final ParallaxProcessRecord app, final IBinder appThread) {
        IParallaxActivityThread activityThread = IParallaxActivityThread.Stub.asInterface(appThread);
        if (activityThread == null) {
            app.kill();
            return;
        }
        try {
            appThread.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    Log.d(TAG, "App Died: " + app.processName);
                    appThread.unlinkToDeath(this, 0);
                    onProcessDie(app);
                }
            }, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        app.bActivityThread = activityThread;
        try {
            app.appThread = ParallaxApplicationThreadCompat.asInterface(activityThread.getActivityThread());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        app.initLock.open();
    }

    public void onProcessDie(ParallaxProcessRecord record) {
        synchronized (mProcessLock) {
            record.kill();
            Map<String, ParallaxProcessRecord> process = mProcessMap.get(record.buid);
            if (process != null) {
                process.remove(record.processName);
                if (process.isEmpty()) {
                    mProcessMap.remove(record.buid);
                }
            }
            mPidsSelfLocked.remove(record);
            removeProc(record);
            ParallaxNotificationManagerService.get().deletePackageNotification(record.getPackageName(), record.userId);
        }
    }

    public ParallaxProcessRecord findProcessRecord(String packageName, String processName, int userId) {
        synchronized (mProcessLock) {
            int appId = ParallaxPackageManagerService.get().getAppId(packageName);
            int buid = ParallaxUserHandle.getUid(userId, appId);
            Map<String, ParallaxProcessRecord> processRecordMap = mProcessMap.get(buid);
            if (processRecordMap == null)
                return null;
            return processRecordMap.get(processName);
        }
    }

    public void killAllByPackageName(String packageName) {
        synchronized (mProcessLock) {
            synchronized (mPidsSelfLocked) {
                List<ParallaxProcessRecord> tmp = new ArrayList<>(mPidsSelfLocked);
                int appId = ParallaxPackageManagerService.get().getAppId(packageName);
                for (ParallaxProcessRecord processRecord : mPidsSelfLocked) {
                    int appId1 = ParallaxUserHandle.getAppId(processRecord.buid);
                    if (appId == appId1) {
                        mProcessMap.remove(processRecord.buid);
                        tmp.remove(processRecord);
                        processRecord.kill();
                    }
                }
                mPidsSelfLocked.clear();
                mPidsSelfLocked.addAll(tmp);
            }
        }
    }

    public void killPackageAsUser(String packageName, int userId) {
        synchronized (mProcessLock) {
            int buid = ParallaxUserHandle.getUid(userId, ParallaxPackageManagerService.get().getAppId(packageName));
            Map<String, ParallaxProcessRecord> process = mProcessMap.get(buid);
            if (process == null)
                return;
            for (ParallaxProcessRecord value : process.values()) {
                value.kill();
                mPidsSelfLocked.remove(value);
            }
            mProcessMap.remove(buid);
        }
    }

    public List<ParallaxProcessRecord> getPackageProcessAsUser(String packageName, int userId) {
        synchronized (mProcessLock) {
            int buid = ParallaxUserHandle.getUid(userId, ParallaxPackageManagerService.get().getAppId(packageName));
            Map<String, ParallaxProcessRecord> process = mProcessMap.get(buid);
            if (process == null)
                return new ArrayList<>();
            return new ArrayList<>(process.values());
        }
    }

    public int getBUidByPidOrPackageName(int pid, String packageName) {
        synchronized (mProcessLock) {
            ParallaxProcessRecord callingProcess = ParallaxProcessManagerService.get().findProcessByPid(pid);
            if (callingProcess == null) {
                return ParallaxPackageManagerService.get().getAppId(packageName);
            }
            return ParallaxUserHandle.getAppId(callingProcess.buid);
        }
    }

    public int getUserIdByCallingPid(int callingPid) {
        synchronized (mProcessLock) {
            ParallaxProcessRecord callingProcess = ParallaxProcessManagerService.get().findProcessByPid(callingPid);
            if (callingProcess == null) {
                return 0;
            }
            return callingProcess.userId;
        }
    }

    public ParallaxProcessRecord findProcessByPid(int pid) {
        synchronized (mPidsSelfLocked) {
            for (ParallaxProcessRecord processRecord : mPidsSelfLocked) {
                if (processRecord.pid == pid)
                    return processRecord;
            }
            return null;
        }
    }

    private static String getProcessName(Context context, int pid) {
        String processName = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == pid) {
                processName = info.processName;
                break;
            }
        }
        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    public static int getPid(Context context, String processName) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo runningAppProcess : runningAppProcesses) {
                if (runningAppProcess.processName.equals(processName)) {
                    return runningAppProcess.pid;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void createProc(ParallaxProcessRecord record) {
        File cmdline = new File(ParallaxEnvironment.getProcDir(record.bpid), "cmdline");
        try {
            ParallaxFileUtils.writeToFile(record.processName.getBytes(), cmdline);
        } catch (IOException ignored) {
        }
    }

    private static void removeProc(ParallaxProcessRecord record) {
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getProcDir(record.bpid));
    }

    @Override
    public void systemReady() {
        ParallaxFileUtils.deleteDir(ParallaxEnvironment.getProcDir());
    }

}
