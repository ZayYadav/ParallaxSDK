package com.Parallax.SDK.core;

import com.Parallax.SDK.R;

import android.app.Activity;
import android.widget.Toast;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.Parallax.SDK.runtime.ParallaxRemoteManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import dalvik.system.DexFile;
import java.io.BufferedReader;
import java.io.IOException;
import me.weishu.reflection.Reflection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.os.BRUserHandle;
import com.Parallax.SDK.core.app.ParallaxLauncherActivity;
import com.Parallax.SDK.core.app.configuration.ParallaxAppLifecycleCallback;
import com.Parallax.SDK.core.app.configuration.ParallaxClientConfiguration;
import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.ParallaxDaemonService;
import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.core.system.user.ParallaxUserInfo;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallResult;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;
import com.Parallax.SDK.core.fake.delegate.ParallaxContentProviderDelegate;
import com.Parallax.SDK.core.fake.frameworks.ParallaxActivityManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxJobManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxPackageManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxStorageManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxUserManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxXposedManager;
import com.Parallax.SDK.core.fake.hook.ParallaxHookManager;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxShellUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxBundleCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxXposedParserCompat;
import com.Parallax.SDK.core.utils.provider.ParallaxProviderCall;
import com.Parallax.SDK.core.core.system.api.ParallaxActivationManager;
/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@SuppressLint({"StaticFieldLeak", "NewApi"})
public class ParallaxCore extends ParallaxClientConfiguration {
    public static final String TAG = "ParallaxCore";

    private static final ParallaxCore sBlackBoxCore = new ParallaxCore();
    private static Context sContext;
    private ProcessType mProcessType;
    private final Map<String, IBinder> mServices = new HashMap<>();
    private Thread.UncaughtExceptionHandler mExceptionHandler;
    private ParallaxClientConfiguration mClientConfiguration;
    private final List<ParallaxAppLifecycleCallback> mAppLifecycleCallbacks = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mHostUid = Process.myUid();
    private final int mHostUserId = BRUserHandle.get().myUserId();
    private ParallaxAppConfig appConfig;
    
    public static ParallaxCore get() {
        return sBlackBoxCore;
    }

    public Handler getHandler() {
        return mHandler;
    }

    public static PackageManager getPackageManager() {
        return sContext.getPackageManager();
    }

    public static String getHostPkg() {
        return get().getHostPackageName();
    }

    public static int getHostUid() {
        return get().mHostUid;
    }

    public static int getHostUserId() {
        return get().mHostUserId;
    }

    public static Context getContext() {
        return sContext;
    }

    public ParallaxAppConfig getAppConfig() {
        return appConfig;
    }

    public Thread.UncaughtExceptionHandler getExceptionHandler() {
        return mExceptionHandler;
    }

    public void setExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler) {
        mExceptionHandler = exceptionHandler;
    }
    
    public static void setHideRoot(boolean hideRoot) { 
        ParallaxRemoteManager.sHideRoot = hideRoot; 
    }
    
    public static void setHideXposed(boolean hide) {
        ParallaxRemoteManager.sHideXposed = hide;
    }
    
    public static void setEnableDaemonService(boolean enableDaemonService) { 
        ParallaxRemoteManager.sEnableDaemonService = enableDaemonService; 
    }

    public void doAttachBaseContext(Context context, ParallaxClientConfiguration clientConfiguration) {
        if (clientConfiguration == null) {
            throw new IllegalArgumentException("ParallaxClientConfiguration is null!");
        }
        
        Reflection.unseal(context);
        sContext = context;
        mClientConfiguration = clientConfiguration;
        initNotificationManager();

        String processName = getProcessName(getContext());
        if (processName.equals(ParallaxCore.getHostPkg())) {
            mProcessType = ProcessType.Main;
            startLogcat();
        } else if (processName.endsWith(getContext().getString(R.string.vbox_service_name))) {
            mProcessType = ProcessType.Server;
        } else {
            mProcessType = ProcessType.BAppClient;
        }

        if (ParallaxCore.get().isBlackProcess()) {
            ParallaxEnvironment.load();
        }
        
        if (isServerProcess()) {
            if (ParallaxRemoteManager.sEnableDaemonService) {
                Intent intent = new Intent();
                intent.setClass(getContext(), ParallaxDaemonService.class);
                if (ParallaxBuildCompat.isOreo_MR1()) {
                    getContext().startForegroundService(intent);
                } else {
                    getContext().startService(intent);
                }
            }
        }
        ParallaxHookManager.get().init();
    }

    public void doCreate() {
        // fix contentProvider
        if (isBlackProcess()) {
            ParallaxContentProviderDelegate.init();
        }
        if (!isServerProcess()) {
            ParallaxServiceManager.initBlackManager();
        }
    }

    public static Object mainThread() {
        return BRActivityThread.get().currentActivityThread();
    }

    public void startActivity(Intent intent, int userId) {
        if (intent == null) { return; }
        if (mClientConfiguration.isEnableLauncherActivity()) {
            ParallaxLauncherActivity.launch(intent, userId);
        } else {
            getBActivityManager().startActivity(intent, userId);
        }
    }
    
    public void onBeforeMainLaunchApk(String packageName,int userid) {
        for (ParallaxAppLifecycleCallback appLifecycleCallback : ParallaxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeMainLaunchApk(packageName,userid);
        }
    }

    public static ParallaxJobManager getBJobManager() {
        return ParallaxJobManager.get();
    }

    public static ParallaxPackageManager getBPackageManager() {
        return ParallaxPackageManager.get();
    }

    public static ParallaxActivityManager getBActivityManager() {
        return ParallaxActivityManager.get();
    }

    public static ParallaxStorageManager getBStorageManager() {
        return ParallaxStorageManager.get();
    }
  
   public boolean launchApk(String packageName, int userId) {
		onBeforeMainLaunchApk(packageName, userId);
		Intent launchIntent = getBPackageManager().getLaunchIntentForPackage(packageName, userId);
		if (launchIntent == null) {
			ParallaxSlog.e(TAG, "Launch intent is null for package: " + packageName);
			return false;
		}
		if (launchIntent.getComponent() == null && launchIntent.getPackage() == null) {
			ParallaxSlog.e(TAG, "Invalid launch intent: " + packageName);
			return false;
		}
		launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			startActivity(launchIntent, userId);
		} catch (Throwable e) {
			ParallaxSlog.e(TAG, "Launch failed: " + packageName, e);
			return false;
		}
		return true;
	}


    public boolean isInstalled(String packageName, int userId) {
        return getBPackageManager().isInstalled(packageName, userId);
    }

    public void uninstallPackageAsUser(String packageName, int userId) {
        getBPackageManager().uninstallPackageAsUser(packageName, userId);
    }

    public void uninstallPackage(String packageName) {
        getBPackageManager().uninstallPackage(packageName);
    }

    public ParallaxInstallResult installPackageAsUser(String packageName, int userId) {
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
			return getBPackageManager().installPackageAsUser(packageInfo.applicationInfo.sourceDir, ParallaxInstallOption.installBySystem(), userId);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
			return new ParallaxInstallResult().installError(e.getMessage());
		}
	}

    public ParallaxInstallResult installPackageAsUser(File apk, int userId) {
        return getBPackageManager().installPackageAsUser(apk.getAbsolutePath(), ParallaxInstallOption.installByStorage(), userId);
    }

    public ParallaxInstallResult installPackageAsUser(Uri apk, int userId) {
        return getBPackageManager().installPackageAsUser(apk.toString(), ParallaxInstallOption.installByStorage().makeUriFile(), userId);
    }

    public ParallaxInstallResult installXPModule(File apk) {
        return getBPackageManager().installPackageAsUser(apk.getAbsolutePath(), ParallaxInstallOption.installByStorage().makeXposed(), ParallaxUserHandle.USER_XPOSED);
    }

    public ParallaxInstallResult installXPModule(Uri apk) {
        return getBPackageManager().installPackageAsUser(apk.toString(), ParallaxInstallOption.installByStorage().makeXposed().makeUriFile(), ParallaxUserHandle.USER_XPOSED);
    }

    public ParallaxInstallResult installXPModule(String packageName) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            String path = packageInfo.applicationInfo.sourceDir;
            return getBPackageManager().installPackageAsUser(path, ParallaxInstallOption.installBySystem().makeXposed(), ParallaxUserHandle.USER_XPOSED);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new ParallaxInstallResult().installError(e.getMessage());
        }
    }

    public void uninstallXPModule(String packageName) {
        uninstallPackage(packageName);
    }

    public boolean isXPEnable() {
        return ParallaxXposedManager.get().isXPEnable();
    }

    public void setXPEnable(boolean enable) {
        ParallaxXposedManager.get().setXPEnable(enable);
    }

    public boolean isXposedModule(File file) {
        return ParallaxXposedParserCompat.isXPModule(file.getAbsolutePath());
    }

    public boolean isInstalledXposedModule(String packageName) {
        return isInstalled(packageName, ParallaxUserHandle.USER_XPOSED);
    }

    public boolean isModuleEnable(String packageName) {
        return ParallaxXposedManager.get().isModuleEnable(packageName);
    }

    public void setModuleEnable(String packageName, boolean enable) {
        ParallaxXposedManager.get().setModuleEnable(packageName, enable);
    }

    public List<ParallaxInstalledModule> getInstalledXPModules() {
        return ParallaxXposedManager.get().getInstalledModules();
    }

    public List<ApplicationInfo> getInstalledApplications(int flags, int userId) {
        return getBPackageManager().getInstalledApplications(flags, userId);
    }

    public List<PackageInfo> getInstalledPackages(int flags, int userId) {
        return getBPackageManager().getInstalledPackages(flags, userId);
    }

    public void clearPackage(String packageName, int userId) {
        ParallaxPackageManager.get().clearPackage(packageName, userId);
    }

    public void stopPackage(String packageName, int userId) {
        ParallaxPackageManager.get().stopPackage(packageName, userId);
    }
    
    public boolean isAppRunning(String packageName, int userId) {
        return getBPackageManager().isAppRunning(packageName, userId);
    }

    public List<ParallaxUserInfo> getUsers() {
        return ParallaxUserManager.get().getUsers();
    }

    public ParallaxUserInfo createUser(int userId) {
        return ParallaxUserManager.get().createUser(userId);
    }

    public ApplicationInfo getApplicationInfo(String packageName) {
        return ParallaxPackageManager.get().getApplicationInfo(packageName, 0, 0);
    }
    
    public void deleteUser(int userId) {
        ParallaxUserManager.get().deleteUser(userId);
    }

    public List<ParallaxAppLifecycleCallback> getAppLifecycleCallbacks() {
        return mAppLifecycleCallbacks;
    }

    public void removeAppLifecycleCallback(ParallaxAppLifecycleCallback appLifecycleCallback) {
        mAppLifecycleCallbacks.remove(appLifecycleCallback);
    }

    public void addAppLifecycleCallback(ParallaxAppLifecycleCallback appLifecycleCallback) {
        mAppLifecycleCallbacks.add(appLifecycleCallback);
    }

    public boolean isSupportGms() {
        return ParallaxGmsCore.isSupportGms();
    }

    public boolean isInstallGms(int userId) {
        return ParallaxGmsCore.isInstalledGoogleService(userId);
    }

    public ParallaxInstallResult installGms(int userId) {
        return ParallaxGmsCore.installGApps(userId);
    }

    public boolean uninstallGms(int userId) {
        ParallaxGmsCore.uninstallGApps(userId);
        return !ParallaxGmsCore.isInstalledGoogleService(userId);
    }

    public IBinder getService(String name) {
        IBinder binder = mServices.get(name);
        if (binder != null && binder.isBinderAlive()) {
            return binder;
        }
        Bundle bundle = new Bundle();
        bundle.putString("_G_|_server_name_", name);
        Bundle vm = ParallaxProviderCall.callSafely(ParallaxProxyManifest.getBindProvider(), "VM", null, bundle);
        binder = ParallaxBundleCompat.getBinder(vm, "_G_|_server_");
        ParallaxSlog.d(TAG, "getService: " + name + ", " + binder);
        mServices.put(name, binder);
        return binder;
    }

    //Process type
    private enum ProcessType {
        Server, //Server process
        BAppClient, //Black app process
        Main, //Main process
    }

    public boolean isBlackProcess() {
        return mProcessType == ProcessType.BAppClient;
    }

    public boolean isMainProcess() {
        return mProcessType == ProcessType.Main;
    }

    public boolean isServerProcess() {
        return mProcessType == ProcessType.Server;
    }

    @Override
    public boolean setHideRoot() {
        return mClientConfiguration.setHideRoot();
    }

    @Override
    public String getHostPackageName() {
        return mClientConfiguration.getHostPackageName();
    }

    @Override
    public boolean requestInstallPackage(File file) {
        return mClientConfiguration.requestInstallPackage(file);
    }
    
    private void startLogcat() {
        new Thread(() -> {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), getContext().getPackageName() + "_logcat.txt");
            ParallaxFileUtils.deleteDir(file);
            ParallaxShellUtils.execCommand("logcat -c", false);
            ParallaxShellUtils.execCommand("logcat -f " + file.getAbsolutePath(), false);
        }).start();
    }

    private static String getProcessName(Context context) {
        int myPid = Process.myPid();
        String processName = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == myPid) {
                processName = info.processName;
                break;
            }
        }
        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    public static boolean is64Bit() {
        if (ParallaxBuildCompat.isM()) {
            return Process.is64Bit();
        } else {
            return Build.CPU_ABI.equals("arm64-v8a");
        }
    }

    private void initNotificationManager() {
        NotificationManager nm = (NotificationManager) ParallaxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ONE_ID = ParallaxCore.getContext().getPackageName() + ".parallaxcore_core";
        String CHANNEL_ONE_NAME = "ParallaxCore";
        if (ParallaxBuildCompat.isOreo_MR1()) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(notificationChannel);
        }
    }

    // 20240801 add request permission add start 0
    public boolean checkSelfPermission(String permission) {
        return getPackageManager().checkPermission(permission, getHostPackageName()) == 0;
    }
    // 20240801 add request permission add end 0
}
