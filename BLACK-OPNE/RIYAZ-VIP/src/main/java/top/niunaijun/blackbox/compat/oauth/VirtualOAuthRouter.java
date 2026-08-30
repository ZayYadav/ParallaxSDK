package top.niunaijun.blackbox.compat.oauth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lsposed.lsparanoid.Obfuscate;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * Detects browser based OAuth launches made by a virtual/cloned application and
 * reroutes only the authentication browser step through an Android Auth Tab.
 *
 * The SDK never reads provider credentials, cookies, passwords, or access tokens.
 * It only carries the final redirect URI back to the virtual package that declared
 * the redirect intent-filter.
 */
@Obfuscate
public final class VirtualOAuthRouter {
    public static final String EXTRA_AUTH_URL =
            "top.niunaijun.blackbox.oauth.AUTH_URL";
    public static final String EXTRA_REDIRECT_URI =
            "top.niunaijun.blackbox.oauth.REDIRECT_URI";
    public static final String EXTRA_VIRTUAL_PACKAGE =
            "top.niunaijun.blackbox.oauth.VIRTUAL_PACKAGE";
    public static final String EXTRA_USER_ID =
            "top.niunaijun.blackbox.oauth.USER_ID";
    public static final String EXTRA_AUTH_PROVIDER =
            "top.niunaijun.blackbox.oauth.AUTH_PROVIDER";

    private static final Set<String> AUTH_HOSTS = new HashSet<>(Arrays.asList(
            "accounts.google.com",
            "oauth2.googleapis.com",
            "www.facebook.com",
            "m.facebook.com",
            "web.facebook.com",
            "facebook.com",
            "x.com",
            "www.x.com",
            "mobile.x.com",
            "api.x.com",
            "oauth.x.com",
            "twitter.com",
            "www.twitter.com",
            "mobile.twitter.com",
            "api.twitter.com",
            "oauth.twitter.com"
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
        if (redirectUri == null) {
            redirectUri = inferDeclaredProviderRedirect(authUri, virtualPackage, userId);
        }
        if (!isSupportedCustomRedirect(redirectUri)) {
            return null;
        }
        if (!redirectBelongsToVirtualPackage(redirectUri, virtualPackage, userId)) {
            return null;
        }

        // The host bridge must receive the custom-scheme callback itself. AndroidX
        // Auth Tab provides exactly that result contract; a normal ACTION_VIEW
        // browser would instead try to resolve the custom scheme in the host OS.
        String authProvider = AuthTabCompat.findProvider(
                BlackBoxCore.getContext(), authUri);
        if (authProvider == null || authProvider.trim().isEmpty()) {
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
        bridge.putExtra(EXTRA_AUTH_PROVIDER, authProvider);
        bridge.addFlags(source.getFlags() & (
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        return bridge;
    }

    public static boolean isTrustedAuthUri(Uri uri) {
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
                || host.endsWith(".googleapis.com")
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
    }

    private static boolean isTwitterHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = lower(uri.getHost());
        return "twitter.com".equals(host)
                || "x.com".equals(host)
                || host.endsWith(".twitter.com")
                || host.endsWith(".x.com");
    }

    private static boolean isFacebookHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = lower(uri.getHost());
        return "facebook.com".equals(host) || host.endsWith(".facebook.com");
    }

    private static boolean isGoogleHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = lower(uri.getHost());
        return "google.com".equals(host)
                || "accounts.google.com".equals(host)
                || "googleapis.com".equals(host)
                || host.endsWith(".google.com")
                || host.endsWith(".googleapis.com");
    }

    private static Uri extractRedirectUri(Uri authUri) {
        for (String key : REDIRECT_QUERY_KEYS) {
            try {
                String value = authUri.getQueryParameter(key);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                String trimmed = value.trim();
                Uri candidate = Uri.parse(trimmed);
                if (candidate.getScheme() != null) {
                    return candidate;
                }

                // Some OAuth clients pre-encode redirect_uri before URL building,
                // leaving one extra percent-encoding layer after query decoding.
                String decoded = Uri.decode(trimmed);
                if (!decoded.equals(trimmed)) {
                    candidate = Uri.parse(decoded);
                    if (candidate.getScheme() != null) {
                        return candidate;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Some provider SDKs omit redirect_uri from the final authorize URL because
     * it was fixed earlier in the provider configuration/request-token exchange.
     * In that case only use a callback explicitly declared by the virtual APK and
     * require strong provider-specific evidence before selecting it.
     */
    private static Uri inferDeclaredProviderRedirect(
            Uri authUri, String virtualPackage, int userId) {
        if (isTwitterHost(authUri)) {
            return inferLegacyTwitterRedirect(virtualPackage, userId);
        }

        final int provider;
        if (isFacebookHost(authUri)) {
            provider = 1;
        } else if (isGoogleHost(authUri)) {
            provider = 2;
        } else {
            return null;
        }

        try {
            List<Uri> candidates = DeclaredOAuthCallbackResolver.findCandidates(
                    virtualPackage, userId);
            Uri best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Uri candidate : candidates) {
                if (!isSupportedCustomRedirect(candidate)
                        || !redirectBelongsToVirtualPackage(
                        candidate, virtualPackage, userId)) {
                    continue;
                }
                int score = provider == 1
                        ? facebookRedirectScore(candidate, virtualPackage)
                        : googleRedirectScore(candidate, virtualPackage);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            return bestScore >= 100 ? best : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * OAuth 1.0a authorize/authenticate URLs usually contain only oauth_token;
     * oauth_callback was supplied during the earlier request-token exchange.
     */
    private static Uri inferLegacyTwitterRedirect(String virtualPackage, int userId) {
        Uri declared = bestDeclaredTwitterRedirect(virtualPackage, userId);
        if (declared != null) {
            return declared;
        }

        Uri[] candidates = new Uri[]{
                Uri.parse("twittersdk://callback"),
                Uri.parse("twittersdk://authorize"),
                Uri.parse("twitterkit://callback"),
                Uri.parse("twitterkit://authorize"),
                Uri.parse("twitter://callback"),
                Uri.parse("twitter://authorize"),
                Uri.parse("twitterauth://callback"),
                Uri.parse("oauth-twitter://callback"),
                Uri.parse("xauth://callback"),
                Uri.parse("x://callback")
        };
        for (Uri candidate : candidates) {
            if (redirectBelongsToVirtualPackage(candidate, virtualPackage, userId)) {
                return candidate;
            }
        }
        return null;
    }

    private static Uri bestDeclaredTwitterRedirect(String virtualPackage, int userId) {
        try {
            List<Uri> candidates = DeclaredOAuthCallbackResolver.findCandidates(
                    virtualPackage, userId);
            Uri best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Uri candidate : candidates) {
                if (!isSupportedCustomRedirect(candidate)
                        || !redirectBelongsToVirtualPackage(
                        candidate, virtualPackage, userId)) {
                    continue;
                }
                int score = twitterRedirectScore(candidate, virtualPackage);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            return bestScore >= 50 ? best : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int twitterRedirectScore(Uri candidate, String virtualPackage) {
        String full = lower(candidate == null ? null : candidate.toString());
        String scheme = lower(candidate == null ? null : candidate.getScheme());
        int score = 0;

        if (scheme.startsWith("twittersdk")) score += 220;
        if (scheme.startsWith("twitterkit")) score += 190;
        if (full.contains("twitter")) score += 130;
        if ("x".equals(scheme) || scheme.startsWith("x-") || scheme.startsWith("x.")) {
            score += 60;
        }
        if (full.contains("oauth")) score += 45;
        if (full.contains("callback")) score += 40;
        if (full.contains("authorize")) score += 25;
        if (virtualPackage != null && scheme.startsWith(lower(virtualPackage))) score += 25;

        if (scheme.startsWith("fb") || full.contains("facebook")) score -= 250;
        if (full.contains("google") || full.contains("googleusercontent")) score -= 250;
        return score;
    }

    private static int facebookRedirectScore(Uri candidate, String virtualPackage) {
        String full = lower(candidate == null ? null : candidate.toString());
        String scheme = lower(candidate == null ? null : candidate.getScheme());
        int score = 0;
        if (scheme.startsWith("fb")) score += 240;
        if (full.contains("facebook")) score += 160;
        if (full.contains("authorize")) score += 60;
        if (full.contains("oauth")) score += 50;
        if (full.contains("callback")) score += 40;
        if (virtualPackage != null && scheme.startsWith(lower(virtualPackage))) score += 20;
        if (full.contains("twitter") || full.contains("googleusercontent")) score -= 300;
        return score;
    }

    private static int googleRedirectScore(Uri candidate, String virtualPackage) {
        String full = lower(candidate == null ? null : candidate.toString());
        String scheme = lower(candidate == null ? null : candidate.getScheme());
        int score = 0;
        if (scheme.startsWith("com.googleusercontent.apps.")) score += 260;
        if (full.contains("googleusercontent")) score += 220;
        if (full.contains("oauth2redirect")) score += 140;
        if (full.contains("google")) score += 100;
        if (full.contains("oauth")) score += 45;
        if (virtualPackage != null && scheme.startsWith(lower(virtualPackage))) score += 20;
        if (scheme.startsWith("fb") || full.contains("facebook") || full.contains("twitter")) {
            score -= 300;
        }
        return score;
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
        return scheme.matches("^[a-z][a-z0-9+.-]{1,127}$");
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
