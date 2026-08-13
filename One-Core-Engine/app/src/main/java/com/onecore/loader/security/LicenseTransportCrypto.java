package com.onecore.loader.security;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okio.ByteString;

/** Application-layer encryption and request/response binding for the licensing API. */
final class LicenseTransportCrypto {
    static final int VERSION = 2;
    static final String REQUEST_AAD = "parallax-license-v2-request";
    static final String RESPONSE_AAD_PREFIX = "parallax-license-v2-response:";
    private static final int GCM_TAG_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LicenseTransportCrypto() {
    }

    static RequestEnvelope encryptRequest(JSONObject payload, String publicKeyBase64)
            throws Exception {
        byte[] publicKeyDer = decodeBase64(publicKeyBase64, 256, 1024);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeyDer));
        byte[] sessionKey = new byte[32];
        byte[] iv = new byte[12];
        byte[] nonceBytes = new byte[18];
        byte[] canaryBytes = new byte[18];
        RANDOM.nextBytes(sessionKey);
        RANDOM.nextBytes(iv);
        RANDOM.nextBytes(nonceBytes);
        RANDOM.nextBytes(canaryBytes);
        String nonce = base64Url(nonceBytes);
        String canary = base64Url(canaryBytes);
        payload.put("nonce", nonce);
        payload.put("canary", canary);
        payload.put("timestamp", System.currentTimeMillis() / 1000L);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKey, "AES"),
                new GCMParameterSpec(128, iv));
        aes.updateAAD(REQUEST_AAD.getBytes(StandardCharsets.US_ASCII));
        byte[] encryptedAndTag = aes.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        int ciphertextLength = encryptedAndTag.length - GCM_TAG_BYTES;

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] wrappedKey = rsa.doFinal(sessionKey);

        JSONObject envelope = new JSONObject();
        envelope.put("v", VERSION);
        envelope.put("k", base64(wrappedKey));
        envelope.put("iv", base64(iv));
        envelope.put("ct", base64(Arrays.copyOfRange(encryptedAndTag, 0, ciphertextLength)));
        envelope.put("tag", base64(Arrays.copyOfRange(
                encryptedAndTag, ciphertextLength, encryptedAndTag.length)));
        return new RequestEnvelope(envelope.toString(), sessionKey, nonce, canary);
    }

    static JSONObject decryptResponse(String envelopeJson, RequestEnvelope request)
            throws Exception {
        JSONObject envelope = new JSONObject(envelopeJson);
        if (envelope.optInt("v", 0) != VERSION) {
            throw new IllegalStateException("Licensing server encryption version is unsupported");
        }
        byte[] iv = decodeBase64(envelope.optString("iv", ""), 12, 12);
        byte[] ciphertext = decodeBase64(envelope.optString("ct", ""), 1, 16 * 1024);
        byte[] tag = decodeBase64(envelope.optString("tag", ""), GCM_TAG_BYTES, GCM_TAG_BYTES);
        byte[] encryptedAndTag = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, encryptedAndTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, encryptedAndTag, ciphertext.length, tag.length);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(request.sessionKey, "AES"),
                new GCMParameterSpec(128, iv));
        aes.updateAAD((RESPONSE_AAD_PREFIX + request.nonce)
                .getBytes(StandardCharsets.US_ASCII));
        byte[] plaintext = aes.doFinal(encryptedAndTag);
        if (plaintext.length == 0 || plaintext.length > 16 * 1024) {
            throw new IllegalStateException("Licensing server payload size is invalid");
        }
        return new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
    }

    private static byte[] decodeBase64(String value, int minimum, int maximum) {
        ByteString decoded = ByteString.decodeBase64(value);
        if (decoded == null || decoded.size() < minimum || decoded.size() > maximum) {
            throw new IllegalArgumentException("Invalid transport configuration");
        }
        return decoded.toByteArray();
    }

    private static String base64(byte[] value) {
        return ByteString.of(value).base64();
    }

    private static String base64Url(byte[] value) {
        return ByteString.of(value).base64Url().replace("=", "");
    }

    static final class RequestEnvelope {
        final String json;
        final byte[] sessionKey;
        final String nonce;
        final String canary;

        RequestEnvelope(String json, byte[] sessionKey, String nonce, String canary) {
            this.json = json;
            this.sessionKey = Arrays.copyOf(sessionKey, sessionKey.length);
            this.nonce = nonce;
            this.canary = canary;
        }
    }
}
