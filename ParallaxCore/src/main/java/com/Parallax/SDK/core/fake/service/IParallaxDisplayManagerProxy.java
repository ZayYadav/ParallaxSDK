package com.Parallax.SDK.core.fake.service;

import android.os.IInterface;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.hardware.display.BRDisplayManagerGlobal;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/16/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxDisplayManagerProxy extends ParallaxClassInvocationStub {

    public IParallaxDisplayManagerProxy() {
    }

    @Override
    protected Object getWho() {
        return BRDisplayManagerGlobal.get(BRDisplayManagerGlobal.get().getInstance()).mDm();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        Object dmg = BRDisplayManagerGlobal.get().getInstance();
        BRDisplayManagerGlobal.get(dmg)._set_mDm(getProxyInvocation());
    }

    @Override
    public boolean isBadEnv() {
        Object dmg = BRDisplayManagerGlobal.get().getInstance();
        IInterface mDm = BRDisplayManagerGlobal.get(dmg).mDm();
        return mDm != getProxyInvocation();
    }


    @ParallaxProxyMethod("createVirtualDisplay")
    public static class CreateVirtualDisplay extends ParallaxMethodHook {

        @Override
        protected String getMethodName() {
            return "createVirtualDisplay";
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
