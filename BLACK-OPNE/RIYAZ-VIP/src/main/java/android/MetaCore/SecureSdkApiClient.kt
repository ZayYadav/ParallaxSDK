package android.MetaCore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.lsposed.lsparanoid.Obfuscate
import top.niunaijun.blackbox.BuildConfig
import top.niunaijun.blackbox.core.RNative
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

/**
 * Parallax activation API v3.
 *
 * The APK contains only server public keys. Every request uses a new ephemeral
 * ECDH key, every response is encrypted, and the panel signs the response with
 * a separate private key. The device also proves possession of an Android
 * Keystore key that is bound on first successful activation.
 */
@Obfuscate
internal class SecureSdkApiClient(private val context: Context) {
    private val random = SecureRandom()

    fun activate(userKey: String, packageName: String, appName: String, deviceId: String): JSONObject {
        require(userKey.isNotBlank()) { "Activation key is required" }
        require(packageName.matches(Regex("^[A-Za-z][A-Za-z0-9_.]{2,190}$"))) { "Invalid package name" }
        val endpointText = RNative.getSdkPanelEndpoint()
        val endpointDigest = sha256(endpointText.toByteArray(StandardCharsets.UTF_8))
        require(MessageDigest.isEqual(endpointDigest, ENDPOINT_SHA256)) {
            "SDK panel configuration integrity failed"
        }
        val endpoint = URL(endpointText)
        require(endpoint.protocol == "https" && endpoint.port == -1 && endpoint.query == null && endpoint.ref == null) {
            "Untrusted SDK panel endpoint"
        }

        val timestamp = System.currentTimeMillis() / 1000L
        val nonceBytes = ByteArray(24).also(random::nextBytes)
        val nonce = b64Url(nonceBytes)
        val clientRequestId = b64Url(ByteArray(24).also(random::nextBytes))
        val appSignature = appSigningCertificateSha256(packageName)
        val deviceKeyPair = getOrCreateDeviceKey()
        val canonicalProof = listOf(
            "3", userKey.trim().uppercase(), packageName, deviceId, appSignature,
            timestamp.toString(), nonce, clientRequestId, SDK_VERSION.toString(),
        ).joinToString("\n")
        val proofSigner = Signature.getInstance("SHA256withECDSA").apply {
            initSign(deviceKeyPair.private)
            update(canonicalProof.toByteArray(StandardCharsets.UTF_8))
        }

        val payload = JSONObject()
            .put("user_key", userKey.trim().uppercase())
            .put("package_name", packageName)
            .put("app_name", appName.take(120))
            .put("device_id", deviceId)
            .put("app_signature_sha256", appSignature)
            .put("device_public_key", b64(deviceKeyPair.public.encoded))
            .put("device_signature", b64(proofSigner.sign()))
            .put("timestamp", timestamp)
            .put("nonce", nonce)
            .put("request_id", clientRequestId)
            .put("sdk_version", SDK_VERSION)

        val ephemeralGenerator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }
        val ephemeral = ephemeralGenerator.generateKeyPair()
        val ephemeralB64 = b64(ephemeral.public.encoded)
        val serverEcdhKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(BuildConfig.SDK_PANEL_ECDH_PUBLIC_KEY, Base64.DEFAULT))
        )
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeral.private)
            doPhase(serverEcdhKey, true)
            generateSecret()
        }
        val salt = sha256("sdk-panel-v3|${BuildConfig.SDK_PANEL_KEY_ID}".toByteArray(StandardCharsets.UTF_8))
        val aesKey = hkdfSha256(sharedSecret, salt, "request|$ephemeralB64".toByteArray(StandardCharsets.UTF_8))
        val requestAad = "sdk-panel-v3|${BuildConfig.SDK_PANEL_KEY_ID}|$ephemeralB64"
            .toByteArray(StandardCharsets.UTF_8)
        val requestEnvelope = encryptEnvelope(payload.toString().toByteArray(StandardCharsets.UTF_8), aesKey, requestAad)
            .put("version", 3)
            .put("key_id", BuildConfig.SDK_PANEL_KEY_ID)
            .put("ephemeral_key", ephemeralB64)

        val connection = endpoint.openConnection() as? HttpsURLConnection
            ?: throw SecurityException("HTTPS connection required")
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-API-Version", "3")
            connection.setRequestProperty("User-Agent", "ParallaxSDK/3.0")
            val bodyBytes = requestEnvelope.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(bodyBytes.size)
            connection.outputStream.use { it.write(bodyBytes) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                ?: throw SecurityException("Panel returned an unsigned error")
            val responseBytes = stream.use { readLimited(it, MAX_RESPONSE_BYTES) }
            val envelope = JSONObject(String(responseBytes, StandardCharsets.UTF_8))
            openResponse(envelope, aesKey, nonce, clientRequestId, appSignature, deviceKeyPair.public.encoded)
        } finally {
            connection.disconnect()
            sharedSecret.fill(0)
            aesKey.fill(0)
        }
    }

    private fun openResponse(
        envelope: JSONObject,
        aesKey: ByteArray,
        requestNonce: String,
        clientRequestId: String,
        appSignature: String,
        devicePublicKey: ByteArray,
    ): JSONObject {
        require(envelope.optInt("version") == 3) { "Unsigned panel response" }
        val keyId = envelope.getString("key_id")
        require(keyId == BuildConfig.SDK_PANEL_KEY_ID) { "Unexpected response key" }
        val requestId = envelope.getString("request_id")
        val expectedRequestId = hex(sha256(requestNonce.toByteArray(StandardCharsets.UTF_8)))
        require(MessageDigest.isEqual(requestId.toByteArray(), expectedRequestId.toByteArray())) {
            "Response is not bound to this request"
        }
        val ivB64 = envelope.getString("iv")
        val ciphertextB64 = envelope.getString("ciphertext")
        val tagB64 = envelope.getString("tag")
        val signatureBytes = Base64.decode(envelope.getString("signature"), Base64.DEFAULT)
        val canonical = listOf("3", keyId, requestId, ivB64, ciphertextB64, tagB64).joinToString("\n")
        val signingKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(BuildConfig.SDK_PANEL_SIGNING_PUBLIC_KEY, Base64.DEFAULT))
        )
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(signingKey)
            update(canonical.toByteArray(StandardCharsets.UTF_8))
            verify(signatureBytes)
        }
        require(verified) { "Panel signature verification failed" }

        val iv = Base64.decode(ivB64, Base64.DEFAULT)
        val ciphertext = Base64.decode(ciphertextB64, Base64.DEFAULT)
        val tag = Base64.decode(tagB64, Base64.DEFAULT)
        require(iv.size == 12 && tag.size == 16 && ciphertext.size <= MAX_RESPONSE_BYTES) {
            "Invalid encrypted panel response"
        }
        val aad = "sdk-panel-v3-response|$keyId|$requestId".toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            updateAAD(aad)
        }
        val data = JSONObject(String(cipher.doFinal(ciphertext + tag), StandardCharsets.UTF_8))
        val serverTime = data.optLong("server_time", 0L)
        require(serverTime > 0 && kotlin.math.abs(System.currentTimeMillis() / 1000L - serverTime) <= 600L) {
            "Panel response time is invalid"
        }
        if (data.optString("status") == "success") {
            require(data.optString("app_signature_sha256") == appSignature) { "App signature binding failed" }
            require(data.optString("authorized_package") == context.packageName) { "Host package binding failed" }
            require(data.optString("authorized_signing_sha256") == appSignature) { "Host signing binding failed" }
            require(data.optString("device_key_fingerprint") == hex(sha256(devicePublicKey))) {
                "Device key binding failed"
            }
            require(data.optString("session_id").matches(Regex("^[a-f0-9]{32}$"))) { "Invalid session ID" }
            require(data.optString("session_token").matches(Regex("^[A-Za-z0-9_-]{40,64}$"))) { "Invalid session token" }
            require(data.optString("request_id").matches(Regex("^[A-Za-z0-9_-]{22,96}$"))) {
                "Invalid client request binding"
            }
            require(data.optString("request_id") == clientRequestId) { "Mismatched client request" }
            require(data.optString("package_policy") in setOf("SPECIFIC", "ANY")) { "Invalid package policy" }
            require(data.optString("signing_policy") in setOf("SPECIFIC", "AUTO", "ANY")) { "Invalid signing policy" }
            require(data.optString("device_policy") in setOf("DISABLED", "SINGLE", "LIMITED", "UNLIMITED")) {
                "Invalid device policy"
            }
            require(data.optInt("sdk_version", 0) == SDK_VERSION) { "Mismatched SDK version" }
            val leaseExpiry = data.optLong("lease_expires_at", 0L)
            require(leaseExpiry > serverTime && leaseExpiry <= serverTime + 1800L) { "Invalid activation lease" }

            val identityCanonical = buildIdentityCanonical(data, keyId)
            val identitySignatureText = data.getString("identity_signature")
            val identitySignature = Base64.decode(identitySignatureText, Base64.DEFAULT)
            val identityVerified = Signature.getInstance("SHA256withECDSA").run {
                initVerify(signingKey)
                update(identityCanonical.toByteArray(StandardCharsets.UTF_8))
                verify(identitySignature)
            }
            require(identityVerified) { "Panel identity binding verification failed" }

            data.put("_server_identity_canonical", identityCanonical)
            data.put("_server_identity_signature", identitySignatureText)
        }
        data.put("_server_response_canonical", canonical)
        data.put("_server_response_signature", envelope.getString("signature"))
        return data
    }

    private fun buildIdentityCanonical(data: JSONObject, keyId: String): String {
        return listOf(
            "sdk-panel-v3-identity",
            keyId,
            data.optString("app_signature_sha256", ""),
            data.optString("authorized_package", ""),
            data.optString("authorized_signing_sha256", ""),
            data.optString("device_key_fingerprint", ""),
            data.optString("session_id", ""),
            data.optString("request_id", ""),
            data.optLong("lease_expires_at", 0L).toString(),
            data.optLong("server_time", 0L).toString(),
            data.optString("package_policy", ""),
            data.optString("signing_policy", ""),
            data.optString("device_policy", ""),
            data.optInt("sdk_version", 0).toString(),
        ).joinToString("\n")
    }

    private fun getOrCreateDeviceKey(): java.security.KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(DEVICE_KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        DEVICE_KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
            }.generateKeyPair()
        }
        val privateKey = store.getKey(DEVICE_KEY_ALIAS, null) as? java.security.PrivateKey
            ?: throw SecurityException("Device proof private key is unavailable")
        val publicKey = store.getCertificate(DEVICE_KEY_ALIAS)?.publicKey
            ?: throw SecurityException("Device proof public key is unavailable")
        return java.security.KeyPair(publicKey, privateKey)
    }

    internal fun appSigningCertificateSha256(packageName: String): String {
        val identity = SdkIdentityGuard.verifyJava(context, packageName)
        require(RNative.verifyInstalledIdentity(context, identity.packageName, identity.signingSha256)) {
            "Native installed APK identity verification failed"
        }
        return identity.signingSha256
    }

    private fun encryptEnvelope(plaintext: ByteArray, aesKey: ByteArray, aad: ByteArray): JSONObject {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            updateAAD(aad)
        }
        val encryptedAndTag = cipher.doFinal(plaintext)
        val split = encryptedAndTag.size - 16
        return JSONObject()
            .put("iv", b64(iv))
            .put("ciphertext", b64(encryptedAndTag.copyOfRange(0, split)))
            .put("tag", b64(encryptedAndTag.copyOfRange(split, encryptedAndTag.size)))
    }

    private fun hkdfSha256(secret: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }
        val prk = extract.doFinal(secret)
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                update(info)
                update(1.toByte())
                doFinal()
            }
        } finally {
            prk.fill(0)
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Panel response is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun b64Url(value: ByteArray): String = Base64.encodeToString(
        value,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )
    private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

    private companion object {
        const val SDK_VERSION = 3
        val ENDPOINT_SHA256 = byteArrayOf(
            0x55, 0xB2.toByte(), 0xFB.toByte(), 0xC9.toByte(), 0x01, 0x80.toByte(), 0x02, 0x7D,
            0xF8.toByte(), 0x0F, 0x3A, 0x31, 0xE7.toByte(), 0xBA.toByte(), 0xD4.toByte(), 0x1C,
            0x2A, 0xBC.toByte(), 0x63, 0xA8.toByte(), 0xB4.toByte(), 0x99.toByte(), 0x8E.toByte(), 0x41,
            0x6A, 0xF1.toByte(), 0x1C, 0x02, 0xB7.toByte(), 0x29, 0x45, 0x6A,
        )
        const val DEVICE_KEY_ALIAS = "parallax_sdk_device_proof_v3"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
