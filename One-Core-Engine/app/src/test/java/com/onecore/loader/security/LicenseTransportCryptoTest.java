package com.onecore.loader.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okio.ByteString;

public class LicenseTransportCryptoTest {
    @Test
    public void responseEncryptionIsBoundToRequestNonceAndCanary() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String publicKey = ByteString.of(pair.getPublic().getEncoded()).base64();
        JSONObject request = new JSONObject();
        request.put("game", "PUBG");
        LicenseTransportCrypto.RequestEnvelope encrypted =
                LicenseTransportCrypto.encryptRequest(request, publicKey);
        assertTrue(encrypted.json.contains("\"v\":2"));

        JSONObject payload = new JSONObject();
        payload.put("status", true);
        payload.put("request_nonce", encrypted.nonce);
        payload.put("canary", encrypted.canary);
        JSONObject responseEnvelope = encryptResponse(payload, encrypted);
        JSONObject decrypted = LicenseTransportCrypto.decryptResponse(
                responseEnvelope.toString(), encrypted);
        assertEquals(encrypted.nonce, decrypted.getString("request_nonce"));
        assertEquals(encrypted.canary, decrypted.getString("canary"));

        JSONObject tampered = new JSONObject(responseEnvelope.toString());
        tampered.put("ct", tampered.getString("ct").substring(0, 4) + "AAAA");
        try {
            LicenseTransportCrypto.decryptResponse(tampered.toString(), encrypted);
            fail("Tampered ciphertext must fail authentication");
        } catch (Exception expected) {
            assertTrue(true);
        }
    }

    private static JSONObject encryptResponse(
            JSONObject payload,
            LicenseTransportCrypto.RequestEnvelope request) throws Exception {
        byte[] iv = new byte[12];
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) (i + 1);
        }
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(request.sessionKey, "AES"),
                new GCMParameterSpec(128, iv));
        aes.updateAAD((LicenseTransportCrypto.RESPONSE_AAD_PREFIX + request.nonce)
                .getBytes(StandardCharsets.US_ASCII));
        byte[] combined = aes.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        int split = combined.length - 16;
        JSONObject envelope = new JSONObject();
        envelope.put("v", 2);
        envelope.put("iv", ByteString.of(iv).base64());
        envelope.put("ct", ByteString.of(combined, 0, split).base64());
        envelope.put("tag", ByteString.of(combined, split, 16).base64());
        return envelope;
    }
}
