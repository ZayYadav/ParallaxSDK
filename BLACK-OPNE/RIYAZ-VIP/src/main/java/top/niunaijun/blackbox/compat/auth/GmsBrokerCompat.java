package top.niunaijun.blackbox.compat.auth;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
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
@Obfuscate
public final class GmsBrokerCompat {
    private static final String BROKER_DESCRIPTOR =
            "com.google.android.gms.common.internal.IGmsServiceBroker";
    private static final String SERVICE_REQUEST_CLASS =
            "com.google.android.gms.common.internal.GetServiceRequest";
    private static final String BASE_GMS_CLIENT_CLASS =
            "com.google.android.gms.common.internal.BaseGmsClient";
    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final int GET_SERVICE_TRANSACTION = 46;

    private static final Map<IBinder, IBinder> BINDER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<IBinder, IInterface> BROKER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

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
                            IInterface broker = getOrCreateBrokerProxy(base, (IBinder) proxy);
                            if (broker != null) {
                                return broker;
                            }
                        }

                        // R8/modern Play-services clients commonly skip a loadable
                        // IGmsServiceBroker Java interface and talk to the Binder
                        // directly. Handle transaction 46 as well, otherwise the
                        // unmodified virtual package reaches real GMS and Android 16
                        // rejects it as "Unknown calling package name".
                        if ("transact".equals(method.getName())
                                && args != null
                                && args.length >= 4
                                && args[0] instanceof Integer
                                && ((Integer) args[0]) == GET_SERVICE_TRANSACTION
                                && args[1] instanceof Parcel
                                && args[2] instanceof Parcel
                                && args[3] instanceof Integer) {
                            Boolean handled = transactGetServiceParcel(
                                    base,
                                    (Parcel) args[1],
                                    (Parcel) args[2],
                                    (Integer) args[3]);
                            if (handled != null) {
                                return handled;
                            }
                        }
                        return invokeBase(base, method, args);
                    });
            BINDER_CACHE.put(base, wrapper);
            return wrapper;
        } catch (Throwable ignored) {
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

    private static IInterface getOrCreateBrokerProxy(IBinder base, IBinder wrapper) {
        IInterface cached = BROKER_CACHE.get(base);
        if (cached != null) {
            return cached;
        }

        try {
            ClassLoader loader = resolveVirtualClassLoader();
            Class<?> brokerInterface = resolveBrokerInterface(loader);
            if (brokerInterface == null) {
                return null;
            }

            Object proxy = Proxy.newProxyInstance(
                    brokerInterface.getClassLoader(),
                    new Class<?>[]{brokerInterface},
                    (brokerProxy, method, args) -> {
                        if ("asBinder".equals(method.getName())) {
                            return wrapper;
                        }
                        if (method.getDeclaringClass() == Object.class) {
                            return invokeObjectMethod(brokerProxy, method, args);
                        }
                        if (isGetServiceMethod(method, args)) {
                            normalizeBrokerArguments(args);
                            transactGetService(base, args);
                            return null;
                        }
                        throw new UnsupportedOperationException(
                                "Unsupported GMS broker call: " + method.getName());
                    });
            IInterface result = (IInterface) proxy;
            BROKER_CACHE.put(base, result);
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Client applications commonly run R8 over their bundled Play services
     * client. The Binder descriptor remains stable, but IGmsServiceBroker's Java
     * class may become a short name such as common.internal.l. Resolve the actual
     * interface from BaseGmsClient's field/method types instead of assuming that
     * the descriptor is also a loadable class name.
     */
    private static Class<?> resolveBrokerInterface(ClassLoader loader) {
        try {
            Class<?> stable = Class.forName(BROKER_DESCRIPTOR, false, loader);
            if (isBrokerInterface(stable)) {
                return stable;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> baseClient = Class.forName(BASE_GMS_CLIENT_CLASS, false, loader);
            Set<Class<?>> visited = new HashSet<>();
            Class<?> current = baseClient;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    Class<?> match = findBrokerInterface(field.getType(), visited);
                    if (match != null) {
                        return match;
                    }
                }
                for (Method method : current.getDeclaredMethods()) {
                    Class<?> match = findBrokerInterface(method.getReturnType(), visited);
                    if (match != null) {
                        return match;
                    }
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        match = findBrokerInterface(parameterType, visited);
                        if (match != null) {
                            return match;
                        }
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> findBrokerInterface(Class<?> type, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return null;
        }
        if (isBrokerInterface(type)) {
            return type;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            Class<?> match = findBrokerInterface(candidate, visited);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static boolean isBrokerInterface(Class<?> type) {
        if (type == null || !type.isInterface() || !IInterface.class.isAssignableFrom(type)) {
            return false;
        }
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2
                    && IInterface.class.isAssignableFrom(parameters[0])
                    && Parcelable.class.isAssignableFrom(parameters[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGetServiceMethod(Method method, Object[] args) {
        if (method == null || args == null || args.length != 2
                || !(args[1] instanceof Parcelable)) {
            return false;
        }
        return args[0] == null || args[0] instanceof IInterface;
    }

    private static void transactGetService(IBinder base, Object[] args)
            throws RemoteException {
        IInterface callbacks = args[0] instanceof IInterface
                ? (IInterface) args[0] : null;
        Parcelable request = (Parcelable) args[1];
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BROKER_DESCRIPTOR);
            data.writeStrongBinder(callbacks == null ? null : callbacks.asBinder());
            data.writeInt(1);
            request.writeToParcel(data, 0);
            data.setDataPosition(0);
            if (!base.transact(GET_SERVICE_TRANSACTION, data, reply, 0)) {
                throw new RemoteException("Google Play services broker rejected getService");
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * Direct Binder fallback for obfuscated Play-services clients. Returns null
     * when the parcel layout cannot be decoded, which makes the caller use the
     * untouched original transaction instead of corrupting a request.
     */
    private static Boolean transactGetServiceParcel(
            IBinder base, Parcel original, Parcel reply, int flags) {
        if (base == null || original == null || reply == null) {
            return null;
        }

        final int oldPosition = original.dataPosition();
        Parcel patched = null;
        try {
            original.setDataPosition(0);
            original.enforceInterface(BROKER_DESCRIPTOR);
            IBinder callbacks = original.readStrongBinder();
            int present = original.readInt();
            if (present == 0) {
                return null;
            }

            Parcelable request = readServiceRequest(original);
            if (request == null) {
                return null;
            }
            normalizeServiceRequest(request);

            patched = Parcel.obtain();
            patched.writeInterfaceToken(BROKER_DESCRIPTOR);
            patched.writeStrongBinder(callbacks);
            patched.writeInt(1);
            request.writeToParcel(patched, 0);
            patched.setDataPosition(0);
            return base.transact(GET_SERVICE_TRANSACTION, patched, reply, flags);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                original.setDataPosition(oldPosition);
            } catch (Throwable ignored) {
            }
            if (patched != null) {
                patched.recycle();
            }
        }
    }

    private static Parcelable readServiceRequest(Parcel data) {
        Parcelable.Creator<?> creator = findServiceRequestCreator(resolveVirtualClassLoader());
        if (creator == null) {
            creator = findServiceRequestCreator(resolveRealGmsClassLoader());
        }
        if (creator == null) {
            return null;
        }
        try {
            Object value = creator.createFromParcel(data);
            return value instanceof Parcelable ? (Parcelable) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Parcelable.Creator<?> findServiceRequestCreator(ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        try {
            Class<?> requestClass = Class.forName(SERVICE_REQUEST_CLASS, false, loader);
            Field creatorField = requestClass.getField("CREATOR");
            Object creator = creatorField.get(null);
            return creator instanceof Parcelable.Creator
                    ? (Parcelable.Creator<?>) creator : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ClassLoader resolveRealGmsClassLoader() {
        try {
            Context context = BlackBoxCore.getContext();
            if (context == null) {
                return null;
            }
            Context gms = context.createPackageContext(
                    GMS_PACKAGE,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            return gms == null ? null : gms.getClassLoader();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "GmsBrokerCompat";
            default:
                return null;
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
                    || className.endsWith(".GetServiceRequest")
                    || arg instanceof Parcelable) {
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
