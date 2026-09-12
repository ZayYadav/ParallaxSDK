package com.Parallax.SDK.core.fake.service;


import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.BRIAutoFillManagerStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;

/**
 * @author Findger
 * @function
 * @date :2022/4/2 21:59
 **/
public class IParallaxSystemUpdateProxy extends ParallaxBinderInvocationStub {
    public IParallaxSystemUpdateProxy() {
        super(BRServiceManager.get().getService("system_update"));
    }

    @Override
    protected Object getWho() {
        return BRIAutoFillManagerStub.get().asInterface(BRServiceManager.get().getService("system_update"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("system_update");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
