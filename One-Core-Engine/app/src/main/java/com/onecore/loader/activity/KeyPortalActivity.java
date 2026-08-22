package com.onecore.loader.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.onecore.loader.security.NativeStringVault;

/** Resolves the public key portal URL from the native encrypted vault. */
public final class KeyPortalActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(NativeStringVault.keyPortalUrl()));
            startActivity(browser);
        } catch (RuntimeException ignored) {
            // Fail closed if the protected constant cannot be recovered or no browser is available.
        } finally {
            finish();
        }
    }
}
