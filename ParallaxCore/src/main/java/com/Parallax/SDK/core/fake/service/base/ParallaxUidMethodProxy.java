package com.Parallax.SDK.core.fake.service.base;

import java.lang.reflect.Method;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;

/**
 * Created by BlackBox on 2022/3/5.
 */
public class ParallaxUidMethodProxy extends ParallaxMethodHook {
    private final int index;
    private final String name;

    public ParallaxUidMethodProxy(String name, int index) {
        this.index = index;
        this.name = name;
    }

    @Override
    protected String getMethodName() {
        return name;
    }

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int uid = (int) args[index];
        if (uid == ParallaxActivityThread.getBUid() || uid == ParallaxActivityThread.getBAppId()) {
            args[index] = ParallaxCore.getHostUid();
        }
        return method.invoke(who, args);
    }
}
