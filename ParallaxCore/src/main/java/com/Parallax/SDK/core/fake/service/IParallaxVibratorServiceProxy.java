package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRIVibratorManagerServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.com.android.internal.os.BRIVibratorServiceStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by BlackBox on 2022/3/7.
 */
public class IParallaxVibratorServiceProxy extends ParallaxBinderInvocationStub {
    private static String NAME;
    static {
        if (ParallaxBuildCompat.isS_V2()) {
            NAME = "vibrator_manager";
        } else {
            NAME = Context.VIBRATOR_SERVICE;
        }
    }

    public IParallaxVibratorServiceProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        IBinder service = BRServiceManager.get().getService(NAME);
        if (ParallaxBuildCompat.isS()) {
            return BRIVibratorManagerServiceStub.get().asInterface(service);
        }
        return BRIVibratorServiceStub.get().asInterface(service);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodParameterUtils.replaceFirstUid(args);
        ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }
}
