package com.onecore.loader.security;

import android.content.Context;
import android.os.SystemClock;

import com.onecore.loader.BuildConfig;

import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.net.Proxy;
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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/** Fail-closed encrypted client for the Parallax licensing API over platform-validated TLS. */
@Obfuscate
public final class HostedLicenseClient {
    static final String GAME_ID = "PUBG";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_RESPONSE_BYTES = 32L * 1024L;
    private static final long MAX_CLOCK_SKEW_SECONDS = 60L;
    private static final Pattern ACTIVATION_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{4,64}$");
    private static final Pattern RECEIPT_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{32,64}$");
    private static final Pattern PUBLIC_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private static final String LICENSE_KEY = "PARALLAX_LICENSE_KEY";
    private static final String LICENSE_TOKEN = "PARALLAX_LICENSE_TOKEN";
    public static final String LICENSE_EXPIRES_AT = "PARALLAX_LICENSE_EXPIRES_AT";
    private static final String VERIFIED_SERVER_TIME = "PARALLAX_VERIFIED_SERVER_TIME";
    private static final String VERIFIED_ELAPSED_TIME = "PARALLAX_VERIFIED_ELAPSED_TIME";

    private final Context context;
    private final okhttp3.OkHttpClient httpClient;
    private final String connectUrl;
    private final String connectHost;
    private final String apiPublicKey;

    public HostedLicenseClient(Context context) {
        this.context = context.getApplicationContext();
        this.apiPublicKey = configuredPublicKey();

        NativeLicenseGuard.assertSecure(this.context, apiPublicKey);
        this.connectUrl = NativeLicenseGuard.connectUrl();
        this.connectHost = NativeLicenseGuard.connectHost();

        HttpUrl parsedUrl = HttpUrl.get(connectUrl);
        if (!"https".equals(parsedUrl.scheme()) || !connectHost.equals(parsedUrl.host())) {
            throw new SecurityException("Native licensing endpoint validation failed");
        }

        // Deliberately use Android/OkHttp's normal CA chain + hostname validation.
        // Certificate/SPKI pinning is not used by the Loader. The encrypted signed
        // application protocol and all existing integrity/runtime checks remain active.
        this.httpClient = new okhttp3.OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    /** Activates or revalidates a key and persists only a cryptographically bound response. */
    public String activate(String activationKey) {
        try {
            // Native pre-flight: endpoint/config + tracer/injection/interception checks.
            NativeLicenseGuard.assertSecure(context, apiPublicKey);

            AppIntegrity.Verification integrity = AppIntegrity.verify(context);
            if (!integrity.isValid()) {
                clearLicense();
                return "Application signature verification failed";
            }
            String normalizedKey = normalizeActivationKey(activationKey);
            if (!isSupportedActivationKey(normalizedKey)) {
                clearLicense();
                return "Use a key created in Parallax Control";
            }
            String serial = DeviceIdentity.deviceId();
            JSONObject payload = new JSONObject();
            payload.put("game", GAME_ID);
            payload.put("user_key", normalizedKey);
            payload.put("serial", serial);
            payload.put("package_name", context.getPackageName());
            payload.put("certificate_sha256",
                    AppIntegrity.currentSigningCertificateSha256(context));
            payload.put("version_code", BuildConfig.VERSION_CODE);
            LicenseTransportCrypto.RequestEnvelope encrypted =
                    LicenseTransportCrypto.encryptRequest(payload, apiPublicKey);
            try {
                Request request = new Request.Builder()
                        .url(connectUrl)
                        .header("Accept", "application/json")
                        .header("Cache-Control", "no-store")
                        .post(RequestBody.create(encrypted.json, JSON))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!connectUrl.equals(response.request().url().toString())
                            || !connectHost.equals(response.request().url().host())
                            || response.handshake() == null) {
                        throw new IllegalStateException("Licensing server transport changed unexpectedly");
                    }

                    // Native post-flight catches a tracer/injection arriving while the request was active.
                    NativeLicenseGuard.assertSecure(context, apiPublicKey);

                    String body = readBoundedJson(response);
                    JSONObject decrypted = LicenseTransportCrypto.decryptResponse(body, encrypted);
                    long receivedAt = System.currentTimeMillis() / 1000L;
                    ParsedLicense license = parseDecryptedResponse(
                            decrypted, encrypted.nonce, encrypted.canary, receivedAt);
                    if (!response.isSuccessful()) {
                        throw new LicenseRejectedException("Licensing server rejected the request");
                    }
                    SecurePreferences preferences = new SecurePreferences(context);
                    preferences.putString(LICENSE_KEY, normalizedKey);
                    preferences.putString(LICENSE_TOKEN, license.receipt);
                    preferences.putString(LICENSE_EXPIRES_AT, Long.toString(license.expiresAt));
                    preferences.putString(VERIFIED_SERVER_TIME, Long.toString(license.serverTime));
                    preferences.putString(
                            VERIFIED_ELAPSED_TIME,
                            Long.toString(SystemClock.elapsedRealtime()));
                    return "OK";
                }
            } finally {
                encrypted.destroy();
            }
        } catch (LicenseRejectedException exception) {
            clearLicense();
            return exception.getMessage();
        } catch (Exception exception) {
            clearLicense();
            return userFacingError(exception);
        }
    }

    public String revalidateStoredLicense() {
        String key = new SecurePreferences(context).getString(LICENSE_KEY, "");
        if (key.isEmpty()) {
            clearLicense();
            return "Sign in again to verify your key";
        }
        return activate(key);
    }

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

    public void reportSecurityEvent(String eventType, String severity) {
        // Security failures remain local and fail closed; no unauthenticated telemetry path.
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

    static ParsedLicense parseDecryptedResponse(
            JSONObject response,
            String requestNonce,
            String requestCanary,
            long receivedAtEpochSeconds) throws Exception {
        if (!constantTimeEquals(requestNonce, response.optString("request_nonce", ""))
                || !constantTimeEquals(requestCanary, response.optString("canary", ""))) {
            throw new LicenseRejectedException("Licensing response canary validation failed");
        }
        if (!response.optBoolean("status", false)) {
            String reason = response.optString("reason", "License was rejected").trim();
            throw new LicenseRejectedException(reason.isEmpty() ? "License was rejected" : reason);
        }
        long serverTime = response.optLong("server_time", 0L);
        if (serverTime <= 0L
                || serverTime < receivedAtEpochSeconds - MAX_CLOCK_SKEW_SECONDS
                || serverTime > receivedAtEpochSeconds + MAX_CLOCK_SKEW_SECONDS) {
            throw new LicenseRejectedException("Licensing server timestamp validation failed");
        }
        String receipt = response.optString("receipt", "");
        if (!RECEIPT_PATTERN.matcher(receipt).matches()) {
            throw new LicenseRejectedException("Licensing receipt is invalid");
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new LicenseRejectedException("Licensing server payload is missing");
        }
        long expiresAt = parseUtcExpiry(data.optString("expired_date", "").trim());
        if (expiresAt <= serverTime) {
            throw new LicenseRejectedException("EXPIRED KEY");
        }
        return new ParsedLicense(receipt, expiresAt, serverTime);
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

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String configuredPublicKey() {
        String value = BuildConfig.PARALLAX_API_PUBLIC_KEY_B64;
        if (value == null || value.length() < 256 || value.length() > 2048
                || !PUBLIC_KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException("Licensing public key is not configured");
        }
        return value;
    }

    private static String userFacingError(Exception exception) {
        String message = exception.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.US);
        if (normalized.contains("environment rejected")
                || normalized.contains("native licensing")) {
            return "Secure verification environment rejected";
        }
        if (normalized.contains("peer not authenticated")
                || normalized.contains("hostname")
                || normalized.contains("certificate")) {
            return "Secure server identity validation failed";
        }
        if (normalized.contains("configured") || normalized.contains("encryption")
                || normalized.contains("canary") || normalized.contains("unsupported response")
                || normalized.contains("transport changed") || normalized.contains("too large")) {
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
        final String receipt;
        final long expiresAt;
        final long serverTime;

        ParsedLicense(String receipt, long expiresAt, long serverTime) {
            this.receipt = receipt;
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
