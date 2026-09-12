package com.Parallax.SDK.core.fake.service;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.usage.BRIStorageStatsManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;

import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * StorageStats compatibility proxy
 * Android 8 → Android 17
 * PUBG / BGMI update & permission fix
 */
@TargetApi(Build.VERSION_CODES.O)
public class IParallaxStorageStatsManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxStorageStatsManagerProxy() {
        super(BRServiceManager.get().getService(Context.STORAGE_STATS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIStorageStatsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.STORAGE_STATS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.STORAGE_STATS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }
}
