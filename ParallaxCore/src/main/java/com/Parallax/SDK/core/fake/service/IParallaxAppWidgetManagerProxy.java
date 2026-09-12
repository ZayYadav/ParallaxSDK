package com.Parallax.SDK.core.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.mirror.com.android.internal.appwidget.BRIAppWidgetServiceStub;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.service.base.ParallaxValueMethodProxy;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 4/5/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IParallaxAppWidgetManagerProxy extends ParallaxBinderInvocationStub {

    public IParallaxAppWidgetManagerProxy() {
        super(BRServiceManager.get().getService(Context.APPWIDGET_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAppWidgetServiceStub.get().asInterface(BRServiceManager.get().getService(Context.APPWIDGET_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.APPWIDGET_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodParameterUtils.replaceAllAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ParallaxValueMethodProxy("startListening", new int[0]));
        addMethodHook(new ParallaxValueMethodProxy("stopListening", 0));
        addMethodHook(new ParallaxValueMethodProxy("allocateAppWidgetId", 0));
        addMethodHook(new ParallaxValueMethodProxy("deleteAppWidgetId", 0));
        addMethodHook(new ParallaxValueMethodProxy("deleteHost", 0));
        addMethodHook(new ParallaxValueMethodProxy("deleteAllHosts", 0));
        addMethodHook(new ParallaxValueMethodProxy("getAppWidgetViews", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("getAppWidgetIdsForHost", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("createAppWidgetConfigIntentSender", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("updateAppWidgetIds", 0));
        addMethodHook(new ParallaxValueMethodProxy("updateAppWidgetOptions", 0));
        addMethodHook(new ParallaxValueMethodProxy("getAppWidgetOptions", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("partiallyUpdateAppWidgetIds", 0));
        addMethodHook(new ParallaxValueMethodProxy("updateAppWidgetProvider", 0));
        addMethodHook(new ParallaxValueMethodProxy("notifyAppWidgetViewDataChanged", 0));
        addMethodHook(new ParallaxValueMethodProxy("getInstalledProvidersForProfile", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("getAppWidgetInfo", (Object) null));
        addMethodHook(new ParallaxValueMethodProxy("hasBindAppWidgetPermission", false));
        addMethodHook(new ParallaxValueMethodProxy("setBindAppWidgetPermission", 0));
        addMethodHook(new ParallaxValueMethodProxy("bindAppWidgetId", false));
        addMethodHook(new ParallaxValueMethodProxy("bindRemoteViewsService", 0));
        addMethodHook(new ParallaxValueMethodProxy("unbindRemoteViewsService", 0));
        addMethodHook(new ParallaxValueMethodProxy("getAppWidgetIds", new int[0]));
        addMethodHook(new ParallaxValueMethodProxy("isBoundWidgetPackage", false));
    }
}
