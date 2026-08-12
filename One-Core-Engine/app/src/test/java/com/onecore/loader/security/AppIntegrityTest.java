package com.onecore.loader.security;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class AppIntegrityTest {
    @Test
    public void decodeHexAcceptsNormalizedSha256Digest() {
        String digest = "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:"
                + "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF";

        byte[] expected = new byte[] {
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
                (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
                (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
                (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF
        };

        assertArrayEquals(expected, AppIntegrity.decodeHex(digest));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeHexRejectsInvalidDigest() {
        AppIntegrity.decodeHex("not-a-sha256-digest");
    }
}
