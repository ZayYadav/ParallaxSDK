package top.niunaijun.blackbox.core;

import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import androidx.annotation.Keep;
import android.content.Context;
import java.io.File;
import java.util.List;
import dalvik.system.DexFile;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.compat.DexFileCompat;

public class RNative {
    
    public static final String TAG = "RNative";
    private static final String NATIVE_ARTIFACT_DIRECTORY = "native";
    private static final String NATIVE_ARTIFACT_NAME = "Parallax.so";
    private static boolean isInjected = false;

    static {
        System.loadLibrary("RIYAZcore");
        File file = new File(
                new File(BlackBoxCore.getContext().getNoBackupFilesDir(), NATIVE_ARTIFACT_DIRECTORY),
                NATIVE_ARTIFACT_NAME);
        if (file.isFile()) {
            System.load(file.getAbsolutePath());
        }
    }

    public static native void init(int apiLevel);
    public static native void enableIO();
    public static native void addIORule(String targetPath, String relocatePath);
    public static native void hideXposed();
    
    @Keep
    public static int getCallingUid(int origCallingUid) {
        if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID) return origCallingUid;
        if (origCallingUid > Process.LAST_APPLICATION_UID) return origCallingUid;
        if (origCallingUid == BlackBoxCore.getHostUid()) {
            if(BActivityThread.getAppPackageName().equals("com.google.android.gms")){
                return Process.ROOT_UID;
            }
            if(BActivityThread.getAppPackageName().equals("com.google.android.webview")){
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
