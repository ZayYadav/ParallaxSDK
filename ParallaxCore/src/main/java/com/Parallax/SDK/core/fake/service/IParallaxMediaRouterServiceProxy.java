package com.Parallax.SDK.core.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.media.BRIMediaRouterServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by BlackBox on 2022/3/1.
 */
public class IParallaxMediaRouterServiceProxy extends ParallaxBinderInvocationStub {

    public IParallaxMediaRouterServiceProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIMediaRouterServiceStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_ROUTER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("registerClientAsUser")
    public static class registerClientAsUser extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ParallaxProxyMethod("registerRouter2")
    public static class registerRouter2 extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
}
