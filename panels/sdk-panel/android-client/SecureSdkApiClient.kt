package android.MetaCore

import org.json.JSONObject
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM client for connect.php API v2.
 * Pass the same base64 32-byte key configured privately on the server.
 * Use only over HTTPS. Do not log the key, activation key, or plaintext body.
 */
class SecureSdkApiClient(
    private val endpoint: String,
    base64Key: String,
) {
    private val keyBytes = Base64.decode(base64Key, Base64.DEFAULT).also {
        require(it.size == 32) { "SDK API encryption key must decode to 32 bytes" }
    }
    private val key = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    fun activate(
        userKey: String,
        packageName: String,
        appName: String,
        deviceId: String,
    ): JSONObject {
        require(endpoint.startsWith("https://")) { "HTTPS endpoint required" }
        val nonceBytes = ByteArray(24).also(random::nextBytes)
        val payload = JSONObject()
            .put("user_key", userKey)
            .put("package_name", packageName)
            .put("app_name", appName)
            .put("device_id", deviceId)
            .put("timestamp", System.currentTimeMillis() / 1000L)
            .put("nonce", Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

        val envelope = encrypt(payload.toString().toByteArray(Charsets.UTF_8))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "POST"
            instanceFollowRedirects = false
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-API-Version", "2")
            setRequestProperty("User-Agent", "MetaSDK/2.0")
        }

        return try {
            connection.outputStream.use { it.write(envelope.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            decrypt(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun encrypt(plaintext: ByteArray): JSONObject {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val encryptedAndTag = cipher.doFinal(plaintext)
        val ciphertext = encryptedAndTag.copyOfRange(0, encryptedAndTag.size - 16)
        val tag = encryptedAndTag.copyOfRange(encryptedAndTag.size - 16, encryptedAndTag.size)
        return JSONObject()
            .put("version", 2)
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
    }

    private fun decrypt(envelope: JSONObject): JSONObject {
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.DEFAULT)
        val tag = Base64.decode(envelope.getString("tag"), Base64.DEFAULT)
        require(iv.size == 12 && tag.size == 16) { "Invalid encrypted response" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val plaintext = cipher.doFinal(ciphertext + tag)
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    private companion object {
        val AAD = "sdk-panel-v2".toByteArray(Charsets.UTF_8)
    }
}
