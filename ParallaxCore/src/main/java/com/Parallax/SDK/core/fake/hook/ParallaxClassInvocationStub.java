package com.Parallax.SDK.core.fake.hook;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * Created by @RIYAZXERO on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public abstract class ParallaxClassInvocationStub implements InvocationHandler, IParallaxInjectHook {
    public static final String TAG = ParallaxClassInvocationStub.class.getSimpleName();

    private final Map<String, ParallaxMethodHook> mMethodHookMap = new HashMap<>();
    private Object mBase;
    private Object mProxyInvocation;
    private boolean onlyProxy;

    protected abstract Object getWho();

    protected abstract void inject(Object baseInvocation, Object proxyInvocation);

    protected void onBindMethod() {

    }

    protected Object getProxyInvocation() {
        return mProxyInvocation;
    }

    protected Object getBase() {
        return mBase;
    }

    protected void onlyProxy(boolean o) {
        onlyProxy = o;
    }

    @Override
    public void injectHook() {
        mBase = getWho();
        mProxyInvocation = Proxy.newProxyInstance(mBase.getClass().getClassLoader(), ParallaxMethodParameterUtils.getAllInterface(mBase.getClass()), this);
        if (!onlyProxy) {
            inject(mBase, mProxyInvocation);
        }
        onBindMethod();
        Class<?>[] declaredClasses = this.getClass().getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            initAnnotation(declaredClass);
        }
        ParallaxScanClass scanClass = this.getClass().getAnnotation(ParallaxScanClass.class);
        if (scanClass != null) {
            for (Class<?> aClass : scanClass.value()) {
                for (Class<?> declaredClass : aClass.getDeclaredClasses()) {
                    initAnnotation(declaredClass);
                }
            }
        }
    }

    protected void initAnnotation(Class<?> clazz) {
        ParallaxProxyMethod proxyMethod = clazz.getAnnotation(ParallaxProxyMethod.class);
        if (proxyMethod != null) {
            final String name = proxyMethod.value();
            if (!TextUtils.isEmpty(name)) {
                try {
                    addMethodHook(name, (ParallaxMethodHook) clazz.newInstance());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        ParallaxProxyMethods proxyMethods = clazz.getAnnotation(ParallaxProxyMethods.class);
        if (proxyMethods != null) {
            String[] value = proxyMethods.value();
            for (String name : value) {
                try {
                    addMethodHook(name, (ParallaxMethodHook) clazz.newInstance());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    protected void addMethodHook(ParallaxMethodHook methodHook) {
        mMethodHookMap.put(methodHook.getMethodName(), methodHook);
    }

    protected void addMethodHook(String name, ParallaxMethodHook methodHook) {
        mMethodHookMap.put(name, methodHook);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ParallaxMethodHook methodHook = mMethodHookMap.get(method.getName());
        if (methodHook == null || !methodHook.isEnable()) {
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
        }

        Object result = methodHook.beforeHook(mBase, method, args);
        if (result != null) {
            return result;
        }
        result = methodHook.hook(mBase, method, args);
        result = methodHook.afterHook(result);
        return result;
    }
}
