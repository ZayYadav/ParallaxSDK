package top.niunaijun.blackbox.compat.oauth;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minimal, dependency-free AndroidX Auth Tab capability detection.
 *
 * The SDK is distributed as a raw AAR, so relying on a transitive
 * androidx.browser dependency would make host integration fragile. AndroidX
 * exposes the browser-facing protocol/category strings publicly; this helper
 * only selects a real browser that advertises Auth Tab support and can handle
 * the requested HTTPS URL.
 *
 * Facebook web login is intentionally Chrome-first. When stable Chrome is
 * installed and advertises Auth Tab support for the exact Facebook URL, the
 * bridge launches Chrome so the user can reuse the Facebook session already
 * owned by Chrome. The SDK never reads or copies Chrome/Facebook cookies.
 */
public final class AuthTabCompat {
    public static final String EXTRA_LAUNCH_AUTH_TAB =
            "androidx.browser.auth.extra.LAUNCH_AUTH_TAB";
    public static final String EXTRA_REDIRECT_SCHEME =
            "androidx.browser.auth.extra.REDIRECT_SCHEME";
    public static final String EXTRA_CUSTOM_TABS_SESSION =
            "android.support.customtabs.extra.SESSION";

    private static final String ACTION_CUSTOM_TABS_CONNECTION =
            "android.support.customtabs.action.CustomTabsService";
    private static final String CATEGORY_AUTH_TAB =
            "androidx.browser.auth.category.AuthTab";
    private static final String CHROME_STABLE_PACKAGE = "com.android.chrome";

    private AuthTabCompat() {
    }

    public static String findProvider(Context context, Uri authUri) {
        if (context == null || authUri == null
                || !"https".equalsIgnoreCase(authUri.getScheme())) {
            return null;
        }

        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        // Facebook must use the real Chrome profile when possible so an existing
        // Chrome Facebook login is naturally reused by the browser itself.
        // This branch is Facebook-only; Twitter/X provider selection is unchanged.
        if (isFacebookAuthUri(authUri)
                && supportsAuthTabProvider(pm, CHROME_STABLE_PACKAGE, authUri)) {
            return CHROME_STABLE_PACKAGE;
        }

        String defaultBrowser = resolveDefaultBrowser(pm, authUri);
        Set<String> seen = new HashSet<>();
        String firstSupported = null;

        try {
            Intent serviceIntent = new Intent(ACTION_CUSTOM_TABS_CONNECTION);
            List<ResolveInfo> services = pm.queryIntentServices(
                    serviceIntent, PackageManager.GET_RESOLVED_FILTER);
            if (services == null) {
                return null;
            }

            for (ResolveInfo info : services) {
                ServiceInfo serviceInfo = info == null ? null : info.serviceInfo;
                IntentFilter filter = info == null ? null : info.filter;
                if (serviceInfo == null || serviceInfo.packageName == null
                        || !seen.add(serviceInfo.packageName)
                        || filter == null
                        || !filter.hasCategory(CATEGORY_AUTH_TAB)) {
                    continue;
                }

                String pkg = serviceInfo.packageName;
                if (!canHandleAuthUrl(pm, pkg, authUri)) {
                    continue;
                }

                if (pkg.equals(defaultBrowser)) {
                    return pkg;
                }
                if (firstSupported == null) {
                    firstSupported = pkg;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        return firstSupported;
    }

    public static boolean isSupportedProvider(Context context, String provider, Uri authUri) {
        if (context == null || provider == null || provider.trim().isEmpty()) {
            return false;
        }
        String selected = findProvider(context, authUri);
        if (provider.equals(selected)) {
            return true;
        }

        try {
            return supportsAuthTabProvider(
                    context.getPackageManager(), provider, authUri);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean supportsAuthTabProvider(
            PackageManager pm, String provider, Uri authUri) {
        if (pm == null || provider == null || provider.trim().isEmpty()
                || authUri == null || !"https".equalsIgnoreCase(authUri.getScheme())) {
            return false;
        }

        try {
            Intent serviceIntent = new Intent(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(provider);
            List<ResolveInfo> services = pm.queryIntentServices(
                    serviceIntent, PackageManager.GET_RESOLVED_FILTER);
            if (services == null) {
                return false;
            }
            for (ResolveInfo info : services) {
                IntentFilter filter = info == null ? null : info.filter;
                ServiceInfo serviceInfo = info == null ? null : info.serviceInfo;
                if (serviceInfo != null
                        && provider.equals(serviceInfo.packageName)
                        && filter != null
                        && filter.hasCategory(CATEGORY_AUTH_TAB)
                        && canHandleAuthUrl(pm, provider, authUri)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isFacebookAuthUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        host = host == null ? "" : host.toLowerCase(Locale.US);
        return "facebook.com".equals(host) || host.endsWith(".facebook.com");
    }

    private static String resolveDefaultBrowser(PackageManager pm, Uri authUri) {
        try {
            Intent view = authViewIntent(authUri);
            ResolveInfo info = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    ? info.activityInfo.packageName : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean canHandleAuthUrl(PackageManager pm, String pkg, Uri authUri) {
        try {
            Intent view = authViewIntent(authUri);
            view.setPackage(pkg);
            ResolveInfo info = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && pkg.equals(info.activityInfo.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Intent authViewIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        return intent;
    }
}
