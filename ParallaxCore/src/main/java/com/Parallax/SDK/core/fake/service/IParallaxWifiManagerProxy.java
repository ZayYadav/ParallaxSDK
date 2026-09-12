package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.os.WorkSource;
import android.util.Log;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.net.wifi.BRIWifiManagerStub;
import com.Parallax.SDK.mirror.android.net.wifi.BRWifiInfo;
import com.Parallax.SDK.mirror.android.net.wifi.BRWifiSsid;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxArrayUtils;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/12/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxWifiManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxWifiManagerProxy";

    public IParallaxWifiManagerProxy() {
        super(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIWifiManagerStub.get().asInterface(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getConnectionInfo")
    public static class GetConnectionInfo extends ParallaxMethodHook {
    
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            WifiInfo wifiInfo = (WifiInfo) method.invoke(who, args);
            BRWifiInfo.get(wifiInfo)._set_mBSSID("ac:62:5a:82:65:c4");
            BRWifiInfo.get(wifiInfo)._set_mMacAddress("ac:62:5a:82:65:c4");
            BRWifiInfo.get(wifiInfo)._set_mWifiSsid(BRWifiSsid.get().createFromAsciiEncoded("BlackBox_Wifi"));
            return wifiInfo;
        }

        public static String intIP2StringIP(int ip) {
            return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + (ip >> 24 & 0xFF);
        }

        public static int ip2Int(String ipString) {
            // 取 ip 的各段
            String[] ipSlices = ipString.split("\\.");
            int rs = 0;
            for (int i = 0; i < ipSlices.length; i++) {
                // 将 ip 的每一段解析为 int，并根据位置左移 8 位
                int intSlice = Integer.parseInt(ipSlices[i]) << 8 * i;
                // 或运算
                rs = rs | intSlice;
            }
            return rs;
        }
    }
}
