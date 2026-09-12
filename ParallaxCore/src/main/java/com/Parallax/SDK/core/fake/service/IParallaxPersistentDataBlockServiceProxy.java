package com.Parallax.SDK.core.fake.service;


import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.service.persistentdata.BRIPersistentDataBlockServiceStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;

/**
 * Created by BlackBox on 2022/3/8.
 */
public class IParallaxPersistentDataBlockServiceProxy extends ParallaxBinderInvocationStub {

    public static final String NAME = "persistent_data_block";

    public IParallaxPersistentDataBlockServiceProxy() {
        super(BRServiceManager.get().getService(NAME));
    }

    @Override
    protected Object getWho() {
        return BRIPersistentDataBlockServiceStub.get().asInterface(BRServiceManager.get().getService(NAME));
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
        addMethodHook(new ParallaxValueMethodProxy("write", -1));
        addMethodHook(new ParallaxValueMethodProxy("read", new byte[0]));
        addMethodHook(new ParallaxValueMethodProxy("wipe", null));
        addMethodHook(new ParallaxValueMethodProxy("getDataBlockSize", 0));
        addMethodHook(new ParallaxValueMethodProxy("getMaximumDataBlockSize", 0));
        addMethodHook(new ParallaxValueMethodProxy("setOemUnlockEnabled", 0));
        addMethodHook(new ParallaxValueMethodProxy("getOemUnlockEnabled", false));
    }
}
