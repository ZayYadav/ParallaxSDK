package com.Parallax.SDK.core.fake.service;

import android.os.IInterface;
import android.os.storage.StorageVolume;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.os.mount.BRIMountServiceStub;
import com.Parallax.SDK.mirror.android.os.storage.BRIStorageManagerStub;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

public class IParallaxStorageManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxStorageManagerProxy() {
        super(BRServiceManager.get().getService("mount"));
    }

    @Override
    protected Object getWho() {
        IInterface mount;
        if (ParallaxBuildCompat.isOreo()) {
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

    @ParallaxProxyMethod("getVolumeList")
    public static class GetVolumeList extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Android 12+ compatibility
                int uid = ParallaxActivityThread.getBUid();
                int userId = ParallaxActivityThread.getUserId();
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

                StorageVolume[] volumeList = ParallaxCore.getBStorageManager().getVolumeList(uid, packageName, flags, userId);
                if (volumeList == null || volumeList.length == 0) {
                   // ParallaxSlog.d("IParallaxStorageManagerProxy", "Volume list is null, calling original method");
                    return method.invoke(who, args);
                }
              //  ParallaxSlog.d("IParallaxStorageManagerProxy", "Returning " + volumeList.length + " storage volumes");
                return volumeList;
            } catch (Throwable t) {
                ParallaxSlog.e("IParallaxStorageManagerProxy", "Error in getVolumeList hook: " + t.getMessage(), t);
                return method.invoke(who, args);
            }
        }
    }

    @ParallaxProxyMethod("mkdirs")
    public static class mkdirs extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
           //     ParallaxSlog.d("IParallaxStorageManagerProxy", "mkdirs hooked, returning 0");
                return 0;
            } catch (Throwable t) {
                return method.invoke(who, args);
            }
        }
    }

    @ParallaxProxyMethod("getVolumePaths")
    public static class GetVolumePaths extends ParallaxMethodHook {
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
        if (ParallaxBuildCompat.isS()) {
            addMethodHook(new GetVolumePaths());
        }
    }
}