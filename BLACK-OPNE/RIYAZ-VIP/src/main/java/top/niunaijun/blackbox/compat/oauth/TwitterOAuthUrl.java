package top.niunaijun.blackbox.compat.oauth;

import java.net.URI;
import java.util.Locale;

/** Pure URL classifier kept separate so the app-to-app boundary is unit-testable. */
final class TwitterOAuthUrl {
    private static final int MAX_URL_LENGTH = 16_384;
    private static final String OAUTH2_AUTHORIZE_PATH = "/i/oauth2/authorize";

    private TwitterOAuthUrl() {
    }

    static boolean isModernOAuth2Authorize(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
                return false;
            }

            String host = uri.getHost();
            host = host == null ? "" : host.toLowerCase(Locale.US);
            if (!("x.com".equals(host)
                    || "www.x.com".equals(host)
                    || "mobile.x.com".equals(host)
                    || "twitter.com".equals(host)
                    || "www.twitter.com".equals(host)
                    || "mobile.twitter.com".equals(host))) {
                return false;
            }

            String path = uri.getPath();
            return path != null && OAUTH2_AUTHORIZE_PATH.equalsIgnoreCase(path);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
