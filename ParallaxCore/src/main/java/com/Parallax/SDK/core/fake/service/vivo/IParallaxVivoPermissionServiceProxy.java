package com.Parallax.SDK.core.fake.service.vivo;

import android.os.Process;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.model.vivo.BRIVivoPermissionServiceStub;
import com.Parallax.SDK.mirror.model.vivo.IVivoPermissionServiceContext;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxArrayUtils;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * @author gm
 * @function
 * @date :2024/4/23 16:54
 **/
public class IParallaxVivoPermissionServiceProxy extends ParallaxBinderInvocationStub {
    public IParallaxVivoPermissionServiceProxy() {//型号
        super(BRServiceManager.get().getService("vivo_permission_service"));
    }

    @Override
    protected Object getWho() {
        return BRIVivoPermissionServiceStub.get().asInterface(BRServiceManager.get().getService("vivo_permission_service"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("vivo_permission_service");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("checkPermission")
    public static class checkPermission extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int uid = (int)args[2];
            if (uid == Process.myUid()){
                args[2] = ParallaxCore.getHostUid();
            }
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getAppPermission")
    public static class getAppPermission extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setAppPermission")
    public static class setAppPermission extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setWhiteListApp")
    public static class setWhiteListApp extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setBlackListApp")
    public static class setBlackListApp extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("noteStartActivityProcess")
    public static class noteStartActivityProcess extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("isBuildInThirdPartApp")
    public static class isBuildInThirdPartApp extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("checkDelete")
    public static class checkDelete extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args[1] instanceof String) {
                args[1] = ParallaxCore.getHostPkg();
            }
            ParallaxMethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setOnePermission")
    public static class setOnePermission extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastUserId(args);
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setOnePermissionExt")
    public static class setOnePermissionExt extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastUserId(args);
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("isVivoImeiPkg")
    public static class isVivoImeiPkg extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
