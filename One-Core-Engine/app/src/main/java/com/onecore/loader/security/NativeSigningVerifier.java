package com.onecore.loader.security;

/** Native, independent SHA-256 comparison for the active APK signing certificates. */
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

    private static native boolean verifySigningIdentity(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage);
}
