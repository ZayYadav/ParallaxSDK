package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import java.lang.reflect.Method;

import black.android.bluetooth.BRIBluetoothManagerStub;
import black.android.os.BRServiceManager;

import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;

public final class IBluetoothManagerProxy extends BinderInvocationStub {

    private static final String SERVICE_NAME = "bluetooth_manager";
    private Object mBase;

    public IBluetoothManagerProxy() {
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
    
    @ProxyMethod("getName")
    public static final class GetName extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}