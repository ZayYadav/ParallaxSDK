package android.MetaCore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ActivationBackup {
    private const val TAG = "ActivationBackup"
    private const val VERSION = 1
    private const val BACKUP_FILE_NAME = "sdk_activation_backup.json"
    private const val BACKUP_DIR_NAME = ".keshavxowner"
    private const val FALLBACK_HOST_PACKAGE = "com.bgmi"
    private val random = SecureRandom()

    fun save(context: Context, licenseKey: String) {
        val normalizedKey = licenseKey.trim().uppercase()
        if (normalizedKey.isEmpty()) return

        val payload = JSONObject()
            .put("version", VERSION)
            .put("license_key", normalizedKey)
            .put("package_name", context.packageName)
            .put("saved_at", System.currentTimeMillis())

        val encrypted = encrypt(context, payload.toString())
        var saved = false
        for (file in backupFiles(context)) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(encrypted.toString(), StandardCharsets.UTF_8)
                saved = true
                Log.i(TAG, "Activation backup saved: " + file.absolutePath)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Activation backup save failed: " + file.absolutePath, throwable)
            }
        }
        if (!saved) {
            Log.w(TAG, "Activation backup was not saved to any durable path")
        }
    }

    fun restore(context: Context): String? {
        for (file in backupFiles(context)) {
            try {
                if (!file.isFile || file.length() <= 0L) continue
                val payload = decrypt(context, JSONObject(file.readText(StandardCharsets.UTF_8)))
                val data = JSONObject(payload)
                if (data.optInt("version") != VERSION) continue
                val key = data.optString("license_key", "").trim().uppercase()
                if (key.isNotEmpty() && key.length <= 256) {
                    Log.i(TAG, "Activation backup restored from: " + file.absolutePath)
                    return key
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "Activation backup restore failed: " + file.absolutePath, throwable)
            }
        }
        return null
    }

    private fun encrypt(context: Context, plaintext: String): JSONObject {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(backupKey(context), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(context.packageName.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return JSONObject()
            .put("version", VERSION)
            .put("iv", b64(iv))
            .put("ciphertext", b64(encrypted))
    }

    private fun decrypt(context: Context, envelope: JSONObject): String {
        if (envelope.optInt("version") != VERSION) {
            throw SecurityException("Unsupported activation backup version")
        }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(backupKey(context), "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(context.packageName.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun backupKey(context: Context): ByteArray {
        val material = listOf(
            "keshavxowner-activation-backup-v1",
            context.packageName,
            androidId(context),
            signingSha256(context),
        ).joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
    }

    private fun androidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    @Suppress("DEPRECATION")
    private fun signingSha256(context: Context): String {
        val packageName = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: throw SecurityException("Signing info unavailable")
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val certificate = signatures?.firstOrNull()?.toByteArray()
            ?: throw SecurityException("Signing certificate unavailable")
        return MessageDigest.getInstance("SHA-256").digest(certificate)
            .joinToString("") { "%02x".format(it) }
            .uppercase()
    }

    private fun backupFiles(context: Context): List<File> {
        val result = ArrayList<File>()
        context.getExternalFilesDir(BACKUP_DIR_NAME)?.let { result.add(File(it, BACKUP_FILE_NAME)) }

        val externalRoot = Environment.getExternalStorageDirectory()
        val packageNames = linkedSetOf(context.packageName, FALLBACK_HOST_PACKAGE)
        for (packageName in packageNames) {
            result.add(File(externalRoot, "Android/media/$packageName/$BACKUP_DIR_NAME/$BACKUP_FILE_NAME"))
        }
        result.add(File(externalRoot, "KESHAVXOWNER/$BACKUP_FILE_NAME"))

        return result.distinctBy { it.absolutePath }
    }

    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
}
