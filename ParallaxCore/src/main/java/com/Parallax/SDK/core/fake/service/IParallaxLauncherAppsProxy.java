package com.Parallax.SDK.core.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.content.pm.BRILauncherAppsStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/13/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxLauncherAppsProxy extends ParallaxBinderInvocationStub {

    public IParallaxLauncherAppsProxy() {
        super(BRServiceManager.get().getService(Context.LAUNCHER_APPS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILauncherAppsStub.get().asInterface(BRServiceManager.get().getService(Context.LAUNCHER_APPS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodParameterUtils.replaceFirstAppPkg(args);
        // todo shouldHideFromSuggestions
        return super.invoke(proxy, method, args);
    }

}
