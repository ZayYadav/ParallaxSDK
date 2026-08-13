package com.onecore.loader.security;

import android.content.Context;
import android.os.SystemClock;

import com.onecore.loader.BuildConfig;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.ConnectionSpec;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/** Fail-closed client for the pinned Parallax legacy key checker. */
public final class HostedLicenseClient {
    static final String CONNECT_URL = "https://parallaxserver.online/connect";
    static final String GAME_ID = "PUBG";
    private static final long MAX_RESPONSE_BYTES = 64L * 1024L;
    private static final long MAX_CLOCK_SKEW_SECONDS = 60L;
    private static final Pattern ACTIVATION_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{4,64}$");

    private static final String LICENSE_KEY = "PARALLAX_LICENSE_KEY";
    private static final String LICENSE_TOKEN = "PARALLAX_LICENSE_TOKEN";
    public static final String LICENSE_EXPIRES_AT = "PARALLAX_LICENSE_EXPIRES_AT";
    private static final String VERIFIED_SERVER_TIME = "PARALLAX_VERIFIED_SERVER_TIME";
    private static final String VERIFIED_ELAPSED_TIME = "PARALLAX_VERIFIED_ELAPSED_TIME";

    private final Context context;
    private final OkHttpClient httpClient;

    public HostedLicenseClient(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    /** Activates or revalidates a key and persists only a cryptographically checked response. */
    public String activate(String activationKey) {
        try {
            AppIntegrity.Verification integrity = AppIntegrity.verify(context);
            if (!integrity.isValid()) {
                clearLicense();
                return "Application signature verification failed";
            }

            String normalizedKey = normalizeActivationKey(activationKey);
            if (!isSupportedActivationKey(normalizedKey)) {
                clearLicense();
                return "Use a key created in OneCore Integrity";
            }

            String secret = configuredTokenSecret();
            String serial = DeviceIdentity.deviceId();
            FormBody requestBody = new FormBody.Builder(StandardCharsets.UTF_8)
                    .add("game", GAME_ID)
                    .add("user_key", normalizedKey)
                    .add("serial", serial)
                    .build();
            Request request = new Request.Builder()
                    .url(CONNECT_URL)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!CONNECT_URL.equals(response.request().url().toString())) {
                    throw new IllegalStateException("Licensing server redirected unexpectedly");
                }
                String body = readBoundedJson(response);
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Licensing server rejected the request");
                }

                long receivedAt = System.currentTimeMillis() / 1000L;
                ParsedLicense license = parseResponse(
                        body, normalizedKey, serial, secret, receivedAt);
                SecurePreferences preferences = new SecurePreferences(context);
                preferences.putString(LICENSE_KEY, normalizedKey);
                preferences.putString(LICENSE_TOKEN, license.token);
                preferences.putString(LICENSE_EXPIRES_AT, Long.toString(license.expiresAt));
                preferences.putString(VERIFIED_SERVER_TIME, Long.toString(license.serverTime));
                preferences.putString(
                        VERIFIED_ELAPSED_TIME,
                        Long.toString(SystemClock.elapsedRealtime()));
                return "OK";
            }
        } catch (LicenseRejectedException exception) {
            clearLicense();
            return exception.getMessage();
        } catch (Exception exception) {
            clearLicense();
            return userFacingError(exception);
        }
    }

    /** Rechecks the securely stored key against the server. */
    public String revalidateStoredLicense() {
        String key = new SecurePreferences(context).getString(LICENSE_KEY, "");
        if (key.isEmpty()) {
            clearLicense();
            return "Sign in again to verify your key";
        }
        return activate(key);
    }

    /** Uses a monotonic server-time anchor and fails closed after reboot or clock rollback. */
    public boolean hasActiveLicense() {
        long expiresAt = readLong(LICENSE_EXPIRES_AT);
        long serverTime = readLong(VERIFIED_SERVER_TIME);
        long verifiedElapsed = readLong(VERIFIED_ELAPSED_TIME);
        long elapsedNow = SystemClock.elapsedRealtime();
        if (expiresAt <= 0L || serverTime <= 0L || verifiedElapsed <= 0L
                || elapsedNow < verifiedElapsed) {
            return false;
        }
        return trustedNowEpochSeconds(serverTime, verifiedElapsed, elapsedNow) < expiresAt;
    }

    public long remainingMillis() {
        long expiresAt = readLong(LICENSE_EXPIRES_AT);
        long serverTime = readLong(VERIFIED_SERVER_TIME);
        long verifiedElapsed = readLong(VERIFIED_ELAPSED_TIME);
        long elapsedNow = SystemClock.elapsedRealtime();
        if (expiresAt <= 0L || serverTime <= 0L || verifiedElapsed <= 0L
                || elapsedNow < verifiedElapsed) {
            return 0L;
        }
        long remainingSeconds = expiresAt
                - trustedNowEpochSeconds(serverTime, verifiedElapsed, elapsedNow);
        return Math.max(0L, remainingSeconds) * 1000L;
    }

    public long expiresAtEpochSeconds() {
        return readLong(LICENSE_EXPIRES_AT);
    }

    public boolean needsOnlineRevalidation(long maximumAgeMillis) {
        long verifiedElapsed = readLong(VERIFIED_ELAPSED_TIME);
        long elapsedNow = SystemClock.elapsedRealtime();
        return verifiedElapsed <= 0L
                || elapsedNow < verifiedElapsed
                || elapsedNow - verifiedElapsed >= maximumAgeMillis;
    }

    /** The legacy checker has no authenticated telemetry route. */
    public void reportSecurityEvent(String eventType, String severity) {
        // Local integrity enforcement remains fail-closed without transmitting to another API.
    }

    public void clearLicense() {
        SecurePreferences preferences = new SecurePreferences(context);
        preferences.remove(LICENSE_KEY);
        preferences.remove(LICENSE_TOKEN);
        preferences.remove(LICENSE_EXPIRES_AT);
        preferences.remove(VERIFIED_SERVER_TIME);
        preferences.remove(VERIFIED_ELAPSED_TIME);
    }

    private long readLong(String key) {
        String value = new SecurePreferences(context).getString(key, "0");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static long trustedNowEpochSeconds(
            long serverTime, long verifiedElapsed, long elapsedNow) {
        long monotonicNow = serverTime + ((elapsedNow - verifiedElapsed) / 1000L);
        long wallNow = System.currentTimeMillis() / 1000L;
        return Math.max(monotonicNow, wallNow);
    }

    private static String readBoundedJson(Response response) throws Exception {
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            throw new IllegalStateException("Licensing server returned an empty response");
        }
        MediaType contentType = responseBody.contentType();
        if (contentType == null || !"application".equals(contentType.type())
                || !"json".equals(contentType.subtype())) {
            throw new IllegalStateException("Licensing server returned an unsupported response");
        }
        long contentLength = responseBody.contentLength();
        if (contentLength > MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Licensing server response is too large");
        }
        BufferedSource source = responseBody.source();
        source.request(MAX_RESPONSE_BYTES + 1L);
        if (source.getBuffer().size() > MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Licensing server response is too large");
        }
        String body = source.readUtf8();
        if (body.trim().isEmpty()) {
            throw new IllegalStateException("Licensing server returned an empty response");
        }
        return body;
    }

    static ParsedLicense parseResponse(
            String body,
            String activationKey,
            String serial,
            String secret,
            long receivedAtEpochSeconds) throws Exception {
        JSONObject response = new JSONObject(body);
        if (!response.optBoolean("status", false)) {
            String reason = response.optString("reason", "License was rejected").trim();
            throw new LicenseRejectedException(
                    reason.isEmpty() ? "License was rejected" : reason);
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new LicenseRejectedException(
                    response.optString("reason", "Licensing server is under maintenance"));
        }

        String token = data.optString("token", "").trim().toLowerCase(Locale.US);
        long serverTime = data.optLong("rng", 0L);
        String expiry = data.optString("expired_date", data.optString("EXP", "")).trim();
        long expiresAt = parseUtcExpiry(expiry);
        if (serverTime <= 0L
                || serverTime < receivedAtEpochSeconds - MAX_CLOCK_SKEW_SECONDS
                || serverTime > receivedAtEpochSeconds + MAX_CLOCK_SKEW_SECONDS) {
            throw new LicenseRejectedException("Licensing server timestamp validation failed");
        }
        if (expiresAt <= serverTime) {
            throw new LicenseRejectedException("EXPIRED KEY");
        }

        String expectedToken = md5Hex(
                GAME_ID + "-" + activationKey + "-" + serial + "-" + secret);
        if (!constantTimeEquals(expectedToken, token)) {
            throw new LicenseRejectedException("Licensing server integrity validation failed");
        }
        return new ParsedLicense(token, expiresAt, serverTime);
    }

    static long parseUtcExpiry(String value) throws LicenseRejectedException {
        if (value == null || value.length() != 19) {
            throw new LicenseRejectedException("Licensing server returned an invalid expiry");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(value, position);
        if (date == null || position.getIndex() != value.length()) {
            throw new LicenseRejectedException("Licensing server returned an invalid expiry");
        }
        return date.getTime() / 1000L;
    }

    public static String normalizeActivationKey(String activationKey) {
        return activationKey == null ? "" : activationKey.trim();
    }

    public static boolean isSupportedActivationKey(String activationKey) {
        return ACTIVATION_KEY_PATTERN.matcher(normalizeActivationKey(activationKey)).matches();
    }

    static String md5Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return result.toString();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String configuredTokenSecret() {
        String secret = BuildConfig.PARALLAX_LEGACY_TOKEN_SECRET;
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Licensing integrity secret is not configured");
        }
        return secret;
    }

    private static String userFacingError(Exception exception) {
        String message = exception.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.US);
        if (normalized.contains("configured")
                || normalized.contains("unsupported response")
                || normalized.contains("redirected")
                || normalized.contains("too large")) {
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

    static final class ParsedLicense {
        final String token;
        final long expiresAt;
        final long serverTime;

        ParsedLicense(String token, long expiresAt, long serverTime) {
            this.token = token;
            this.expiresAt = expiresAt;
            this.serverTime = serverTime;
        }
    }

    static final class LicenseRejectedException extends Exception {
        LicenseRejectedException(String message) {
            super(message == null || message.trim().isEmpty()
                    ? "License was rejected"
                    : message.trim());
        }
    }
}
