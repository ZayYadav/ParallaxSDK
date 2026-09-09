package com.onecore.loader.libhelper;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.onecore.loader.BoxApplication;
import com.onecore.loader.utils.FLog;
import static com.onecore.loader.Config.GAME_LIST_PKG;
import com.Jagdish.tastytoast.TastyToast;
import java.io.File;
import java.io.IOException;
import top.niunaijun.blackbox.BlackBoxCore;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class ApkEnv {

    private static final String PRIMARY_ARTIFACT_NAME = "libbgmi.so";
    private static final String PRIVATE_ARTIFACT_DIRECTORY = "loader";
    private static final ApkEnv INSTANCE = new ApkEnv();
    private static final Object LOADER_LOCK = new Object();
    private static volatile boolean loaderLoaded = false;

    public static ApkEnv getInstance() {
        return INSTANCE;
    }
    
    public static void LaunchApplication(String packageName) {
        try {
            BlackBoxCore.get().launchApk(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void unInstallApp(String packageName) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isInstalled(String packageName) {
        try {
            return BlackBoxCore.get().isInstalled(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean installByPackage(String packageName) {
        try {
            return BlackBoxCore.get().installPackageAsUser(packageName,0).success;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void stopRunningApp(String packageName) {
    	try {
            BlackBoxCore.get().stopPackage(packageName,0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public ApplicationInfo getApplicationInfo(String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
        	applicationInfo = BoxApplication.get().getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException err) {
        	FLog.error(err.getMessage());
            BoxApplication.get().showToastWithImage(err.getMessage(), TastyToast.WARNING);
            return null;
        }
        return applicationInfo;
    }
    
    public ApplicationInfo getApplicationInfoContainer(String packageName) {
    	if (!isInstalled(packageName)) {
            BoxApplication.get().showToastWithImage("App not install, install first", TastyToast.WARNING);
            return null;
        }

        ApplicationInfo applicationInfo = null;
        try {
         //   applicationInfo = BlackBoxCore.get().getApplicationInfo(packageName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (applicationInfo == null) {
            return null;
        }
        return applicationInfo;
    }
    
    public boolean tryAddLoader(String packageName) {
        if (!GAME_LIST_PKG[0].equals(packageName)) {
            FLog.warning("Native runtime is only configured for the BGMI profile");
            return false;
        }

        boolean isOnline = BoxApplication.STATUS_BY.equals("online");
        File loader = isOnline
                ? new File(new File(BoxApplication.get().getFilesDir(), PRIVATE_ARTIFACT_DIRECTORY),
                        PRIMARY_ARTIFACT_NAME)
                : new File(BoxApplication.get().getApplicationInfo().nativeLibraryDir,
                        PRIMARY_ARTIFACT_NAME);

        try {
            File canonicalLoader = loader.getCanonicalFile();

            if (isOnline) {
                File expectedDirectory = new File(
                        BoxApplication.get().getFilesDir(),
                        PRIVATE_ARTIFACT_DIRECTORY).getCanonicalFile();

                File parent = canonicalLoader.getParentFile();
                if (parent == null
                        || !expectedDirectory.equals(parent)
                        || !PRIMARY_ARTIFACT_NAME.equals(canonicalLoader.getName())) {
                    FLog.error("Native runtime path validation failed");
                    return false;
                }
            }

            if (!canonicalLoader.isFile() || canonicalLoader.length() <= 0) {
                FLog.error("Native runtime is unavailable");
                return false;
            }

            synchronized (LOADER_LOCK) {
                if (!loaderLoaded) {
                    System.load(canonicalLoader.getAbsolutePath());
                    loaderLoaded = true;
                }
            }
            return true;
        } catch (Throwable err) {
            FLog.error("Native runtime load failed", err);
            return false;
        }
    }
    
}


