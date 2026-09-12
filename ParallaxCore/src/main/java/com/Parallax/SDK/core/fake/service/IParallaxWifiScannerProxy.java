package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import com.Parallax.SDK.mirror.android.net.wifi.BRIWifiManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;

/**
 * @author Findger
 * @function
 * @date :2022/4/3 13:05
 **/
public class IParallaxWifiScannerProxy extends ParallaxBinderInvocationStub {

    public IParallaxWifiScannerProxy() {
        super(BRServiceManager.get().getService("wifiscanner"));
    }

    @Override
    protected Object getWho() {
        return BRIWifiManagerStub.get().asInterface(BRServiceManager.get().getService("wifiscanner"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("wifiscanner");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
