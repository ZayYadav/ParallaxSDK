package com.Parallax.SDK.core.fake.service.libcore;

import android.os.Process;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.libcore.io.BRLibcore;
import java.util.Objects;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.ParallaxRuntimeCore;
import com.Parallax.SDK.core.fake.hook.ParallaxClassInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethods;
import com.Parallax.SDK.core.utils.ParallaxReflector;
import com.Parallax.SDK.core.utils.ParallaxSlog;

/**
 * Created by @RIYAZXERO on 4/9/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxOsStub extends ParallaxClassInvocationStub {
    public static final String TAG = "ParallaxOsStub";
    private Object mBase;

    public ParallaxOsStub() {
        mBase = BRLibcore.get().os();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRLibcore.get()._set_os(proxyInvocation);
    }

    @Override
    protected void onBindMethod() {
    }

    @Override
    public boolean isBadEnv() {
        return BRLibcore.get().os() != getProxyInvocation();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null)
                    continue;
                if (args[i] instanceof String && ((String) args[i]).startsWith("/")) {
                    String orig = (String) args[i];
                    args[i] = ParallaxRuntimeCore.get().redirectPath(orig);
                }
            }
        }
        return super.invoke(proxy, method, args);
    }

    @ParallaxProxyMethod("getuid")
    public static class getuid extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int callUid = (int) method.invoke(who, args);
            return getFakeUid(callUid);
        }
    }

    @ParallaxProxyMethod("stat")
    public static class stat extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object invoke = null;
            try {
                invoke = method.invoke(who, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
            ParallaxReflector.with(invoke).field("st_uid").set(getFakeUid(-1));
            return invoke;
        }
    }

    private static int getFakeUid(int callUid) {
        if (callUid > 0 && callUid <= Process.FIRST_APPLICATION_UID) return callUid;
        if (ParallaxActivityThread.isThreadInit() && ParallaxActivityThread.currentActivityThread().isInit()) {
            return ParallaxActivityThread.getBAppId();
        } else {
            return ParallaxCore.getHostUid();
        }
    }
}
