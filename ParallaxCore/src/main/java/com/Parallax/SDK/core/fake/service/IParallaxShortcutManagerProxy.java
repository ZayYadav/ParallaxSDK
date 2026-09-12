package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.Parallax.SDK.mirror.android.content.pm.BRIShortcutServiceStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.fake.service.base.ParallaxPkgMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxParceledListSliceCompat;

/**
 * Created by @RIYAZXERO on 4/5/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 * 未实现，全部拦截
 */
public class IParallaxShortcutManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxShortcutManagerProxy() {
        super(BRServiceManager.get().getService(Context.SHORTCUT_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIShortcutServiceStub.get().asInterface(BRServiceManager.get().getService(Context.SHORTCUT_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.SHORTCUT_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxPkgMethodProxy("getShortcuts"));//修复whtasApp启动黑屏问题
        addMethodHook(new ParallaxPkgMethodProxy("disableShortcuts"));
        addMethodHook(new ParallaxPkgMethodProxy("enableShortcuts"));
        addMethodHook(new ParallaxPkgMethodProxy("getRemainingCallCount"));
        addMethodHook(new ParallaxPkgMethodProxy("getRateLimitResetTime"));
        addMethodHook(new ParallaxPkgMethodProxy("getIconMaxDimensions"));
        addMethodHook(new ParallaxPkgMethodProxy("getMaxShortcutCountPerActivity"));
        addMethodHook(new ParallaxPkgMethodProxy("reportShortcutUsed"));
        addMethodHook(new ParallaxPkgMethodProxy("onApplicationActive"));
        addMethodHook(new ParallaxPkgMethodProxy("hasShortcutHostPermission"));
        addMethodHook(new ParallaxPkgMethodProxy("removeAllDynamicShortcuts"));
        addMethodHook(new ParallaxPkgMethodProxy("removeDynamicShortcuts"));
        addMethodHook(new ParallaxPkgMethodProxy("removeLongLivedShortcuts"));
        addMethodHook(new ParallaxPkgMethodProxy("getManifestShortcuts"){
            @Override
            protected Object hook(Object who, Method method, Object[] args) throws Throwable {
                return ParallaxParceledListSliceCompat.create(new ArrayList<ShortcutInfo>());
            }
        });
    }

    @ParallaxProxyMethod("requestPinShortcut")
    public static class RequestPinShortcut extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ParallaxProxyMethod("setDynamicShortcuts")
    public static class SetDynamicShortcuts extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ParallaxProxyMethod("addDynamicShortcuts")
    public static class AddDynamicShortcuts extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ParallaxProxyMethod("createShortcutResultIntent")
    public static class CreateShortcutResultIntent extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return new Intent();
        }
    }

    @ParallaxProxyMethod("pushDynamicShortcut")
    public static class pushDynamicShortcut extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodParameterUtils.replaceAllAppPkg(args);
        return super.invoke(proxy, method, args);
    }
}
