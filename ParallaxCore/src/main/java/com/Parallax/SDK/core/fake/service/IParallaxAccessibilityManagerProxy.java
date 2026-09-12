package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.android.view.accessibility.BRIAccessibilityManagerStub;
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethods;

/**
 * Created by @RIYAZXERO on 4/25/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxAccessibilityManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxAccessibilityManagerProxy() {
        super(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAccessibilityManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ACCESSIBILITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethods({"interrupt", "sendAccessibilityEvent", "addClient","getInstalledAccessibilityServiceList", "getEnabledAccessibilityServiceList","addAccessibilityInteractionConnection", "getWindowToken"})
    public static class ReplaceUserId extends ParallaxMethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                int index = args.length - 1;
                Object arg = args[index];
                if (arg instanceof Integer) {
                    ApplicationInfo applicationInfo = ParallaxCore.getContext().getApplicationInfo();
                    args[index] = ParallaxUserHandle.getUserId(applicationInfo.uid);
                }
            }
            return method.invoke(who, args);
        }
    }
}
