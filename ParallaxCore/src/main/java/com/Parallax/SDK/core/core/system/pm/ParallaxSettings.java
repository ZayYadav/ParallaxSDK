package com.Parallax.SDK.core.core.system.pm;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.os.Parcel;
import android.os.Process;
import android.util.ArrayMap;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.core.env.ParallaxEnvironment;
import com.Parallax.SDK.core.core.system.ParallaxProcessManagerService;
import com.Parallax.SDK.core.core.system.user.ParallaxUserHandle;
import com.Parallax.SDK.core.entity.pm.ParallaxInstallOption;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.utils.ParallaxSlog;
import com.Parallax.SDK.core.utils.compat.ParallaxPackageParserCompat;

/**
 * Created by Milk on 4/13/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
/*public*/ class ParallaxSettings { // 等同于 PackageCacheManager
    public static final String TAG = "ParallaxSettings";

    final ArrayMap<String, ParallaxPackageSettings> mPackages = new ArrayMap<>();
    private final Map<String, Integer> mAppIds = new HashMap<>();
    private final Map<String, ParallaxSharedUserSetting> mSharedUsers = ParallaxSharedUserSetting.sSharedUsers;
    private int mCurrUid = 0;

    public ParallaxSettings() {
        synchronized (mPackages) {
            loadUidLP();
            ParallaxSharedUserSetting.loadSharedUsers();
        }
    }

    ParallaxPackageSettings getPackageLPw(String name, PackageParser.Package aPackage, ParallaxInstallOption installOption) {
        ParallaxPackageSettings pkgSettings;
        ParallaxPackageSettings origSettings = new ParallaxPackageSettings();
        origSettings.pkg = new ParallaxPackage(aPackage);
        origSettings.pkg.installOption = installOption;
        origSettings.installOption = installOption;
        origSettings.pkg.mExtras = origSettings;
        origSettings.pkg.applicationInfo = ParallaxPackageManagerCompat.generateApplicationInfo(origSettings.pkg, 0, ParallaxPackageUserState.create(), 0);
        synchronized (mPackages) {
            pkgSettings = mPackages.get(name);
            if (pkgSettings != null) {
                origSettings.appId = pkgSettings.appId;
                origSettings.userState = pkgSettings.userState;
            } else {
                boolean b = registerAppIdLPw(origSettings);
                if (!b) {
                    throw new RuntimeException("registerAppIdLPw err.");
                }
            }
        }
        return origSettings;
    }

    boolean registerAppIdLPw(ParallaxPackageSettings p) {
        boolean createdNew = false;
        String sharedUserId = p.pkg.mSharedUserId;
        ParallaxSharedUserSetting sharedUserSetting = null;
        if (sharedUserId != null) {
            sharedUserSetting = mSharedUsers.get(sharedUserId);
            if (sharedUserSetting == null) {
                sharedUserSetting = new ParallaxSharedUserSetting(sharedUserId);
                sharedUserSetting.userId = acquireAndRegisterNewAppIdLPw(p);
                mSharedUsers.put(sharedUserId, sharedUserSetting);
            }
        }
        if (sharedUserSetting != null) {
            p.appId = sharedUserSetting.userId;
            ParallaxSlog.d(TAG, p.pkg.packageName + " sharedUserId = " + sharedUserId + ", setAppId = " + p.appId);
        }
        if (p.appId == 0) {
            // Assign new user ID
            p.appId = acquireAndRegisterNewAppIdLPw(p);
        }
        if (p.appId < 0) {
            createdNew = false;
//            PackageManagerService.reportSettingsProblem(Log.WARN,
//                    "Package " + p.name + " could not be assigned a valid UID");
//            throw new PackageManagerException(INSTALL_FAILED_INSUFFICIENT_STORAGE,
//                    "Package " + p.name + " could not be assigned a valid UID");
        } else {
            createdNew = true;
        }
        saveUidLP();
        ParallaxSharedUserSetting.saveSharedUsers();
        return createdNew;
    }

    private int acquireAndRegisterNewAppIdLPw(ParallaxPackageSettings obj) {
        // Let's be stupidly inefficient for now...
        Integer integer = mAppIds.get(obj.pkg.packageName);
        if (integer != null)
            return integer;

        if (mCurrUid >= Process.LAST_APPLICATION_UID) {
            return -1;
        }
        mCurrUid++;
        mAppIds.put(obj.pkg.packageName, mCurrUid);
        return Process.FIRST_APPLICATION_UID + mCurrUid;
    }

    private void saveUidLP() {
        Parcel parcel = Parcel.obtain();
        FileOutputStream fileOutputStream = null;
        AtomicFile atomicFile = new AtomicFile(ParallaxEnvironment.getUidConf());
        try {
            Set<String> pkgName = mPackages.keySet();
            for (String s : new HashSet<>(mAppIds.keySet())) {
                if (!pkgName.contains(s)) {
                    mAppIds.remove(s);
                }
            }
            parcel.writeInt(mCurrUid);
            parcel.writeMap(mAppIds);

            fileOutputStream = atomicFile.startWrite();
            ParallaxFileUtils.writeParcelToOutput(parcel, fileOutputStream);
            atomicFile.finishWrite(fileOutputStream);
        } catch (Exception e) {
            e.printStackTrace();
            atomicFile.failWrite(fileOutputStream);
        } finally {
            parcel.recycle();
        }
    }

    private void loadUidLP() {
        Parcel parcel = Parcel.obtain();
        try {
            byte[] uidBytes = ParallaxFileUtils.toByteArray(ParallaxEnvironment.getUidConf());
            parcel.unmarshall(uidBytes, 0, uidBytes.length);
            parcel.setDataPosition(0);

            mCurrUid = parcel.readInt();
            HashMap hashMap = parcel.readHashMap(HashMap.class.getClassLoader());
            synchronized (mAppIds) {
                mAppIds.clear();
                mAppIds.putAll(hashMap);
            }
        } catch (Exception e) {
//            e.printStackTrace();
        } finally {
            parcel.recycle();
        }
    }

    public void scanPackage() {
        synchronized (mPackages) {
            File appRootDir = ParallaxEnvironment.getAppRootDir();
            ParallaxFileUtils.mkdirs(appRootDir);
            File[] apps = appRootDir.listFiles();
            for (File app : apps) {
                if (!app.isDirectory()) {
                    continue;
                }
                scanPackage(app.getName());
            }
        }
    }

    public void scanPackage(String packageName) {
        synchronized (mPackages) {
            updatePackageLP(ParallaxEnvironment.getAppDir(packageName));
        }
    }

    private void updatePackageLP(File app) {
        String packageName = app.getName();
        Parcel packageSettingsIn = Parcel.obtain();
        File packageConf = ParallaxEnvironment.getPackageConf(packageName);
        try {
            byte[] bPackageSettingsBytes = ParallaxFileUtils.toByteArray(packageConf);

            packageSettingsIn.unmarshall(bPackageSettingsBytes, 0, bPackageSettingsBytes.length);
            packageSettingsIn.setDataPosition(0);

            ParallaxPackageSettings bPackageSettings = new ParallaxPackageSettings(packageSettingsIn);
            bPackageSettings.pkg.mExtras = bPackageSettings;
            if (bPackageSettings.installOption.isFlag(ParallaxInstallOption.FLAG_SYSTEM)) {
                PackageInfo packageInfo = ParallaxCore.getPackageManager().getPackageInfo(packageName, ParallaxFileUtils.FileMode.MODE_IWUSR);
                String currPackageSourcePath = packageInfo.applicationInfo.sourceDir;
                if (!currPackageSourcePath.equals(bPackageSettings.pkg.baseCodePath)) {
                    // update baseCodePath And Re install
                    ParallaxProcessManagerService.get().killAllByPackageName(bPackageSettings.pkg.packageName);
                    ParallaxPackageSettings newPkg = reInstallBySystem(packageInfo, bPackageSettings.installOption);
                    bPackageSettings.pkg = newPkg.pkg;
                }
            } else {
                bPackageSettings.pkg.applicationInfo = ParallaxPackageManagerCompat.generateApplicationInfo(bPackageSettings.pkg, 0, ParallaxPackageUserState.create(), 0);
            }
            bPackageSettings.save();
            mPackages.put(bPackageSettings.pkg.packageName, bPackageSettings);
            // 20240801 add request permission add start 0
            ParallaxPackageManagerService.get().analyzePackageLocked(bPackageSettings);
            // 20240801 add request permission add end 0
            ParallaxSlog.d(TAG, "loaded Package: " + packageName);
        } catch (Throwable e) {
            e.printStackTrace();
            // bad package
            ParallaxFileUtils.deleteDir(app);
            removePackage(packageName);
            ParallaxProcessManagerService.get().killAllByPackageName(packageName);
            ParallaxPackageManagerService.get().onPackageUninstalled(packageName, true, ParallaxUserHandle.USER_ALL);
            ParallaxSlog.d(TAG, "bad Package: " + packageName);
        } finally {
            packageSettingsIn.recycle();
        }
    }

    private ParallaxPackageSettings reInstallBySystem(PackageInfo systemPackageInfo, ParallaxInstallOption option) throws Exception {
        ParallaxSlog.d(TAG, "reInstallBySystem: " + systemPackageInfo.packageName);
        PackageParser.Package aPackage = parserApk(systemPackageInfo.applicationInfo.sourceDir);
        if (aPackage == null) {
            throw new RuntimeException("parser apk error.");
        }
        aPackage.applicationInfo = ParallaxCore.getPackageManager().getPackageInfo(aPackage.packageName, 0).applicationInfo;
        return getPackageLPw(aPackage.packageName, aPackage, option);
    }

    public void removePackage(String packageName) {
        mPackages.remove(packageName);
    }

    private PackageParser.Package parserApk(String file) {
        try {
            PackageParser parser = ParallaxPackageParserCompat.createParser(new File(file));
            PackageParser.Package aPackage = ParallaxPackageParserCompat.parsePackage(parser, new File(file), 0);
            ParallaxPackageParserCompat.collectCertificates(parser, aPackage, 0);
            return aPackage;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }
}
