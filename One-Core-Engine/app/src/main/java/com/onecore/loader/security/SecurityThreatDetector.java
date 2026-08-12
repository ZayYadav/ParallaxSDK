package com.onecore.loader.security;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.StringRes;

import com.onecore.loader.R;

/** Performs supported, user-visible runtime environment checks. */
public final class SecurityThreatDetector {
    public enum Threat {
        NONE(0),
        INVALID_SIGNATURE(R.string.security_warning_signature),
        VPN_ACTIVE(R.string.security_warning_vpn);

        private final int messageResource;

        Threat(@StringRes int messageResource) {
            this.messageResource = messageResource;
        }

        @StringRes
        public int messageResource() {
            return messageResource;
        }
    }

    private SecurityThreatDetector() {
    }

    public static Threat detect(Context context) {
        if (!AppIntegrity.isSignatureValid(context)) {
            return Threat.INVALID_SIGNATURE;
        }
        if (isVpnActive(context)) {
            return Threat.VPN_ACTIVE;
        }
        return Threat.NONE;
    }

    private static boolean isVpnActive(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        try {
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkCapabilities capabilities =
                        connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null
                        && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (SecurityException ignored) {
            // ACCESS_NETWORK_STATE is declared; fail open only when the OS denies visibility.
        }
        return false;
    }
}
