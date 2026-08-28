package top.niunaijun.blackbox.fake.service;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;
import top.niunaijun.blackbox.compat.auth.SocialPermissionCompat;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

/**
 * ActivityManager compatibility for real, installed auth providers.
 *
 * The important rule is that a validated provider Activity is dispatched directly
 * by Android with the original resultTo/requestCode binder arguments untouched.
 * We do not wrap a normal provider Activity inside another Loader Activity. That
 * keeps Google/Facebook/Twitter ActivityResult semantics as close as possible to
 * a normal Android launch while the provider package/signature remains real.
 */
public final class IAuthActivityManagerProxy extends IActivityManagerProxy {
    private static final String AUTH_TAG = "ParallaxAuthDirect";

    @Override
    public void injectHook() {
        super.injectHook();

        // ClassInvocationStub scans only the runtime subclass. Restore the normal
        // ActivityManager and common hooks, then install the two narrow overrides.
        for (Class<?> declaredClass : IActivityManagerProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }
        for (Class<?> declaredClass : ActivityManagerCommonProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }

        addMethodHook("startActivity", new DirectTrustedProviderStart());
        addMethodHook("checkPermission", new CheckPermission());
    }

    private static final class DirectTrustedProviderStart
            extends ActivityManagerCommonProxy.StartActivity {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            if (intent != null && ExternalAuthRouter.isTrustedProviderIntent(intent)) {
                ExternalAuthRouter.clearDirectProviderDispatch(intent);

                // Binder caller is the Loader UID, so Android's calling-package
                // argument must stay paired with that real UID. Provider-owned
                // OAuth/client metadata inside the Intent is not rewritten.
                MethodParameterUtils.replaceFirstAppPkg(args);
                ContextCompat.fix(BActivityThread.getApplication());

                Slog.d(AUTH_TAG, "direct provider activity: "
                        + ExternalAuthRouter.getTrustedProviderPackage(intent)
                        + " / " + intent.getComponent());
                return method.invoke(who, args);
            }
            return super.hook(who, method, args);
        }
    }

    private static final class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = findPermission(args);
            if (SocialPermissionCompat.guestDeclaresNormalNetworkPermission(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }

            MethodParameterUtils.replaceLastUid(args);
            if (Manifest.permission.ACCOUNT_MANAGER.equals(permission)
                    || Manifest.permission.SEND_SMS.equals(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    private static String findPermission(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof String
                    && ((String) arg).startsWith("android.permission.")) {
                return (String) arg;
            }
        }
        return null;
    }
}
