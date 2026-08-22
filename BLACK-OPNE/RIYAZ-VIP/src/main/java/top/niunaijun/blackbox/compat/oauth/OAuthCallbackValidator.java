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
            if (expectedState != null && !expectedState.isEmpty()
                    && !expectedState.equals(callback.getQueryParameter("state"))) {
                return false;
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

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
