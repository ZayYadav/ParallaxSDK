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

        val archivePaths = LinkedHashSet<String>()
        addArchivePath(archivePaths, contextAppInfo.sourceDir, required = true)
        addArchivePath(archivePaths, contextAppInfo.publicSourceDir, required = false)
        addArchivePath(archivePaths, packageAppInfo.sourceDir, required = true)
        addArchivePath(archivePaths, packageAppInfo.publicSourceDir, required = false)
        contextAppInfo.splitSourceDirs?.forEach { addArchivePath(archivePaths, it, required = true) }
        contextAppInfo.splitPublicSourceDirs?.forEach { addArchivePath(archivePaths, it, required = false) }
        packageAppInfo.splitSourceDirs?.forEach { addArchivePath(archivePaths, it, required = true) }
        packageAppInfo.splitPublicSourceDirs?.forEach { addArchivePath(archivePaths, it, required = false) }

        require(archivePaths.isNotEmpty()) { "Installed APK path is unavailable" }

        var verifiedArchives = 0
        for (path in archivePaths) {
            val file = File(path)
            require(file.isFile) { "Installed APK archive is unavailable" }
            val archiveInfo = pm.getPackageArchiveInfo(path, signingFlags())
                ?: throw SecurityException("Installed APK archive could not be parsed")
            require(archiveInfo.packageName == expectedPackage) { "APK archive package identity mismatch" }
            val archiveSigning = currentSignerSha256(archiveInfo)
            require(sameHex(liveSigning, archiveSigning)) { "APK archive signing identity mismatch" }
            verifiedArchives++
        }

        require(verifiedArchives > 0) { "No installed APK archive was verified" }
        return Snapshot(
            packageName = expectedPackage,
            signingSha256 = liveSigning,
            sourceDir = contextAppInfo.sourceDir.orEmpty(),
            verifiedArchiveCount = verifiedArchives,
        )
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
