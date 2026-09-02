package top.niunaijun.blackbox.core;

import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import androidx.annotation.Keep;
import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import android.util.Base64;
import dalvik.system.DexFile;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.BuildConfig;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.compat.DexFileCompat;

public class RNative {
    
    public static final String TAG = "RNative";
    private static final String NATIVE_ARTIFACT_DIRECTORY = "native";
    private static final String NATIVE_ARTIFACT_NAME = "Parallax.so";
    private static boolean isInjected = false;

    static {
        System.loadLibrary("ParallaxCore");
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
    public static native boolean authorizeSdkSession(
            Context context,
            String currentPackage,
            String currentSigningSha256,
            String authorizedPackage,
            String authorizedSigningSha256,
            String responseCanonical,
            String responseSignature,
            String identityCanonical,
            String identitySignature,
            long leaseExpiresAt,
            long serverTime);
    public static native boolean verifyInstalledIdentity(
            Context context,
            String expectedPackage,
            String expectedSigningSha256);
    public static native boolean isSdkSessionValid(long currentTime);
    public static native void clearSdkSession();
    public static native String getSdkPanelEndpoint();

    @Keep
    private static boolean verifyServerSignature(String canonical, String signatureBase64) {
        try {
            byte[] publicDer = Base64.decode(BuildConfig.SDK_PANEL_SIGNING_PUBLIC_KEY, Base64.DEFAULT);
            byte[] signatureBytes = Base64.decode(signatureBase64, Base64.DEFAULT);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicDer)));
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Exception ignored) {
            return false;
        }
    }
    
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
