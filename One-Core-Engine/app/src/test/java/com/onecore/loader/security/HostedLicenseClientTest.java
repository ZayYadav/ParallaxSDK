package com.onecore.loader.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

public class HostedLicenseClientTest {
    private static final String NONCE = "abcdefghijklmnopqrstuv";
    private static final String CANARY = "zyxwvutsrqponmlkjihgfe";
    private static final String RECEIPT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef";
    private static final long SERVER_TIME = 1_800_000_000L;

    @Test
    public void encryptedCheckerIdentityIsPinnedInTheLoader() {
        assertEquals("https://parallaxloader.parallaxserver.online/api/v2/connect",
                HostedLicenseClient.CONNECT_URL);
        assertEquals("parallaxloader.parallaxserver.online", HostedLicenseClient.CONNECT_HOST);
        assertEquals("PUBG", HostedLicenseClient.GAME_ID);
    }

    @Test
    public void acceptsFreshResponseBoundToNonceAndCanary() throws Exception {
        HostedLicenseClient.ParsedLicense license = HostedLicenseClient.parseDecryptedResponse(
                successfulResponse("2030-01-02 03:04:05", SERVER_TIME),
                NONCE,
                CANARY,
                SERVER_TIME);
        assertEquals(RECEIPT, license.receipt);
        assertEquals(1_893_553_445L, license.expiresAt);
        assertEquals(SERVER_TIME, license.serverTime);
    }

    @Test
    public void rejectsResponseWithWrongCanaryOrNonce() throws Exception {
        expectRejected(successfulResponse("2030-01-02 03:04:05", SERVER_TIME),
                "canary validation failed", "wrong_nonce", CANARY, SERVER_TIME);
        expectRejected(successfulResponse("2030-01-02 03:04:05", SERVER_TIME),
                "canary validation failed", NONCE, "wrong_canary", SERVER_TIME);
    }

    @Test
    public void rejectsStaleResponseTimestamp() throws Exception {
        expectRejected(successfulResponse("2030-01-02 03:04:05", SERVER_TIME),
                "timestamp validation failed", NONCE, CANARY, SERVER_TIME + 61L);
    }

    @Test
    public void rejectsMalformedOrExpiredServerExpiry() throws Exception {
        expectRejected(successfulResponse("2030-99-99 03:04:05", SERVER_TIME),
                "invalid expiry", NONCE, CANARY, SERVER_TIME);
        expectRejected(successfulResponse("2026-01-01 00:00:00", SERVER_TIME),
                "EXPIRED KEY", NONCE, CANARY, SERVER_TIME);
    }

    @Test
    public void surfacesEncryptedCheckerRejectionReason() throws Exception {
        JSONObject response = bindingFields();
        response.put("status", false);
        response.put("reason", "MAX DEVICE REACHED");
        expectRejected(response, "MAX DEVICE REACHED", NONCE, CANARY, SERVER_TIME);
    }

    @Test
    public void validatesPanelKeyAlphabetWithoutChangingCase() {
        assertTrue(HostedLicenseClient.isSupportedActivationKey("Key_Name-1234"));
        assertTrue(HostedLicenseClient.isSupportedActivationKey(
                "A".repeat(64)));
        assertFalse(HostedLicenseClient.isSupportedActivationKey(
                "A".repeat(65)));
        assertFalse(HostedLicenseClient.isSupportedActivationKey("bad key"));
        assertFalse(HostedLicenseClient.isSupportedActivationKey("abc"));
        assertEquals("Mixed_Case-Key", HostedLicenseClient.normalizeActivationKey(
                "  Mixed_Case-Key  "));
    }

    private static JSONObject successfulResponse(String expiry, long serverTime)
            throws Exception {
        JSONObject data = new JSONObject();
        data.put("expired_date", expiry);
        JSONObject response = bindingFields();
        response.put("status", true);
        response.put("server_time", serverTime);
        response.put("receipt", RECEIPT);
        response.put("data", data);
        return response;
    }

    private static JSONObject bindingFields() throws Exception {
        JSONObject response = new JSONObject();
        response.put("request_nonce", NONCE);
        response.put("canary", CANARY);
        return response;
    }

    private static void expectRejected(
            JSONObject response,
            String message,
            String nonce,
            String canary,
            long receivedAt) throws Exception {
        try {
            HostedLicenseClient.parseDecryptedResponse(response, nonce, canary, receivedAt);
            fail("Expected the license response to be rejected");
        } catch (HostedLicenseClient.LicenseRejectedException exception) {
            assertTrue(exception.getMessage(), exception.getMessage().contains(message));
        }
    }
}
