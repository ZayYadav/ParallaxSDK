package top.niunaijun.blackbox.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import black.android.media.BRIMediaRouterServiceStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.utils.MethodParameterUtils;

/**
 * Created by BlackBox on 2022/3/1.
 */
public class IMediaRouterServiceProxy extends BinderInvocationStub {

    public IMediaRouterServiceProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIMediaRouterServiceStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_ROUTER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("registerClientAsUser")
    public static class registerClientAsUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("registerRouter2")
    public static class registerRouter2 extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    /**
     * Android 15/16 validates the caller package on MediaRouter2 read APIs.
     * Virtual applications run under the host UID, so forward the host package
     * for every signature variant before the platform performs that check.
     */
    @ProxyMethods({
            "getSystemRoutes",
            "getSystemSessionInfo",
            "getRemoteSessions"
    })
    public static class mediaRouter2ReadCalls extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
}
