package com.Parallax.SDK.core.fake.frameworks;

import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.Parallax.SDK.core.core.system.ParallaxServiceManager;
import com.Parallax.SDK.core.core.system.pm.IParallaxXposedManagerService;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;

/**
 * Created by @RIYAZXERO on 5/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxXposedManager extends ParallaxBlackManager<IParallaxXposedManagerService> {
    private static final ParallaxXposedManager sXposedManager = new ParallaxXposedManager();

    public static ParallaxXposedManager get() {
        return sXposedManager;
    }

    @Override
    protected String getServiceName() {
        return ParallaxServiceManager.XPOSED_MANAGER;
    }

    public boolean isXPEnable() {
        try {
            return getService().isXPEnable();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setXPEnable(boolean enable) {
        try {
            getService().setXPEnable(enable);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean isModuleEnable(String packageName) {
        try {
            return getService().isModuleEnable(packageName);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setModuleEnable(String packageName, boolean enable) {
        try {
            getService().setModuleEnable(packageName, enable);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<ParallaxInstalledModule> getInstalledModules() {
        try {
            return getService().getInstalledModules();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
