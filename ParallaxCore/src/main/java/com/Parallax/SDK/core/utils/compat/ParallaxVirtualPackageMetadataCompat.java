package com.Parallax.SDK.core.utils.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.Parallax.SDK.core.core.ParallaxGmsCore;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxReflector;

/**
 * Keeps Android 16 Google measurement compatibility metadata visible through
 * the same PackageManager instance used by code inside a virtual application.
 *
 * This wrapper delegates every package-manager operation to the existing
 * BlackBox IPackageManager proxy and only post-processes ApplicationInfo /
 * PackageInfo results. It never changes package names, UIDs, signatures or
 * provider authentication results.
 */
public final class ParallaxVirtualPackageMetadataCompat {
    private ParallaxVirtualPackageMetadataCompat() {
    }

    public static void install(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 36) {
            return;
        }
        try {
            // Cover direct Context.getApplicationInfo() reads before providers
            // initialize.
            ParallaxGmsCore.applyVirtualAppGmsSafety(context.getApplicationInfo());

            PackageManager packageManager = context.getPackageManager();
            if (packageManager == null) {
                return;
            }

            ParallaxReflector mPmField = ParallaxReflector.on("android.app.ApplicationPackageManager")
                    .field("mPM");
            Object current = mPmField.get(packageManager);
            if (current == null || isOurProxy(current)) {
                return;
            }

            Class<?>[] interfaces = ParallaxMethodParameterUtils.getAllInterface(current.getClass());
            if (interfaces.length == 0) {
                return;
            }

            ClassLoader loader = current.getClass().getClassLoader();
            if (loader == null) {
                loader = ParallaxVirtualPackageMetadataCompat.class.getClassLoader();
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
            if (method != null && "checkPermission".equals(method.getName())) {
                String permission = args != null && args.length > 0
                        && args[0] instanceof String ? (String) args[0] : null;
                String requestedPackage = args != null && args.length > 1
                        && args[1] instanceof String ? (String) args[1] : null;
                if (requestedPackage != null
                        && ParallaxVirtualPermissionCompat.shouldGrantDeclaredNetworkPermission(
                        permission, requestedPackage)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
            }

            final Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException invocationFailure) {
                Throwable cause = invocationFailure.getTargetException();
                throw cause != null ? cause : invocationFailure;
            }

            if (result instanceof ApplicationInfo) {
                return ParallaxGmsCore.applyVirtualAppGmsSafety((ApplicationInfo) result);
            }
            if (result instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) result;
                ParallaxGmsCore.applyVirtualAppGmsSafety(packageInfo.applicationInfo);
                return packageInfo;
            }
            return result;
        }
    }
}
