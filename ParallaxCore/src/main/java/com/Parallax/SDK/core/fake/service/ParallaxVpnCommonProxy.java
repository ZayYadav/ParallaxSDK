package com.Parallax.SDK.core.fake.service;

import java.lang.reflect.Method;
import java.util.List;

import com.Parallax.SDK.mirror.com.android.internal.net.BRVpnConfig;
import com.Parallax.SDK.mirror.com.android.internal.net.VpnConfigContext;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.proxy.ParallaxProxyVpnService;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

public class ParallaxVpnCommonProxy {
    @ParallaxProxyMethod("setVpnPackageAuthorization")
    public static class setVpnPackageAuthorization extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("prepareVpn")
    public static class PrepareVpn extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("establishVpn")
    public static class establishVpn extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            VpnConfigContext vpnConfigContext = BRVpnConfig.get(args[0]);
            vpnConfigContext._set_user(ParallaxProxyVpnService.class.getName());

            handlePackage(vpnConfigContext.allowedApplications());
            handlePackage(vpnConfigContext.disallowedApplications());
            return method.invoke(who, args);
        }

        private void handlePackage(List<String> applications) {
            if (applications == null)
                return;
            if (applications.contains(ParallaxActivityThread.getAppPackageName())) {
                applications.add(ParallaxCore.getHostPkg());
            }
        }
    }

}
