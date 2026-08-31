package top.niunaijun.blackbox.compat.oauth;

import android.net.Uri;

import java.util.Locale;

/**
 * Validates browser-controlled OAuth callback URIs before they cross back into
 * a virtual application. Provider-added result parameters are allowed, while
 * the registered callback target, fixed parameters, and OAuth state must match.
 */
public final class OAuthCallbackValidator {
    private OAuthCallbackValidator() {
    }

    public static boolean matches(Uri authUri, Uri expectedRedirect, Uri callback) {
        if (authUri == null || expectedRedirect == null || callback == null) {
            return false;
        }
        if (!lower(expectedRedirect.getScheme()).equals(lower(callback.getScheme()))) {
            return false;
        }
        if (!lower(expectedRedirect.getEncodedAuthority())
                .equals(lower(callback.getEncodedAuthority()))) {
            return false;
        }
        if (!same(expectedRedirect.getEncodedPath(), callback.getEncodedPath())) {
            return false;
        }
        if (expectedRedirect.getFragment() != null
                && !same(expectedRedirect.getEncodedFragment(), callback.getEncodedFragment())) {
            return false;
        }

        try {
            String expectedState = authUri.getQueryParameter("state");
            if (expectedState != null && !expectedState.isEmpty()) {
                String callbackState = callback.getQueryParameter("state");

                // Meta/Facebook token and hybrid Custom Tab flows may return
                // provider result parameters in the fragment. Meta's own Android
                // SDK merges query + fragment before validating state. Permit
                // that fallback only for a trusted Facebook auth host so the
                // existing Twitter/X and other-provider validation semantics stay
                // exactly query-only.
                if ((callbackState == null || callbackState.isEmpty())
                        && isFacebookAuthHost(authUri)) {
                    callbackState = getFragmentParameter(callback, "state");
                }

                if (!expectedState.equals(callbackState)) {
                    return false;
                }
            }

            for (String name : expectedRedirect.getQueryParameterNames()) {
                if (!expectedRedirect.getQueryParameters(name)
                        .equals(callback.getQueryParameters(name))) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

    private static boolean isFacebookAuthHost(Uri authUri) {
        if (authUri == null) {
            return false;
        }
        String host = lower(authUri.getHost());
        return "facebook.com".equals(host)
                || "www.facebook.com".equals(host)
                || "m.facebook.com".equals(host)
                || "web.facebook.com".equals(host)
                || host.endsWith(".facebook.com");
    }

    /**
     * Reads only the named validation field from the fragment. OAuth result
     * values are not logged or persisted.
     */
    private static String getFragmentParameter(Uri uri, String name) {
        if (uri == null || name == null || name.isEmpty()) {
            return null;
        }

        String fragment;
        try {
            fragment = uri.getEncodedFragment();
        } catch (Throwable ignored) {
            return null;
        }
        if (fragment == null || fragment.isEmpty()) {
            return null;
        }

        String[] pairs = fragment.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String encodedKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String key;
            try {
                key = Uri.decode(encodedKey);
            } catch (Throwable ignored) {
                continue;
            }
            if (!name.equals(key)) {
                continue;
            }

            if (separator < 0 || separator + 1 >= pair.length()) {
                return "";
            }
            try {
                return Uri.decode(pair.substring(separator + 1));
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
