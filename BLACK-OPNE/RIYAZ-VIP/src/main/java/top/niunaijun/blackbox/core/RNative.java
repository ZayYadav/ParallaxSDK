package top.niunaijun.blackbox.core;

import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Keep;
import java.io.File;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;

public class RNative {
    
    public static final String TAG = "RNative";
    private static final String[] GAME_PACKAGE_PREFIXES = {
            "com.pubg.",
            "com.tencent.ig",
            "com.krafton.",
            "com.rekoo.",
            "com.vng.",
            "com.proximabeta.",
            "com.activision."
    };
    private static boolean sExternalLoaderAttempted = false;
    private static boolean sExternalLoaderLoaded = false;
    public static String libtarget = "libbgmi.so";

    static {
        System.loadLibrary("RIYAZcore");
    }

    public static native void init(int apiLevel);
    public static native void enableIO();
    public static native void addIORule(String targetPath, String relocatePath);
    public static native void hideXposed();

    public static synchronized void loadExternalGameSdkIfReady(String packageName, String processName) {
        if (sExternalLoaderAttempted || !isGameMainProcess(packageName, processName)) {
            return;
        }
        sExternalLoaderAttempted = true;
        File file = new File(BlackBoxCore.getContext().getFilesDir(), "loader/" + libtarget);
        if (!file.isFile()) {
            Log.i(TAG, "External loader not found: " + file.getAbsolutePath());
            return;
        }
        try {
            System.load(file.getAbsolutePath());
            sExternalLoaderLoaded = true;
            Log.i(TAG, "External loader loaded for " + packageName + ": " + file.getAbsolutePath());
        } catch (Throwable e) {
            Log.e(TAG, "External loader failed, continuing without it: " + file.getAbsolutePath(), e);
        }
    }

    public static boolean isExternalLoaderLoaded() {
        return sExternalLoaderLoaded;
    }

    private static boolean isGameMainProcess(String packageName, String processName) {
        if (TextUtils.isEmpty(packageName) || !packageName.equals(processName)) {
            return false;
        }
        for (String prefix : GAME_PACKAGE_PREFIXES) {
            if (packageName.startsWith(prefix) || packageName.equals(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    @Keep
    public static int getCallingUid(int origCallingUid) {
        if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID) return origCallingUid;
        if (origCallingUid > Process.LAST_APPLICATION_UID) return origCallingUid;
        if (origCallingUid == BlackBoxCore.getHostUid()) {
            String packageName = BActivityThread.getAppPackageName();
            if ("com.google.android.gms".equals(packageName)) {
                return Process.ROOT_UID;
            }
            if ("com.google.android.webview".equals(packageName)) {
                return Process.myUid();
            }
            return BActivityThread.getCallingBUid();
        }
        return origCallingUid;
    }

    @Keep
    public static String redirectPath(String path) {
        return RCore.get().redirectPath(path);
    }

    @Keep
    public static File redirectPath(File path) {
        return RCore.get().redirectPath(path);
    }

}
