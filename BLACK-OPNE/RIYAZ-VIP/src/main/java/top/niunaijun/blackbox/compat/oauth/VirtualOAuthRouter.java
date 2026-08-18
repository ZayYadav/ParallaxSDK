package top.niunaijun.blackbox.compat.oauth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * Detects browser based OAuth launches made by a virtual/cloned application and
 * reroutes only the authentication browser step through an AndroidX Auth Tab.
 *
 * The SDK never reads provider credentials, cookies, passwords, or access tokens.
 * It only carries the final redirect URI back to the virtual package that declared
 * the redirect intent-filter.
 */
public final class VirtualOAuthRouter {
    public static final String EXTRA_AUTH_URL =
            "top.niunaijun.blackbox.oauth.AUTH_URL";
    public static final String EXTRA_REDIRECT_URI =
            "top.niunaijun.blackbox.oauth.REDIRECT_URI";
    public static final String EXTRA_VIRTUAL_PACKAGE =
            "top.niunaijun.blackbox.oauth.VIRTUAL_PACKAGE";
    public static final String EXTRA_USER_ID =
            "top.niunaijun.blackbox.oauth.USER_ID";

    private static final Set<String> AUTH_HOSTS = new HashSet<>(Arrays.asList(
            "accounts.google.com",
            "oauth2.googleapis.com",
            "www.facebook.com",
            "m.facebook.com",
            "web.facebook.com",
            "facebook.com",
            "x.com",
            "www.x.com",
            "twitter.com",
            "www.twitter.com",
            "mobile.twitter.com",
            "api.twitter.com"
    ));

    private static final String[] REDIRECT_QUERY_KEYS = {
            "redirect_uri",
            "redirect_url",
            "callback_uri",
            "callback_url",
            "oauth_callback"
    };

    private VirtualOAuthRouter() {
    }

    /**
     * Returns an explicit host-side bridge intent when the supplied intent is a
     * supported OAuth browser launch whose custom redirect belongs to the current
     * virtual package. Returns null for every other intent.
     */
    public static Intent createBridgeIntent(Intent source, int userId, String virtualPackage) {
        if (source == null || virtualPackage == null || virtualPackage.trim().isEmpty()) {
            return null;
        }
        if (!Intent.ACTION_VIEW.equals(source.getAction())) {
            return null;
        }
        Uri authUri = source.getData();
        if (!isTrustedAuthUri(authUri)) {
            return null;
        }

        Uri redirectUri = extractRedirectUri(authUri);
        if (!isSupportedCustomRedirect(redirectUri)) {
            return null;
        }
        if (!redirectBelongsToVirtualPackage(redirectUri, virtualPackage, userId)) {
            return null;
        }

        Intent bridge = new Intent();
        bridge.setComponent(new ComponentName(
                BlackBoxCore.getHostPkg(),
                VirtualOAuthBridgeActivity.class.getName()));
        bridge.putExtra(EXTRA_AUTH_URL, authUri.toString());
        bridge.putExtra(EXTRA_REDIRECT_URI, redirectUri.toString());
        bridge.putExtra(EXTRA_VIRTUAL_PACKAGE, virtualPackage);
        bridge.putExtra(EXTRA_USER_ID, userId);
        bridge.addFlags(source.getFlags() & (
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        return bridge;
    }

    private static boolean isTrustedAuthUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = lower(uri.getScheme());
        if (!"https".equals(scheme)) {
            return false;
        }
        String host = lower(uri.getHost());
        if (host.isEmpty()) {
            return false;
        }
        if (AUTH_HOSTS.contains(host)) {
            return true;
        }
        return host.endsWith(".facebook.com")
                || host.endsWith(".google.com")
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
    }

    private static Uri extractRedirectUri(Uri authUri) {
        for (String key : REDIRECT_QUERY_KEYS) {
            try {
                String value = authUri.getQueryParameter(key);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                Uri candidate = Uri.parse(value.trim());
                if (candidate.getScheme() != null) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean isSupportedCustomRedirect(Uri redirectUri) {
        if (redirectUri == null) {
            return false;
        }
        String scheme = lower(redirectUri.getScheme());
        if (scheme.isEmpty()
                || "http".equals(scheme)
                || "https".equals(scheme)
                || "file".equals(scheme)
                || "content".equals(scheme)
                || "javascript".equals(scheme)
                || "data".equals(scheme)
                || "intent".equals(scheme)) {
            return false;
        }
        return scheme.matches("^[a-z][a-z0-9+.-]{1,63}$");
    }

    private static boolean redirectBelongsToVirtualPackage(
            Uri redirectUri, String virtualPackage, int userId) {
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

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
