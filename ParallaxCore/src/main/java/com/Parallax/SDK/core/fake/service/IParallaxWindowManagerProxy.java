package com.Parallax.SDK.core.fake.service;

import android.os.IInterface;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.BRIWindowManagerStub;
import com.Parallax.SDK.mirror.android.view.BRWindowManagerGlobal;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/6/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxWindowManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "WindowManagerStub";

    public IParallaxWindowManagerProxy() {
        super(BRServiceManager.get().getService("window"));
    }

    @Override
    protected Object getWho() {
        return BRIWindowManagerStub.get().asInterface(BRServiceManager.get().getService("window"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("window");
        BRWindowManagerGlobal.get()._set_sWindowManagerService(null);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("openSession")
    public static class OpenSession extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface session = (IInterface) method.invoke(who, args);
            IParallaxWindowSessionProxy IParallaxWindowSessionProxy = new IParallaxWindowSessionProxy(session);
            IParallaxWindowSessionProxy.injectHook();
            return IParallaxWindowSessionProxy.getProxyInvocation();
        }
    }
}
