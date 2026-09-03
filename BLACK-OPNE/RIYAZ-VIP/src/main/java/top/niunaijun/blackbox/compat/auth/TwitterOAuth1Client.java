package top.niunaijun.blackbox.compat.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal OAuth 1.0a client used only by the legacy Twitter Kit SSO compatibility
 * bridge. HTTPS remains authenticated by Android's normal trust manager and
 * hostname verifier; this class deliberately installs no custom TLS hooks.
 */
final class TwitterOAuth1Client {
    private static final String REQUEST_TOKEN_URL =
            "https://api.twitter.com/oauth/request_token";
    private static final String ACCESS_TOKEN_URL =
            "https://api.twitter.com/oauth/access_token";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_RESPONSE_CHARS = 65_536;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TwitterOAuth1Client() {
    }

    static RequestToken requestToken(
            String consumerKey, String consumerSecret, String callbackUrl, String sdkVersion)
            throws IOException, GeneralSecurityException {
        String authorization = createAuthorizationHeader(
                "POST", REQUEST_TOKEN_URL, consumerKey, consumerSecret,
                null, null, callbackUrl, newNonce(), currentTimestamp());
        Map<String, String> response = parseForm(executePost(
                REQUEST_TOKEN_URL, authorization, sdkVersion));
        String token = response.get("oauth_token");
        String secret = response.get("oauth_token_secret");
        if (isBlank(token) || isBlank(secret)) {
            throw new IOException("Twitter request-token response was incomplete");
        }
        return new RequestToken(token, secret);
    }

    static AccessToken accessToken(
            String consumerKey,
            String consumerSecret,
            RequestToken requestToken,
            String verifier,
            String sdkVersion) throws IOException, GeneralSecurityException {
        if (requestToken == null || isBlank(requestToken.token)
                || isBlank(requestToken.secret) || isBlank(verifier)) {
            throw new IOException("Twitter access-token input was incomplete");
        }
        String authorization = createAuthorizationHeader(
                "POST", ACCESS_TOKEN_URL, consumerKey, consumerSecret,
                requestToken.token, requestToken.secret, null,
                newNonce(), currentTimestamp());
        String requestUrl = ACCESS_TOKEN_URL + "?oauth_verifier=" + percentEncode(verifier);
        Map<String, String> response = parseForm(executePost(
                requestUrl, authorization, sdkVersion));
        String token = response.get("oauth_token");
        String secret = response.get("oauth_token_secret");
        String screenName = response.get("screen_name");
        long userId = parseLong(response.get("user_id"));
        if (isBlank(token) || isBlank(secret)) {
            throw new IOException("Twitter access-token response was incomplete");
        }
        return new AccessToken(token, secret, screenName, userId);
    }

    static String createAuthorizationHeader(
            String method,
            String url,
            String consumerKey,
            String consumerSecret,
            String token,
            String tokenSecret,
            String callback,
            String nonce,
            String timestamp) throws GeneralSecurityException {
        if (isBlank(method) || isBlank(url) || isBlank(consumerKey)
                || isBlank(consumerSecret) || isBlank(nonce) || isBlank(timestamp)) {
            throw new GeneralSecurityException("OAuth signing input was incomplete");
        }

        TreeMap<String, String> parameters = queryParameters(url);
        putIfPresent(parameters, "oauth_callback", callback);
        parameters.put("oauth_consumer_key", consumerKey);
        parameters.put("oauth_nonce", nonce);
        parameters.put("oauth_signature_method", "HMAC-SHA1");
        parameters.put("oauth_timestamp", timestamp);
        putIfPresent(parameters, "oauth_token", token);
        parameters.put("oauth_version", "1.0");

        URI uri = URI.create(url);
        String baseUrl = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        StringBuilder normalized = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (normalized.length() > 0) normalized.append('&');
            normalized.append(percentEncode(entry.getKey()))
                    .append('=')
                    .append(percentEncode(entry.getValue()));
        }
        String signatureBase = method.toUpperCase() + '&'
                + percentEncode(baseUrl) + '&' + percentEncode(normalized.toString());
        String signingKey = percentEncode(consumerSecret) + '&' + percentEncode(tokenSecret);
        String signature = hmacSha1Base64(signingKey, signatureBase);

        TreeMap<String, String> header = new TreeMap<>();
        putIfPresent(header, "oauth_callback", callback);
        header.put("oauth_consumer_key", consumerKey);
        header.put("oauth_nonce", nonce);
        header.put("oauth_signature", signature);
        header.put("oauth_signature_method", "HMAC-SHA1");
        header.put("oauth_timestamp", timestamp);
        putIfPresent(header, "oauth_token", token);
        header.put("oauth_version", "1.0");

        StringBuilder result = new StringBuilder("OAuth ");
        for (Map.Entry<String, String> entry : header.entrySet()) {
            if (result.length() > 6) result.append(", ");
            result.append(percentEncode(entry.getKey())).append("=\"")
                    .append(percentEncode(entry.getValue())).append('"');
        }
        return result.toString();
    }

    static Map<String, String> parseForm(String body) throws IOException {
        TreeMap<String, String> result = new TreeMap<>();
        if (body == null || body.isEmpty()) return result;
        for (String pair : body.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            try {
                result.put(URLDecoder.decode(key, StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
            } catch (IllegalArgumentException error) {
                throw new IOException("Twitter OAuth response was malformed", error);
            }
        }
        return result;
    }

    static String percentEncode(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String executePost(String url, String authorization, String sdkVersion)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            connection.setRequestProperty("Authorization", authorization);
            connection.setRequestProperty("User-Agent", "TwitterAndroidSDK/" + safeVersion(sdkVersion));
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.connect();
            connection.getOutputStream().close();

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBounded(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Twitter OAuth endpoint returned HTTP " + status);
            }
            return body;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (result.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Twitter OAuth response exceeded limit");
                }
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    private static TreeMap<String, String> queryParameters(String url) {
        TreeMap<String, String> result = new TreeMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null || query.isEmpty()) return result;
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            try {
                result.put(URLDecoder.decode(key, StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String hmacSha1Base64(String key, String value)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return base64(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64(byte[] bytes) {
        final char[] alphabet =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        StringBuilder out = new StringBuilder(((bytes.length + 2) / 3) * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int b0 = bytes[i] & 0xff;
            int b1 = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
            int b2 = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
            out.append(alphabet[b0 >>> 2]);
            out.append(alphabet[((b0 & 3) << 4) | (b1 >>> 4)]);
            out.append(i + 1 < bytes.length ? alphabet[((b1 & 15) << 2) | (b2 >>> 6)] : '=');
            out.append(i + 2 < bytes.length ? alphabet[b2 & 63] : '=');
        }
        return out.toString();
    }

    private static String newNonce() {
        return Long.toString(System.nanoTime()) + Long.toUnsignedString(RANDOM.nextLong());
    }

    private static String currentTimestamp() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }

    private static String safeVersion(String value) {
        if (isBlank(value) || value.length() > 80) return "3.3.0";
        return value.replaceAll("[^0-9A-Za-z._-]", "");
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null) values.put(key, value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    static final class RequestToken {
        final String token;
        final String secret;

        RequestToken(String token, String secret) {
            this.token = token;
            this.secret = secret;
        }
    }

    static final class AccessToken {
        final String token;
        final String secret;
        final String screenName;
        final long userId;

        AccessToken(String token, String secret, String screenName, long userId) {
            this.token = token;
            this.secret = secret;
            this.screenName = screenName;
            this.userId = userId;
        }
    }
}
