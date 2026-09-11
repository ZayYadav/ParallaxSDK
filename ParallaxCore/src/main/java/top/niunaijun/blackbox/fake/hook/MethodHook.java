package top.niunaijun.blackbox.fake.hook;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import top.niunaijun.blackbox.BlackBoxCore;

public abstract class MethodHook {
    protected String getMethodName() {
        return null;
    }

    protected Object afterHook(Object result) throws Throwable {
        return result;
    }

    protected Object beforeHook(Object who, Method method, Object[] args) throws Throwable {
        return null;
    }

    protected abstract Object hook(Object who, Method method, Object[] args) throws Throwable;

    protected boolean isEnable() {
        return BlackBoxCore.get().isBlackProcess();
    }
}
