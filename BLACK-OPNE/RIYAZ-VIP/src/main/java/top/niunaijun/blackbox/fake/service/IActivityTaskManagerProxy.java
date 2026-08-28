package top.niunaijun.blackbox.fake.service;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.app.BRActivityTaskManager;
import black.android.app.BRIActivityTaskManagerStub;
import black.android.os.BRServiceManager;
import black.android.util.BRSingleton;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.compat.oauth.VirtualOAuthRouter;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.compat.TaskDescriptionCompat;

/**
 * Created by @RIYAZXERO
 * Android 10 (API 29) -> Android 16 (API 35+)
 * Fully compatible & crash-safe
 */
@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityTaskManagerProxy extends BinderInvocationStub {

    public static final String TAG = "ActivityTaskManager";

    public IActivityTaskManagerProxy() {
        super(BRServiceManager.get().getService("activity_task"));
    }

    @Override
    protected Object getWho() {
        return BRIActivityTaskManagerStub.get().asInterface(BRServiceManager.get().getService("activity_task"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("activity_task");
        BRActivityTaskManager.get().getService();
        Object singleton = BRActivityTaskManager.get().IActivityTaskManagerSingleton();
        BRSingleton.get(singleton)._set_mInstance(BRIActivityTaskManagerStub.get().asInterface(this));
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    /**
     * Some modern framework/provider clients do not use the plain startActivity
     * binder entry point. Keep provider-owned Google/Play Games/Facebook/X flows
     * on the same hardened bridge for the alternate ActivityTaskManager variants.
     *
     * This never makes an arbitrary external intent trusted: native provider
     * launches still have to resolve to ExternalAuthRouter's allow-list and web
     * OAuth still has to pass VirtualOAuthRouter's HTTPS/callback validation.
     */
    @ProxyMethods({
            "startActivityAsUser",
            "startActivityAsCaller",
            "startActivityWithConfig",
            "startActivityWithFeature"
    })
    public static class AuthStartActivityVariants extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (method == null || args == null) {
                return method == null ? 0 : method.invoke(who, args);
            }

            int intentIndex = findIntentIndex(method, args);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }

            Intent intent = (Intent) args[intentIndex];
            if (intent == null) {
                return method.invoke(who, args);
            }

            if (ExternalAuthRouter.isDirectProviderDispatch(intent)) {
                ExternalAuthRouter.clearDirectProviderDispatch(intent);
                if (ExternalAuthRouter.isTrustedProviderIntent(intent)) {
                    prepareHostLaunch(method, args);
                    return method.invoke(who, args);
                }
                // A virtual app can forge the marker extra. Do not turn that
                // marker into authorization for an untrusted external target.
                return method.invoke(who, args);
            }

            IBinder resultTo = findResultTo(method, args, intentIndex);
            int resultToIndex = findParameterIndexAfter(
                    method, args, intentIndex, IBinder.class);
            int resultWhoIndex = findStringParameterAfter(method, resultToIndex);
            String resultWho = valueAsString(args, resultWhoIndex);
            int requestCode = findIntValueAfter(method, args, resultWhoIndex);

            Intent providerBridge = ExternalAuthRouter.createResultBridgeIntent(
                    intent,
                    resultTo,
                    resultWho,
                    requestCode,
                    BActivityThread.getAppPackageName());
            if (providerBridge != null) {
                args[intentIndex] = providerBridge;
                prepareHostLaunch(method, args);
                return method.invoke(who, args);
            }

            Intent oauthBridge = VirtualOAuthRouter.createBridgeIntent(
                    intent,
                    BActivityThread.getUserId(),
                    BActivityThread.getAppPackageName());
            if (oauthBridge != null) {
                args[intentIndex] = oauthBridge;
                prepareHostLaunch(method, args);
                return method.invoke(who, args);
            }

            return method.invoke(who, args);
        }

        private static void prepareHostLaunch(Method method, Object[] args) {
            MethodParameterUtils.replaceFirstAppPkg(args);
            String name = method == null ? "" : method.getName();
            if (name.contains("AsUser")
                    || name.contains("AsCaller")
                    || name.contains("WithConfig")) {
                MethodParameterUtils.replaceLastUserId(args);
            }
        }

        private static int findIntentIndex(Method method, Object[] args) {
            if (method != null) {
                Class<?>[] types = method.getParameterTypes();
                int count = Math.min(types.length, args.length);
                for (int i = 0; i < count; i++) {
                    if (Intent.class.isAssignableFrom(types[i]) && args[i] instanceof Intent) {
                        return i;
                    }
                }
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    return i;
                }
            }
            return -1;
        }

        private static IBinder findResultTo(Method method, Object[] args, int intentIndex) {
            int index = findParameterIndexAfter(method, args, intentIndex, IBinder.class);
            return index >= 0 && args[index] instanceof IBinder ? (IBinder) args[index] : null;
        }

        private static int findParameterIndexAfter(
                Method method, Object[] args, int afterIndex, Class<?> expectedType) {
            if (method == null || args == null || expectedType == null) {
                return -1;
            }
            Class<?>[] types = method.getParameterTypes();
            int count = Math.min(types.length, args.length);
            for (int i = Math.max(0, afterIndex + 1); i < count; i++) {
                if (expectedType.isAssignableFrom(types[i])) {
                    return i;
                }
            }
            return -1;
        }

        private static int findStringParameterAfter(Method method, int afterIndex) {
            if (method == null) {
                return -1;
            }
            Class<?>[] types = method.getParameterTypes();
            for (int i = Math.max(0, afterIndex + 1); i < types.length; i++) {
                if (String.class.equals(types[i])) {
                    return i;
                }
            }
            return -1;
        }

        private static int findIntValueAfter(
                Method method, Object[] args, int afterIndex) {
            if (method == null || args == null) {
                return -1;
            }
            Class<?>[] types = method.getParameterTypes();
            int count = Math.min(types.length, args.length);
            for (int i = Math.max(0, afterIndex + 1); i < count; i++) {
                if ((int.class.equals(types[i]) || Integer.class.equals(types[i]))
                        && args[i] instanceof Integer) {
                    return (Integer) args[i];
                }
            }
            return -1;
        }

        private static String valueAsString(Object[] args, int index) {
            return args != null && index >= 0 && index < args.length && args[index] instanceof String
                    ? (String) args[index] : null;
        }
    }

    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {

            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ActivityManager.TaskDescription) {
                        ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[i];
                        args[i] = TaskDescriptionCompat.fix(td);
                        break;
                    }
                }
            }

            return method.invoke(who, args);
        }
    }
}
