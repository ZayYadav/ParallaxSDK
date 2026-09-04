package com.onecore.loader.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores sensitive values with an app-scoped Android Keystore AES-GCM key. */
public final class SecurePreferences {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "onecore_secure_preferences_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFERENCES_NAME = "secure_settings";
    private static final String VALUE_SEPARATOR = ".";

    private final SharedPreferences preferences;

    public SecurePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void putString(String key, String value) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + VALUE_SEPARATOR
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            preferences.edit().putString(key, encoded).apply();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect local credentials", exception);
        }
    }

    public String getString(String key, String defaultValue) {
        String encoded = preferences.getString(key, null);
        if (encoded == null) {
            return defaultValue;
        }

        try {
            String[] parts = encoded.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted value");
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            preferences.edit().remove(key).apply();
            return defaultValue;
        }
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
