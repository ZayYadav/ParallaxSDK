package com.Parallax.SDK.core.fake.service;

import android.content.ComponentName;
import android.content.Context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.admin.BRIDevicePolicyManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 2021/5/17.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxDevicePolicyManagerProxy extends ParallaxBinderInvocationStub {
    public IParallaxDevicePolicyManagerProxy() {
        super(BRServiceManager.get().getService(Context.DEVICE_POLICY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIDevicePolicyManagerStub.get().asInterface(BRServiceManager.get().getService(Context.DEVICE_POLICY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("getStorageEncryptionStatus")
    public static class GetStorageEncryptionStatus extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("getDeviceOwnerComponent")
    public static class GetDeviceOwnerComponent extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return new ComponentName("", "");
        }
    }

    @ParallaxProxyMethod("getDeviceOwnerName")
    public static class getDeviceOwnerName extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return "ParallaxCore";
        }
    }

    @ParallaxProxyMethod("getProfileOwnerName")
    public static class getProfileOwnerName extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return "ParallaxCore";
        }
    }

    @ParallaxProxyMethod("isDeviceProvisioned")
    public static class isDeviceProvisioned extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }
}
