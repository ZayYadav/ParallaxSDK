package com.Parallax.SDK.core.fake.service.context;

import android.content.Context;
import com.Parallax.SDK.mirror.android.content.BRIRestrictionsManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import java.lang.reflect.Method;

public class ParallaxRestrictionsManagerStub extends ParallaxBinderInvocationStub {

    public ParallaxRestrictionsManagerStub() {
        super(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRestrictionsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.RESTRICTIONS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getApplicationRestrictions")
    public static class GetApplicationRestrictions extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = ParallaxCore.getHostPkg();
            }
            return method.invoke(who, args);
        }
    }
}