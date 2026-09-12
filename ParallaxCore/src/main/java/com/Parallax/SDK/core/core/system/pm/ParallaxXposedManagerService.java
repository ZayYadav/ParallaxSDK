package com.Parallax.SDK.core.core.system.pm;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;

import androidx.core.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.IParallaxSystemService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.pm.ParallaxInstalledModule;
import com.Parallax.SDK.core.entity.pm.ParallaxXposedConfig;
import com.Parallax.SDK.core.utils.ParallaxCloseUtils;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.compat.ParallaxXposedParserCompat;

/**
 * Created by @RIYAZXERO on 5/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ParallaxXposedManagerService extends IParallaxXposedManagerService.Stub implements IParallaxSystemService, ParallaxPackageMonitor {
    private static final ParallaxXposedManagerService sService = new ParallaxXposedManagerService();

    private ParallaxXposedConfig mXposedConfig;
    private final Object mLock = new Object();
    private ParallaxPackageManagerService mPms;
    private final Map<String, ParallaxInstalledModule> mCacheModule = new HashMap<>();

    public static ParallaxXposedManagerService get() {
        return sService;
    }

    public ParallaxXposedManagerService() {
    }

    @Override
    public void systemReady() {
        loadModuleStateLr();
        mPms = ParallaxPackageManagerService.get();
        mPms.addPackageMonitor(this);
    }

    @Override
    public boolean isXPEnable() {
        synchronized (mLock) {
            return mXposedConfig.enable;
        }
    }

    @Override
    public void setXPEnable(boolean enable) {
        synchronized (mLock) {
            mXposedConfig.enable = enable;
            saveModuleStateLw();
        }
    }

    @Override
    public boolean isModuleEnable(String packageName) {
        synchronized (mLock) {
            Boolean enable = mXposedConfig.moduleState.get(packageName);
            return enable != null && enable;
        }
    }

    @Override
    public void setModuleEnable(String packageName, boolean enable) {
        synchronized (mLock) {
            if (!mPms.isInstalled(packageName, ParallaxUserHandle.USER_XPOSED)) {
                return;
            }
            mXposedConfig.moduleState.put(packageName, enable);
            saveModuleStateLw();
        }
    }

    @Override
    public List<ParallaxInstalledModule> getInstalledModules() {
        List<ApplicationInfo> installedApplications = mPms.getInstalledApplications(ParallaxFileUtils.FileMode.MODE_IWUSR, ParallaxUserHandle.USER_XPOSED);
        synchronized (mCacheModule) {
            for (ApplicationInfo installedApplication : installedApplications) {
                if (mCacheModule.containsKey(installedApplication.packageName))
                    continue;
                ParallaxInstalledModule installedModule = ParallaxXposedParserCompat.parseModule(installedApplication);
                if (installedModule != null) {
                    mCacheModule.put(installedApplication.packageName, installedModule);
                }
            }
            ArrayList<ParallaxInstalledModule> installedModules = new ArrayList<>(mCacheModule.values());
            for (ParallaxInstalledModule installedModule : installedModules) {
                installedModule.enable = isModuleEnable(installedModule.packageName);
            }
            return installedModules;
        }
    }

    private void loadModuleStateLr() {
        File xpModuleConf = ParallaxEnvironment.getXPModuleConf();
        if (!xpModuleConf.exists()) {
            mXposedConfig = new ParallaxXposedConfig();
            saveModuleStateLw();
            return;
        }
        Parcel parcel = null;
        try {
            parcel = ParallaxFileUtils.readToParcel(xpModuleConf);
            mXposedConfig = new ParallaxXposedConfig(parcel);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }

    private void saveModuleStateLw() {
        Parcel parcel = Parcel.obtain();
        AtomicFile atomicFile = new AtomicFile(ParallaxEnvironment.getXPModuleConf());
        FileOutputStream fileOutputStream = null;
        try {
            mXposedConfig.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            fileOutputStream = atomicFile.startWrite();
            ParallaxFileUtils.writeParcelToOutput(parcel, fileOutputStream);
            atomicFile.finishWrite(fileOutputStream);
        } catch (Exception ignored) {
            atomicFile.failWrite(fileOutputStream);
        } finally {
            parcel.recycle();
            ParallaxCloseUtils.close(fileOutputStream);
        }
    }

    @Override
    public void onPackageUninstalled(String packageName, boolean removeApp, int userId) {
        if (userId != ParallaxUserHandle.USER_XPOSED && userId != ParallaxUserHandle.USER_ALL) {
            return;
        }
        synchronized (mCacheModule) {
            mCacheModule.remove(packageName);
        }
        synchronized (mLock) {
            mXposedConfig.moduleState.remove(packageName);
            saveModuleStateLw();
        }
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
        if (userId != ParallaxUserHandle.USER_XPOSED && userId != ParallaxUserHandle.USER_ALL) {
            return;
        }
        synchronized (mCacheModule) {
            mCacheModule.remove(packageName);
        }
        synchronized (mLock) {
            mXposedConfig.moduleState.put(packageName, false);
            saveModuleStateLw();
        }
    }
}
