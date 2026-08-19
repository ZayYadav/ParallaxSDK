package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Keeps Android 16 Google measurement compatibility metadata visible through
 * the same PackageManager instance used by code inside a virtual application.
 *
 * This wrapper delegates every package-manager operation to the existing
 * BlackBox IPackageManager proxy and only post-processes ApplicationInfo /
 * PackageInfo results. It never changes package names, UIDs, signatures or
 * provider authentication results.
 */
public final class VirtualPackageMetadataCompat {
    private VirtualPackageMetadataCompat() {
    }

    public static void install(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 36) {
            return;
        }
        try {
            // Cover direct Context.getApplicationInfo() reads before providers
            // initialize.
            GmsCore.applyVirtualAppGmsSafety(context.getApplicationInfo());

            PackageManager packageManager = context.getPackageManager();
            if (packageManager == null) {
                return;
            }

            Reflector mPmField = Reflector.on("android.app.ApplicationPackageManager")
                    .field("mPM");
            Object current = mPmField.get(packageManager);
            if (current == null || isOurProxy(current)) {
                return;
            }

            Class<?>[] interfaces = MethodParameterUtils.getAllInterface(current.getClass());
            if (interfaces.length == 0) {
                return;
            }

            ClassLoader loader = current.getClass().getClassLoader();
            if (loader == null) {
                loader = VirtualPackageMetadataCompat.class.getClassLoader();
            }
            Object proxy = Proxy.newProxyInstance(
                    loader,
                    interfaces,
                    new MetadataInvocationHandler(current));
            mPmField.set(packageManager, proxy);
        } catch (Throwable ignored) {
            // OEM/private API differences must not stop app startup.
        }
    }

    private static boolean isOurProxy(Object candidate) {
        if (candidate == null || !Proxy.isProxyClass(candidate.getClass())) {
            return false;
        }
        try {
            return Proxy.getInvocationHandler(candidate) instanceof MetadataInvocationHandler;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MetadataInvocationHandler implements InvocationHandler {
        private final Object delegate;

        private MetadataInvocationHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException invocationFailure) {
                Throwable cause = invocationFailure.getTargetException();
                throw cause != null ? cause : invocationFailure;
            }

            if (result instanceof ApplicationInfo) {
                return GmsCore.applyVirtualAppGmsSafety((ApplicationInfo) result);
            }
            if (result instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) result;
                GmsCore.applyVirtualAppGmsSafety(packageInfo.applicationInfo);
                return packageInfo;
            }
            return result;
        }
    }
}
