package com.Parallax.SDK.core.fake.service.context.providers;

import android.os.Build;
import android.os.Bundle;
import android.os.IInterface;
import android.util.Log;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.utils.compat.ParallaxContextCompat;
import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.content.BRAttributionSource;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;

/**
 * HARD FINAL FIX
 * Android 10 → 16
 * Solves AttributionSource.enforceCallingUid crash
 */
public class ParallaxContentProviderStub extends ParallaxClassInvocationStub implements ParallaxContentProvider {
    public static final String TAG = "ParallaxContentProviderStub";
    private IInterface mBase;
    private String mAppPkg;

    public IInterface wrapper(final IInterface contentProviderProxy, final String appPkg) {
        mBase = contentProviderProxy;
        mAppPkg = appPkg;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg instanceof String) {
                args[0] = mAppPkg;
            } else if (arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
                ParallaxContextCompat.fixAttributionSourceState(arg, ParallaxCore.getHostUid());
            }
        }
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            throw e.getCause();
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
