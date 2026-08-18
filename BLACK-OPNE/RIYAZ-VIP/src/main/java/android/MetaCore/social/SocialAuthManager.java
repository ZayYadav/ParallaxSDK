package android.MetaCore.social;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Social authentication entry point shipped inside the SDK AAR.
 * Provider secrets stay on the SDK panel, never inside the APK/AAR.
 */
public final class SocialAuthManager {
    private static final String DEFAULT_BASE_URL =
            "https://parallaxloadersdk.parallaxserver.online/social-auth";
    private static final String PREFS = "parallax_social_auth";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_INSTALL_NONCE = "install_nonce";
    private static final String KEY_FALLBACK_DEVICE = "fallback_device";
    private static final String KEY_SESSION = "session_token";
    private static final String KEY_SESSION_EXPIRES = "session_expires";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_AUTH_PENDING = "auth_pending";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int MAX_RESPONSE_CHARS = 128 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile Listener activeListener;

    private SocialAuthManager() {
    }

    public enum Provider {
        GOOGLE("google"), FACEBOOK("facebook"), X("x");

        private final String wireName;

        Provider(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public interface Listener {
        void onSuccess(SocialUser user);
        void onError(String code, String message);
        void onCancelled();
    }

    public static final class SocialUser {
        public final String provider;
        public final String id;
        public final String email;
        public final String name;
        public final String avatarUrl;
        public final long sessionExpiresAtEpochSeconds;

        SocialUser(String provider, String id, String email, String name,
                   String avatarUrl, long sessionExpiresAtEpochSeconds) {
            this.provider = safe(provider);
            this.id = safe(id);
            this.email = safe(email);
            this.name = safe(name);
            this.avatarUrl = safe(avatarUrl);
            this.sessionExpiresAtEpochSeconds = sessionExpiresAtEpochSeconds;
        }

        public JSONObject toJson() {
            JSONObject value = new JSONObject();
            try {
                value.put("provider", provider);
                value.put("id", id);
                value.put("email", email);
                value.put("name", name);
                value.put("avatar_url", avatarUrl);
                value.put("session_expires", sessionExpiresAtEpochSeconds);
            } catch (Exception ignored) {
            }
            return value;
        }
    }

    public static void configure(Context context, String baseUrl) {
        if (context != null) {
            prefs(context).edit().putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl)).apply();
        }
    }

    public static void showLogin(Activity activity, Listener listener) {
        if (activity == null) {
            if (listener != null) {
                listener.onError("INVALID_ACTIVITY", "Activity is required.");
            }
            return;
        }
        activeListener = listener;
        activity.startActivity(new Intent(activity, SocialLoginActivity.class));
    }

    public static void login(Activity activity, Provider provider, Listener listener) {
        activeListener = listener;
        login(activity, provider);
    }

    static void login(Activity activity, Provider provider) {
        if (activity == null || provider == null) {
            notifyError(activity, "INVALID_REQUEST", "Login provider is missing.");
            return;
        }
        Context app = activity.getApplicationContext();
        prefs(app).edit().putBoolean(KEY_AUTH_PENDING, true).remove(KEY_LAST_ERROR).apply();
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = identityEnvelope(app);
                request.put("provider", provider.wireName());
                request.put("return_uri", callbackUri(app));
                JSONObject response = postAction(app, "start", request);
                String authorizationUrl = response.optString("authorization_url", "");
                Uri authorizationUri = Uri.parse(authorizationUrl);
                if (!"https".equalsIgnoreCase(authorizationUri.getScheme())
                        || authorizationUri.getHost() == null) {
                    throw new AuthException("INVALID_AUTH_URL", "Provider authorization URL is invalid.");
                }
                MAIN.post(() -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, authorizationUri);
                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        activity.startActivity(intent);
                    } catch (Throwable error) {
                        notifyError(app, "BROWSER_UNAVAILABLE", "No browser is available for sign in.");
                    }
                });
            } catch (AuthException error) {
                notifyError(app, error.code, error.getMessage());
            } catch (Throwable error) {
                notifyError(app, "NETWORK_ERROR", "Unable to contact the sign-in server.");
            }
        });
    }

    static void cancelActiveLogin(Context context) {
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_AUTH_PENDING, false)
                    .putString(KEY_LAST_ERROR, "Sign in cancelled.").apply();
        }
        Listener listener = activeListener;
        activeListener = null;
        if (listener != null) {
            MAIN.post(listener::onCancelled);
        }
    }

    static void handleCallback(Activity activity, Uri uri) {
        if (activity == null) {
            return;
        }
        Context app = activity.getApplicationContext();
        if (!isExpectedCallback(app, uri)) {
            notifyError(app, "INVALID_CALLBACK", "The sign-in callback was not recognized.");
            activity.finish();
            return;
        }

        String provider = uri.getQueryParameter("provider");
        String error = uri.getQueryParameter("error");
        if (error != null && !error.isEmpty()) {
            if ("cancelled".equalsIgnoreCase(error) || "access_denied".equalsIgnoreCase(error)) {
                cancelActiveLogin(app);
            } else {
                notifyError(app, "PROVIDER_ERROR", sanitizeMessage(error));
            }
            activity.finish();
            return;
        }

        String ticket = uri.getQueryParameter("ticket");
        if (ticket == null || !ticket.matches("^[A-Za-z0-9_-]{40,128}$")) {
            notifyError(app, "INVALID_TICKET", "The sign-in ticket is invalid.");
            activity.finish();
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject request = identityEnvelope(app);
                request.put("ticket", ticket);
                request.put("provider", safe(provider));
                JSONObject response = postAction(app, "complete", request);
                saveSession(app, response);
                SocialUser user = currentUser(app);
                if (user == null) {
                    throw new AuthException("SESSION_INVALID", "Sign in session was not created.");
                }
                prefs(app).edit().putBoolean(KEY_AUTH_PENDING, false).remove(KEY_LAST_ERROR).apply();
                Listener listener = activeListener;
                activeListener = null;
                if (listener != null) {
                    MAIN.post(() -> listener.onSuccess(user));
                }
            } catch (AuthException errorValue) {
                notifyError(app, errorValue.code, errorValue.getMessage());
            } catch (Throwable errorValue) {
                notifyError(app, "NETWORK_ERROR", "Unable to complete sign in.");
            } finally {
                MAIN.post(activity::finish);
            }
        });
    }

    public static boolean isLoggedIn(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = prefs(context);
        String token = preferences.getString(KEY_SESSION, "");
        long expires = preferences.getLong(KEY_SESSION_EXPIRES, 0L);
        return token != null && !token.isEmpty() && expires > System.currentTimeMillis() / 1000L;
    }

    public static SocialUser currentUser(Context context) {
        if (context == null || !isLoggedIn(context)) {
            return null;
        }
        SharedPreferences preferences = prefs(context);
        return new SocialUser(
                preferences.getString(KEY_PROVIDER, ""),
                preferences.getString(KEY_USER_ID, ""),
                preferences.getString(KEY_EMAIL, ""),
                preferences.getString(KEY_NAME, ""),
                preferences.getString(KEY_AVATAR, ""),
                preferences.getLong(KEY_SESSION_EXPIRES, 0L));
    }

    public static void validateSession(Context context, Listener listener) {
        if (context == null) {
            if (listener != null) {
                listener.onError("INVALID_CONTEXT", "Context is required.");
            }
            return;
        }
        Context app = context.getApplicationContext();
        String session = prefs(app).getString(KEY_SESSION, "");
        if (session == null || session.isEmpty()) {
            if (listener != null) {
                MAIN.post(() -> listener.onError("NOT_SIGNED_IN", "No social session is available."));
            }
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = identityEnvelope(app);
                request.put("session_token", session);
                JSONObject response = postAction(app, "session", request);
                saveSession(app, response);
                SocialUser user = currentUser(app);
                if (user == null) {
                    throw new AuthException("SESSION_INVALID", "Session is no longer valid.");
                }
                if (listener != null) {
                    MAIN.post(() -> listener.onSuccess(user));
                }
            } catch (AuthException error) {
                clearSession(app);
                if (listener != null) {
                    MAIN.post(() -> listener.onError(error.code, error.getMessage()));
                }
            } catch (Throwable error) {
                if (listener != null) {
                    MAIN.post(() -> listener.onError("NETWORK_ERROR", "Unable to validate social session."));
                }
            }
        });
    }

    public static void logout(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        String session = prefs(app).getString(KEY_SESSION, "");
        clearSession(app);
        if (session == null || session.isEmpty()) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                JSONObject request = identityEnvelope(app);
                request.put("session_token", session);
                postAction(app, "logout", request);
            } catch (Throwable ignored) {
            }
        });
    }

    static String consumeLastError(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences preferences = prefs(context);
        String value = preferences.getString(KEY_LAST_ERROR, "");
        preferences.edit().remove(KEY_LAST_ERROR).apply();
        return safe(value);
    }

    private static JSONObject identityEnvelope(Context context) throws Exception {
        JSONObject request = new JSONObject();
        request.put("package_name", context.getPackageName());
        request.put("device_id", deviceId(context));
        request.put("install_nonce", installNonce(context));
        return request;
    }

    private static JSONObject postAction(Context context, String action, JSONObject request) throws Exception {
        request.put("action", action);
        JSONObject response = postJson(endpoint(context), request);
        if (!"success".equalsIgnoreCase(response.optString("status"))) {
            throw new AuthException(
                    response.optString("code", "AUTH_FAILED"),
                    response.optString("message", "Social sign in failed."));
        }
        return response;
    }

    private static void saveSession(Context context, JSONObject response) {
        JSONObject user = response.optJSONObject("user");
        if (user == null) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        preferences.edit()
                .putString(KEY_SESSION, response.optString("session_token",
                        preferences.getString(KEY_SESSION, "")))
                .putLong(KEY_SESSION_EXPIRES, response.optLong("session_expires", 0L))
                .putString(KEY_PROVIDER, user.optString("provider", ""))
                .putString(KEY_USER_ID, user.optString("id", ""))
                .putString(KEY_EMAIL, user.optString("email", ""))
                .putString(KEY_NAME, user.optString("name", ""))
                .putString(KEY_AVATAR, user.optString("avatar_url", ""))
                .apply();
    }

    private static void clearSession(Context context) {
        prefs(context).edit()
                .remove(KEY_SESSION).remove(KEY_SESSION_EXPIRES)
                .remove(KEY_PROVIDER).remove(KEY_USER_ID).remove(KEY_EMAIL)
                .remove(KEY_NAME).remove(KEY_AVATAR)
                .putBoolean(KEY_AUTH_PENDING, false).apply();
    }

    private static void notifyError(Context context, String code, String message) {
        String safeMessage = sanitizeMessage(message);
        if (context != null) {
            prefs(context).edit().putBoolean(KEY_AUTH_PENDING, false)
                    .putString(KEY_LAST_ERROR, safeMessage).apply();
        }
        Listener listener = activeListener;
        activeListener = null;
        if (listener != null) {
            MAIN.post(() -> listener.onError(safe(code), safeMessage));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String endpoint(Context context) {
        String base = prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        return normalizeBaseUrl(base) + "/connect.php";
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null ? "" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        Uri uri = Uri.parse(base);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return DEFAULT_BASE_URL;
        }
        return base;
    }

    private static String callbackUri(Context context) {
        return context.getPackageName().toLowerCase(Locale.US)
                + ".parallaxsdk://social-auth/callback";
    }

    private static boolean isExpectedCallback(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        String expectedScheme = context.getPackageName().toLowerCase(Locale.US) + ".parallaxsdk";
        return expectedScheme.equalsIgnoreCase(uri.getScheme())
                && "social-auth".equalsIgnoreCase(uri.getHost())
                && "/callback".equals(uri.getPath());
    }

    private static String installNonce(Context context) {
        SharedPreferences preferences = prefs(context);
        String existing = preferences.getString(KEY_INSTALL_NONCE, "");
        if (existing != null && existing.matches("^[A-Za-z0-9_-]{43}$")) {
            return existing;
        }
        String created = randomToken(32);
        preferences.edit().putString(KEY_INSTALL_NONCE, created).apply();
        return created;
    }

    private static String deviceId(Context context) throws Exception {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.trim().isEmpty()) {
            SharedPreferences preferences = prefs(context);
            androidId = preferences.getString(KEY_FALLBACK_DEVICE, "");
            if (androidId == null || androidId.isEmpty()) {
                androidId = randomToken(24);
                preferences.edit().putString(KEY_FALLBACK_DEVICE, androidId).apply();
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] value = digest.digest((androidId + "|" + context.getPackageName()
                + "|parallax-social-v1").getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        URL url = new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new AuthException("HTTPS_REQUIRED", "Social auth requires HTTPS.");
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Parallax-SocialAuth/1.0");
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String text = readLimited(input);
        connection.disconnect();
        if (text.isEmpty()) {
            throw new AuthException("EMPTY_RESPONSE", "Sign-in server returned an empty response.");
        }
        JSONObject response = new JSONObject(text);
        if (status < 200 || status >= 300) {
            throw new AuthException(response.optString("code", "HTTP_" + status),
                    response.optString("message", "Sign-in server rejected the request."));
        }
        return response;
    }

    private static String readLimited(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (value.length() + read > MAX_RESPONSE_CHARS) {
                    throw new AuthException("RESPONSE_TOO_LARGE", "Sign-in response was too large.");
                }
                value.append(buffer, 0, read);
            }
        }
        return value.toString();
    }

    private static String sanitizeMessage(String value) {
        String message = safe(value).replace('\n', ' ').replace('\r', ' ').trim();
        if (message.isEmpty()) {
            return "Sign in failed.";
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class AuthException extends Exception {
        final String code;
        AuthException(String code, String message) {
            super(message);
            this.code = safe(code);
        }
    }
}
