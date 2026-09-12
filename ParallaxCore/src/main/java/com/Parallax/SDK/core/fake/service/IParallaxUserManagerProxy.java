package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.Parallax.SDK.mirror.android.content.pm.BRUserInfo;
import com.Parallax.SDK.mirror.android.os.BRIUserManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;

public class IParallaxUserManagerProxy extends ParallaxBinderInvocationStub {
    public IParallaxUserManagerProxy() {
        super(BRServiceManager.get().getService(Context.USER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIUserManagerStub.get().asInterface(BRServiceManager.get().getService(Context.USER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.USER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getApplicationRestrictions")
    public static class GetApplicationRestrictions extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            args[0] = ParallaxCore.getHostPkg();
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getProfileParent")
    public static class GetProfileParent extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object sVBoxCore = BRUserInfo.get()._new(ParallaxActivityThread.getUserId(), "ParallaxCore", BRUserInfo.get().FLAG_PRIMARY());
            return sVBoxCore;
        }
    }

    @ParallaxProxyMethod("getUsers")
    public static class getUsers extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return new ArrayList<>();
        }
    }
}
