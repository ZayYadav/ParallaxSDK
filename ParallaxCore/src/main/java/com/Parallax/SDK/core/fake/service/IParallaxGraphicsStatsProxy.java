package com.Parallax.SDK.core.fake.service;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.BRIGraphicsStatsStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/13/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxGraphicsStatsProxy extends ParallaxBinderInvocationStub {

    public IParallaxGraphicsStatsProxy() {
        super(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected Object getWho() {
        return BRIGraphicsStatsStub.get().asInterface(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("graphicsstats");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("requestBufferForProcess")
    public static class RequestBufferForProcess extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
