package com.onecore.loader.security;

/**
 * Native signing/process-binding verifier independent from PackageManager signer metadata.
 *
 * <p>The host APK path is bound in native code to the real installed base.apk associated with the
 * currently executing Loader library. Cryptographic signer identity is then checked separately
 * against the build-pinned SHA-256 certificate using certificates produced by apksig. Keeping
 * process binding separate from runtime relocated native-text bytes avoids false signature alarms
 * on genuine Android linker/runtime states while still preventing an embedded untouched APK from
 * being substituted for the actual installed host.</p>
 */
final class NativeSigningVerifier {
    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("KESHAVXOWNERLoader");
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
            // Bind only to the currently executing installation. Actual signer + signed-content
            // validation is performed by AppIntegrity/apksig and verifySigningIdentity().
            return verifyProcessBoundApkNative(apkPath, actualPackage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static native boolean verifySigningIdentity(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage);

    // Kept for native compatibility/diagnostics; it is deliberately not used as the signing
    // identity gate because it also contains runtime-native mapping checks that can be unstable
    // across legitimate Android linker states.
    private static native boolean verifyInstalledApkNative(
            String apkPath,
            String actualPackage);

    private static native boolean verifyProcessBoundApkNative(
            String apkPath,
            String actualPackage);
}
