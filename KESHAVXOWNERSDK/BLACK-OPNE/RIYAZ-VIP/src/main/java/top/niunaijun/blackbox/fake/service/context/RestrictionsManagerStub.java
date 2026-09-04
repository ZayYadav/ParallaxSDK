package top.niunaijun.blackbox.fake.service.context;

import android.content.Context;
import black.android.content.BRIRestrictionsManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import java.lang.reflect.Method;

public class RestrictionsManagerStub extends BinderInvocationStub {

    public RestrictionsManagerStub() {
        super(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRestrictionsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.RESTRICTIONS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getApplicationRestrictions")
    public static class GetApplicationRestrictions extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = BlackBoxCore.getHostPkg();
            }
            return method.invoke(who, args);
        }
    }
}