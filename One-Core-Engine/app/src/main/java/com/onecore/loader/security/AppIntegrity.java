package com.onecore.loader.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.onecore.loader.BuildConfig;

import java.security.MessageDigest;
import java.util.Locale;

/** Centralized, fail-closed release signing-certificate validation. */
public final class AppIntegrity {
    private AppIntegrity() {
    }

    public static boolean isSignatureValid(Context context) {
        String configuredHash = BuildConfig.EXPECTED_SIGNATURE_SHA256;
        if (configuredHash == null || configuredHash.trim().isEmpty()) {
            return BuildConfig.DEBUG;
        }

        try {
            byte[] expected = decodeHex(configuredHash);
            for (Signature signature : getSignatures(context)) {
                byte[] actual = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                if (MessageDigest.isEqual(actual, expected)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Integrity failures are intentionally fail-closed in release builds.
        }
        return false;
    }

    /** Returns the first APK signing certificate as uppercase SHA-256 hex. */
    public static String currentSigningCertificateSha256(Context context) {
        try {
            Signature[] signatures = getSignatures(context);
            if (signatures.length == 0) {
                throw new IllegalStateException("APK has no signing certificate");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.US, "%02X", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read APK signing certificate", exception);
        }
    }

    private static Signature[] getSignatures(Context context) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (packageInfo.signingInfo == null) {
                return new Signature[0];
            }
            return packageInfo.signingInfo.hasMultipleSigners()
                    ? packageInfo.signingInfo.getApkContentsSigners()
                    : packageInfo.signingInfo.getSigningCertificateHistory();
        }

        @SuppressWarnings("deprecation")
        PackageInfo packageInfo = packageManager.getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES);
        return packageInfo.signatures == null ? new Signature[0] : packageInfo.signatures;
    }

    static byte[] decodeHex(String value) {
        String normalized = value.replace(":", "").trim().toUpperCase(Locale.US);
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("Expected a SHA-256 certificate digest");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            int high = Character.digit(normalized.charAt(index), 16);
            int low = Character.digit(normalized.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid certificate digest");
            }
            result[index / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
