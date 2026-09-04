package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import top.niunaijun.blackbox.compat.auth.ExternalAuthRouter;

/**
 * Compatibility SSO surface for legacy Twitter Kit when current X builds no
 * longer export com.twitter.android.SingleSignOnActivity.
 *
 * Uses X's documented OAuth 1.0a out-of-band/PIN flow. Consumer credentials and
 * OAuth tokens are never logged or persisted. Success is returned using the
 * historical Twitter Kit tk/ts/screen_name/user_id Activity result contract.
 */
@Obfuscate
public final class TwitterLegacyPinAuthActivity extends Activity {
    public static final String EXTRA_PIN_COMPAT =
            "top.niunaijun.blackbox.auth.TWITTER_OAUTH1_PIN_COMPAT";

    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String X_URL_ACTIVITY =
            "com.x.android.deeplink.XUrlInterpreterActivity";
    private static final String REQUEST_TOKEN_URL =
            "https://api.x.com/oauth/request_token";
    private static final String AUTHORIZE_URL =
            "https://api.x.com/oauth/authorize";
    private static final String ACCESS_TOKEN_URL =
            "https://api.x.com/oauth/access_token";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();

    private TextView status;
    private String consumerKey;
    private String consumerSecret;
    private String requestToken;
    private String requestTokenSecret;
    private boolean providerStarted;
    private boolean providerPaused;
    private boolean pinDialogShown;
    private boolean terminal;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent launch = getIntent();
        if (!validLaunch(launch)) {
            cancelAndFinish();
            return;
        }

        consumerKey = trim(launch.getStringExtra("ck"));
        consumerSecret = trim(launch.getStringExtra("cs"));
        if (consumerKey.isEmpty() || consumerSecret.isEmpty()) {
            cancelAndFinish();
            return;
        }

        setContentView(createProgressView());
        if (state == null) {
            requestTemporaryToken();
        } else {
            // OAuth request-token secrets are intentionally memory-only.
            cancelAndFinish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (providerStarted) providerPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (providerStarted && providerPaused && requestToken != null
                && !pinDialogShown && !terminal) {
            showPinDialog();
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        consumerKey = null;
        consumerSecret = null;
        requestToken = null;
        requestTokenSecret = null;
        super.onDestroy();
    }

    private ViewGroup createProgressView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = dp(28);
        root.setPadding(padding, padding, padding, padding);

        root.addView(new ProgressBar(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setText("Starting X authorization...");
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(18);
        root.addView(status, textParams);
        return root;
    }

    private void requestTemporaryToken() {
        setStatus("Preparing secure X authorization...");
        worker.execute(() -> {
            try {
                Map<String, String> oauth = baseOAuth(null);
                oauth.put("oauth_callback", "oob");
                HttpResult response = signedPost(
                        REQUEST_TOKEN_URL, oauth, "", consumerSecret);
                if (response.code != HttpURLConnection.HTTP_OK) {
                    failOnMain("X rejected the authorization request (HTTP "
                            + response.code + ").");
                    return;
                }

                Map<String, String> values = parseForm(response.body);
                String token = trim(values.get("oauth_token"));
                String secret = trim(values.get("oauth_token_secret"));
                String confirmed = trim(values.get("oauth_callback_confirmed"));
                if (token.isEmpty() || secret.isEmpty()
                        || !"true".equalsIgnoreCase(confirmed)) {
                    failOnMain("X returned an invalid request-token response.");
                    return;
                }

                requestToken = token;
                requestTokenSecret = secret;
                runOnUiThread(this::launchAuthorization);
            } catch (Throwable ignored) {
                failOnMain("Unable to start X authorization.");
            }
        });
    }

    private void launchAuthorization() {
        if (terminal || requestToken == null) return;

        Uri uri = Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("oauth_token", requestToken)
                .build();
        Intent view = new Intent(Intent.ACTION_VIEW, uri);
        view.addCategory(Intent.CATEGORY_DEFAULT);
        view.addCategory(Intent.CATEGORY_BROWSABLE);

        boolean launched = false;
        ComponentName exact = new ComponentName(TWITTER_PACKAGE, X_URL_ACTIVITY);
        if (isUsableXActivity(exact)) {
            try {
                view.setComponent(exact);
                providerStarted = true;
                providerPaused = false;
                setStatus("Approve access in X, then return here.");
                startActivity(view);
                launched = true;
            } catch (Throwable ignored) {
                providerStarted = false;
                view.setComponent(null);
            }
        }

        if (!launched) {
            try {
                view.setComponent(null);
                view.setPackage(null);
                providerStarted = true;
                providerPaused = false;
                setStatus("Approve access in X, then return here.");
                startActivity(view);
                launched = true;
            } catch (Throwable ignored) {
                providerStarted = false;
            }
        }

        if (!launched) {
            showFailure("No X/browser authorization screen is available.");
        }
    }

    private boolean isUsableXActivity(ComponentName component) {
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo info = pm.getActivityInfo(component, 0);
            return info != null
                    && TWITTER_PACKAGE.equals(info.packageName)
                    && X_URL_ACTIVITY.equals(info.name)
                    && info.enabled
                    && info.exported
                    && (info.applicationInfo == null || info.applicationInfo.enabled);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void showPinDialog() {
        if (terminal || isFinishing() || pinDialogShown) return;
        pinDialogShown = true;

        EditText pin = new EditText(this);
        pin.setSingleLine(true);
        pin.setHint("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(24), 0, dp(24), 0);
        holder.addView(pin, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enter X authorization PIN")
                .setMessage("Approve access in X, then enter the PIN shown there.")
                .setView(holder)
                .setPositiveButton("Continue", null)
                .setNeutralButton("Open X again", null)
                .setNegativeButton("Cancel", (d, w) -> cancelAndFinish())
                .setOnCancelListener(d -> cancelAndFinish())
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String verifier = trim(pin.getText() == null
                        ? null : pin.getText().toString());
                if (!verifier.matches("^[0-9]{4,16}$")) {
                    pin.setError("Enter the PIN shown by X");
                    return;
                }
                dialog.dismiss();
                exchangeAccessToken(verifier);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                pinDialogShown = false;
                dialog.dismiss();
                launchAuthorization();
            });
        });
        dialog.show();
    }

    private void exchangeAccessToken(String verifier) {
        if (terminal || requestToken == null || requestTokenSecret == null) {
            cancelAndFinish();
            return;
        }
        providerStarted = false;
        setStatus("Completing X authorization...");

        worker.execute(() -> {
            try {
                Map<String, String> oauth = baseOAuth(requestToken);
                oauth.put("oauth_verifier", verifier);
                HttpResult response = signedPost(
                        ACCESS_TOKEN_URL, oauth, requestTokenSecret, consumerSecret);
                if (response.code != HttpURLConnection.HTTP_OK) {
                    failOnMain("X could not complete authorization (HTTP "
                            + response.code + ").");
                    return;
                }

                Map<String, String> values = parseForm(response.body);
                String token = trim(values.get("oauth_token"));
                String secret = trim(values.get("oauth_token_secret"));
                String screenName = trim(values.get("screen_name"));
                long userId = positiveLong(trim(values.get("user_id")));
                if (token.isEmpty() || secret.isEmpty() || userId <= 0L) {
                    failOnMain("X returned an incomplete login result.");
                    return;
                }

                Intent result = new Intent();
                result.putExtra("tk", token);
                result.putExtra("ts", secret);
                result.putExtra("screen_name", screenName);
                result.putExtra("user_id", userId);

                runOnUiThread(() -> {
                    if (terminal || isFinishing()) return;
                    terminal = true;
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (Throwable ignored) {
                failOnMain("Unable to complete X authorization.");
            }
        });
    }

    private Map<String, String> baseOAuth(String token) {
        Map<String, String> oauth = new LinkedHashMap<>();
        oauth.put("oauth_consumer_key", consumerKey);
        oauth.put("oauth_nonce", nonce());
        oauth.put("oauth_signature_method", "HMAC-SHA1");
        oauth.put("oauth_timestamp",
                Long.toString(System.currentTimeMillis() / 1000L));
        if (token != null && !token.isEmpty()) oauth.put("oauth_token", token);
        oauth.put("oauth_version", "1.0");
        return oauth;
    }

    private HttpResult signedPost(
            String endpoint,
            Map<String, String> oauth,
            String tokenSecret,
            String clientSecret) throws Exception {
        Map<String, String> headerParams = new LinkedHashMap<>(oauth);
        headerParams.put("oauth_signature", oauthSignature(
                "POST", endpoint, oauth, clientSecret, tokenSecret));

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Accept", "application/x-www-form-urlencoded");
            connection.setRequestProperty(
                    "Authorization", oauthHeader(headerParams));
            connection.setFixedLengthStreamingMode(0);
            connection.getOutputStream().close();

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new HttpResult(code, readSmall(input));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String oauthSignature(
            String method,
            String endpoint,
            Map<String, String> params,
            String consumerSecret,
            String tokenSecret) throws Exception {
        List<String> encoded = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            encoded.add(percentEncode(entry.getKey()) + "="
                    + percentEncode(entry.getValue()));
        }
        Collections.sort(encoded);

        StringBuilder normalized = new StringBuilder();
        for (String item : encoded) {
            if (normalized.length() > 0) normalized.append('&');
            normalized.append(item);
        }

        String base = method.toUpperCase(Locale.US)
                + "&" + percentEncode(endpoint)
                + "&" + percentEncode(normalized.toString());
        String key = percentEncode(consumerSecret)
                + "&" + percentEncode(tokenSecret == null ? "" : tokenSecret);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.encodeToString(
                mac.doFinal(base.getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    }

    static String percentEncode(String value) throws Exception {
        return URLEncoder.encode(
                value == null ? "" : value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String oauthHeader(Map<String, String> params) throws Exception {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("oauth_")) {
                items.add(percentEncode(entry.getKey()) + "=\""
                        + percentEncode(entry.getValue()) + "\"");
            }
        }
        Collections.sort(items);
        return "OAuth " + join(items, ", ");
    }

    private String nonce() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.encodeToString(bytes,
                Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    private static Map<String, String> parseForm(String body) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) return out;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int pos = pair.indexOf('=');
            String key = pos >= 0 ? pair.substring(0, pos) : pair;
            String value = pos >= 0 ? pair.substring(pos + 1) : "";
            out.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8.name()),
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
        }
        return out;
    }

    private static String readSmall(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("OAuth response too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private boolean validLaunch(Intent launch) {
        if (launch == null
                || !launch.getBooleanExtra(EXTRA_PIN_COMPAT, false)) {
            return false;
        }
        Bundle extras = launch.getExtras();
        if (extras == null) return false;

        IBinder resultTo = extras.getBinder(
                ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(
                ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
        String virtualPackage = extras.getString(
                ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        return resultTo != null
                && requestCode >= 0
                && bpid >= 0 && bpid <= 24
                && virtualPackage != null
                && !virtualPackage.trim().isEmpty();
    }

    private void failOnMain(String message) {
        runOnUiThread(() -> showFailure(message));
    }

    private void showFailure(String message) {
        if (terminal || isFinishing()) return;
        providerStarted = false;
        terminal = true;
        new AlertDialog.Builder(this)
                .setTitle("X authorization failed")
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setOnCancelListener(d -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
    }

    private void cancelAndFinish() {
        if (terminal || isFinishing()) return;
        terminal = true;
        try {
            setResult(RESULT_CANCELED);
        } catch (Throwable ignored) {
        }
        finish();
    }

    private void setStatus(String value) {
        if (status != null) status.setText(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? parsed : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
