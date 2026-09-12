package com.Parallax.SDK.core.fake.service;

import android.content.Context;

import com.Parallax.SDK.mirror.android.os.BRIPowerManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;

/**
 * Created by BlackBox on 2022/3/1.
 */
public class IParallaxPowerManagerProxy extends ParallaxBinderInvocationStub {
    public IParallaxPowerManagerProxy() {
        super(BRServiceManager.get().getService(Context.POWER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIPowerManagerStub.get().asInterface(BRServiceManager.get().getService(Context.POWER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.POWER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxValueMethodProxy("acquireWakeLock", 0));
        addMethodHook(new ParallaxValueMethodProxy("acquireWakeLockWithUid", 0));
        addMethodHook(new ParallaxValueMethodProxy("releaseWakeLock", 0));
        addMethodHook(new ParallaxValueMethodProxy("updateWakeLockWorkSource", 0));
        addMethodHook(new ParallaxValueMethodProxy("isWakeLockLevelSupported", true));
    }
}
