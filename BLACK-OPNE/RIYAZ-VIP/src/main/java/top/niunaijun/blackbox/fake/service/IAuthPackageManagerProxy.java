package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.MethodHook;

/**
 * Stable package-manager hook set for virtual apps that use real Android auth
 * providers installed on the phone.
 *
 * Keep IPackageManagerProxy's proven virtual-package behavior, but do not replace
 * Android's queryIntentActivities/queryIntentServices paths with a second filtered
 * merge layer. Those methods are intentionally left unhooked here so the real
 * PackageManager answers them, subject to the final Loader APK's <queries>
 * declarations. This matches the package-discovery behavior used before the
 * social-PM regression while still keeping provider signatures authoritative.
 */
public final class IAuthPackageManagerProxy extends IPackageManagerProxy {

    @Override
    public void injectHook() {
        super.injectHook();

        // ClassInvocationStub scans the runtime subclass only. Re-register the
        // original IPackageManagerProxy annotations so all normal virtual-package
        // hooks remain active.
        for (Class<?> declaredClass : IPackageManagerProxy.class.getDeclaredClasses()) {
            initAnnotation(declaredClass);
        }

        // The legacy parent hook reports SIGNATURE_MATCH unconditionally. That is
        // not appropriate for external Google/Meta/Twitter providers. Real Android
        // remains the source of truth for UID/signature comparison.
        addMethodHook("checkUidSignatures", new RealCheckUidSignatures());
    }

    private static final class RealCheckUidSignatures extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}
