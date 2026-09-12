package com.Parallax.SDK.core.fake.service;

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

import com.Parallax.SDK.mirror.android.app.BRLoadedApkServiceDispatcher;
import com.Parallax.SDK.mirror.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.compat.auth.ParallaxExternalAuthRouter;
import com.Parallax.SDK.core.compat.auth.ParallaxExternalAuthServiceConnectionDelegate;
import com.Parallax.SDK.core.compat.oauth.ParallaxVirtualOAuthRouter;
import com.Parallax.SDK.core.core.env.ParallaxAppSystemEnv;
import com.Parallax.SDK.core.fake.delegate.ParallaxServiceConnectionDelegate;
import com.Parallax.SDK.core.fake.frameworks.ParallaxActivityManager;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethods;
import com.Parallax.SDK.core.fake.provider.ParallaxFileProviderHandler;
import com.Parallax.SDK.core.utils.ParallaxComponentUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxContextCompat;
import com.Parallax.SDK.core.utils.compat.ParallaxStartActivityCompat;

/**
 * Created by @RIYAZXERO on 3/30/21.
 */
public class ParallaxActivityManagerCommonProxy {

    public static final String TAG = "ParallaxActivityManagerCommonProxy";

    @ParallaxProxyMethod("startActivity")
    public static class StartActivity extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);

            if (intent == null) {
                ParallaxSlog.e(TAG, "Intent is null, calling original method");
                return method.invoke(who, args);
            }

            if (ParallaxExternalAuthRouter.isDirectProviderDispatch(intent)) {
                // The marker lives in an Intent extra and can therefore be set by
                // virtual applications too. Never treat it as authorization on
                // its own: only the provider intent created by our bridge may
                // leave the virtual namespace directly.
                ParallaxExternalAuthRouter.clearDirectProviderDispatch(intent);
                if (ParallaxExternalAuthRouter.isTrustedProviderIntent(intent)) {
                    return method.invoke(who, args);
                }
            }

            if (intent.getParcelableExtra("_G_|_target_") != null) {
                return method.invoke(who, args);
            }
            if (ParallaxComponentUtils.isRequestInstall(intent)) {
                File file = ParallaxFileProviderHandler.convertFile(ParallaxActivityThread.getApplication(), intent.getData());
                if (ParallaxCore.get().requestInstallPackage(file)) {
                    intent.setData(ParallaxFileProviderHandler.convertFileUri(ParallaxActivityThread.getApplication(), intent.getData()));
                    return method.invoke(who, args);
                }
                intent.setData(ParallaxFileProviderHandler.convertFileUri(ParallaxActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + ParallaxActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + ParallaxCore.getHostPkg()));
            }

            Intent externalAuthBridge = ParallaxExternalAuthRouter.createResultBridgeIntent(
                    intent,
                    ParallaxStartActivityCompat.getResultTo(args),
                    ParallaxStartActivityCompat.getResultWho(args),
                    ParallaxStartActivityCompat.getRequestCode(args),
                    ParallaxActivityThread.getAppPackageName());
            if (externalAuthBridge != null) {
                replaceIntent(args, externalAuthBridge);
                return method.invoke(who, args);
            }

            Intent oauthBridge = ParallaxVirtualOAuthRouter.createBridgeIntent(
                    intent,
                    ParallaxActivityThread.getUserId(),
                    ParallaxActivityThread.getAppPackageName());
            if (oauthBridge != null) {
                replaceIntent(args, oauthBridge);
                return method.invoke(who, args);
            }

            ParallaxSlog.d(TAG, "Hook in : " + intent);

            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveActivity(
                    intent,
                    ParallaxFileUtils.FileMode.MODE_IWUSR,
                    ParallaxStartActivityCompat.getResolvedType(args),
                    ParallaxActivityThread.getUserId());
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(ParallaxActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = ParallaxCore.getBPackageManager().resolveActivity(
                        intent,
                        ParallaxFileUtils.FileMode.MODE_IWUSR,
                        ParallaxStartActivityCompat.getResolvedType(args),
                        ParallaxActivityThread.getUserId());
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);
                    return method.invoke(who, args);
                }
            }

            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            ParallaxCore.getBActivityManager().startActivityAms(
                    ParallaxActivityThread.getUserId(),
                    ParallaxStartActivityCompat.getIntent(args),
                    ParallaxStartActivityCompat.getResolvedType(args),
                    ParallaxStartActivityCompat.getResultTo(args),
                    ParallaxStartActivityCompat.getResultWho(args),
                    ParallaxStartActivityCompat.getRequestCode(args),
                    ParallaxStartActivityCompat.getFlags(args),
                    ParallaxStartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            if (args == null) return null;
            int index = ParallaxBuildCompat.isR() ? 3 : 2;
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
            int index = ParallaxBuildCompat.isR() ? 3 : 2;
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

    @ParallaxProxyMethod("startActivities")
    public static class StartActivities extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            if (!ParallaxComponentUtils.isSelf(intents)) {
                return method.invoke(who, args);
            }

            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return ParallaxCore.getBActivityManager().startActivities(
                    ParallaxActivityThread.getUserId(), intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            return ParallaxBuildCompat.isR() ? 3 : 2;
        }
    }

    /**
     * Registered after IParallaxActivityManagerProxy's own bind hooks via @ScanClass, so
     * this compatibility hook becomes authoritative for all bindService variants.
     * Android has changed these signatures repeatedly, so arguments are located
     * by type instead of assuming fixed slots. Real auth providers are handed to
     * Android with the real caller package/user and their IServiceConnection is
     * wrapped to normalize real GMS broker requests.
     */
    @ParallaxProxyMethods({"bindService", "bindServiceInstance", "bindIsolatedService"})
    public static class BindServiceCompat extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null) {
                return method.invoke(who, args);
            }

            int intentIndex = ParallaxMethodParameterUtils.getIndex(args, Intent.class);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }
            Intent intent = (Intent) args[intentIndex];

            int connectionIndex = ParallaxMethodParameterUtils.getIndex(args, IServiceConnection.class);
            IServiceConnection connection = connectionIndex < 0
                    ? null : (IServiceConnection) args[connectionIndex];

            if (ParallaxAppSystemEnv.isOpenPackage(intent)) {
                // Refresh the outbound Context right at the provider boundary.
                // This is best-effort and keeps Android 16 package/UID attribution
                // paired even when a provider client cached Context state earlier.
                ParallaxContextCompat.fix(ParallaxActivityThread.getApplication());

                if (connection != null) {
                    IServiceConnection proxy =
                            ParallaxExternalAuthServiceConnectionDelegate.createProxy(connection);
                    args[connectionIndex] = proxy;
                    replaceLoadedApkConnection(connection, proxy);
                }

                intent.removeExtra("_G_|_UserId");
                ParallaxMethodParameterUtils.replaceAllAppPkg(args);
                ParallaxMethodParameterUtils.replaceLastUserId(args);
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
            userId = userId == -1 ? ParallaxActivityThread.getUserId() : userId;
            ResolveInfo resolveInfo = ParallaxCore.getBPackageManager().resolveService(
                    intent, 0, resolvedType, userId);
            if (resolveInfo == null) {
                return 0;
            }

            Intent bindService = ParallaxCore.getBActivityManager().bindService(
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
                IServiceConnection proxy = ParallaxServiceConnectionDelegate.createProxy(connection, intent);
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
    @ParallaxProxyMethods({"startActivityIntentSender", "startIntentSenderForResult"})
    public static class StartIntentSenderForResult extends ParallaxMethodHook {
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

            IntentSender sender = ParallaxExternalAuthRouter.wrapIntentSender(args[targetIndex]);
            if (!ParallaxExternalAuthRouter.isTrustedProviderIntentSender(sender)) {
                return method.invoke(who, args);
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            int resultToIndex = findParameterIndexAfter(
                    parameterTypes, fillInIndex, IBinder.class);
            int resultWhoIndex = findParameterIndexAfter(
                    parameterTypes, resultToIndex, String.class);
            int requestCodeIndex = findIntParameterIndexAfter(
                    parameterTypes, resultWhoIndex);
            int flagsMaskIndex = findIntParameterIndexAfter(
                    parameterTypes, requestCodeIndex);
            int flagsValuesIndex = findIntParameterIndexAfter(
                    parameterTypes, flagsMaskIndex);
            int optionsIndex = findLastParameterIndexAfter(
                    parameterTypes, fillInIndex, Bundle.class);
            if (!validIndex(resultToIndex, args)
                    || !validIndex(resultWhoIndex, args)
                    || !validIndex(requestCodeIndex, args)
                    || !validIndex(flagsMaskIndex, args)
                    || !validIndex(flagsValuesIndex, args)
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
            Bundle options = validIndex(optionsIndex, args)
                    && args[optionsIndex] instanceof Bundle
                    ? (Bundle) args[optionsIndex] : null;

            Intent bridge = ParallaxExternalAuthRouter.createIntentSenderBridgeIntent(
                    sender,
                    fillInIntent,
                    flagsMask,
                    flagsValues,
                    options,
                    resultTo,
                    resultWho,
                    requestCode,
                    ParallaxActivityThread.getAppPackageName());
            if (bridge == null) {
                return method.invoke(who, args);
            }

            try {
                ParallaxContextCompat.fix(ParallaxActivityThread.getApplication());
                ParallaxCore.getContext().startActivity(bridge);
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

        private static int findParameterIndexAfter(
                Class<?>[] parameterTypes, int afterIndex, Class<?> expectedType) {
            if (parameterTypes == null || expectedType == null) {
                return -1;
            }
            for (int i = Math.max(0, afterIndex + 1); i < parameterTypes.length; i++) {
                Class<?> actual = parameterTypes[i];
                if (actual != null && expectedType.isAssignableFrom(actual)) {
                    return i;
                }
            }
            return -1;
        }

        private static int findLastParameterIndexAfter(
                Class<?>[] parameterTypes, int afterIndex, Class<?> expectedType) {
            if (parameterTypes == null || expectedType == null) {
                return -1;
            }
            for (int i = parameterTypes.length - 1; i > afterIndex; i--) {
                Class<?> actual = parameterTypes[i];
                if (actual != null && expectedType.isAssignableFrom(actual)) {
                    return i;
                }
            }
            return -1;
        }

        private static int findIntParameterIndexAfter(
                Class<?>[] parameterTypes, int afterIndex) {
            if (parameterTypes == null) {
                return -1;
            }
            for (int i = Math.max(0, afterIndex + 1); i < parameterTypes.length; i++) {
                if (parameterTypes[i] == int.class || parameterTypes[i] == Integer.class) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean validIndex(int index, Object[] args) {
            return args != null && index >= 0 && index < args.length;
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
    @ParallaxProxyMethod("getPackageForIntentSender")
    public static class GetPackageForIntentSenderCompat extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof IInterface) {
                try {
                    String virtualPackage = ParallaxActivityManager.get().getPackageForIntentSender(
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

    @ParallaxProxyMethod("getUidForIntentSender")
    public static class GetUidForIntentSenderCompat extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof IInterface) {
                try {
                    int virtualUid = ParallaxActivityManager.get().getUidForIntentSender(
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

    @ParallaxProxyMethod("activityResumed")
    public static class ActivityResumed extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("finishActivity")
    public static class FinishActivity extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getAppTasks")
    public static class GetAppTasks extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getCallingPackage")
    public static class getCallingPackage extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxCore.getBActivityManager().getCallingPackage(
                    (IBinder) args[0], ParallaxActivityThread.getUserId());
        }
    }

    @ParallaxProxyMethod("getCallingActivity")
    public static class getCallingActivity extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxCore.getBActivityManager().getCallingActivity(
                    (IBinder) args[0], ParallaxActivityThread.getUserId());
        }
    }
}
