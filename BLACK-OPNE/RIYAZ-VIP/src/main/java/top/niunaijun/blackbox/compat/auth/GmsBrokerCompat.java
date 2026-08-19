package top.niunaijun.blackbox.compat.auth;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

/**
 * Compatibility facade for the real Google Play services broker.
 *
 * Virtual applications run inside the Loader UID. Modern Play services validates
 * the package name carried in GetServiceRequest against Binder.getCallingUid().
 * Sending the virtual package therefore fails on Android 16 because that package
 * is not installed for the Loader UID. This facade only normalizes the broker
 * caller identity to the actual host package/UID before forwarding the request.
 *
 * OAuth client ids, accounts, tokens, signatures and provider responses are never
 * modified here. Provider-side authorization remains authoritative.
 */
public final class GmsBrokerCompat {
    private static final String TAG = "GmsBrokerCompat";
    private static final String BROKER_DESCRIPTOR =
            "com.google.android.gms.common.internal.IGmsServiceBroker";
    private static final String SERVICE_REQUEST_CLASS =
            "com.google.android.gms.common.internal.GetServiceRequest";

    private static final Map<IBinder, IBinder> BINDER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<IBinder, IInterface> BROKER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean sLoggedTypedProxyFailure;

    private GmsBrokerCompat() {
    }

    public static IBinder wrap(IBinder base) {
        if (base == null || !isGmsBroker(base)) {
            return base;
        }

        IBinder cached = BINDER_CACHE.get(base);
        if (cached != null) {
            return cached;
        }

        try {
            IBinder wrapper = (IBinder) Proxy.newProxyInstance(
                    GmsBrokerCompat.class.getClassLoader(),
                    new Class<?>[]{IBinder.class},
                    (proxy, method, args) -> {
                        if ("queryLocalInterface".equals(method.getName())
                                && args != null
                                && args.length == 1
                                && BROKER_DESCRIPTOR.equals(args[0])) {
                            IInterface broker = getOrCreateBrokerProxy(base);
                            if (broker != null) {
                                return broker;
                            }
                        }
                        return invokeBase(base, method, args);
                    });
            BINDER_CACHE.put(base, wrapper);
            return wrapper;
        } catch (Throwable ignored) {
            logTypedProxyFailure(ignored);
            return base;
        }
    }

    private static boolean isGmsBroker(IBinder binder) {
        try {
            return BROKER_DESCRIPTOR.equals(binder.getInterfaceDescriptor());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static IInterface getOrCreateBrokerProxy(IBinder base) {
        IInterface cached = BROKER_CACHE.get(base);
        if (cached != null) {
            return cached;
        }

        try {
            ClassLoader loader = resolveVirtualClassLoader();
            Class<?> brokerInterface = Class.forName(BROKER_DESCRIPTOR, false, loader);
            Class<?> brokerStub = Class.forName(BROKER_DESCRIPTOR + "$Stub", false, loader);
            Method asInterface = brokerStub.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            Object realBroker = asInterface.invoke(null, base);
            if (realBroker == null || !brokerInterface.isInstance(realBroker)) {
                return null;
            }

            Object proxy = Proxy.newProxyInstance(
                    brokerInterface.getClassLoader(),
                    new Class<?>[]{brokerInterface},
                    (ignoredProxy, method, args) -> {
                        if ("asBinder".equals(method.getName())) {
                            return base;
                        }
                        normalizeBrokerArguments(args);
                        return invokeBase(realBroker, method, args);
                    });
            IInterface result = (IInterface) proxy;
            BROKER_CACHE.put(base, result);
            return result;
        } catch (Throwable failure) {
            // R8/minified clients may not expose the source-level broker interface
            // class name even though the Binder descriptor is stable. Do not fall
            // back to raw Parcel rewriting: provider caller verification remains
            // authoritative. Native Activity/IntentSender and OAuth routing can
            // still handle provider-controlled interactive sign-in surfaces.
            logTypedProxyFailure(failure);
            return null;
        }
    }

    private static void logTypedProxyFailure(Throwable failure) {
        if (sLoggedTypedProxyFailure) {
            return;
        }
        synchronized (GmsBrokerCompat.class) {
            if (sLoggedTypedProxyFailure) {
                return;
            }
            sLoggedTypedProxyFailure = true;
            String reason = failure == null ? "unknown" : failure.getClass().getSimpleName();
            Slog.w(TAG, "Typed GMS broker proxy unavailable (" + reason
                    + "); keeping provider verification intact and using external auth routing where supported");
        }
    }

    private static ClassLoader resolveVirtualClassLoader() {
        try {
            if (BActivityThread.getApplication() != null
                    && BActivityThread.getApplication().getClassLoader() != null) {
                return BActivityThread.getApplication().getClassLoader();
            }
        } catch (Throwable ignored) {
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : GmsBrokerCompat.class.getClassLoader();
    }

    private static void normalizeBrokerArguments(Object[] args) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String className = arg.getClass().getName();
            if (SERVICE_REQUEST_CLASS.equals(className)
                    || className.endsWith(".GetServiceRequest")) {
                normalizeServiceRequest(arg);
            }
        }
    }

    private static void normalizeServiceRequest(Object request) {
        final String virtualPackage = BActivityThread.getAppPackageName();
        final String hostPackage = BlackBoxCore.getHostPkg();
        if (request == null || virtualPackage == null || hostPackage == null
                || virtualPackage.equals(hostPackage)) {
            return;
        }

        Class<?> type = request.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(request);
                    if (value instanceof String && virtualPackage.equals(value)) {
                        field.set(request, hostPackage);
                    } else if (value instanceof Bundle) {
                        normalizeBundle((Bundle) value, virtualPackage, hostPackage);
                    } else if (value != null
                            && "android.content.AttributionSource".equals(
                            value.getClass().getName())) {
                        ContextCompat.fixAttributionSourceState(
                                value, BlackBoxCore.getHostUid());
                    }
                } catch (Throwable ignored) {
                    // Play services internals vary by version. Normalize the fields
                    // that are accessible and leave every other field untouched.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void normalizeBundle(
            Bundle bundle, String virtualPackage, String hostPackage) {
        if (bundle == null) {
            return;
        }
        try {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof String && virtualPackage.equals(value)) {
                    bundle.putString(key, hostPackage);
                } else if (value != null
                        && "android.content.AttributionSource".equals(
                        value.getClass().getName())) {
                    ContextCompat.fixAttributionSourceState(
                            value, BlackBoxCore.getHostUid());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object invokeBase(Object base, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(base, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getTargetException();
            throw cause != null ? cause : failure;
        }
    }
}
