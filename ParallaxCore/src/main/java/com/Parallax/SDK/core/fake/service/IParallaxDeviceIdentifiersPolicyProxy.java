package com.Parallax.SDK.core.fake.service;


import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRIDeviceIdentifiersPolicyServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMd5Utils;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/3/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxDeviceIdentifiersPolicyProxy extends ParallaxBinderInvocationStub {

    public IParallaxDeviceIdentifiersPolicyProxy() {
        super(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected Object getWho() {
        return BRIDeviceIdentifiersPolicyServiceStub.get().asInterface(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("device_identifiers");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getSerialForPackage")
    public static class x extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParallaxMd5Utils.md5(ParallaxCore.getHostPkg());
        }
    }
}
