package com.Parallax.SDK.core.fake.service;

import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

import com.Parallax.SDK.mirror.android.app.backup.BRIBackupManager;
import com.Parallax.SDK.mirror.android.app.backup.BRIBackupManagerStub;
import com.Parallax.SDK.mirror.android.os.BRServiceManager;
import com.Parallax.SDK.core.fake.hook.ParallaxBinderInvocationStub;
import com.Parallax.SDK.core.fake.hook.ParallaxMethodHook;
import com.Parallax.SDK.core.fake.hook.ParallaxProxyMethod;
import com.Parallax.SDK.core.utils.ParallaxMethodParameterUtils;

/**
 * @author gm
 * @function
 * @date :2024/4/20 21:39
 **/
public class IParallaxBackupManagerProxy extends ParallaxBinderInvocationStub {
    public IParallaxBackupManagerProxy() {
        super(BRServiceManager.get().getService("backup"));
    }

    @Override
    protected Object getWho() {
        return BRIBackupManagerStub.get().asInterface(BRServiceManager.get().getService("backup"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("backup");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ParallaxProxyMethod("beginRestoreSession")
    public static class beginRestoreSession extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("hasBackupPassword")
    public static class hasBackupPassword extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ParallaxProxyMethod("setBackupPassword")
    public static class setBackupPassword extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }

    @ParallaxProxyMethod("isBackupEnabled")
    public static class isBackupEnabled extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    @ParallaxProxyMethod("selectBackupTransport")
    public static class selectBackupTransport extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("listAllTransports")
    public static class listAllTransports extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return new String[0];
        }
    }

    @ParallaxProxyMethod("getCurrentTransport")
    public static class getCurrentTransport extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("acknowledgeFullBackupOrRestore")
    public static class acknowledgeFullBackupOrRestore extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("fullRestore")
    public static class fullRestore extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("fullTransportBackup")
    public static class fullTransportBackup extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("fullBackup")
    public static class fullBackup extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("backupNow")
    public static class backupNow extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("setBackupProvisioned")
    public static class setBackupProvisioned extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("setBackupEnabled")
    public static class setBackupEnabled extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("restoreAtInstall")
    public static class restoreAtInstall extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("agentDisconnected")
    public static class agentDisconnected extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("agentConnected")
    public static class agentConnected extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("clearBackupData")
    public static class clearBackupData extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ParallaxProxyMethod("dataChanged")
    public static class dataChanged extends ParallaxMethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }
}
