package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.os.storage.StorageVolume;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.os.mount.BRIMountServiceStub;
import black.android.os.storage.BRIStorageManagerStub;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class IStorageManagerProxy extends BinderInvocationStub {

    public IStorageManagerProxy() {
        super(BRServiceManager.get().getService("mount"));
    }

    @Override
    protected Object getWho() {
        IInterface mount;
        if (BuildCompat.isOreo()) {
            mount = BRIStorageManagerStub.get().asInterface(BRServiceManager.get().getService("mount"));
        } else {
            mount = BRIMountServiceStub.get().asInterface(BRServiceManager.get().getService("mount"));
        }
        return mount;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("mount");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getVolumeList")
    public static class GetVolumeList extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Android 12+ compatibility
                int uid = BActivityThread.getBUid();
                int userId = BActivityThread.getUserId();
                String packageName = null;
                int flags = 0;
                if (args != null && args.length > 0) {
                    if (args.length >= 3) {
                        if (args[0] instanceof Integer) {
                            uid = (Integer) args[0];
                        }
                        if (args[1] instanceof String) {
                            packageName = (String) args[1];
                        }
                        flags = getFlags(args[2]);
                    } else if (args.length == 1 && args[0] instanceof Integer) {
                        uid = (Integer) args[0];
                    }
                }

                StorageVolume[] volumeList = BlackBoxCore.getBStorageManager().getVolumeList(uid, packageName, flags, userId);
                if (volumeList == null || volumeList.length == 0) {
                   // Slog.d("IStorageManagerProxy", "Volume list is null, calling original method");
                    return method.invoke(who, args);
                }
              //  Slog.d("IStorageManagerProxy", "Returning " + volumeList.length + " storage volumes");
                return volumeList;
            } catch (Throwable t) {
                Slog.e("IStorageManagerProxy", "Error in getVolumeList hook: " + t.getMessage(), t);
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("mkdirs")
    public static class mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
           //     Slog.d("IStorageManagerProxy", "mkdirs hooked, returning 0");
                return 0;
            } catch (Throwable t) {
                return method.invoke(who, args);
            }
        }
    }

    @ProxyMethod("getVolumePaths")
    public static class GetVolumePaths extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable t) {
                return new String[0];
            }
        }
    }

    private static int getFlags(Object arg) {
        if (arg instanceof Integer) {
            return (Integer) arg;
        }
        if (arg instanceof Long) {
            return ((Long) arg).intValue();
        }
        if (arg instanceof String) {
            try {
                return Integer.parseInt((String) arg);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        if (BuildCompat.isS()) {
            addMethodHook(new GetVolumePaths());
        }
    }
}