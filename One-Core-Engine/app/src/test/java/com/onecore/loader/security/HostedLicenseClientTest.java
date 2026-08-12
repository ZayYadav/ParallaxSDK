package com.onecore.loader.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HostedLicenseClientTest {
    @Test
    public void acceptsDashboardActivationKey() {
        assertTrue(HostedLicenseClient.isSupportedActivationKey(
                "OC-1234-ABCD-5678-EF90-1111-2222-3333-4444"));
    }

    @Test
    public void normalizesDashboardActivationKey() {
        assertEquals(
                "OC-1234-ABCD-5678-EF90-1111-2222-3333-4444",
                HostedLicenseClient.normalizeActivationKey(
                        "  oc-1234-abcd-5678-ef90-1111-2222-3333-4444  "));
    }

    @Test
    public void rejectsLegacyAndMalformedKeys() {
        assertFalse(HostedLicenseClient.isSupportedActivationKey(
                "PUBG-ADMIN-1920H-2634A-YOUR_SERIAL"));
        assertFalse(HostedLicenseClient.isSupportedActivationKey("OC-1234"));
        assertFalse(HostedLicenseClient.isSupportedActivationKey(null));
    }
}
