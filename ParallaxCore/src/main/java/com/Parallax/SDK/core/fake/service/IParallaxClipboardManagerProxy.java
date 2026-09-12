package com.Parallax.SDK.core.fake.service;

import android.content.ClipData;
import android.util.Log;

import com.Parallax.SDK.mirror.android.content.BRIClipboardStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;

import java.lang.reflect.Method;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;

public class IParallaxClipboardManagerProxy extends ParallaxBinderInvocationStub {
    private static final String TAG = "IParallaxClipboardManagerProxy";

    private static ClipData sSharedClipData;

    public IParallaxClipboardManagerProxy() {
        super(BRServiceManager.get().getService("clipboard"));
    }

    public Object getWho() {
        return BRIClipboardStub.get().asInterface(BRServiceManager.get().getService("clipboard"));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        int argIndex = getPackNameIndex(args);
        if (argIndex != -1) {
            args[argIndex] = ParallaxCore.getHostPkg();
        }

        if ("setPrimaryClip".equals(methodName) || "setPrimaryClipAsPackage".equals(methodName)) {
            cacheClipData(args);
            try {
                return super.invoke(proxy, method, args);
            } catch (Throwable throwable) {
                Log.w(TAG, "System clipboard write failed, using shared clipboard cache", throwable);
                return null;
            }
        }

        if ("clearPrimaryClip".equals(methodName)) {
            sSharedClipData = null;
            try {
                return super.invoke(proxy, method, args);
            } catch (Throwable throwable) {
                Log.w(TAG, "System clipboard clear failed", throwable);
                return null;
            }
        }

        if ("getPrimaryClip".equals(methodName)) {
            try {
                Object result = super.invoke(proxy, method, args);
                return result != null ? result : sSharedClipData;
            } catch (Throwable throwable) {
                Log.w(TAG, "System clipboard read failed, using shared clipboard cache", throwable);
                return sSharedClipData;
            }
        }

        if ("getPrimaryClipDescription".equals(methodName)) {
            try {
                Object result = super.invoke(proxy, method, args);
                if (result != null || sSharedClipData == null) {
                    return result;
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "System clipboard description failed, using shared clipboard cache", throwable);
            }
            return sSharedClipData == null ? null : sSharedClipData.getDescription();
        }

        if ("hasPrimaryClip".equals(methodName)) {
            try {
                Object result = super.invoke(proxy, method, args);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "System clipboard status failed, using shared clipboard cache", throwable);
            }
            return sSharedClipData != null;
        }

        return super.invoke(proxy, method, args);
    }

    private void cacheClipData(Object[] args) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            if (arg instanceof ClipData) {
                sSharedClipData = (ClipData) arg;
                return;
            }
        }
    }

    private int getPackNameIndex(Object[] args) {
        if (args == null) {
            return -1;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                Log.d(TAG, "args[" + i + "] " + args[i]);
                return i;
            }
        }
        return -1;
    }

    public void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("clipboard");
    }

    public boolean isBadEnv() {
        return false;
    }
}
