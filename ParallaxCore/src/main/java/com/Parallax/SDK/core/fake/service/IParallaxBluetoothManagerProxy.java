package com.Parallax.SDK.core.fake.service;

import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.bluetooth.BRIBluetoothManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;

import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;

public final class IParallaxBluetoothManagerProxy extends ParallaxBinderInvocationStub {

    private static final String SERVICE_NAME = "bluetooth_manager";
    private Object mBase;

    public IParallaxBluetoothManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }
    
    @Override
    protected Object getWho() {
        if (mBase != null) {
            return mBase;
        }

        Object service = BRServiceManager.get().getService(SERVICE_NAME);
        if (service == null) {
            return null;
        }
        IBinder binder = (IBinder) service;
        mBase = BRIBluetoothManagerStub.get().asInterface(binder);
        return mBase;
    }
    
    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        Object base = (mBase != null) ? mBase : getWho();
        if (base == null) {
            return null;
        }
        
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(base, args);
        }

        try {
            return method.invoke(base, args);
        } catch (Throwable e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }
    
    @ParallaxProxyMethod("getName")
    public static final class GetName extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}