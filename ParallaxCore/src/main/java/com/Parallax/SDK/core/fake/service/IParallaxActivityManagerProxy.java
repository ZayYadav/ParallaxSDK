package com.Parallax.SDK.core.fake.service;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.app.IServiceConnection;
import android.app.Notification;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import com.Parallax.SDK.mirror.android.app.BRActivityManagerNative;
import com.Parallax.SDK.mirror.android.app.BRActivityManagerOreo;
import com.Parallax.SDK.mirror.android.app.BRLoadedApkReceiverDispatcher;
import com.Parallax.SDK.mirror.android.app.BRLoadedApkReceiverDispatcherInnerReceiver;
import com.Parallax.SDK.mirror.android.app.BRLoadedApkServiceDispatcher;
import com.Parallax.SDK.mirror.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import com.Parallax.SDK.mirror.android.content.BRContentProviderNative;
import com.Parallax.SDK.mirror.android.content.pm.BRUserInfo;
import com.Parallax.SDK.mirror.android.util.BRSingleton;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.env.ParallaxAppSystemEnv;
import com.Parallax.SDK.core.core.system.ParallaxDaemonService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.ParallaxAppConfig;
import com.Parallax.SDK.core.entity.am.ParallaxRunningAppProcessInfo;
import com.Parallax.SDK.core.entity.am.ParallaxRunningServiceInfo;
import com.Parallax.SDK.core.fake.delegate.ParallaxContentProviderDelegate;
import com.Parallax.SDK.core.fake.delegate.ParallaxInnerReceiverDelegate;
import com.Parallax.SDK.core.fake.delegate.ParallaxServiceConnectionDelegate;
import com.Parallax.SDK.core.fake.frameworks.ParallaxActivityManager;
import com.Parallax.SDK.core.fake.frameworks.ParallaxPackageManager;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethods;
import com.Parallax.SDK.core.fake.hook.ParallaxScanClass;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.fake.service.context.providers.ParallaxContentProviderStub;
import com.Parallax.SDK.core.fake.service.context.providers.ParallaxSystemProviderStub;
import com.Parallax.SDK.core.proxy.ParallaxProxyManifest;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyBroadcastRecord;
import com.Parallax.SDK.core.proxy.record.ParallaxProxyPendingRecord;
import com.Parallax.SDK.core.utils.ParallaxArrayUtils;
import com.Parallax.SDK.core.utils.ParallaxComponentUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxReflector;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxActivityManagerCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxParceledListSliceCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxTaskDescriptionCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxVirtualPermissionCompat;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@ParallaxScanClass(ParallaxActivityManagerCommonProxy.class)
public class IParallaxActivityManagerProxy extends ParallaxClassInvocationStub {
    public static final String TAG = "ActivityManagerStub";

    @Override
    protected Object getWho() {
        Object iActivityManager = null;
        if (ParallaxBuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (ParallaxBuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        return BRSingleton.get(iActivityManager).get();
    }

    @Override
    protected void inject(Object base, Object proxy) {
        Object iActivityManager = null;
        if (ParallaxBuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (ParallaxBuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        BRSingleton.get(iActivityManager)._set_mInstance(proxy);
    }

    @Override
    public boolean isBadEnv() {
        return getProxyInvocation() != getWho();
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxPkgMethodProxy("getAppStartMode"));
        addMethodHook(new ParallaxPkgMethodProxy("setAppLockedVerifying"));
        addMethodHook(new ParallaxPkgMethodProxy("reportJunkFromApp"));
    }

    @ParallaxProxyMethod("getContentProvider")
	public static class GetContentProvider extends ParallaxMethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Exception {

			int authIndex = getAuthIndex();
			Object auth = args[authIndex];

			if (!(auth instanceof String)) {
				return method.invoke(who, args);
			}

			String authority = (String) auth;

			if (ParallaxProxyManifest.isProxy(authority)) {
				return method.invoke(who, args);
			}

			if (ParallaxBuildCompat.isQ() || ParallaxBuildCompat.isR()) {
				if (args.length > 1 && args[1] instanceof String) {
					args[1] = ParallaxCore.getHostPkg();
				}
			}

			if ("settings".equals(authority) || "media".equals(authority) || "telephony".equals(authority)) {
				Object content = method.invoke(who, args);
				if (content != null) {
					ParallaxContentProviderDelegate.update(content, authority);
				}
				return content;
			}

			ProviderInfo providerInfo = ParallaxCore.getBPackageManager().resolveContentProvider(authority, ParallaxFileUtils.FileMode.MODE_IWUSR, ParallaxActivityThread.getUserId());

			if (providerInfo == null) {
				return method.invoke(who, args);
			}

			IBinder providerBinder = null;

			if (ParallaxActivityThread.getAppPid() != -1) {

				ParallaxAppConfig appConfig = ParallaxCore.getBActivityManager().initProcess(providerInfo.packageName, providerInfo.processName, ParallaxActivityThread.getUserId());

				if (appConfig == null) {
					return method.invoke(who, args);
				}

				if (appConfig.bpid != ParallaxActivityThread.getAppPid()) {
					providerBinder = ParallaxCore.getBActivityManager().acquireContentProviderClient(providerInfo);
				}

				if (providerBinder == null) {
					return method.invoke(who, args);
				}

				args[authIndex] = ParallaxProxyManifest.getProxyAuthorities(appConfig.bpid);

				int userIndex = getUserIndex();
				if (args.length > userIndex) {
					args[userIndex] = ParallaxCore.getHostUserId();
				}
			}

			Object content = method.invoke(who, args);

			if (content == null) {
				return null;
			}

			try {
				ParallaxReflector.with(content).field("info").set(providerInfo);
			} catch (Throwable ignored) { }

			try {
				ParallaxReflector.with(content).field("provider").set(new ParallaxContentProviderStub().wrapper(BRContentProviderNative.get().asInterface(providerBinder),ParallaxActivityThread.getAppPackageName()));
			} catch (Throwable ignored) { }
			return content;
		}

		private int getAuthIndex() {
			if (ParallaxBuildCompat.isQ() || ParallaxBuildCompat.isR()) {
				return 2;
			}
			return 1;
		}

		private int getUserIndex() {
			return getAuthIndex() + 1;
		}
	}
    
    @ParallaxProxyMethod("startService")
    public static class StartService extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[1];
            String resolvedType = (String) args[2];
            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveService(intent, 0, resolvedType, ParallaxActivityThread.getUserId());
            if (resolveInfo == null) {
                return method.invoke(who, args);
            }

            int requireForegroundIndex = getRequireForeground();
            boolean requireForeground = false;
            if (requireForegroundIndex != -1) {
                requireForeground = (boolean) args[requireForegroundIndex];
            }
            return ParallaxCore.getBActivityManager().startService(intent, resolvedType, requireForeground, ParallaxActivityThread.getUserId());
        }

        public int getRequireForeground() {
            if (ParallaxBuildCompat.isOreo()) {
                return 3;
            }
            return -1;
        }
    }

    @ParallaxProxyMethod("stopService")
    public static class StopService extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[1];
            String resolvedType = (String) args[2];
            return ParallaxCore.getBActivityManager().stopService(intent, resolvedType, ParallaxActivityThread.getUserId());
        }
    }

    @ParallaxProxyMethod("stopServiceToken")
    public static class StopServiceToken extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            IBinder token = (IBinder) args[1];
            ParallaxCore.getBActivityManager().stopServiceToken(componentName, token, ParallaxActivityThread.getUserId());
            return true;
        }
    }
    
    //TODO 待修复
    @ParallaxProxyMethod("bindService")
    public static class BindService extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[2];
            String resolvedType = (String) args[3];
            IServiceConnection connection = (IServiceConnection) args[4];

            int userId = intent.getIntExtra("_G_|_UserId", -1);
            userId = userId == -1 ? ParallaxActivityThread.getUserId() : userId;
            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveService(intent, 0, resolvedType, userId);
            if (resolveInfo != null || ParallaxAppSystemEnv.isOpenPackage(intent.getComponent())) {
                Intent bindService = ParallaxCore.getBActivityManager().bindService(intent,connection == null ? null : connection.asBinder(),resolvedType,userId);
                if (connection != null) {
                    if (intent.getComponent() == null && resolveInfo != null) {
                        intent.setComponent(new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name));
                    }
                    IServiceConnection proxy = ParallaxServiceConnectionDelegate.createProxy(connection, intent);
                    args[4] = proxy;

                    WeakReference<?> weakReference = BRLoadedApkServiceDispatcherInnerConnection.get(connection).mDispatcher();
                    if (weakReference != null) {
                        BRLoadedApkServiceDispatcher.get(weakReference.get())._set_mConnection(proxy);
                    }
                }
                if (bindService != null) {
                    args[2] = bindService;
                    return method.invoke(who, args);
                }
            }
            return 0;
        }

        @Override
        protected boolean isEnable() {
            return ParallaxCore.get().isBlackProcess() || ParallaxCore.get().isServerProcess();
        }
    }

    //android 13.0变更
    @ParallaxProxyMethod("bindServiceInstance")
    public static class BindServiceInstance extends BindIsolatedService {

    }
    

    // 10.0
    @ParallaxProxyMethod("bindIsolatedService")
    public static class BindIsolatedService extends BindService {
        @Override
        protected Object beforeHook(Object who, Method method, Object[] args) throws Throwable {
            // instanceName
            args[6] = null;
            return super.beforeHook(who, method, args);
        }
    }

    @ParallaxProxyMethod("unbindService")
    public static class UnbindService extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IServiceConnection iServiceConnection = (IServiceConnection) args[0];
            if (iServiceConnection == null) {
                return method.invoke(who, args);
            }
            ParallaxCore.getBActivityManager().unbindService(iServiceConnection.asBinder(), ParallaxActivityThread.getUserId());
            ParallaxServiceConnectionDelegate delegate = ParallaxServiceConnectionDelegate.getDelegate(iServiceConnection.asBinder());
            if (delegate != null) {
                args[0] = delegate;
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getRunningAppProcesses")
    public static class GetRunningAppProcesses extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxRunningAppProcessInfo runningAppProcesses = ParallaxActivityManager.get().getRunningAppProcesses(ParallaxActivityThread.getAppPackageName(), ParallaxActivityThread.getUserId());
            if (runningAppProcesses == null) {
                return new ArrayList<>();
            }
            return runningAppProcesses.mAppProcessInfoList;
        }
    }

    @ParallaxProxyMethod("getServices")
    public static class GetServices extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxRunningServiceInfo runningServices = ParallaxActivityManager.get().getRunningServices(ParallaxActivityThread.getAppPackageName(), ParallaxActivityThread.getUserId());
            if (runningServices == null) {
                return new ArrayList<>();
            }
            return runningServices.mRunningServiceInfoList;
        }
    }

    @ParallaxProxyMethod("getIntentSender")
    public static class GetIntentSender extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int type = (int) args[0];
            Intent[] intents = (Intent[]) args[getIntentsIndex(args)];
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);

            for (int i = 0; i < intents.length; i++) {
                Intent intent = intents[i];
                switch (type) {
                    case ParallaxActivityManagerCompat.INTENT_SENDER_ACTIVITY:
                        Intent shadow = new Intent();
                        shadow.setComponent(new ComponentName(ParallaxCore.getHostPkg(), ParallaxProxyManifest.getProxyPendingActivity(ParallaxActivityThread.getAppPid())));
                        ParallaxProxyPendingRecord.saveStub(shadow, intent, ParallaxActivityThread.getUserId());
                        intents[i] = shadow;
                        break;
                }
            }

            // Android 12 (API 31) compat: PendingIntent requires FLAG_IMMUTABLE or FLAG_MUTABLE
            if (ParallaxBuildCompat.isS()) {
                int flagsIndex = getIntentsIndex(args) + 2;
                if (flagsIndex < args.length && args[flagsIndex] instanceof Integer) {
                    int flags = (int) args[flagsIndex];
                    if ((flags & (0x4000000 | 0x2000000)) == 0) {
                        flags |= 0x4000000; // FLAG_IMMUTABLE
                        args[flagsIndex] = flags;
                    }
                }
            }

            IInterface invoke = (IInterface) method.invoke(who, args);
            if (invoke != null) {
                String[] packagesForUid = ParallaxPackageManager.get().getPackagesForUid(ParallaxActivityThread.getCallingBUid());
                if (packagesForUid.length < 1) {
                    packagesForUid = new String[]{ParallaxCore.getHostPkg()};
                }
                ParallaxCore.getBActivityManager().getIntentSender(invoke.asBinder(), packagesForUid[0], ParallaxActivityThread.getCallingBUid());
            }
            return invoke;
        }

        private int getIntentsIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent[]) {
                    return i;
                }
            }
            if (ParallaxBuildCompat.isR()) {
                return 6;
            } else {
                return 5;
            }
        }
    }

    @ParallaxProxyMethod("getPackageForIntentSender")
    public static class getPackageForIntentSender extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return ParallaxCore.getBActivityManager().getPackageForIntentSender(invoke.asBinder());
        }
    }

    @ParallaxProxyMethod("getUidForIntentSender")
    public static class getUidForIntentSender extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return ParallaxCore.getBActivityManager().getUidForIntentSender(invoke.asBinder());
        }
    }

    @ParallaxProxyMethod("getIntentSenderWithSourceToken")
    public static class GetIntentSenderWithSourceToken extends GetIntentSender {
    }

    @ParallaxProxyMethod("getIntentSenderWithFeature")
    public static class GetIntentSenderWithFeature extends GetIntentSender {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("broadcastIntent")
    public static class BroadcastIntent extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = getIntentIndex(args);
            Intent intent = (Intent) args[intentIndex];
            String resolvedType = (String) args[intentIndex + 1];

            Intent proxyIntent = ParallaxCore.getBActivityManager().sendBroadcast(intent, resolvedType, ParallaxActivityThread.getUserId());
            if (proxyIntent != null) {
                proxyIntent.setExtrasClassLoader(ParallaxActivityThread.getApplication().getClassLoader());

                ParallaxProxyBroadcastRecord.saveStub(proxyIntent, intent, ParallaxActivityThread.getUserId());
                args[intentIndex] = proxyIntent;
            }
            // ignore permission
            for (int i = 0; i < args.length; i++) {
                Object o = args[i];
                if (o instanceof String[]) {
                    args[i] = null;
                }
            }
            return method.invoke(who, args);
        }

        int getIntentIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Intent) {
                    return i;
                }
            }
            return 1;
        }
    }

    @ParallaxProxyMethod("unregisterReceiver")
    public static class unregisterReceiver extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("finishReceiver")
    public static class finishReceiver extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("publishService")
    public static class PublishService extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("peekService")
    public static class PeekService extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            return ParallaxCore.getBActivityManager().peekService(intent, resolvedType, ParallaxActivityThread.getUserId());
        }
    }

    // todo
    @ParallaxProxyMethod("sendIntentSender")
    public static class SendIntentSender extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    // android 10
    @ParallaxProxyMethod("registerReceiverWithFeature")
    public static class RegisterReceiverWithFeature extends RegisterReceiver {

    }

    @ParallaxProxyMethod("registerReceiver")
    public static class RegisterReceiver extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            int receiverIndex = getReceiverIndex();
            if (args[receiverIndex] != null) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = ParallaxInnerReceiverDelegate.createProxy(intentReceiver);

                WeakReference<?> weakReference = BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                if (weakReference != null) {
                    BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
                }

                args[receiverIndex] = proxy;
            }
            // ignore permission
            if (args[getPermissionIndex()] != null) {
                args[getPermissionIndex()] = null;
            }
            return method.invoke(who, args);
        }

        public int getReceiverIndex() {
            if (ParallaxBuildCompat.isS()) {
                return 4;
            } else if (ParallaxBuildCompat.isR()) {
                return 3;
            }
            return 2;
        }

        public int getPermissionIndex() {
            if (ParallaxBuildCompat.isS()) {
                return 6;
            } else if (ParallaxBuildCompat.isR()) {
                return 5;
            }
            return 4;
        }
    }

    //这里需要修复
    @ParallaxProxyMethod("grantUriPermission")
    public static class GrantUriPermission extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastUid(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setServiceForeground")
    public static class setServiceForeground extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ParallaxProxyMethod("getHistoricalProcessExitReasons")
    public static class getHistoricalProcessExitReasons extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ParallaxProxyMethod("getCurrentUser")
    public static class getCurrentUser extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BRUserInfo.get()._new(ParallaxActivityThread.getUserId(), "ParallaxCore", BRUserInfo.get().FLAG_PRIMARY());
        }
    }

    /**
     * Android 16 routes Context permission checks through
     * IActivityManager.checkPermissionForDevice instead of checkPermission.
     * Hook both names so install-time network permissions declared by the guest
     * remain visible to SDKs without granting any dangerous/runtime permission.
     */
    @ParallaxProxyMethods({"checkPermission", "checkPermissionForDevice"})
    public static class checkPermission extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            if (ParallaxVirtualPermissionCompat.shouldGrantDeclaredNetworkPermission(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            replacePermissionCheckUid(args);
            if (Manifest.permission.ACCOUNT_MANAGER.equals(permission)
                    || Manifest.permission.SEND_SMS.equals(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }

        private static void replacePermissionCheckUid(Object[] args) {
            // Both signatures place uid at index 2. On Android 16 the last int is
            // deviceId, so replaceLastUid() would target the wrong argument.
            if (args == null || args.length <= 2 || !(args[2] instanceof Integer)) {
                return;
            }
            int uid = (Integer) args[2];
            if (uid == ParallaxActivityThread.getBUid()) {
                args[2] = ParallaxCore.getHostUid();
            }
        }
    }

    @ParallaxProxyMethod("checkUriPermission")
    public static class checkUriPermission extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return PackageManager.PERMISSION_GRANTED;
        }
    }

    // for < Android 10
    @ParallaxProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = ParallaxTaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("setRequestedOrientation")
    public static class setRequestedOrientation extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @ParallaxProxyMethod("registerUidObserver")
    public static class registerUidObserver extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ParallaxProxyMethod("unregisterUidObserver")
    public static class unregisterUidObserver extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ParallaxProxyMethod("updateConfiguration")
    public static class updateConfiguration extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

}
