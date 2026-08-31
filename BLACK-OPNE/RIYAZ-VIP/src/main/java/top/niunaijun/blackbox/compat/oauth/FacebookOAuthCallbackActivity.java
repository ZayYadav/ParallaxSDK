package top.niunaijun.blackbox.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.Locale;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.fake.frameworks.BActivityManager;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * Host-side receiver for Facebook SDK Custom Tab redirects.
 *
 * A normal installed application receives fbconnect://cct.<package> in
 * com.facebook.CustomTabActivity. A virtual package is absent from Android's
 * real PackageManager, so Chrome cannot deliver that custom-scheme redirect to
 * the guest directly. This activity receives the browser callback and
 * re-dispatches the original Intent into the matching virtual package.
 *
 * Query/fragment values are never read, logged, copied to storage, or persisted.
 */
@Obfuscate
public final class FacebookOAuthCallbackActivity extends Activity {
    private static final String TAG = "ParallaxFacebookOAuth";
    private static final String FACEBOOK_SCHEME = "fbconnect";
    private static final String CCT_HOST_PREFIX = "cct.";
    private static final String FACEBOOK_CALLBACK_ACTIVITY =
            "com.facebook.CustomTabActivity";
    private static final int MAX_VIRTUAL_USER_ID = 24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dispatch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatch(intent);
    }

    private void dispatch(Intent incoming) {
        boolean delivered = false;
        String targetPackage = null;
        int targetUserId = -1;

        try {
            Uri callbackUri = incoming == null ? null : incoming.getData();
            if (!isFacebookCallback(incoming, callbackUri)) {
                safeDiagnostic(false, null, -1);
                finish();
                return;
            }

            targetPackage = packageFromCallback(callbackUri);
            if (!isValidPackageName(targetPackage)) {
                safeDiagnostic(false, null, -1);
                finish();
                return;
            }

            for (int userId = 0; userId <= MAX_VIRTUAL_USER_ID; userId++) {
                Intent guestCallback = buildGuestCallback(callbackUri, targetPackage);
                ResolveInfo resolved = BPackageManager.get().resolveActivity(
                        guestCallback,
                        FileUtils.FileMode.MODE_IWUSR,
                        null,
                        userId);
                if (!isTrustedFacebookCallbackTarget(resolved, targetPackage)) {
                    continue;
                }

                guestCallback.setComponent(new ComponentName(
                        resolved.activityInfo.packageName,
                        resolved.activityInfo.name));
                guestCallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                BActivityManager.get().startActivity(guestCallback, userId);
                delivered = true;
                targetUserId = userId;
                break;
            }
        } catch (Throwable ignored) {
            delivered = false;
        }

        safeDiagnostic(delivered, targetPackage, targetUserId);
        finish();
    }

    private static Intent buildGuestCallback(Uri callbackUri, String targetPackage) {
        Intent callback = new Intent(Intent.ACTION_VIEW, callbackUri);
        callback.addCategory(Intent.CATEGORY_DEFAULT);
        callback.addCategory(Intent.CATEGORY_BROWSABLE);
        callback.setPackage(targetPackage);
        return callback;
    }

    private static boolean isFacebookCallback(Intent intent, Uri uri) {
        if (intent == null || uri == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        String scheme = lower(uri.getScheme());
        String host = lower(uri.getHost());
        return FACEBOOK_SCHEME.equals(scheme)
                && host.startsWith(CCT_HOST_PREFIX)
                && host.length() > CCT_HOST_PREFIX.length();
    }

    private static String packageFromCallback(Uri uri) {
        String host = lower(uri == null ? null : uri.getHost());
        if (!host.startsWith(CCT_HOST_PREFIX)) {
            return null;
        }
        return host.substring(CCT_HOST_PREFIX.length());
    }

    private static boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.length() < 3 || packageName.length() > 255) {
            return false;
        }
        return packageName.matches("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$");
    }

    private static boolean isTrustedFacebookCallbackTarget(
            ResolveInfo resolved, String targetPackage) {
        return resolved != null
                && resolved.activityInfo != null
                && targetPackage != null
                && targetPackage.equals(resolved.activityInfo.packageName)
                && FACEBOOK_CALLBACK_ACTIVITY.equals(resolved.activityInfo.name);
    }

    private static void safeDiagnostic(boolean delivered, String targetPackage, int userId) {
        String safePackage = isValidPackageName(targetPackage) ? targetPackage : "unknown";
        Log.i(TAG, "fbconnect delivered=" + delivered
                + " target=" + safePackage
                + " user=" + userId);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
