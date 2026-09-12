package com.Parallax.SDK.core.core;

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
import com.Parallax.SDK.core.ParallaxCore;
import com.Parallax.SDK.BuildConfig;
import com.Parallax.SDK.core.app.ParallaxActivityThread;
import com.Parallax.SDK.core.utils.compat.ParallaxDexFileCompat;

public class ParallaxNative {
    
    public static final String TAG = "ParallaxNative";
    private static final String NATIVE_ARTIFACT_DIRECTORY = "native";
    private static final String NATIVE_ARTIFACT_NAME = "ParallaxCore.so";
    private static boolean isInjected = false;

    static {
        System.loadLibrary("ParallaxCore");
        File file = new File(
                new File(ParallaxCore.getContext().getNoBackupFilesDir(), NATIVE_ARTIFACT_DIRECTORY),
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
            String currentPackage,
            String currentSigningSha256,
            String authorizedPackage,
            String authorizedSigningSha256,
            String responseCanonical,
            String responseSignature,
            long leaseExpiresAt,
            long serverTime);
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
        if (origCallingUid == ParallaxCore.getHostUid()) {
            if(ParallaxActivityThread.getAppPackageName().equals("com.google.android.gms")){
                return Process.ROOT_UID;
            }
            if(ParallaxActivityThread.getAppPackageName().equals("com.google.android.webview")){
                return Process.myUid();
            }
            return ParallaxActivityThread.getCallingBUid();
        }
        return origCallingUid;
    }

    @Keep
    public static String redirectPath(String path) {
        return ParallaxRuntimeCore.get().redirectPath(path);
    }

    @Keep
    public static File redirectPath(File path) {
        return ParallaxRuntimeCore.get().redirectPath(path);
    }

}
