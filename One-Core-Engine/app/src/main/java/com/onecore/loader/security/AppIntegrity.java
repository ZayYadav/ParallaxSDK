package com.onecore.loader.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.onecore.loader.BuildConfig;
import com.onecore.loader.utils.FLog;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.security.cert.X509Certificate;

/**
 * Fail-closed APK signing identity validation.
 *
 * <p>The authoritative checks are the installed PackageManager signer set and an independent
 * cryptographic verification of the exact installed APK with Android apksig. Both signer sets
 * must be identical and every signer must be explicitly allowed.</p>
 *
 * <p>PackageManager archive parsing and the JNI/OpenSSL digest comparison are retained only as
 * defense-in-depth diagnostics. Some Android 15/16 builds do not reliably expose signingInfo for
 * an installed APK when it is reparsed through getPackageArchiveInfo(), and an optional native
 * cross-check must not reject an APK that already passed both authoritative cryptographic paths.</p>
 */
public final class AppIntegrity {
    private static final long MIN_APK_BYTES = 4 * 1024L;

    public enum Status {
        VALID,
        CONFIGURATION_MISSING,
        PACKAGE_IDENTITY_MISMATCH,
        APK_SOURCE_INVALID,
        APK_SIGNATURE_INVALID,
        SIGNER_MISSING,
        SIGNER_MISMATCH,
        ARCHIVE_SIGNER_MISMATCH,
        NATIVE_VERIFICATION_FAILED,
        VERIFICATION_ERROR
    }

    public static final class Verification {
        private final Status status;

        private Verification(Status status) {
            this.status = status;
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        public Status status() {
            return status;
        }
    }

    private AppIntegrity() {
    }

    public static boolean isSignatureValid(Context context) {
        return verify(context).isValid();
    }

    public static Verification verify(Context context) {
        if (context == null) {
            return result(Status.VERIFICATION_ERROR);
        }

        try {
            byte[][] allowedDigests = configuredSignerDigests();
            if (allowedDigests.length == 0) {
                return result(Status.CONFIGURATION_MISSING);
            }

            Context appContext = context.getApplicationContext();
            if (appContext == null) {
                appContext = context;
            }
            String packageName = appContext.getPackageName();
            if (!BuildConfig.APPLICATION_ID.equals(packageName)) {
                return result(Status.PACKAGE_IDENTITY_MISMATCH);
            }

            PackageManager packageManager = appContext.getPackageManager();
            PackageInfo installedInfo = getInstalledPackageInfo(packageManager, packageName);
            if (!packageName.equals(installedInfo.packageName)
                    || installedInfo.applicationInfo == null
                    || !uidOwnsPackage(packageManager, installedInfo.applicationInfo.uid, packageName)) {
                return result(Status.PACKAGE_IDENTITY_MISMATCH);
            }

            File apkFile = canonicalApk(installedInfo.applicationInfo, appContext.getApplicationInfo());
            if (!isStructurallyValidApk(apkFile)) {
                return result(Status.APK_SOURCE_INVALID);
            }

            // Authoritative path #1: Android PackageManager active signer set.
            Signature[] installedSigners = getActiveSigners(installedInfo);
            if (installedSigners.length == 0) {
                return result(Status.SIGNER_MISSING);
            }
            byte[][] installedCertificates = certificateBytes(installedSigners);
            byte[][] installedDigests = sha256Digests(installedCertificates);
            if (!matchesAllowedSignerDigests(allowedDigests, installedDigests)) {
                return result(Status.SIGNER_MISMATCH);
            }

            // Authoritative path #2: parse and cryptographically verify the exact installed APK.
            byte[][] cryptographicCertificates = verifyApkAndGetCertificates(apkFile);
            if (cryptographicCertificates.length == 0) {
                return result(Status.APK_SIGNATURE_INVALID);
            }
            byte[][] cryptographicDigests = sha256Digests(cryptographicCertificates);
            if (!matchesAllowedSignerDigests(allowedDigests, cryptographicDigests)
                    || !sameSignerSets(installedDigests, cryptographicDigests)) {
                return result(Status.APK_SIGNATURE_INVALID);
            }

            // Best-effort PackageManager archive cross-check. Android 15/16 may return archive
            // metadata without signingInfo for an already-installed base APK, so absence is not an
            // identity failure after the two authoritative signer checks above have succeeded.
            try {
                PackageInfo archiveInfo = getArchivePackageInfo(packageManager, apkFile);
                if (archiveInfo != null) {
                    if (archiveInfo.packageName != null && !packageName.equals(archiveInfo.packageName)) {
                        return result(Status.PACKAGE_IDENTITY_MISMATCH);
                    }
                    Signature[] archiveSigners = getActiveSigners(archiveInfo);
                    if (archiveSigners.length > 0
                            && !sameSignerSets(
                                    installedDigests,
                                    sha256Digests(certificateBytes(archiveSigners)))) {
                        return result(Status.ARCHIVE_SIGNER_MISMATCH);
                    }
                }
            } catch (Throwable archiveError) {
                FLog.info("Optional archive signer cross-check unavailable: "
                        + archiveError.getClass().getSimpleName());
            }

            // Best-effort native digest cross-check. Do not turn a JNI/library availability issue
            // into a false identity rejection after PackageManager + apksig already agreed exactly.
            if (!NativeSigningVerifier.verify(
                    allowedDigests,
                    installedCertificates,
                    packageName,
                    BuildConfig.APPLICATION_ID)) {
                FLog.info("Optional native signing cross-check unavailable or rejected the duplicate check");
            }

            return result(Status.VALID);
        } catch (Throwable error) {
            FLog.error("APK identity verification error", error);
            return result(Status.VERIFICATION_ERROR);
        }
    }

    /** Returns the first active APK signing certificate as uppercase SHA-256 hex. */
    public static String currentSigningCertificateSha256(Context context) {
        Verification verification = verify(context);
        if (!verification.isValid()) {
            throw new IllegalStateException("APK signing identity is invalid: " + verification.status());
        }
        try {
            PackageInfo packageInfo = getInstalledPackageInfo(
                    context.getPackageManager(), context.getPackageName());
            Signature[] signatures = getActiveSigners(packageInfo);
            if (signatures.length == 0) {
                throw new IllegalStateException("APK has no active signing certificate");
            }
            return encodeHex(sha256(signatures[0].toByteArray()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read APK signing certificate", exception);
        }
    }

    private static Verification result(Status status) {
        return new Verification(status);
    }

    private static byte[][] configuredSignerDigests() {
        String configured = BuildConfig.EXPECTED_SIGNATURE_SHA256;
        if (configured == null || configured.trim().isEmpty()) {
            return new byte[0][];
        }
        String[] values = configured.split("[,;]");
        List<byte[]> digests = new ArrayList<>();
        for (String value : values) {
            if (!value.trim().isEmpty()) {
                digests.add(decodeHex(value));
            }
        }
        return digests.toArray(new byte[0][]);
    }

    private static PackageInfo getInstalledPackageInfo(
            PackageManager packageManager,
            String packageName) throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return packageManager.getPackageInfo(packageName, flags);
    }

    private static PackageInfo getArchivePackageInfo(PackageManager packageManager, File apkFile) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
        if (packageInfo != null && packageInfo.applicationInfo != null) {
            packageInfo.applicationInfo.sourceDir = apkFile.getAbsolutePath();
            packageInfo.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
        }
        return packageInfo;
    }

    private static File canonicalApk(
            ApplicationInfo installedInfo,
            ApplicationInfo contextInfo) throws IOException {
        if (installedInfo.sourceDir == null || contextInfo.sourceDir == null) {
            throw new IOException("APK source path is unavailable");
        }
        File installed = new File(installedInfo.sourceDir).getCanonicalFile();
        File context = new File(contextInfo.sourceDir).getCanonicalFile();
        if (!installed.equals(context)) {
            throw new IOException("APK source paths disagree");
        }
        return installed;
    }

    private static boolean uidOwnsPackage(
            PackageManager packageManager,
            int uid,
            String packageName) {
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null) {
            return false;
        }
        for (String candidate : packages) {
            if (packageName.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStructurallyValidApk(File apkFile) {
        if (!apkFile.isFile() || !apkFile.canRead() || apkFile.length() < MIN_APK_BYTES) {
            return false;
        }
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            ZipEntry manifest = zipFile.getEntry("AndroidManifest.xml");
            ZipEntry dex = zipFile.getEntry("classes.dex");
            return manifest != null && manifest.getSize() != 0L
                    && dex != null && dex.getSize() != 0L;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static byte[][] verifyApkAndGetCertificates(File apkFile) throws Exception {
        ApkVerifier.Result verification = new ApkVerifier.Builder(apkFile)
                .setMinCheckedPlatformVersion(24)
                .build()
                .verify();
        if (!verification.isVerified() || verification.getSignerCertificates().isEmpty()) {
            return new byte[0][];
        }
        List<X509Certificate> certificates = verification.getSignerCertificates();
        byte[][] encoded = new byte[certificates.size()][];
        for (int index = 0; index < certificates.size(); index++) {
            encoded[index] = certificates.get(index).getEncoded();
        }
        return encoded;
    }

    private static Signature[] getActiveSigners(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo == null) {
                return new Signature[0];
            }
            Signature[] signers = packageInfo.signingInfo.getApkContentsSigners();
            return signers == null ? new Signature[0] : signers;
        }
        @SuppressWarnings("deprecation")
        Signature[] signatures = packageInfo.signatures;
        return signatures == null ? new Signature[0] : signatures;
    }

    private static byte[][] certificateBytes(Signature[] signatures) {
        byte[][] certificates = new byte[signatures.length][];
        for (int index = 0; index < signatures.length; index++) {
            certificates[index] = signatures[index].toByteArray();
        }
        return certificates;
    }

    private static byte[][] sha256Digests(byte[][] values) throws Exception {
        byte[][] digests = new byte[values.length][];
        for (int index = 0; index < values.length; index++) {
            digests[index] = sha256(values[index]);
        }
        return digests;
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    static boolean matchesAllowedSignerDigests(byte[][] allowed, byte[][] actual) {
        if (allowed == null || allowed.length == 0 || actual == null || actual.length == 0) {
            return false;
        }
        for (byte[] actualDigest : actual) {
            boolean found = false;
            for (byte[] allowedDigest : allowed) {
                if (allowedDigest != null
                        && actualDigest != null
                        && MessageDigest.isEqual(allowedDigest, actualDigest)) {
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    static boolean sameSignerSets(byte[][] first, byte[][] second) {
        if (first == null || second == null || first.length == 0 || first.length != second.length) {
            return false;
        }
        List<byte[]> left = sortedDigests(first);
        List<byte[]> right = sortedDigests(second);
        for (int index = 0; index < left.size(); index++) {
            if (!MessageDigest.isEqual(left.get(index), right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<byte[]> sortedDigests(byte[][] digests) {
        List<byte[]> values = new ArrayList<>();
        for (byte[] digest : digests) {
            values.add(digest == null ? new byte[0] : Arrays.copyOf(digest, digest.length));
        }
        Collections.sort(values, new Comparator<byte[]>() {
            @Override
            public int compare(byte[] first, byte[] second) {
                int length = Math.min(first.length, second.length);
                for (int index = 0; index < length; index++) {
                    int comparison = Integer.compare(first[index] & 0xff, second[index] & 0xff);
                    if (comparison != 0) {
                        return comparison;
                    }
                }
                return Integer.compare(first.length, second.length);
            }
        });
        return values;
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

    private static String encodeHex(byte[] value) {
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte item : value) {
            hex.append(String.format(Locale.US, "%02X", item & 0xff));
        }
        return hex.toString();
    }
}
