package com.Parallax.SDK.core.proxy;

import java.util.Locale;
import com.Parallax.SDK.core.ParallaxCore;

public class ParallaxProxyManifest {
    public static final int FREE_COUNT = 50;
    public static final String PROXY_PACKAGE_NAME = "com.Parallax.SDK.core";

    public static boolean isProxy(String msg) {
        return getBindProvider().equals(msg) || msg.contains("proxy_content_provider_");
    }

    public static String getBindProvider() {
        return ParallaxCore.getHostPkg() + ".SystemCallProvider";
    }

    public static String getProxyAuthorities(int index) {
        return String.format(Locale.CHINA, "%s.proxy_content_provider_%d", ParallaxCore.getHostPkg(), index);
    }

    public static String getProxyPendingActivity(int index) {
        return String.format(Locale.CHINA, PROXY_PACKAGE_NAME + ".proxy.ProxyPendingActivity$P%d", index);
    }

    public static String getProxyActivity(int index) {
        return String.format(Locale.CHINA, PROXY_PACKAGE_NAME + ".proxy.ProxyActivity$P%d", index);
    }

    public static String ParallaxTransparentProxyActivity(int index) {
        return String.format(Locale.CHINA, PROXY_PACKAGE_NAME + ".proxy.TransparentProxyActivity$P%d", index);
    }

    public static String getProxyService(int index) {
        return String.format(Locale.CHINA, PROXY_PACKAGE_NAME + ".proxy.ProxyService$P%d", index);
    }

    public static String getProxyJobService(int index) {
        return String.format(Locale.CHINA, PROXY_PACKAGE_NAME + ".proxy.ProxyJobService$P%d", index);
    }

    public static String getProxyFileProvider() {
        return ParallaxCore.getHostPkg() + ".FileProvider";
    }

    public static String getProxyReceiver() {
        return ParallaxCore.getHostPkg() + ".stub_receiver";
    }

    public static String getProcessName(int bPid) {
        return ParallaxCore.getHostPkg() + ":p" + bPid;
    }
}
