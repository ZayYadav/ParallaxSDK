package top.niunaijun.blackbox.compat.oauth;

import android.net.Uri;

import java.util.Locale;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Keeps Meta host matching in one obfuscated location so release DEX files do
 * not expose provider routing constants as plaintext.
 */
@Obfuscate
final class FacebookAuthHost {
    private FacebookAuthHost() {
    }

    static boolean matches(Uri uri) {
        return uri != null && matches(uri.getHost());
    }

    static boolean matches(String rawHost) {
        String host = rawHost == null ? "" : rawHost.toLowerCase(Locale.US);
        return "facebook.com".equals(host)
                || "www.facebook.com".equals(host)
                || "m.facebook.com".equals(host)
                || "web.facebook.com".equals(host)
                || host.endsWith(".facebook.com");
    }
}
