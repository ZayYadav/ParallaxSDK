package top.niunaijun.blackbox.app;

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
import android.MetaCore.RemoteManager;
import android.webkit.WebView;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import black.android.app.ActivityThreadAppBindDataContext;
import black.android.app.BRActivity;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import black.android.app.BRActivityThreadAppBindData;
import black.android.app.BRActivityThreadNMR1;
import black.android.app.BRActivityThreadQ;
import black.android.app.BRContextImpl;
import black.android.app.BRLoadedApk;
import black.android.app.BRService;
import black.android.content.BRBroadcastReceiver;
import black.android.content.BRContentProviderClient;
import black.android.graphics.BRCompatibility;
import black.android.security.net.config.BRNetworkSecurityConfigProvider;
import black.com.android.internal.content.BRReferrerIntent;
import black.dalvik.system.BRVMRuntime;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.dispatcher.AppServiceDispatcher;
import top.niunaijun.blackbox.core.CrashHandler;
import top.niunaijun.blackbox.core.IBActivityThread;
import top.niunaijun.blackbox.core.RCore;
import top.niunaijun.blackbox.core.RNative;
import top.niunaijun.blackbox.core.env.VirtualRuntime;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.entity.am.ReceiverData;
import top.niunaijun.blackbox.entity.pm.InstalledModule;
import top.niunaijun.blackbox.fake.delegate.AppInstrumentation;
import top.niunaijun.blackbox.fake.delegate.ContentProviderDelegate;
import top.niunaijun.blackbox.fake.frameworks.BXposedManager;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.service.HCallbackStub;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;
import top.niunaijun.blackbox.utils.compat.StrictModeCompat;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class BActivityThread extends IBActivityThread.Stub {
    public static final String TAG = "BActivityThread";
    private static final Object mConfigLock = new Object();
    private static volatile BActivityThread sBActivityThread;
    private AppConfig mAppConfig;
    private AppBindData mBoundApplication;
    private Application mInitialApplication;
    private final List<ProviderInfo> mProviders = new ArrayList<>();
    private static final long BIND_APPLICATION_TIMEOUT_MS = 30_000L;
    private final Handler mH = BlackBoxCore.get().getHandler();

    public static class AppBindData {
        ApplicationInfo appInfo;
        Object info;
        String processName;
        List<ProviderInfo> providers;
    }

    public static boolean isThreadInit() {
        return sBActivityThread != null;
    }

    public static BActivityThread currentActivityThread() {
        if (sBActivityThread == null) {
            synchronized (BActivityThread.class) {
                if (sBActivityThread == null) {
                    sBActivityThread = new BActivityThread();
                }
            }
        }
        return sBActivityThread;
    }

    public static AppConfig getAppConfig() {
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
        return getAppConfig() == null ? BUserHandle.AID_APP_START : getAppConfig().buid;
    }

    public static int getBAppId() {
        return BUserHandle.getAppId(BlackBoxCore.getHostUid());
    }

    public static int getCallingBUid() {
        return getAppConfig() == null ? BlackBoxCore.getHostUid() : getAppConfig().callingBUid;
    }

    public static int getUid() {
        return getAppConfig() == null ? -1 : getAppConfig().uid;
    }

    public static int getUserId() {
        return getAppConfig() == null ? 0 : getAppConfig().userId;
    }

    public void initProcess(AppConfig appConfig) {
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
                            synchronized (BActivityThread.mConfigLock) {
                                try {
                                    iBinder.linkToDeath(this, 0);
                                } catch (RemoteException e) {
                                    // ignore
                                }
                                BActivityThread.this.mAppConfig = null;
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
            Context context = BlackBoxCore.getContext().createPackageContext(serviceInfo.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(context, BlackBoxCore.mainThread(), serviceInfo.name,token, this.mInitialApplication, BRActivityManagerNative.get().getDefault());
            ContextCompat.fix(context);
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
            Context context = BlackBoxCore.getContext().createPackageContext(serviceInfo.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(context, BlackBoxCore.mainThread(), serviceInfo.name,getActivityThread(), this.mInitialApplication, BRActivityManagerNative.get().getDefault());
            ContextCompat.fix(context);
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
            BlackBoxCore.get().getHandler().post(() -> {
                try {
                    handleBindApplication(packageName, processName);
                } finally {
                    conditionVariable.open();
                }
            });
            if (!conditionVariable.block(BIND_APPLICATION_TIMEOUT_MS)) {
                throw new IllegalStateException("Timed out binding " + packageName + " in process " + processName);
            }
        } else {
            handleBindApplication(packageName, processName);
        }
    }

    public synchronized void handleBindApplication(String packageName, String processName) {
        if (isInit())
            return;
        try {
            CrashHandler.create();
        } catch (Throwable ignored) {
        }
        Binder.clearCallingIdentity();
        PackageInfo packageInfo = BlackBoxCore.getBPackageManager().getPackageInfo(packageName, PackageManager.GET_PROVIDERS, BActivityThread.getUserId());
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            throw new IllegalStateException("Unable to resolve package info for " + packageName);
        }
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        if (packageInfo.providers == null) {
            packageInfo.providers = new ProviderInfo[]{};
        }
        mProviders.addAll(Arrays.asList(packageInfo.providers));
        Object boundApplication = BRActivityThread.get(BlackBoxCore.mainThread()).mBoundApplication();
        Context packageContext = createPackageContext(applicationInfo);
        if (packageContext == null) {
            throw new IllegalStateException("Unable to create package context for " + packageName);
        }
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
                StrictModeCompat.disableDeathOnFileUriExposure();
            }
        }
        
        setupWebViewDataDirectory(packageName, processName);
        
        VirtualRuntime.setupRuntime(processName, applicationInfo);
        BRVMRuntime.get(BRVMRuntime.get().getRuntime()).setTargetSdkVersion(applicationInfo.targetSdkVersion);
        if (BuildCompat.isS()) {
            BRCompatibility.get().setTargetSdkVersion(applicationInfo.targetSdkVersion);
        }
        RNative.init(Build.VERSION.SDK_INT);
        assert packageContext != null;
        RCore.get().enableRedirect(packageContext);
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
            ContextCompat.fix(application);
            ContextCompat.fix((Context) BRActivityThread.get(BlackBoxCore.mainThread()).getSystemContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && "com.tencent.mm:recovery".equals(processName)) {
                fixWeChatRecovery(mInitialApplication);
            }
            mInitialApplication = application;
            BRActivityThread.get(BlackBoxCore.mainThread())._set_mInitialApplication(mInitialApplication);
            installProviders(mInitialApplication, bindData.processName, bindData.providers);
            preloadWebViewIfNeeded(packageName);
            try {
				fixAiLiaoPhoto(mInitialApplication);
			} catch (Throwable e) {
				e.printStackTrace();
			}
            onBeforeApplicationOnCreate(packageName, processName, application);
            AppInstrumentation.get().callApplicationOnCreate(application);
            onAfterApplicationOnCreate(packageName, processName, application);
            RNative.loadExternalGameSdkIfReady(packageName, processName);
            HookManager.get().checkEnv(HCallbackStub.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to makeApplication", e);
        }
    }
    
    private void setupWebViewDataDirectory(String packageName, String processName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || isGamePackage(packageName)) {
            return;
        }
        try {
            WebView.setDataDirectorySuffix(getUserId() + "_" + packageName + "_" + processName);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to configure WebView data directory for " + packageName, e);
        }
    }

    private void preloadWebViewIfNeeded(String packageName) {
        if (isGamePackage(packageName)) {
            return;
        }
        try {
            new WebView(mInitialApplication).destroy();
        } catch (Throwable e) {
            Log.w(TAG, "WebView preload skipped", e);
        }
    }

    private boolean isGamePackage(String packageName) {
        return packageName != null && (
                packageName.startsWith("com.pubg.") ||
                packageName.startsWith("com.tencent.ig") ||
                packageName.startsWith("com.krafton.") ||
                packageName.startsWith("com.rekoo.") ||
                packageName.startsWith("com.vng.") ||
                packageName.startsWith("com.proximabeta.") ||
                packageName.startsWith("com.activision."));
    }

    private void fixAiLiaoPhoto(Application application) throws Throwable {
		if (application.getPackageName().equals("com.mosheng")) {
			ClassLoader loader = AppInstrumentation.get().getDelegateAppClassLoader();
			Class fileProviderClass = loader.loadClass("androidx.core.content.FileProvider");
			Method parsePathStrategyMethod = fileProviderClass.getDeclaredMethod("getPathStrategy", Context.class, String.class);
			parsePathStrategyMethod.setAccessible(true);
			Object pathStrategy = parsePathStrategyMethod.invoke(null, application, "com.mosheng.provider");
			Field fieldAuthority = pathStrategy.getClass().getDeclaredField("mAuthority");
			fieldAuthority.setAccessible(true);
            String newAuthority = "files." + BlackBoxCore.getHostPkg();
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
            return BlackBoxCore.getContext().createPackageContext(info.packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
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
                        installProvider(BlackBoxCore.mainThread(), context, providerInfo, null);
                    }
                } catch (Throwable ignored) { }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
            ContentProviderDelegate.init();
        }
    }

    public static void installProvider(Object mainThread, Context context, ProviderInfo providerInfo, Object holder) throws Throwable {
        Method installProvider = Reflector.findMethodByFirstName(mainThread.getClass(), "installProvider");
        if (installProvider != null) {
            installProvider.setAccessible(true);
            installProvider.invoke(mainThread, context, holder, providerInfo, false, true, true);
        }
    }
    
    public void loadXposed(Context context) {
        String vPackageName = getAppPackageName();
        String vProcessName = getAppProcessName();
        if (!TextUtils.isEmpty(vPackageName) && !TextUtils.isEmpty(vProcessName) && BXposedManager.get().isXPEnable()) {
            assert vPackageName != null;
            assert vProcessName != null;
            boolean isFirstApplication = vPackageName.equals(vProcessName);
            List<InstalledModule> installedModules = BXposedManager.get().getInstalledModules();
            for (InstalledModule installedModule : installedModules) {
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
        if (RemoteManager.sHideXposed) {
            RNative.hideXposed();
        }
    }

    @Override
    public IBinder getActivityThread() {
        return BRActivityThread.get(BlackBoxCore.mainThread()).getApplicationThread();
    }

    @Override
    public void bindApplication() {
        if (!isInit()) bindApplication(getAppPackageName(), getAppProcessName());
    }

    @Override
    public void stopService(Intent intent) {
        AppServiceDispatcher.get().stopService(intent);
    }

    @Override
    public void restartJobService(String selfId) {}

    @Override
    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
        if (!isInit()) bindApplication(getAppConfig().packageName, getAppConfig().processName);
        for (String auth : providerInfo.authority.split(";")) {
            ContentProviderClient client = BlackBoxCore.getContext().getContentResolver().acquireContentProviderClient(auth);
            IInterface iInterface = BRContentProviderClient.get(client).mContentProvider();
            if (iInterface != null) return iInterface.asBinder();
        }
        return null;
    }

    @Override
    public IBinder peekService(Intent intent) {
        return AppServiceDispatcher.get().peekService(intent);
    }

    @Override
    public void finishActivity(final IBinder token) {
        mH.post(() -> {
            Map<IBinder, Object> activities = BRActivityThread.get(BlackBoxCore.mainThread()).mActivities();
            Object clientRecord = activities.get(token);
            if (clientRecord == null) return;
            Activity activity = getActivityByToken(token);
            while (activity.getParent() != null) activity = activity.getParent();
            int resultCode = BRActivity.get(activity).mResultCode();
            Intent resultData = BRActivity.get(activity).mResultData();
            ActivityManagerCompat.finishActivity(token, resultCode, resultData);
            BRActivity.get(activity)._set_mFinished(true);
        });
    }

    @Override
    public void handleNewIntent(final IBinder token, final Intent intent) {
        mH.post(() -> {
            Intent newIntent = BuildCompat.isLollipop_MR1() ? BRReferrerIntent.get()._new(intent, BlackBoxCore.getHostPkg()) : intent;
            Object mainThread = BlackBoxCore.mainThread();
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
    public void scheduleReceiver(final ReceiverData data) {
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
                BlackBoxCore.getBActivityManager().finishBroadcast(data.data);
            } catch (Throwable e) {
                Log.e(TAG, "error", e);
                Slog.e(TAG, "Error receiving broadcast " + data.intent);
            }
        });
    }

    public static Activity getActivityByToken(IBinder token) {
        Map<IBinder, Object> map = BRActivityThread.get(BlackBoxCore.mainThread()).mActivities();
        return BRActivityThreadActivityClientRecord.get(map.get(token)).activity();
    }

    private void onBeforeCreateApplication(String packageName, String processName, Context context) {
        for (AppLifecycleCallback cb : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            cb.beforeCreateApplication(packageName, processName, context, getUserId());
        }
    }

    private void onBeforeApplicationOnCreate(String packageName, String processName, Application app) {
        for (AppLifecycleCallback cb : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            cb.beforeApplicationOnCreate(packageName, processName, app, getUserId());
        }
    }

    private void onAfterApplicationOnCreate(String packageName, String processName, Application app) {
        for (AppLifecycleCallback cb : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            cb.afterApplicationOnCreate(packageName, processName, app, getUserId());
        }
    }
}
