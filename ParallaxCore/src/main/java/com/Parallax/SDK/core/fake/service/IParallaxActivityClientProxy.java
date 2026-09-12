package com.Parallax.SDK.core.fake.service;

import android.app.ActivityManager;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.BRActivityClient;
import com.Parallax.SDK.mirror.android.util.BRSingleton;
import com.Parallax.SDK.core.fake.frameworks.ParallaxActivityManager;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.compat.ParallaxTaskDescriptionCompat;

/**
 * Created by BlackBox on 2022/2/22.
 */
public class IParallaxActivityClientProxy extends ParallaxClassInvocationStub {
    public static final String TAG = "IParallaxActivityClientProxy";
    private final Object who;

    public IParallaxActivityClientProxy(Object who) {
        this.who = who;
    }

    @Override
    protected Object getWho() {
        if (who != null) {
            return who;
        }
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        return BRSingleton.get(singleton).get();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        BRSingleton.get(singleton)._set_mInstance(proxyInvocation);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @Override
    public void onlyProxy(boolean o) {
        super.onlyProxy(o);
    }

    @ParallaxProxyMethod("finishActivity")
    public static class FinishActivity extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            ParallaxActivityManager.get().onFinishActivity(token);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("activityResumed")
    public static class ActivityResumed extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            ParallaxActivityManager.get().onActivityResumed(token);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            ParallaxActivityManager.get().onActivityDestroyed(token);
            return method.invoke(who, args);
        }
    }

    // for >= Android 12
    @ParallaxProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = ParallaxTaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }
}
