package top.niunaijun.blackbox.fake.service;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import black.android.app.BRLoadedApkServiceDispatcher;
import black.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.compat.auth.ExternalAuthServiceConnectionDelegate;
import top.niunaijun.blackbox.compat.oauth.VirtualOAuthRouter;
import top.niunaijun.blackbox.core.env.AppSystemEnv;
import top.niunaijun.blackbox.fake.delegate.ServiceConnectionDelegate;
import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.fake.provider.FileProviderHandler;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;
import top.niunaijun.blackbox.utils.compat.StartActivityCompat;

/**
 * Created by @RIYAZXERO on 3/30/21.
 */
public class ActivityManagerCommonProxy {

    public static final String TAG = "ActivityManagerCommonProxy";

    @ProxyMethod("startActivity")
    public static class StartActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);

            if (intent == null) {
                Slog.e(TAG, "Intent is null, calling original method");
                return method.invoke(who, args);
            }

            if (ExternalAuthRouter.isDirectProviderDispatch(intent)) {
                // The marker lives in an Intent extra and can therefore be set by
                // virtual applications too. Never treat it as authorization on
                // its own: only the provider intent created by our bridge may
                // leave the virtual namespace directly.
                ExternalAuthRouter.clearDirectProviderDispatch(intent);
                if (ExternalAuthRouter.isTrustedProviderIntent(intent)) {
                    return method.invoke(who, args);
                }
            }

            if (intent.getParcelableExtra("_G_|_target_") != null) {
                return method.invoke(who, args);
            }
            if (ComponentUtils.isRequestInstall(intent)) {
                File file = FileProviderHandler.convertFile(BActivityThread.getApplication(), intent.getData());
                if (BlackBoxCore.get().requestInstallPackage(file)) {
                    intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                    return method.invoke(who, args);
                }
                intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + BActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + BlackBoxCore.getHostPkg()));
            }

            Intent externalAuthBridge = ExternalAuthRouter.createResultBridgeIntent(
                    intent,
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    BActivityThread.getAppPackageName());
            if (externalAuthBridge != null) {
                replaceIntent(args, externalAuthBridge);
                return method.invoke(who, args);
            }

            Intent oauthBridge = VirtualOAuthRouter.createBridgeIntent(
                    intent,
                    BActivityThread.getUserId(),
                    BActivityThread.getAppPackageName());
            if (oauthBridge != null) {
                replaceIntent(args, oauthBridge);
                return method.invoke(who, args);
            }

            Slog.d(TAG, "Hook in : " + intent);

            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                    intent,
                    FileUtils.FileMode.MODE_IWUSR,
                    StartActivityCompat.getResolvedType(args),
                    BActivityThread.getUserId());
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(BActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                        intent,
                        FileUtils.FileMode.MODE_IWUSR,
                        StartActivityCompat.getResolvedType(args),
                        BActivityThread.getUserId());
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);
                    return method.invoke(who, args);
                }
            }

            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            BlackBoxCore.getBActivityManager().startActivityAms(
                    BActivityThread.getUserId(),
                    StartActivityCompat.getIntent(args),
                    StartActivityCompat.getResolvedType(args),
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    StartActivityCompat.getFlags(args),
                    StartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            if (args == null) return null;
            int index = BuildCompat.isR() ? 3 : 2;
            if (index < args.length && args[index] instanceof Intent) {
                return (Intent) args[index];
            }
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    return (Intent) arg;
                }
            }
            return null;
        }

        private void replaceIntent(Object[] args, Intent replacement) {
            if (args == null || replacement == null) return;
            int index = BuildCompat.isR() ? 3 : 2;
            if (index < args.length && args[index] instanceof Intent) {
                args[index] = replacement;
                return;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    args[i] = replacement;
                    return;
                }
            }
        }
    }

    @ProxyMethod("startActivities")
    public static class StartActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            if (!ComponentUtils.isSelf(intents)) {
                return method.invoke(who, args);
            }

            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return BlackBoxCore.getBActivityManager().startActivities(
                    BActivityThread.getUserId(), intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            return BuildCompat.isR() ? 3 : 2;
        }
    }

    /**
     * Registered after IActivityManagerProxy's own bind hooks via @ScanClass, so
     * this compatibility hook becomes authoritative for all bindService variants.
     * Android has changed these signatures repeatedly, so arguments are located
     * by type instead of assuming fixed slots. Real auth providers are handed to
     * Android with the real caller package/user and their IServiceConnection is
     * wrapped to normalize real GMS broker requests.
     */
    @ProxyMethods({"bindService", "bindServiceInstance", "bindIsolatedService"})
    public static class BindServiceCompat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null) {
                return method.invoke(who, args);
            }

            int intentIndex = MethodParameterUtils.getIndex(args, Intent.class);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }
            Intent intent = (Intent) args[intentIndex];

            int connectionIndex = MethodParameterUtils.getIndex(args, IServiceConnection.class);
            IServiceConnection connection = connectionIndex < 0
                    ? null : (IServiceConnection) args[connectionIndex];

            if (AppSystemEnv.isOpenPackage(intent)) {
                // Refresh the outbound Context right at the provider boundary.
                // This is best-effort and keeps Android 16 package/UID attribution
                // paired even when a provider client cached Context state earlier.
                ContextCompat.fix(BActivityThread.getApplication());

                if (connection != null) {
                    IServiceConnection proxy =
                            ExternalAuthServiceConnectionDelegate.createProxy(connection);
                    args[connectionIndex] = proxy;
                    replaceLoadedApkConnection(connection, proxy);
                }

                intent.removeExtra("_G_|_UserId");
                MethodParameterUtils.replaceAllAppPkg(args);
                MethodParameterUtils.replaceLastUserId(args);
                return method.invoke(who, args);
            }

            if (connectionIndex < 0) {
                return method.invoke(who, args);
            }

            String resolvedType = null;
            int resolvedTypeIndex = intentIndex + 1;
            if (resolvedTypeIndex < args.length && args[resolvedTypeIndex] instanceof String) {
                resolvedType = (String) args[resolvedTypeIndex];
            }

            int userId = intent.getIntExtra("_G_|_UserId", -1);
            userId = userId == -1 ? BActivityThread.getUserId() : userId;
            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveService(
                    intent, 0, resolvedType, userId);
            if (resolveInfo == null) {
                return 0;
            }

            Intent bindService = BlackBoxCore.getBActivityManager().bindService(
                    intent,
                    connection == null ? null : connection.asBinder(),
                    resolvedType,
                    userId);

            if (connection != null) {
                if (intent.getComponent() == null) {
                    intent.setComponent(new ComponentName(
                            resolveInfo.serviceInfo.packageName,
                            resolveInfo.serviceInfo.name));
                }
                IServiceConnection proxy = ServiceConnectionDelegate.createProxy(connection, intent);
                args[connectionIndex] = proxy;
                replaceLoadedApkConnection(connection, proxy);
            }

            if (bindService != null) {
                args[intentIndex] = bindService;
                return method.invoke(who, args);
            }
            return 0;
        }

        private static void replaceLoadedApkConnection(
                IServiceConnection original, IServiceConnection proxy) {
            if (original == null || proxy == null) {
                return;
            }
            try {
                WeakReference<?> weakReference =
                        BRLoadedApkServiceDispatcherInnerConnection.get(original).mDispatcher();
                if (weakReference != null && weakReference.get() != null) {
                    BRLoadedApkServiceDispatcher.get(weakReference.get())._set_mConnection(proxy);
                }
            } catch (Throwable ignored) {
                // OEM framework internals can vary; the actual system bind still
                // receives the proxy even when this local dispatcher field differs.
            }
        }
    }

    /**
     * Android's binder method is startActivityIntentSender. Older compatibility
     * layers exposed the public API name startIntentSenderForResult, so register
     * both. Only provider-owned senders with an allow-listed Android creator are
     * launched outside the virtual namespace.
     */
    @ProxyMethods({"startActivityIntentSender", "startIntentSenderForResult"})
    public static class StartIntentSenderForResult extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null || method == null) {
                return method == null ? 0 : method.invoke(who, args);
            }

            int targetIndex = findIntentSenderTargetIndex(method, args);
            int fillInIndex = findIntentParameterIndex(method, targetIndex);
            if (targetIndex < 0 || fillInIndex < 0) {
                return method.invoke(who, args);
            }

            IntentSender sender = ExternalAuthRouter.wrapIntentSender(args[targetIndex]);
            if (!ExternalAuthRouter.isTrustedProviderIntentSender(sender)) {
                return method.invoke(who, args);
            }

            int resultToIndex = fillInIndex + 2;
            int resultWhoIndex = fillInIndex + 3;
            int requestCodeIndex = fillInIndex + 4;
            int flagsMaskIndex = fillInIndex + 5;
            int flagsValuesIndex = fillInIndex + 6;
            int optionsIndex = fillInIndex + 7;
            if (optionsIndex >= args.length
                    || !(args[resultToIndex] instanceof IBinder)
                    || !(args[requestCodeIndex] instanceof Integer)
                    || !(args[flagsMaskIndex] instanceof Integer)
                    || !(args[flagsValuesIndex] instanceof Integer)) {
                return method.invoke(who, args);
            }

            IBinder resultTo = (IBinder) args[resultToIndex];
            String resultWho = args[resultWhoIndex] instanceof String
                    ? (String) args[resultWhoIndex] : null;
            int requestCode = (Integer) args[requestCodeIndex];
            int flagsMask = (Integer) args[flagsMaskIndex];
            int flagsValues = (Integer) args[flagsValuesIndex];
            Intent fillInIntent = args[fillInIndex] instanceof Intent
                    ? (Intent) args[fillInIndex] : null;
            Bundle options = args[optionsIndex] instanceof Bundle
                    ? (Bundle) args[optionsIndex] : null;

            Intent bridge = ExternalAuthRouter.createIntentSenderBridgeIntent(
                    sender,
                    fillInIntent,
                    flagsMask,
                    flagsValues,
                    options,
                    resultTo,
                    resultWho,
                    requestCode,
                    BActivityThread.getAppPackageName());
            if (bridge == null) {
                return method.invoke(who, args);
            }

            try {
                ContextCompat.fix(BActivityThread.getApplication());
                BlackBoxCore.getContext().startActivity(bridge);
                return 0;
            } catch (Throwable ignored) {
                return method.invoke(who, args);
            }
        }

        private static int findIntentSenderTargetIndex(Method method, Object[] args) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            int count = Math.min(parameterTypes.length, args.length);
            for (int i = 0; i < count; i++) {
                Class<?> type = parameterTypes[i];
                String typeName = type == null ? "" : type.getName();
                if (IntentSender.class.equals(type)
                        || "android.content.IIntentSender".equals(typeName)) {
                    return i;
                }
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof IntentSender || implementsIntentSenderInterface(arg)) {
                    return i;
                }
            }
            return -1;
        }

        private static int findIntentParameterIndex(Method method, int targetIndex) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = Math.max(0, targetIndex + 1); i < parameterTypes.length; i++) {
                if (Intent.class.equals(parameterTypes[i])) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean implementsIntentSenderInterface(Object value) {
            if (value == null) {
                return false;
            }
            try {
                Class<?> type = value.getClass();
                while (type != null && type != Object.class) {
                    for (Class<?> iface : type.getInterfaces()) {
                        if ("android.content.IIntentSender".equals(iface.getName())) {
                            return true;
                        }
                    }
                    type = type.getSuperclass();
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /**
     * Preserve the real creator metadata for external provider PendingIntents.
     * Virtual metadata is returned only when BlackBox actually owns the sender.
     */
    @ProxyMethod("getPackageForIntentSender")
    public static class GetPackageForIntentSenderCompat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof IInterface) {
                try {
                    String virtualPackage = BActivityManager.get().getPackageForIntentSender(
                            ((IInterface) args[0]).asBinder());
                    if (virtualPackage != null && !virtualPackage.trim().isEmpty()) {
                        return virtualPackage;
                    }
                } catch (Throwable ignored) {
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getUidForIntentSender")
    public static class GetUidForIntentSenderCompat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof IInterface) {
                try {
                    int virtualUid = BActivityManager.get().getUidForIntentSender(
                            ((IInterface) args[0]).asBinder());
                    if (virtualUid >= 0) {
                        return virtualUid;
                    }
                } catch (Throwable ignored) {
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAppTasks")
    public static class GetAppTasks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCallingPackage")
    public static class getCallingPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingPackage(
                    (IBinder) args[0], BActivityThread.getUserId());
        }
    }

    @ProxyMethod("getCallingActivity")
    public static class getCallingActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingActivity(
                    (IBinder) args[0], BActivityThread.getUserId());
        }
    }
}
