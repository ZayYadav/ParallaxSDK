package com.onecore.loader.security;

/**
 * Native AES-backed storage for the small set of release constants that should never be placed
 * directly in Android resources or Java/Kotlin string pools. This is defense in depth; callers
 * should still treat server-side authorization as the security boundary.
 */
public final class NativeStringVault {
    private static final int KEY_PORTAL_URL = 1;
    private static final int GAME_PKG_INDIA = 2;
    private static final int GAME_PKG_GLOBAL = 3;
    private static final int GAME_PKG_KOREA = 4;

    static {
        System.loadLibrary("ParallaxLoader");
    }

    private NativeStringVault() {
    }

    private static native String getSecret(int id);

    private static String require(int id) {
        String value = getSecret(id);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Native protected constant is unavailable");
        }
        return value;
    }

    public static String keyPortalUrl() {
        return require(KEY_PORTAL_URL);
    }

    public static String[] gamePackages() {
        return new String[]{
                require(GAME_PKG_INDIA),
                require(GAME_PKG_GLOBAL),
                require(GAME_PKG_KOREA)
        };
    }
}
