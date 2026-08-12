package com.onecore.loader.security;

import android.content.Context;
import android.util.Base64;

import com.onecore.loader.BuildConfig;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Encrypted HTTPS client for the self-hosted OneCore licensing API. */
public final class HostedLicenseClient {
    private static final String AAD = "onecore-api-v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern ACTIVATION_KEY_PATTERN = Pattern.compile(
            "^[A-Z0-9]{2,8}-(?:[A-F0-9]{4}-){7}[A-F0-9]{4}$");
    private static final String LICENSE_TOKEN = "HOSTED_LICENSE_TOKEN";
    public static final String LICENSE_EXPIRES_AT = "HOSTED_LICENSE_EXPIRES_AT";

    private final Context context;
    private final SecretKeySpec encryptionKey;
    private final OkHttpClient httpClient;

    public HostedLicenseClient(Context context) {
        this.context = context.getApplicationContext();
        this.encryptionKey = configuredKey();
        this.httpClient = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public String activate(String activationKey) {
        try {
            AppIntegrity.Verification integrity = AppIntegrity.verify(context);
            if (!integrity.isValid()) {
                return "Application signature verification failed";
            }
            String normalizedKey = normalizeActivationKey(activationKey);
            if (!isSupportedActivationKey(normalizedKey)) {
                return "Use an activation key created in OneCore Integrity";
            }
            long timestamp = System.currentTimeMillis() / 1000L;
            String nonce = randomNonce();
            String deviceId = DeviceIdentity.deviceId();
            String bindingHash = sha256Hex(normalizedKey);
            String proofMessage = proofMessage(
                    "activate", deviceId, nonce, timestamp, bindingHash);

            JSONObject payload = new JSONObject();
            payload.put("activation_key", normalizedKey);
            payload.put("device_id", deviceId);
            payload.put("timestamp", timestamp);
            payload.put("nonce", nonce);
            payload.put("app_package_name", context.getPackageName());
            payload.put("app_certificate_sha256",
                    AppIntegrity.currentSigningCertificateSha256(context));
            payload.put("app_version_code", BuildConfig.VERSION_CODE);
            payload.put("device_public_key", DeviceIdentity.publicKeyBase64());
            payload.put("device_proof", DeviceIdentity.signBase64(proofMessage));

            JSONObject response = postEncrypted("/verify", payload);
            if (!"success".equals(response.optString("status"))) {
                return safeError(response);
            }
            if (!nonce.equals(response.optString("request_nonce"))) {
                return "Server response binding failed";
            }
            String token = response.optString("license_token", "");
            long expiresAt = response.optLong("expires_at", 0L);
            if (token.isEmpty() || expiresAt <= timestamp) {
                return "Server returned an invalid license";
            }
            SecurePreferences preferences = new SecurePreferences(context);
            preferences.putString(LICENSE_TOKEN, token);
            preferences.putString(LICENSE_EXPIRES_AT, Long.toString(expiresAt));
            return "OK";
        } catch (Exception exception) {
            return userFacingError(exception);
        }
    }

    /** Best-effort signed telemetry for user-visible security warnings. */
    public void reportSecurityEvent(String eventType, String severity) {
        try {
            String normalizedType = eventType.trim().toUpperCase(Locale.US);
            long timestamp = System.currentTimeMillis() / 1000L;
            String nonce = randomNonce();
            String deviceId = DeviceIdentity.deviceId();
            String proof = proofMessage(
                    "security",
                    deviceId,
                    nonce,
                    timestamp,
                    sha256Hex(normalizedType));
            JSONObject payload = new JSONObject();
            payload.put("device_id", deviceId);
            payload.put("timestamp", timestamp);
            payload.put("nonce", nonce);
            payload.put("event_type", normalizedType);
            payload.put("severity", severity);
            payload.put("device_public_key", DeviceIdentity.publicKeyBase64());
            payload.put("device_proof", DeviceIdentity.signBase64(proof));
            postEncrypted("/security/event", payload);
        } catch (Exception ignored) {
            // Reporting must never delay or suppress the local warning/closure.
        }
    }

    private JSONObject postEncrypted(String path, JSONObject payload) throws Exception {
        String baseUrl = BuildConfig.ONECORE_API_BASE_URL;
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("Licensing server is not configured");
        }
        Request request = new Request.Builder()
                .url(baseUrl.replaceAll("/+$", "") + path)
                .header("Accept", "application/json")
                .post(RequestBody.create(encrypt(payload).toString(), JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (body.isEmpty()) {
                throw new IllegalStateException("Licensing server returned an empty response");
            }
            JSONObject envelope = new JSONObject(body);
            if (!envelope.has("iv") || !envelope.has("tag") || !envelope.has("ciphertext")) {
                String bootstrapError = envelope.optString("error", "").trim();
                if (!bootstrapError.isEmpty()) {
                    throw new IllegalStateException("Licensing server error: " + bootstrapError);
                }
                throw new IllegalStateException("Licensing server returned an unsupported response");
            }
            JSONObject decrypted = decrypt(envelope);
            if (!response.isSuccessful() && !decrypted.has("message")) {
                throw new IllegalStateException("Licensing server rejected the request");
            }
            return decrypted;
        }
    }

    private JSONObject encrypt(JSONObject payload) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedAndTag = cipher.doFinal(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        int ciphertextLength = encryptedAndTag.length - 16;

        JSONObject envelope = new JSONObject();
        envelope.put("version", 1);
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("tag", Base64.encodeToString(
                Arrays.copyOfRange(encryptedAndTag, ciphertextLength, encryptedAndTag.length),
                Base64.NO_WRAP));
        envelope.put("ciphertext", Base64.encodeToString(
                Arrays.copyOfRange(encryptedAndTag, 0, ciphertextLength),
                Base64.NO_WRAP));
        return envelope;
    }

    private JSONObject decrypt(JSONObject envelope) throws Exception {
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT);
        byte[] tag = Base64.decode(envelope.getString("tag"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.DEFAULT);
        byte[] encryptedAndTag = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, encryptedAndTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, encryptedAndTag, ciphertext.length, tag.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        return new JSONObject(new String(
                cipher.doFinal(encryptedAndTag),
                StandardCharsets.UTF_8));
    }

    private static SecretKeySpec configuredKey() {
        byte[] decoded;
        try {
            decoded = Base64.decode(BuildConfig.ONECORE_API_ENCRYPTION_KEY, Base64.DEFAULT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Licensing encryption key is invalid", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("Licensing encryption key is not configured");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private static String randomNonce() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String normalizeActivationKey(String activationKey) {
        return activationKey == null
                ? ""
                : activationKey.trim().toUpperCase(Locale.US);
    }

    public static boolean isSupportedActivationKey(String activationKey) {
        return ACTIVATION_KEY_PATTERN.matcher(normalizeActivationKey(activationKey)).matches();
    }

    private static String proofMessage(
            String purpose,
            String deviceId,
            String nonce,
            long timestamp,
            String bindingHash) {
        return "onecore-device-proof-v1\n"
                + purpose + "\n"
                + deviceId + "\n"
                + nonce + "\n"
                + timestamp + "\n"
                + bindingHash.toLowerCase(Locale.US);
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return result.toString();
    }

    private static String safeError(JSONObject response) {
        String message = response.optString("message", "License was rejected").trim();
        return message.isEmpty() ? "License was rejected" : message;
    }

    private static String userFacingError(Exception exception) {
        String message = exception.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.US);
        if (normalized.contains("configured")
                || normalized.contains("server error")
                || normalized.contains("unsupported response")) {
            return message;
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return "Licensing server timed out. Please try again";
        }
        if (normalized.contains("unable to resolve host")
                || normalized.contains("failed to connect")) {
            return "Unable to reach the licensing server";
        }
        return "Secure license verification is temporarily unavailable";
    }
}
