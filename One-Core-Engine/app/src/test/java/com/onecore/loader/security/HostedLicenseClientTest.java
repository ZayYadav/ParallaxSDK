package com.onecore.loader.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

public class HostedLicenseClientTest {
    private static final String KEY = "PARALLAX_TEST_KEY";
    private static final String SERIAL = "test-device-identity";
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final long SERVER_TIME = 1_800_000_000L;

    @Test
    public void checkerIdentityIsPinnedInTheLoader() {
        assertEquals("https://parallaxserver.online/connect", HostedLicenseClient.CONNECT_URL);
        assertEquals("PUBG", HostedLicenseClient.GAME_ID);
    }

    @Test
    public void acceptsMatchingFreshTokenAndUtcExpiry() throws Exception {
        HostedLicenseClient.ParsedLicense license = HostedLicenseClient.parseResponse(
                successfulResponse(validToken(), "2030-01-02 03:04:05", SERVER_TIME),
                KEY,
                SERIAL,
                SECRET,
                SERVER_TIME);

        assertEquals(validToken(), license.token);
        assertEquals(1_893_553_445L, license.expiresAt);
        assertEquals(SERVER_TIME, license.serverTime);
    }

    @Test
    public void rejectsTokenThatIsNotBoundToKeyAndDevice() throws Exception {
        expectRejected(
                successfulResponse("00000000000000000000000000000000",
                        "2030-01-02 03:04:05", SERVER_TIME),
                "integrity validation failed");
    }

    @Test
    public void rejectsStaleResponseTimestamp() throws Exception {
        expectRejected(
                successfulResponse(validToken(), "2030-01-02 03:04:05", SERVER_TIME),
                "timestamp validation failed",
                SERVER_TIME + 61L);
    }

    @Test
    public void rejectsMalformedOrExpiredServerExpiry() throws Exception {
        expectRejected(
                successfulResponse(validToken(), "2030-99-99 03:04:05", SERVER_TIME),
                "invalid expiry");
        expectRejected(
                successfulResponse(validToken(), "2026-01-01 00:00:00", SERVER_TIME),
                "EXPIRED KEY");
    }

    @Test
    public void surfacesCheckerRejectionReason() throws Exception {
        JSONObject response = new JSONObject();
        response.put("status", false);
        response.put("reason", "MAX DEVICE REACHED");

        expectRejected(response.toString(), "MAX DEVICE REACHED");
    }

    @Test
    public void validatesLegacyPanelKeyAlphabetWithoutChangingCase() {
        assertTrue(HostedLicenseClient.isSupportedActivationKey("Key_Name-1234"));
        assertFalse(HostedLicenseClient.isSupportedActivationKey("bad key"));
        assertFalse(HostedLicenseClient.isSupportedActivationKey("abc"));
        assertEquals("Mixed_Case-Key", HostedLicenseClient.normalizeActivationKey(
                "  Mixed_Case-Key  "));
    }

    private static String successfulResponse(String token, String expiry, long serverTime)
            throws Exception {
        JSONObject data = new JSONObject();
        data.put("token", token);
        data.put("rng", serverTime);
        data.put("expired_date", expiry);
        JSONObject response = new JSONObject();
        response.put("status", true);
        response.put("data", data);
        return response.toString();
    }

    private static String validToken() throws Exception {
        return HostedLicenseClient.md5Hex(
                HostedLicenseClient.GAME_ID + "-" + KEY + "-" + SERIAL + "-" + SECRET);
    }

    private static void expectRejected(String response, String message) throws Exception {
        expectRejected(response, message, SERVER_TIME);
    }

    private static void expectRejected(String response, String message, long receivedAt)
            throws Exception {
        try {
            HostedLicenseClient.parseResponse(
                    response, KEY, SERIAL, SECRET, receivedAt);
            fail("Expected the license response to be rejected");
        } catch (HostedLicenseClient.LicenseRejectedException exception) {
            assertTrue(exception.getMessage(), exception.getMessage().contains(message));
        }
    }
}
