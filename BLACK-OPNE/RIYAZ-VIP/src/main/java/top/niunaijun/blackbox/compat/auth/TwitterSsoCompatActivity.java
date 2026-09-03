package top.niunaijun.blackbox.compat.auth;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.compat.oauth.AuthTabCompat;

/**
 * Host-side implementation of the discontinued Twitter Kit SSO activity wire
 * contract. It is used only when the signed, installed X app no longer exposes
 * com.twitter.android.SingleSignOnActivity.
 */
public final class TwitterSsoCompatActivity extends Activity {
    public static final String EXTRA_COMPAT_MODE =
            "top.niunaijun.blackbox.auth.TWITTER_LEGACY_SSO";
    public static final String EXTRA_SDK_VERSION =
            "top.niunaijun.blackbox.auth.TWITTER_SDK_VERSION";

    private static final String TAG = "TwitterSSOCompat";
    private static final String CALLBACK_SCHEME = "twittersdk";
    private static final String CALLBACK_HOST = "callback";
    private static final int REQUEST_AUTHORIZE = 0x5457;
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static volatile WeakReference<TwitterSsoCompatActivity> pendingActivity =
            new WeakReference<>(null);
    private static final String[] PROVIDER_PACKAGES = {
            "com.twitter.android", "com.x.android", "com.twitter.android.lite"
    };

    private String consumerKey;
    private String consumerSecret;
    private String sdkVersion;
    private TwitterOAuth1Client.RequestToken requestToken;
    private boolean exchangeStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_COMPAT_MODE, false)
                || !hasValidResultTarget(intent)) {
            Log.w(TAG, "compat stage=launch_rejected");
            finish();
            return;
        }

        consumerKey = boundedExtra(intent, "ck", 512);
        consumerSecret = boundedExtra(intent, "cs", 1024);
        sdkVersion = boundedExtra(intent, EXTRA_SDK_VERSION, 80);
        if (isBlank(consumerKey) || isBlank(consumerSecret)) {
            completeCanceled("credentials_missing");
            return;
        }

        pendingActivity = new WeakReference<>(this);
        if (savedInstanceState == null) {
            requestTemporaryToken();
        } else {
            // OAuth secrets are intentionally not persisted into saved-state.
            completeCanceled("state_lost");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUTHORIZE || exchangeStarted || isFinishing()) return;
        Uri callback = data == null ? null : data.getData();
        if (isExpectedCallback(callback)) {
            handleCallback(callback);
        } else {
            completeCanceled("authorization_canceled");
        }
    }

    private void requestTemporaryToken() {
        Log.i(TAG, "compat stage=request_token_started tls=platform");
        NETWORK.execute(() -> {
            try {
                String callbackUrl = Uri.parse(CALLBACK_SCHEME + "://" + CALLBACK_HOST)
                        .buildUpon()
                        .appendQueryParameter("version", isBlank(sdkVersion) ? "3.3.0" : sdkVersion)
                        .appendQueryParameter("app", consumerKey)
                        .build().toString();
                TwitterOAuth1Client.RequestToken token = TwitterOAuth1Client.requestToken(
                        consumerKey, consumerSecret, callbackUrl, sdkVersion);
                runOnUiThread(() -> launchAuthorization(token));
            } catch (Throwable error) {
                runOnUiThread(() -> completeCanceled("request_token_failed"));
            }
        });
    }

    private void launchAuthorization(TwitterOAuth1Client.RequestToken token) {
        if (isFinishing() || token == null) return;
        requestToken = token;
        Uri authorizeUri = Uri.parse("https://api.twitter.com/oauth/authorize")
                .buildUpon().appendQueryParameter("oauth_token", token.token).build();

        Intent providerIntent = new Intent(Intent.ACTION_VIEW, authorizeUri);
        providerIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        String provider = resolveNativeProvider(providerIntent);
        if (provider != null) {
            providerIntent.setPackage(provider);
            Log.i(TAG, "compat stage=authorize_launch surface=native provider=" + provider);
            startForResult(providerIntent);
            return;
        }

        String browser = AuthTabCompat.findProvider(this, authorizeUri);
        if (browser != null) {
            providerIntent.setPackage(browser);
            providerIntent.putExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, true);
            providerIntent.putExtra(AuthTabCompat.EXTRA_REDIRECT_SCHEME, CALLBACK_SCHEME);
            Bundle session = new Bundle();
            session.putBinder(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION, null);
            providerIntent.putExtras(session);
            Log.i(TAG, "compat stage=authorize_launch surface=auth_tab");
            startForResult(providerIntent);
            return;
        }

        Log.i(TAG, "compat stage=authorize_launch surface=browser");
        startForResult(providerIntent);
    }

    private void startForResult(Intent intent) {
        try {
            startActivityForResult(intent, REQUEST_AUTHORIZE);
        } catch (Throwable error) {
            completeCanceled("authorize_launch_failed");
        }
    }

    private void handleCallback(Uri callback) {
        if (exchangeStarted || !isExpectedCallback(callback) || requestToken == null) {
            if (!exchangeStarted) completeCanceled("callback_rejected");
            return;
        }
        String returnedToken = callback.getQueryParameter("oauth_token");
        String verifier = callback.getQueryParameter("oauth_verifier");
        String denied = callback.getQueryParameter("denied");
        if (!isBlank(denied)) {
            completeCanceled("authorization_denied");
            return;
        }
        if (!requestToken.token.equals(returnedToken) || isBlank(verifier)) {
            completeCanceled("callback_incomplete");
            return;
        }

        exchangeStarted = true;
        Log.i(TAG, "compat stage=access_token_started tls=platform");
        NETWORK.execute(() -> {
            try {
                TwitterOAuth1Client.AccessToken token = TwitterOAuth1Client.accessToken(
                        consumerKey, consumerSecret, requestToken, verifier, sdkVersion);
                runOnUiThread(() -> completeSuccess(token));
            } catch (Throwable error) {
                runOnUiThread(() -> completeCanceled("access_token_failed"));
            }
        });
    }

    static boolean dispatchCallback(Uri callback) {
        TwitterSsoCompatActivity activity = pendingActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        activity.handleCallback(callback);
        return true;
    }

    @Override
    protected void onDestroy() {
        TwitterSsoCompatActivity pending = pendingActivity.get();
        if (pending == this) pendingActivity.clear();
        super.onDestroy();
    }

    private void completeSuccess(TwitterOAuth1Client.AccessToken token) {
        if (isFinishing() || token == null) return;
        Intent result = new Intent();
        result.putExtra("tk", token.token);
        result.putExtra("ts", token.secret);
        result.putExtra("screen_name", token.screenName);
        result.putExtra("user_id", token.userId);
        Log.i(TAG, "compat stage=complete success=true");
        setResult(RESULT_OK, result);
        finish();
    }

    private void completeCanceled(String stage) {
        if (isFinishing()) return;
        Log.w(TAG, "compat stage=" + stage + " success=false");
        setResult(RESULT_CANCELED);
        finish();
    }

    private String resolveNativeProvider(Intent source) {
        PackageManager pm = getPackageManager();
        if (pm == null) return null;
        for (String packageName : PROVIDER_PACKAGES) {
            try {
                Intent candidate = new Intent(source).setPackage(packageName);
                ResolveInfo info = pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY);
                if (info != null && info.activityInfo != null
                        && packageName.equals(info.activityInfo.packageName)) {
                    return packageName;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isExpectedCallback(Uri uri) {
        return uri != null
                && CALLBACK_SCHEME.equalsIgnoreCase(uri.getScheme())
                && CALLBACK_HOST.equalsIgnoreCase(uri.getHost());
    }

    private static boolean hasValidResultTarget(Intent intent) {
        try {
            Bundle extras = intent.getExtras();
            return extras != null
                    && extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER) != null
                    && extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1) >= 0
                    && !isBlank(extras.getString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String boundedExtra(Intent intent, String name, int maxLength) {
        String value = intent.getStringExtra(name);
        return value != null && value.length() <= maxLength ? value : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
