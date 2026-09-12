package com.Parallax.SDK.core.core.env;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.Parallax.SDK.core.utils.compat.ParallaxBuildCompat;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Locale;

import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.core.utils.ParallaxFileUtils;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.core.system.api.ParallaxActivationManager;

public class ParallaxEnvironment {
   
  //  private static final File sExternalVirtualRoot = ParallaxCore.getContext().getExternalFilesDir("ParallaxCore");
    private static final File sVBoxRoot = ParallaxCore.getContext().getFilesDir();
  //  private static final File sVBoxRoot = new File(ParallaxCore.getContext().getCacheDir().getParent(), "ParallaxCore");
    private static final File sExternalVBoxRoot = Environment.getExternalStorageDirectory();
    
	public static void load() {
		ParallaxFileUtils.mkdirs(sVBoxRoot);
        ParallaxFileUtils.mkdirs(sExternalVBoxRoot);
		ParallaxFileUtils.mkdirs(getSystemDir());
		ParallaxFileUtils.mkdirs(getCacheDir());
		ParallaxFileUtils.mkdirs(getProcDir());
	}
    
    public static File getVBoxRoot() {
		return sVBoxRoot;
	}
    
    public static ArrayList<String> getAllDex(String str) {
		File appDir = getAppDir(str);
		ArrayList<String> arrayList = new ArrayList<>();
		File[] listFiles = appDir.listFiles((FilenameFilter) new FilenameFilter() {
	        @Override
		    public boolean accept(File dir, String name) {
			   return str.endsWith(".apk");
		    }
	    });
		if (listFiles != null) {
			for (File file : listFiles) {
				if (ParallaxBuildCompat.isUpsideDownCake()) {
					try {
						file.setReadOnly();
					} catch (Throwable th) {
						th.printStackTrace();
					}
				}
				arrayList.add(file.getAbsolutePath());
			}
		}
		return arrayList;
	}

    public static File getSystemDir() {
        return new File(sVBoxRoot, "system");
    }

    public static File getProcDir() {
        return new File(sVBoxRoot, "proc");
    }

    public static File getCacheDir() {
        return new File(sVBoxRoot, "cache");
    }
    
    public static File getUserDir(int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }
    
    public static File getDataDir(String packageName, int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user/%d/%s", userId, packageName));
    }
    
    public static File getAppDir(String packageName) {
        return new File(sVBoxRoot, "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        return new File(sVBoxRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }
    
    public static File getFakeDeviceConf() {
        return new File(getSystemDir(), "fake-device.conf");
    }
    
    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }
    
    public static File getExternalStorageDirectory() {
        if (Build.VERSION.SDK_INT == 29) return new File(sExternalVBoxRoot, "SdCard");
        return new File(sExternalVBoxRoot, "SdCard");
    }

    public static File getExternalDataDir(String packageName) {
        return new File(getExternalStorageDirectory(),String.format(Locale.CHINA, "Android/data/%s", packageName));
    }

    public static File getExternalObbDir(String packageName) {
        return new File(getExternalStorageDirectory(),String.format(Locale.CHINA, "Android/obb/%s/", packageName));
    }
    
    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
        ParallaxFileUtils.mkdirs(file);
        return file;
    }

    public static File getExternalDataFilesDir(String packageName) {
        return new File(getExternalDataDir(packageName), "files");
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getExternalDataCacheDir(String packageName) {
        return new File(getExternalDataDir(packageName), "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }
    
    public static File getXSharedPreferences(String packageName, String prefFileName) {
		return new File(ParallaxEnvironment.getDataDir(packageName, ParallaxActivityThread.getUserId()),"shared_prefs/" + prefFileName + ".xml");
	}
    
    
}
