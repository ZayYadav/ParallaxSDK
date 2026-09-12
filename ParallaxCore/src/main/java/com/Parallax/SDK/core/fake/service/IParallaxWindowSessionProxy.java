package com.Parallax.SDK.core.fake.service;

import android.os.IInterface;
import android.view.WindowManager;

import java.lang.reflect.Method;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;


/**
 * Created by @RIYAZXERO on 4/6/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxWindowSessionProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "WindowSessionStub";

    private IInterface mSession;

    public IParallaxWindowSessionProxy(IInterface session) {
        super(session.asBinder());
        mSession = session;
    }

    @Override
    protected Object getWho() {
        return mSession;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @ParallaxProxyMethod("addToDisplay")
    public static class AddToDisplay extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    ((WindowManager.LayoutParams) arg).packageName = ParallaxCore.getHostPkg();
                }
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("addToDisplayAsUser")
    public static class AddToDisplayAsUser extends AddToDisplay {
    }
}
