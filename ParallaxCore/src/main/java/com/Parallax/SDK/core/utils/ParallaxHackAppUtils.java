package com.Parallax.SDK.core.utils;

public class ParallaxHackAppUtils {
    public static void enableQQLogOutput(String packageName, ClassLoader classLoader) {
        if ("com.tencent.mobileqq".equals(packageName)) {
            try {
                ParallaxReflector.on("com.tencent.qphone.base.util.QLog", true, classLoader).field("UIN_REPORTLOG_LEVEL").set(100);
            } catch (Exception e) {
                e.printStackTrace();
                // ignore
            }
        }
    }
}
