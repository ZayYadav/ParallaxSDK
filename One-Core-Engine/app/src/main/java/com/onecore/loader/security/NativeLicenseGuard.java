package com.onecore.loader.security;

import android.content.Context;
import android.os.Debug;

import com.onecore.loader.utils.FLog;

/**
 * Native pre/post-flight guard for the licensing transport.
 *
 * <p>The endpoint lives in the native library instead of DEX. Before any sensitive license work,
 * the guard also performs a PackageManager-independent APK Signature Scheme v2 verification of
 * the exact installed base APK and validates the native library's executable mapping.</p>
 */
final class NativeLicenseGuard {
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

    private NativeLicenseGuard() {
    }

    static String connectUrl() {
        if (!AVAILABLE) {
            throw new SecurityException("Native licensing guard is unavailable");
        }
        String value = nativeConnectUrl();
        if (value == null || value.length() < 12 || !value.startsWith("https://")) {
            throw new SecurityException("Native licensing endpoint validation failed");
        }
        return value;
    }

    static String connectHost() {
        if (!AVAILABLE) {
            throw new SecurityException("Native licensing guard is unavailable");
        }
        String value = nativeConnectHost();
        if (value == null || value.isEmpty() || value.indexOf('/') >= 0 || value.indexOf(':') >= 0) {
            throw new SecurityException("Native licensing host validation failed");
        }
        return value;
    }

    static void assertSecure(Context context, String[] tlsPins, String publicKeyB64) {
        if (!AVAILABLE || context == null) {
            throw new SecurityException("Native licensing guard is unavailable");
        }

        String apkPath = context.getApplicationInfo() == null
                ? null : context.getApplicationInfo().sourceDir;
        if (apkPath == null || apkPath.isEmpty()
                || !nativeVerifyInstalledApk(apkPath, context.getPackageName())) {
            throw new SecurityException("Secure verification environment rejected");
        }

        boolean proxyConfigured = hasConfiguredProxy();
        boolean debuggerConnected = Debug.isDebuggerConnected() || Debug.waitingForDebugger();
        int result;
        try {
            result = nativeCheckEnvironment(
                    context.getPackageName(),
                    tlsPins == null ? new String[0] : tlsPins,
                    publicKeyB64 == null ? "" : publicKeyB64,
                    proxyConfigured,
                    debuggerConnected);
        } catch (Throwable error) {
            FLog.error("Native licensing guard execution failed", error);
            throw new SecurityException("Secure verification environment rejected");
        }

        if (result != 0) {
            // Keep the public error intentionally generic so the guard does not become a bypass map.
            FLog.error("Native licensing guard rejected environment code=" + result);
            throw new SecurityException("Secure verification environment rejected");
        }
    }

    private static boolean hasConfiguredProxy() {
        String httpsProxy = System.getProperty("https.proxyHost", "");
        String httpProxy = System.getProperty("http.proxyHost", "");
        return (httpsProxy != null && !httpsProxy.trim().isEmpty())
                || (httpProxy != null && !httpProxy.trim().isEmpty());
    }

    private static native String nativeConnectUrl();

    private static native String nativeConnectHost();

    private static native boolean nativeVerifyInstalledApk(String apkPath, String packageName);

    /** Returns 0 when the native process/transport environment passes all checks. */
    private static native int nativeCheckEnvironment(
            String packageName,
            String[] tlsPins,
            String publicKeyB64,
            boolean proxyConfigured,
            boolean debuggerConnected);
}
