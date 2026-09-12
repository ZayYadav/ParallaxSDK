package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import com.Parallax.SDK.mirror.android.app.BRILocaleManager;
import com.Parallax.SDK.mirror.android.app.BRILocaleManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;

/**
 * @author gm
 * @function
 * @date :2024/4/20 20:06
 **/
public class IParallaxLocaleManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxLocaleManagerProxy";

    public IParallaxLocaleManagerProxy() {
        super(BRServiceManager.get().getService("locale"));
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxPkgMethodProxy("setApplicationLocales"));
        addMethodHook(new ParallaxPkgMethodProxy("getApplicationLocales"));
    }

    @Override
    protected Object getWho() {
        return BRILocaleManagerStub.get().asInterface(BRServiceManager.get().getService("locale"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("locale");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
