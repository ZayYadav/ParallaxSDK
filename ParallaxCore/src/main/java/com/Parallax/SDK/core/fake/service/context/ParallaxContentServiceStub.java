package com.Parallax.SDK.core.fake.service.context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.content.BRIContentServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
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
public class ParallaxContentServiceStub extends ParallaxBinderInvocationStub {

    public ParallaxContentServiceStub() {
        super(BRServiceManager.get().getService("content"));
    }

    @Override
    protected Object getWho() {
        return BRIContentServiceStub.get().asInterface(BRServiceManager.get().getService("content"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("content");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("registerContentObserver")
    public static class RegisterContentObserver extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ParallaxProxyMethod("notifyChange")
    public static class NotifyChange extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
}
