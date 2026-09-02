package android.MetaCore

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.lsposed.lsparanoid.Obfuscate
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Verifies the host package identity from independent framework views.
 *
 * The panel still owns the policy decision (SPECIFIC/AUTO/ANY). This guard only
 * proves that the package/signing identity sent to the panel is the identity of
 * the APK that is actually installed and executing on this device.
 *
 * The live PackageManager identity and at least one base APK view are mandatory.
 * Split/public APK views are additional tamper signals: if Android can parse one,
 * it must match the live identity, but an OEM that cannot expose signing metadata
 * for an individual split must not block a genuine activation.
 */
@Obfuscate
internal object SdkIdentityGuard {

    data class Snapshot(
        val packageName: String,
        val signingSha256: String,
        val sourceDir: String,
        val verifiedArchiveCount: Int,
    )

    @Suppress("DEPRECATION")
    fun verifyJava(context: Context, expectedPackage: String): Snapshot {
        require(expectedPackage.isNotBlank()) { "Host package is unavailable" }
        require(context.packageName == expectedPackage) { "Context package identity mismatch" }

        val pm = context.packageManager
        val contextAppInfo = context.applicationInfo
        require(contextAppInfo.packageName == expectedPackage) { "ApplicationInfo package identity mismatch" }

        val installedInfo = pm.getPackageInfo(expectedPackage, signingFlags())
        require(installedInfo.packageName == expectedPackage) { "Installed package identity mismatch" }
        val liveSigning = currentSignerSha256(installedInfo)

        val packageAppInfo = pm.getApplicationInfo(expectedPackage, 0)
        require(packageAppInfo.packageName == expectedPackage) { "PackageManager application identity mismatch" }

        val primaryArchives = LinkedHashSet<String>()
        addArchivePath(primaryArchives, contextAppInfo.sourceDir, required = true)
        addArchivePath(primaryArchives, packageAppInfo.sourceDir, required = true)
        addArchivePath(primaryArchives, context.packageCodePath, required = true)

        val optionalArchives = LinkedHashSet<String>()
        addArchivePath(optionalArchives, contextAppInfo.publicSourceDir, required = false)
        addArchivePath(optionalArchives, packageAppInfo.publicSourceDir, required = false)
        addArchivePath(optionalArchives, context.packageResourcePath, required = false)
        contextAppInfo.splitSourceDirs?.forEach { addArchivePath(optionalArchives, it, required = false) }
        contextAppInfo.splitPublicSourceDirs?.forEach { addArchivePath(optionalArchives, it, required = false) }
        packageAppInfo.splitSourceDirs?.forEach { addArchivePath(optionalArchives, it, required = false) }
        packageAppInfo.splitPublicSourceDirs?.forEach { addArchivePath(optionalArchives, it, required = false) }
        optionalArchives.removeAll(primaryArchives)

        require(primaryArchives.isNotEmpty()) { "Installed base APK path is unavailable" }

        var verifiedArchives = 0
        var verifiedPrimary = 0

        for (path in primaryArchives) {
            val file = File(path)
            require(file.isFile) { "Installed base APK archive is unavailable" }
            verifyArchive(pm, path, expectedPackage, liveSigning, mandatory = true)
            verifiedArchives++
            verifiedPrimary++
        }

        for (path in optionalArchives) {
            val file = File(path)
            if (!file.isFile) continue
            if (verifyArchive(pm, path, expectedPackage, liveSigning, mandatory = false)) {
                verifiedArchives++
            }
        }

        require(verifiedPrimary > 0) { "No installed base APK archive was verified" }
        return Snapshot(
            packageName = expectedPackage,
            signingSha256 = liveSigning,
            sourceDir = contextAppInfo.sourceDir.orEmpty(),
            verifiedArchiveCount = verifiedArchives,
        )
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(
        pm: PackageManager,
        path: String,
        expectedPackage: String,
        liveSigning: String,
        mandatory: Boolean,
    ): Boolean {
        val archiveInfo = try {
            pm.getPackageArchiveInfo(path, signingFlags())
        } catch (throwable: RuntimeException) {
            if (mandatory) throw SecurityException("Installed base APK archive could not be parsed", throwable)
            return false
        }

        if (archiveInfo == null) {
            if (mandatory) throw SecurityException("Installed base APK archive could not be parsed")
            return false
        }

        if (archiveInfo.packageName != expectedPackage) {
            throw SecurityException("APK archive package identity mismatch")
        }

        val archiveSigning = try {
            currentSignerSha256(archiveInfo)
        } catch (throwable: RuntimeException) {
            if (mandatory) throw SecurityException("Installed base APK signing identity is unavailable", throwable)
            return false
        }

        require(sameHex(liveSigning, archiveSigning)) { "APK archive signing identity mismatch" }
        return true
    }

    private fun addArchivePath(paths: LinkedHashSet<String>, path: String?, required: Boolean) {
        if (path.isNullOrBlank()) {
            if (required) throw SecurityException("Required APK archive path is unavailable")
            return
        }
        paths += path
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSignerSha256(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
                ?: throw SecurityException("APK signing info is unavailable")
            signingInfo.apkContentsSigners
        } else {
            info.signatures
        }

        require(signatures != null && signatures.size == 1) {
            "Exactly one current APK signer is required"
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())).uppercase()
    }

    private fun sameHex(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.uppercase().toByteArray(StandardCharsets.US_ASCII),
            b.uppercase().toByteArray(StandardCharsets.US_ASCII),
        )
    }

    private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }
}
