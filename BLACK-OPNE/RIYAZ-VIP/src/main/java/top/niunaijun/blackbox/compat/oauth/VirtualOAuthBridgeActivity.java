package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import java.util.Locale;

import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * Host-side trampoline for browser OAuth started by a virtual application.
 *
 * The Auth Tab protocol is emitted directly so the SDK AAR stays self-contained
 * when it is copied into a host app as a raw local AAR. The browser returns the
 * final redirect URI through Activity result data; this bridge immediately routes
 * that URI into BlackBox's virtual PackageManager/ActivityManager.
 */
public final class VirtualOAuthBridgeActivity extends Activity {
    private static final int REQUEST_AUTH_TAB = 0x5041;

    // Public AndroidX Browser Auth Tab protocol constants. These are browser-facing
    // Intent extras documented by AndroidX; using the protocol directly avoids a
    // transitive Maven dependency that a raw local AAR cannot carry by itself.
    private static final String EXTRA_LAUNCH_AUTH_TAB =
            "androidx.browser.auth.extra.LAUNCH_AUTH_TAB";
    private static final String EXTRA_REDIRECT_SCHEME =
            "androidx.browser.auth.extra.REDIRECT_SCHEME";
    private static final String EXTRA_CUSTOM_TABS_SESSION =
            "android.support.customtabs.extra.SESSION";

    private String virtualPackage;
    private String expectedRedirectScheme;
    private int userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getIntent();
        String authUrl = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL);
        String redirectUriValue = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI);
        virtualPackage = launchIntent == null ? null
                : launchIntent.getStringExtra(VirtualOAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        userId = launchIntent == null ? -1
                : launchIntent.getIntExtra(VirtualOAuthRouter.EXTRA_USER_ID, -1);

        Uri authUri = safeHttpsUri(authUrl);
        Uri redirectUri = safeCustomRedirectUri(redirectUriValue);
        if (authUri == null || redirectUri == null || virtualPackage == null
                || virtualPackage.trim().isEmpty() || userId < 0) {
            finish();
            return;
        }

        expectedRedirectScheme = lower(redirectUri.getScheme());
        if (!redirectResolvesToVirtualPackage(redirectUri)) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            launchAuthTab(authUri, expectedRedirectScheme);
        }
    }

    private void launchAuthTab(Uri authUri, String redirectScheme) {
        try {
            Intent authIntent = new Intent(Intent.ACTION_VIEW, authUri);
            authIntent.putExtra(EXTRA_LAUNCH_AUTH_TAB, true);
            authIntent.putExtra(EXTRA_REDIRECT_SCHEME, redirectScheme);

            // AndroidX AuthTabIntent.Builder adds a null Custom Tabs session so
            // browsers also recognize this as a Custom Tab-style request.
            Bundle session = new Bundle();
            session.putBinder(EXTRA_CUSTOM_TABS_SESSION, null);
            authIntent.putExtras(session);

            startActivityForResult(authIntent, REQUEST_AUTH_TAB);
        } catch (Throwable ignored) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUTH_TAB) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri callbackUri = data.getData();
        if (!expectedRedirectScheme.equals(lower(callbackUri.getScheme()))) {
            finish();
            return;
        }

        dispatchToVirtualPackage(callbackUri);
        finish();
    }

    private void dispatchToVirtualPackage(Uri callbackUri) {
        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, callbackUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            callback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback,
                    FileUtils.FileMode.MODE_IWUSR,
                    null,
                    userId);
            if (resolved == null || resolved.activityInfo == null
                    || !virtualPackage.equals(resolved.activityInfo.packageName)) {
                return;
            }

            callback.setComponent(new ComponentName(
                    resolved.activityInfo.packageName,
                    resolved.activityInfo.name));
            BActivityManager.get().startActivity(callback, userId);
        } catch (Throwable ignored) {
            // Fail closed. Do not leak redirect data to another package or logs.
        }
    }

    private boolean redirectResolvesToVirtualPackage(Uri redirectUri) {
        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, redirectUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback,
                    FileUtils.FileMode.MODE_IWUSR,
                    null,
                    userId);
            return resolved != null
                    && resolved.activityInfo != null
                    && virtualPackage.equals(resolved.activityInfo.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Uri safeHttpsUri(String value) {
        if (value == null || value.length() > 16_384) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Uri safeCustomRedirectUri(String value) {
        if (value == null || value.length() > 8_192) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value);
            String scheme = lower(uri.getScheme());
            if (scheme.isEmpty()
                    || "http".equals(scheme)
                    || "https".equals(scheme)
                    || "file".equals(scheme)
                    || "content".equals(scheme)
                    || "javascript".equals(scheme)
                    || "data".equals(scheme)
                    || "intent".equals(scheme)
                    || !scheme.matches("^[a-z][a-z0-9+.-]{1,63}$")) {
                return null;
            }
            return uri;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
