package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.media.BRIAudioServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.frameworks.ParallaxLocationManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * @author gm
 * @function
 * @date :2024/4/20 20:48
 **/
public class IParallaxAudioManagerProxy extends ParallaxBinderInvocationStub {
    public IParallaxAudioManagerProxy() {
        super(BRServiceManager.get().getService(Context.AUDIO_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAudioServiceStub.get().asInterface(BRServiceManager.get().getService(Context.AUDIO_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("unregisterAudioFocusClient")
    public static class unregisterAudioFocusClient extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("registerRemoteControlClient")
    public static class registerRemoteControlClient extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("disableSafeMediaVolume")
    public static class disableSafeMediaVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("startBluetoothSco")
    public static class startBluetoothSco extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("stopBluetoothSco")
    public static class stopBluetoothSco extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setBluetoothScoOn")
    public static class setBluetoothScoOn extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setSpeakerphoneOn")
    public static class setSpeakerphoneOn extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setWiredDeviceConnectionState")
    public static class setWiredDeviceConnectionState extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("requestAudioFocus")
    public static class requestAudioFocus extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("abandonAudioFocus")
    public static class abandonAudioFocus extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("avrcpSupportsAbsoluteVolume")
    public static class avrcpSupportsAbsoluteVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setMode")
    public static class setMode extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setRingerModeInternal")
    public static class setRingerModeInternal extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setRingerModeExternal")
    public static class setRingerModeExternal extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setMasterVolume")
    public static class setMasterVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setStreamVolume")
    public static class setStreamVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("adjustMasterVolume")
    public static class adjustMasterVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("adjustStreamVolume")
    public static class adjustStreamVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("adjustSuggestedStreamVolume")
    public static class adjustSuggestedStreamVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("adjustLocalOrRemoteStreamVolume")
    public static class adjustLocalOrRemoteStreamVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("adjustVolume")
    public static class adjustVolume extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("isHardwareDetected")
    public static class isHardwareDetected extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ParallaxProxyMethod("setMicrophoneMute")
    public static class setMicrophoneMute extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ParallaxMethodParameterUtils.replaceLastAppPkg(args);
            ParallaxMethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }
}
