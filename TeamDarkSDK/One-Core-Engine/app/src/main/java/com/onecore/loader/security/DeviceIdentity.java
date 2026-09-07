package com.onecore.loader.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/** A per-install, non-exportable Android Keystore signing identity. */
public final class DeviceIdentity {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "onecore_hosted_license_identity_v1";

    private DeviceIdentity() {
    }

    public static String deviceId() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey().getEncoded());
        return Base64.encodeToString(
                digest,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String publicKeyBase64() throws Exception {
        return Base64.encodeToString(publicKey().getEncoded(), Base64.NO_WRAP);
    }

    public static String signBase64(String message) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey());
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP);
    }

    private static PublicKey publicKey() throws Exception {
        return getOrCreateKeyPair().getPublic();
    }

    private static PrivateKey privateKey() throws Exception {
        return getOrCreateKeyPair().getPrivate();
    }

    private static synchronized KeyPair getOrCreateKeyPair() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.PrivateKeyEntry) {
            KeyStore.PrivateKeyEntry privateEntry = (KeyStore.PrivateKeyEntry) entry;
            return new KeyPair(privateEntry.getCertificate().getPublicKey(), privateEntry.getPrivateKey());
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE);
        generator.initialize(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKeyPair();
    }
}
