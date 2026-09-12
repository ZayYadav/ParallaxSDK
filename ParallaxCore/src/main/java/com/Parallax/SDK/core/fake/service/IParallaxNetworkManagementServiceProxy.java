package com.Parallax.SDK.core.fake.service;

import static com.Parallax.SDK.core.app.ParallaxActivityThread.getUid;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRINetworkManagementServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxUidMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by BlackBox on 2022/3/5.
 */
public class IParallaxNetworkManagementServiceProxy extends ParallaxBinderInvocationStub {
    public static final String NAME = "network_management";

    public IParallaxNetworkManagementServiceProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        return BRINetworkManagementServiceStub.get().asInterface(BRServiceManager.get().getService(NAME));
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
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxUidMethodProxy("setUidCleartextNetworkPolicy", 0));
        addMethodHook(new ParallaxUidMethodProxy("setUidMeteredNetworkBlacklist", 0));
        addMethodHook(new ParallaxUidMethodProxy("setUidMeteredNetworkWhitelist", 0));
    }

    @ParallaxProxyMethod("getNetworkStatsUidDetail")
    public static class GetNetworkStatsUidDetail extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstUid(args);
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

}
