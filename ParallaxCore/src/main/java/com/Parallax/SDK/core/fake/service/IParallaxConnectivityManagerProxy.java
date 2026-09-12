package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.Parallax.SDK.core.fake.hook.ParallaxScanClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.net.BRIConnectivityManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxSlog;

@ParallaxScanClass(ParallaxVpnCommonProxy.class)
public class IParallaxConnectivityManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxConnectivityManagerProxy";

    public IParallaxConnectivityManagerProxy() {
        super(BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIConnectivityManagerStub.get().asInterface(BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
