package com.onecore.loader.security;

/**
 * Native signing verifier independent from PackageManager signer metadata.
 *
 * <p>The on-disk path parses and verifies APK Signature Scheme v2 directly in native code,
 * validates libParallaxLoader's executable mapping against its on-disk ELF image, and first binds
 * the claimed APK path to the real installed base.apk associated with the currently executing
 * native library. This prevents wrapper/repacker code from pointing the verifier at an embedded
 * untouched copy of the original APK.</p>
 */
final class NativeSigningVerifier {
    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("ParallaxLoader");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private NativeSigningVerifier() {
    }

    static boolean verify(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage) {
        if (!AVAILABLE) {
            return false;
        }
        try {
            return verifySigningIdentity(
                    allowedDigests, certificates, actualPackage, expectedPackage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean verifyInstalledApk(String apkPath, String actualPackage) {
        if (!AVAILABLE || apkPath == null || apkPath.isEmpty()
                || actualPackage == null || actualPackage.isEmpty()) {
            return false;
        }
        try {
            // Process binding is intentionally checked both before and after the direct v2
            // attestation. An embedded original APK cannot be substituted for the host base.apk.
            return verifyProcessBoundApkNative(apkPath, actualPackage)
                    && verifyInstalledApkNative(apkPath, actualPackage)
                    && verifyProcessBoundApkNative(apkPath, actualPackage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static native boolean verifySigningIdentity(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage);

    private static native boolean verifyInstalledApkNative(
            String apkPath,
            String actualPackage);

    private static native boolean verifyProcessBoundApkNative(
            String apkPath,
            String actualPackage);
}
