package top.niunaijun.blackbox.compat.oauth;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal, dependency-free AndroidX Auth Tab capability detection.
 *
 * The SDK is distributed as a raw AAR, so relying on a transitive
 * androidx.browser dependency would make host integration fragile. AndroidX
 * exposes the browser-facing protocol/category strings publicly; this helper
 * only selects a real browser that advertises Auth Tab support and can handle
 * the requested HTTPS URL.
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

        String defaultBrowser = resolveDefaultBrowser(pm, authUri);
        Set<String> seen = new HashSet<>();
        String firstSupported = null;

        try {
            // Query the capability category directly. This is both simpler and
            // more reliable than depending on ResolveInfo.filter being populated
            // by every Android/OEM PackageManager implementation.
            Intent serviceIntent = authTabServiceIntent(null);
            List<ResolveInfo> services = pm.queryIntentServices(serviceIntent, 0);
            if (services == null) {
                return null;
            }

            for (ResolveInfo info : services) {
                ServiceInfo serviceInfo = info == null ? null : info.serviceInfo;
                if (serviceInfo == null || serviceInfo.packageName == null
                        || !seen.add(serviceInfo.packageName)) {
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
            PackageManager pm = context.getPackageManager();
            Intent serviceIntent = authTabServiceIntent(provider);
            List<ResolveInfo> services = pm.queryIntentServices(serviceIntent, 0);
            if (services == null) {
                return false;
            }
            for (ResolveInfo info : services) {
                ServiceInfo serviceInfo = info == null ? null : info.serviceInfo;
                if (serviceInfo != null
                        && provider.equals(serviceInfo.packageName)
                        && canHandleAuthUrl(pm, provider, authUri)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Intent authTabServiceIntent(String provider) {
        Intent intent = new Intent(ACTION_CUSTOM_TABS_CONNECTION);
        intent.addCategory(CATEGORY_AUTH_TAB);
        if (provider != null && !provider.trim().isEmpty()) {
            intent.setPackage(provider);
        }
        return intent;
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
