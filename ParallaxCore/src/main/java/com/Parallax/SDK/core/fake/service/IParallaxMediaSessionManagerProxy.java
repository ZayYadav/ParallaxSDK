package com.Parallax.SDK.core.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.media.session.BRISessionManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxMediaSessionManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxMediaSessionManagerProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRISessionManagerStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("createSession")
    public static class CreateSession extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = ParallaxCore.getHostPkg();
            }
            return method.invoke(who, args);
        }
    }
}
