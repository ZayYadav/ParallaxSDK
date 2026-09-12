package com.Parallax.SDK.core.fake.service;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.BRActivityTaskManager;
import com.Parallax.SDK.mirror.android.app.BRIActivityTaskManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.util.BRSingleton;

import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.compat.auth.ParallaxExternalAuthRouter;
import com.Parallax.SDK.core.compat.oauth.ParallaxVirtualOAuthRouter;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethods;
import com.Parallax.SDK.core.fake.hook.ParallaxScanClass;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxTaskDescriptionCompat;

/**
 * Created by @RIYAZXERO
 * Android 10 (API 29) -> Android 16 (API 35+)
 * Fully compatible & crash-safe
 */
@ParallaxScanClass(ParallaxActivityManagerCommonProxy.class)
public class IParallaxActivityTaskManagerProxy extends ParallaxBinderInvocationStub {

    public static final String TAG = "ActivityTaskManager";

    public IParallaxActivityTaskManagerProxy() {
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
     * launches still have to resolve to ParallaxExternalAuthRouter's allow-list and web
     * OAuth still has to pass ParallaxVirtualOAuthRouter's HTTPS/callback validation.
     */
    @ParallaxProxyMethods({
            "startActivityAsUser",
            "startActivityAsCaller",
            "startActivityWithConfig",
            "startActivityWithFeature"
    })
    public static class AuthStartActivityVariants extends ParallaxMethodHook {
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

            if (ParallaxExternalAuthRouter.isDirectProviderDispatch(intent)) {
                ParallaxExternalAuthRouter.clearDirectProviderDispatch(intent);
                if (ParallaxExternalAuthRouter.isTrustedProviderIntent(intent)) {
                    prepareHostLaunch(method, args);
                    return method.invoke(who, args);
                }
                // A virtual app can forge the marker extra. Fail closed instead
                // of letting an untrusted target escape the virtual namespace.
                return 0;
            }

            IBinder resultTo = findResultTo(method, args, intentIndex);
            int resultToIndex = findParameterIndexAfter(
                    method, args, intentIndex, IBinder.class);
            int resultWhoIndex = findStringParameterAfter(method, resultToIndex);
            String resultWho = valueAsString(args, resultWhoIndex);
            int requestCode = findIntValueAfter(method, args, resultWhoIndex);

            Intent providerBridge = ParallaxExternalAuthRouter.createResultBridgeIntent(
                    intent,
                    resultTo,
                    resultWho,
                    requestCode,
                    ParallaxActivityThread.getAppPackageName());
            if (providerBridge != null) {
                args[intentIndex] = providerBridge;
                prepareHostLaunch(method, args);
                return method.invoke(who, args);
            }

            Intent oauthBridge = ParallaxVirtualOAuthRouter.createBridgeIntent(
                    intent,
                    ParallaxActivityThread.getUserId(),
                    ParallaxActivityThread.getAppPackageName());
            if (oauthBridge != null) {
                args[intentIndex] = oauthBridge;
                prepareHostLaunch(method, args);
                return method.invoke(who, args);
            }

            return method.invoke(who, args);
        }

        private static void prepareHostLaunch(Method method, Object[] args) {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            String name = method == null ? "" : method.getName();
            if (name.contains("AsUser")
                    || name.contains("AsCaller")
                    || name.contains("WithConfig")) {
                ParallaxMethodParameterUtils.replaceLastUserId(args);
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

    @ParallaxProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {

            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ActivityManager.TaskDescription) {
                        ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[i];
                        args[i] = ParallaxTaskDescriptionCompat.fix(td);
                        break;
                    }
                }
            }

            return method.invoke(who, args);
        }
    }
}
