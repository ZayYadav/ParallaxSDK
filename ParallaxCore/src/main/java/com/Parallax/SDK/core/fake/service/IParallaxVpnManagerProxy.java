package com.Parallax.SDK.core.fake.service;

import com.Parallax.SDK.mirror.android.net.BRIVpnManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxScanClass;

/**
 * Created by @RIYAZXERO on 4/12/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@ParallaxScanClass(ParallaxVpnCommonProxy.class)
public class IParallaxVpnManagerProxy extends ParallaxBinderInvocationStub {
    public static final String TAG = "IParallaxVpnManagerProxy";
    public static final String VPN_MANAGEMENT_SERVICE = "vpn_management";

    public IParallaxVpnManagerProxy() {
        super(BRServiceManager.get().getService(VPN_MANAGEMENT_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIVpnManagerStub.get().asInterface(BRServiceManager.get().getService(VPN_MANAGEMENT_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(VPN_MANAGEMENT_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
