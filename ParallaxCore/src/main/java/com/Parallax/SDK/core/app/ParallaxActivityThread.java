package com.Parallax.SDK.core.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.Service;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import com.Parallax.SDK.runtime.ParallaxRemoteManager;
import android.webkit.WebView;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.mirror.android.app.ActivityThreadAppBindDataContext;
import com.Parallax.SDK.mirror.android.app.BRActivity;
import com.Parallax.SDK.mirror.android.app.BRActivityManagerNative;
import com.Parallax.SDK.mirror.android.app.BRActivityThread;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadActivityClientRecord;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadAppBindData;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadNMR1;
import com.Parallax.SDK.mirror.android.app.BRActivityThreadQ;
import com.Parallax.SDK.mirror.android.app.BRContextImpl;
import com.Parallax.SDK.mirror.android.app.BRLoadedApk;
import com.Parallax.SDK.mirror.android.app.BRService;
import com.Parallax.SDK.mirror.android.content.BRBroadcastReceiver;
import com.Parallax.SDK.mirror.android.content.BRContentProviderClient;
import com.Parallax.SDK.mirror.android.graphics.BRCompatibility;
import com.Parallax.SDK.mirror.android.security.net.config.BRNetworkSecurityConfigProvider;
import com.Parallax.SDK.mirror.com.android.internal.content.BRReferrerIntent;
import com.Parallax.SDK.mirror.dalvik.system.BRVMRuntime;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.configuration.ParallaxAppLifecycleCallback;
import com.Parallax.SDK.core.app.dispatcher.ParallaxAppServiceDispatcher;
import com.Parallax.SDK.core.core.ParallaxCrashHandler;
import com.Parallax.SDK.core.core.IParallaxActivityThread;
import com.Parallax.SDK.core.core.ParallaxRuntimeCore;
import com.Parallax.SDK.core.core.ParallaxNative;
import com.Parallax.SDK.core.core.env.ParallaxVirtualRuntime;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.entity.am.ParallaxReceiverData;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;
import com.Parallax.SDK.core.fake.delegate.ParallaxAppInstrumentation;
import com.Parallax.SDK.core.fake.delegate.ParallaxContentProviderDelegate;
import com.Parallax.SDK.core.fake.frameworks.ParallaxXposedManager;
import com.Parallax.SDK.core.fake.hook.ParallaxHookManager;
import com.Parallax.SDK.core.fake.service.ParallaxHCallbackStub;
import com.Parallax.SDK.core.utils.ParallaxReflector;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxActivityManagerCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxContextCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxStrictModeCompat;

public class ParallaxActivityThread extends IParallaxActivityThread.Stub {
    public static final String TAG = "ParallaxActivityThread";
    private static final String BGMI_PACKAGE_NAME = "com.pubg.imobile";
    private static final String BGMI_HOST_PACKAGE_NAME = "com.bgmi";
    private static final String BGMI_LOADER_RELATIVE_PATH = "loader/libbgmi.so";
    private static volatile boolean sBgmiServerLibraryLoaded;
    private static final Object mConfigLock = new Object();
    private static volatile ParallaxActivityThread sBActivityThread;
    private ParallaxAppConfig mAppConfig;
    private AppBindData mBoundApplication;
    private Application mInitialApplication;
    private final List<ProviderInfo> mProviders = new ArrayList<>();
    private final Handler mH = ParallaxCore.get().getHandler();

    public static class AppBindData {
        ApplicationInfo appInfo;
        Object info;
        String processName;
        List<ProviderInfo> providers;
    }

    public static boolean isThreadInit() {
        return sBActivityThread != null;
    }

    public static ParallaxActivityThread currentActivityThread() {
        if (sBActivityThread == null) {
            synchronized (ParallaxActivityThread.class) {
                if (sBActivityThread == null) {
                    sBActivityThread = new ParallaxActivityThread();
                }
            }
        }
        return sBActivityThread;
    }

    public static ParallaxAppConfig getAppConfig() {
        synchronized (mConfigLock) {
            return currentActivityThread().mAppConfig;
        }
    }

    public static List<ProviderInfo> getProviders() {
        return currentActivityThread().mProviders;
    }

    public static String getAppProcessName() {
        if (getAppConfig() != null) return getAppConfig().processName;
        if (currentActivityThread().mBoundApplication != null) return currentActivityThread().mBoundApplication.processName;
        return null;
    }

    public static String getAppPackageName() {
        if (getAppConfig() != null) return getAppConfig().packageName;
        if (currentActivityThread().mInitialApplication != null) return currentActivityThread().mInitialApplication.getPackageName();
        return null;
    }

    public static Application getApplication() {
        return currentActivityThread().mInitialApplication;
    }

    public static int getAppPid() {
        return getAppConfig() == null ? -1 : getAppConfig().bpid;
    }

    public static int getBUid() {
        return getAppConfig() == null ? ParallaxUserHandle.AID_APP_START : getAppConfig().buid;
    }

    public static int getBAppId() {
        return ParallaxUserHandle.getAppId(ParallaxCore.getHostUid());
    }

    public static int getCallingBUid() {
        return getAppConfig() == null ? ParallaxCore.getHostUid() : getAppConfig().callingBUid;
    }

    public static int getUid() {
        return getAppConfig() == null ? -1 : getAppConfig().uid;
    }

    public static int getUserId() {
        return getAppConfig() == null ? 0 : getAppConfig().userId;
    }

    public void initProcess(ParallaxAppConfig appConfig) {
        synchronized (mConfigLock) {
            if (this.mAppConfig != null && !this.mAppConfig.packageName.equals(appConfig.packageName)) {
                throw new RuntimeException("reject init process: " + appConfig.processName + ", this process is : " + this.mAppConfig.processName);
            }
            this.mAppConfig = appConfig;
            final IBinder iBinder = asBinder();
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                        @Override
                        public void binderDied() {
                            synchronized (ParallaxActivityThread.mConfigLock) {
                                try {
                                    iBinder.linkToDeath(this, 0);
                                } catch (RemoteException e) {
                                    // ignore
                                }
                                ParallaxActivityThread.this.mAppConfig = null;
                            }
                        }
                    }, 0);

            } catch (RemoteException e) {
                Log.e(TAG, "error", e);
            }
        }
    }

    public boolean isInit() {
        return this.mBoundApplication != null;
    }

    public Service createService(ServiceInfo serviceInfo, IBinder token) {
        if (!isInit()) bindApplication(serviceInfo.packageName, serviceInfo.processName);
        try {
            Service service = (Service) BRLoadedApk.get(this.mBoundApplication.info).getClassLoader().loadClass(serviceInfo.name).newInstance();
            Context context = ParallaxCore.getContext().createPackageContext(serviceInfo.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(context, ParallaxCore.mainThread(), serviceInfo.name,token, this.mInitialApplication, BRActivityManagerNative.get().getDefault());
            ParallaxContextCompat.fix(context);
            service.onCreate();
            return service;
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            throw new RuntimeException("Unable to create service " + serviceInfo.name, e);
        }
    }

    public JobService createJobService(ServiceInfo serviceInfo) {
        if (!isInit()) bindApplication(serviceInfo.packageName, serviceInfo.processName);
        try {
            JobService service = (JobService) BRLoadedApk.get(this.mBoundApplication.info).getClassLoader().loadClass(serviceInfo.name).newInstance();
            Context context = ParallaxCore.getContext().createPackageContext(serviceInfo.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(context, ParallaxCore.mainThread(), serviceInfo.name,getActivityThread(), this.mInitialApplication, BRActivityManagerNative.get().getDefault());
            ParallaxContextCompat.fix(context);
            service.onCreate();
            service.onBind(null);
            return service;
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            throw new RuntimeException("Unable to create JobService " + serviceInfo.name, e);
        }
    }

    public void bindApplication(final String packageName, final String processName) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final ConditionVariable conditionVariable = new ConditionVariable();
            ParallaxCore.get().getHandler().post(() -> {
                handleBindApplication(packageName, processName);
                conditionVariable.open();
            });
            conditionVariable.block();
        } else {
            handleBindApplication(packageName, processName);
        }
    }

    public synchronized void handleBindApplication(String packageName, String processName) {
        if (isInit())
            return;
        try {
            ParallaxCrashHandler.create();
        } catch (Throwable ignored) {
        }
        Binder.clearCallingIdentity();
        PackageInfo packageInfo = ParallaxCore.getBPackageManager().getPackageInfo(packageName, PackageManager.GET_PROVIDERS, ParallaxActivityThread.getUserId());
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        if (packageInfo.providers == null) {
            packageInfo.providers = new ProviderInfo[]{};
        }
        mProviders.addAll(Arrays.asList(packageInfo.providers));
        Object boundApplication = BRActivityThread.get(ParallaxCore.mainThread()).mBoundApplication();
        Context packageContext = createPackageContext(applicationInfo);
        Object loadedApk = BRContextImpl.get(packageContext).mPackageInfo();
        BRLoadedApk.get(loadedApk)._set_mSecurityViolation(false);
        // fix applicationInfo
        BRLoadedApk.get(loadedApk)._set_mApplicationInfo(applicationInfo);
        int targetSdkVersion = applicationInfo.targetSdkVersion;
        if (targetSdkVersion < Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy newPolicy = new StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy()).permitNetwork().build();
            StrictMode.setThreadPolicy(newPolicy);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (targetSdkVersion < Build.VERSION_CODES.N) {
                ParallaxStrictModeCompat.disableDeathOnFileUriExposure();
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix(getUserId() + ":" + packageName + ":" + processName);
        }
        
        ParallaxVirtualRuntime.setupRuntime(processName, applicationInfo);
        BRVMRuntime.get(BRVMRuntime.get().getRuntime()).setTargetSdkVersion(applicationInfo.targetSdkVersion);
        if (ParallaxBuildCompat.isS()) {
            BRCompatibility.get().setTargetSdkVersion(applicationInfo.targetSdkVersion);
        }
        ParallaxNative.init(Build.VERSION.SDK_INT);
        assert packageContext != null;
        ParallaxRuntimeCore.get().enableRedirect(packageContext);
        AppBindData bindData = new AppBindData();
        bindData.appInfo = applicationInfo;
        bindData.processName = processName;
        bindData.info = loadedApk;
        bindData.providers = mProviders;
        ActivityThreadAppBindDataContext activityThreadAppBindData = BRActivityThreadAppBindData.get(boundApplication);
        activityThreadAppBindData._set_instrumentationName(new ComponentName(bindData.appInfo.packageName, Instrumentation.class.getName()));
        activityThreadAppBindData._set_appInfo(bindData.appInfo);
        activityThreadAppBindData._set_info(bindData.info);
        activityThreadAppBindData._set_processName(bindData.processName);
        activityThreadAppBindData._set_providers(bindData.providers);
        mBoundApplication = bindData;
        //ssl适配
        if (BRNetworkSecurityConfigProvider.getRealClass() != null) {
            Security.removeProvider("AndroidNSSP");
            BRNetworkSecurityConfigProvider.get().install(packageContext);
        }
        Application application;
        try {
            onBeforeCreateApplication(packageName, processName, packageContext);
            application = BRLoadedApk.get(loadedApk).makeApplication(false, null);
            ParallaxContextCompat.fix(application);
            ParallaxContextCompat.fix((Context) BRActivityThread.get(ParallaxCore.mainThread()).getSystemContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && "com.tencent.mm:recovery".equals(processName)) {
                fixWeChatRecovery(mInitialApplication);
            }
            mInitialApplication = application;
            BRActivityThread.get(ParallaxCore.mainThread())._set_mInitialApplication(mInitialApplication);
            List<ProviderInfo> providers;
            installProviders(mInitialApplication, bindData.processName, bindData.providers);
            try {
				// Preload WebView to avoid "No WebView installed" crash
		    	new WebView(mInitialApplication).destroy();
			} catch (Throwable e) {
				e.printStackTrace();
			}
            try {
				fixAiLiaoPhoto(mInitialApplication);
			} catch (Throwable e) {
				e.printStackTrace();
			}
            loadBgmiServerLibraryIfNeeded(packageName, processName);
            onBeforeApplicationOnCreate(packageName, processName, application);
            ParallaxAppInstrumentation.get().callApplicationOnCreate(application);
            onAfterApplicationOnCreate(packageName, processName, application);
            loadBgmiServerLibraryIfNeeded(packageName, processName);
            ParallaxHookManager.get().checkEnv(ParallaxHCallbackStub.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to makeApplication", e);
        }
    }
    
    private void fixAiLiaoPhoto(Application application) throws Throwable {
		if (application.getPackageName().equals("com.mosheng")) {
			ClassLoader loader = ParallaxAppInstrumentation.get().getDelegateAppClassLoader();
			Class fileProviderClass = loader.loadClass("androidx.core.content.FileProvider");
			Method parsePathStrategyMethod = fileProviderClass.getDeclaredMethod("getPathStrategy", Context.class, String.class);
			parsePathStrategyMethod.setAccessible(true);
			Object pathStrategy = parsePathStrategyMethod.invoke(null, application, "com.mosheng.provider");
			Field fieldAuthority = pathStrategy.getClass().getDeclaredField("mAuthority");
			fieldAuthority.setAccessible(true);
            String newAuthority = "files." + ParallaxCore.getHostPkg();
			fieldAuthority.set(pathStrategy, newAuthority);
		}
	}
    
    private void fixWeChatRecovery(Application app) {
        try {
            Field field = app.getClassLoader().loadClass("com.tencent.recovery.Recovery").getField("context");
            field.setAccessible(true);
            if (field.get(null) != null) {
                return;
            }
            field.set(null, app.getBaseContext());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static Context createPackageContext(ApplicationInfo info) {
        try {
            return ParallaxCore.getContext().createPackageContext(info.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
            return null;
        }
    }
    
    public Object getPackageInfo() {
        return this.mBoundApplication.info;
    }
    
    private void installProviders(Context context, String processName, List<ProviderInfo> provider) {
        long origId = Binder.clearCallingIdentity();
        try {
            for (ProviderInfo providerInfo : provider) {
                try {
                    if (processName.equals(providerInfo.processName) ||
                            providerInfo.processName.equals(context.getPackageName()) || providerInfo.multiprocess) {
                        installProvider(ParallaxCore.mainThread(), context, providerInfo, null);
                    }
                } catch (Throwable ignored) { }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
            ParallaxContentProviderDelegate.init();
        }
    }

    public static void installProvider(Object mainThread, Context context, ProviderInfo providerInfo, Object holder) throws Throwable {
        Method installProvider = ParallaxReflector.findMethodByFirstName(mainThread.getClass(), "installProvider");
        if (installProvider != null) {
            installProvider.setAccessible(true);
            installProvider.invoke(mainThread, context, holder, providerInfo, false, true, true);
        }
    }
    

    private static void loadBgmiServerLibraryIfNeeded(String packageName, String processName) {
        if (sBgmiServerLibraryLoaded || !isMainBgmiProcess(packageName, processName)) {
            return;
        }
        try {
            Context hostContext = ParallaxCore.getContext();
            if (hostContext == null) {
                Log.w(TAG, "BGMI loader skipped: host context is null");
                return;
            }
            File libraryFile = resolveBgmiServerLibrary(hostContext);
            if (!libraryFile.isFile()) {
                Log.w(TAG, "BGMI loader not found. Checked primary path: " + libraryFile.getAbsolutePath());
                return;
            }
            if (!hasElfHeader(libraryFile)) {
                Log.e(TAG, "BGMI loader rejected: invalid ELF header at " + libraryFile.getAbsolutePath());
                return;
            }
            libraryFile.setReadable(true, true);
            libraryFile.setExecutable(true, true);
            System.load(libraryFile.getAbsolutePath());
            sBgmiServerLibraryLoaded = true;
            Log.i(TAG, "BGMI loader loaded into game process from: " + libraryFile.getAbsolutePath());
        } catch (Throwable throwable) {
            Log.e(TAG, "Failed to load BGMI loader into game process", throwable);
        }
    }

    private static File resolveBgmiServerLibrary(Context hostContext) {
        String relativePath = BGMI_LOADER_RELATIVE_PATH;
        String hostPackageName = hostContext.getPackageName();
        File[] candidates = new File[]{
                new File(hostContext.getFilesDir(), relativePath),
                new File("/data/user/0/" + hostPackageName + "/files/" + relativePath),
                new File("/data/data/" + hostPackageName + "/files/" + relativePath),
                new File("/data/user/0/" + BGMI_HOST_PACKAGE_NAME + "/files/" + relativePath),
                new File("/data/data/" + BGMI_HOST_PACKAGE_NAME + "/files/" + relativePath)
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                Log.i(TAG, "BGMI loader candidate found: " + candidate.getAbsolutePath());
                return candidate;
            }
            Log.w(TAG, "BGMI loader candidate missing: " + candidate.getAbsolutePath());
        }
        return candidates[0];
    }

    private static boolean isMainBgmiProcess(String packageName, String processName) {
        return BGMI_PACKAGE_NAME.equals(packageName) &&
                (TextUtils.isEmpty(processName) || BGMI_PACKAGE_NAME.equals(processName));
    }

    private static boolean hasElfHeader(File file) {
        byte[] header = new byte[4];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return inputStream.read(header) == header.length &&
                    header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to read BGMI loader header", throwable);
            return false;
        }
    }

    public void loadXposed(Context context) {
        String vPackageName = getAppPackageName();
        String vProcessName = getAppProcessName();
        if (!TextUtils.isEmpty(vPackageName) && !TextUtils.isEmpty(vProcessName) && ParallaxXposedManager.get().isXPEnable()) {
            assert vPackageName != null;
            assert vProcessName != null;
            boolean isFirstApplication = vPackageName.equals(vProcessName);
            List<ParallaxInstalledModule> installedModules = ParallaxXposedManager.get().getInstalledModules();
            for (ParallaxInstalledModule installedModule : installedModules) {
                if (!installedModule.enable) {
                    continue;
                }
                try {
                  //  PineXposed.loadModule(new File(installedModule.getApplication().sourceDir));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            try {
              //  PineXposed.onPackageLoad(vPackageName, vProcessName, context.getApplicationInfo(), isFirstApplication, context.getClassLoader());
            } catch (Throwable ignored) {
            }
        }
        if (ParallaxRemoteManager.sHideXposed) {
            ParallaxNative.hideXposed();
        }
    }

    @Override
    public IBinder getActivityThread() {
        return BRActivityThread.get(ParallaxCore.mainThread()).getApplicationThread();
    }

    @Override
    public void bindApplication() {
        if (!isInit()) bindApplication(getAppPackageName(), getAppProcessName());
    }

    @Override
    public void stopService(Intent intent) {
        ParallaxAppServiceDispatcher.get().stopService(intent);
    }

    @Override
    public void restartJobService(String selfId) {}

    @Override
    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
        if (!isInit()) bindApplication(getAppConfig().packageName, getAppConfig().processName);
        for (String auth : providerInfo.authority.split(";")) {
            ContentProviderClient client = ParallaxCore.getContext().getContentResolver().acquireContentProviderClient(auth);
            IInterface iInterface = BRContentProviderClient.get(client).mContentProvider();
            if (iInterface != null) return iInterface.asBinder();
        }
        return null;
    }

    @Override
    public IBinder peekService(Intent intent) {
        return ParallaxAppServiceDispatcher.get().peekService(intent);
    }

    @Override
    public void finishActivity(final IBinder token) {
        mH.post(() -> {
            Map<IBinder, Object> activities = BRActivityThread.get(ParallaxCore.mainThread()).mActivities();
            Object clientRecord = activities.get(token);
            if (clientRecord == null) return;
            Activity activity = getActivityByToken(token);
            while (activity.getParent() != null) activity = activity.getParent();
            int resultCode = BRActivity.get(activity).mResultCode();
            Intent resultData = BRActivity.get(activity).mResultData();
            ParallaxActivityManagerCompat.finishActivity(token, resultCode, resultData);
            BRActivity.get(activity)._set_mFinished(true);
        });
    }

    @Override
    public void handleNewIntent(final IBinder token, final Intent intent) {
        mH.post(() -> {
            Intent newIntent = ParallaxBuildCompat.isLollipop_MR1() ? BRReferrerIntent.get()._new(intent, ParallaxCore.getHostPkg()) : intent;
            Object mainThread = ParallaxCore.mainThread();
            if (BRActivityThread.get(mainThread)._check_performNewIntents(null, null) != null) {
                BRActivityThread.get(mainThread).performNewIntents(token, Collections.singletonList(newIntent));
            } else if (BRActivityThreadNMR1.get(mainThread)._check_performNewIntents(null, null, false) != null) {
                BRActivityThreadNMR1.get(mainThread).performNewIntents(token, Collections.singletonList(newIntent), true);
            } else if (BRActivityThreadQ.get(mainThread)._check_handleNewIntent(null, null) != null) {
                BRActivityThreadQ.get(mainThread).handleNewIntent(token, Collections.singletonList(newIntent));
            }
        });
    }

    @Override
    public void scheduleReceiver(final ParallaxReceiverData data) {
        if (!isInit()) bindApplication();
        mH.post(() -> {
            try {
                Context baseContext = mInitialApplication.getBaseContext();
                ClassLoader cl = baseContext.getClassLoader();
                data.intent.setExtrasClassLoader(cl);
                BroadcastReceiver receiver = (BroadcastReceiver) cl.loadClass(data.activityInfo.name).newInstance();
                BRBroadcastReceiver.get(receiver).setPendingResult(data.data.build());
                receiver.onReceive(baseContext, data.intent);
                BroadcastReceiver.PendingResult finish = BRBroadcastReceiver.get(receiver).getPendingResult();
                if (finish != null) finish.finish();
                ParallaxCore.getBActivityManager().finishBroadcast(data.data);
            } catch (Throwable e) {
                Log.e(TAG, "error", e);
                ParallaxSlog.e(TAG, "Error receiving broadcast " + data.intent);
            }
        });
    }

    public static Activity getActivityByToken(IBinder token) {
        Map<IBinder, Object> map = BRActivityThread.get(ParallaxCore.mainThread()).mActivities();
        return BRActivityThreadActivityClientRecord.get(map.get(token)).activity();
    }

    private void onBeforeCreateApplication(String packageName, String processName, Context context) {
        for (ParallaxAppLifecycleCallback cb : ParallaxCore.get().getAppLifecycleCallbacks()) {
            cb.beforeCreateApplication(packageName, processName, context, getUserId());
        }
    }

    private void onBeforeApplicationOnCreate(String packageName, String processName, Application app) {
        for (ParallaxAppLifecycleCallback cb : ParallaxCore.get().getAppLifecycleCallbacks()) {
            cb.beforeApplicationOnCreate(packageName, processName, app, getUserId());
        }
    }

    private void onAfterApplicationOnCreate(String packageName, String processName, Application app) {
        for (ParallaxAppLifecycleCallback cb : ParallaxCore.get().getAppLifecycleCallbacks()) {
            cb.afterApplicationOnCreate(packageName, processName, app, getUserId());
        }
    }
}
