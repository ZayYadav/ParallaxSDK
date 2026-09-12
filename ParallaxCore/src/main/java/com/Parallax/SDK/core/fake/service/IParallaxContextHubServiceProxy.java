package com.Parallax.SDK.core.fake.service;


import com.Parallax.SDK.mirror.android.hardware.location.BRIContextHubServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;
import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;

/**
 * Created by BlackBox on 2022/3/2.
 */
public class IParallaxContextHubServiceProxy extends ParallaxBinderInvocationStub {

    public IParallaxContextHubServiceProxy() {
        super(BRServiceManager.get().getService(getServiceName()));
    }

    private static String getServiceName() {
        return ParallaxBuildCompat.isOreo() ? "contexthub" : "contexthub_service";
    }

    @Override
    protected Object getWho() {
        return BRIContextHubServiceStub.get().asInterface(BRServiceManager.get().getService(getServiceName()));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(getServiceName());
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxValueMethodProxy("registerCallback", 0));
        addMethodHook(new ParallaxValueMethodProxy("getContextHubInfo", null));
        addMethodHook(new ParallaxValueMethodProxy("getContextHubHandles",new int[]{}));
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
