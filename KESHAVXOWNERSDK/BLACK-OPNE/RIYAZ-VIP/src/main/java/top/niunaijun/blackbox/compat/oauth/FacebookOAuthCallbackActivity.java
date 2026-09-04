package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Locale;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Host-side fallback for Meta's fbconnect custom-scheme redirect.
 *
 * This Activity is exported only because the browser must be able to open it.
 * Browser-controlled data is never trusted as a virtual routing target: a
 * callback is accepted only when it matches a live Facebook OAuth session and
 * its OAuth state/registered redirect target validate successfully.
 */
@Obfuscate
public final class FacebookOAuthCallbackActivity extends Activity {
    private static final String TAG = "ParallaxOAuth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri callbackUri = intent == null ? null : intent.getData();
        boolean actionOk = intent != null && Intent.ACTION_VIEW.equals(intent.getAction());
        boolean schemeOk = callbackUri != null
                && "fbconnect".equals(lower(callbackUri.getScheme()));
        String host = callbackUri == null ? "" : lower(callbackUri.getHost());
        boolean hostOk = host.startsWith("cct.") && host.length() > 4;

        FacebookOAuthSessionStore.Claim claim = null;
        boolean delivered = false;
        if (actionOk && schemeOk && hostOk) {
            claim = FacebookOAuthSessionStore.claim(callbackUri);
            if (claim != null) {
                delivered = VirtualOAuthBridgeActivity.dispatchFacebookCallback(
                        claim.virtualPackage, claim.userId, callbackUri);
                if (delivered) {
                    FacebookOAuthSessionStore.complete(claim.generation);
                } else {
                    FacebookOAuthSessionStore.release(claim.generation);
                }
            }
        }

        Log.i(TAG, "facebook stage=host_callback"
                + " action=" + actionOk
                + " scheme=" + schemeOk
                + " host=" + hostOk
                + " session=" + (claim != null)
                + " delivered=" + delivered);
        finish();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
