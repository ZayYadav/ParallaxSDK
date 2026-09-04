package top.niunaijun.blackbox.utils.compat;

import android.Manifest;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VirtualPermissionCompatTest {
    private static final String VIRTUAL_PACKAGE = "com.pubg.imobile";

    @Test
    public void acceptsOnlyNormalNetworkPermissionsForVirtualPackage() {
        assertTrue(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.INTERNET, VIRTUAL_PACKAGE, VIRTUAL_PACKAGE));
        assertTrue(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.ACCESS_NETWORK_STATE, null, VIRTUAL_PACKAGE));
        assertTrue(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.ACCESS_WIFI_STATE, VIRTUAL_PACKAGE, VIRTUAL_PACKAGE));

        assertFalse(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.CAMERA, VIRTUAL_PACKAGE, VIRTUAL_PACKAGE));
        assertFalse(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.INTERNET, "other.package", VIRTUAL_PACKAGE));
        assertFalse(VirtualPermissionCompat.isEligibleRequest(
                Manifest.permission.INTERNET, VIRTUAL_PACKAGE, null));
    }

    @Test
    public void requiresPermissionToBeDeclaredByGuest() {
        String[] declared = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
        };

        assertTrue(VirtualPermissionCompat.declaresPermission(
                declared, Manifest.permission.INTERNET));
        assertFalse(VirtualPermissionCompat.declaresPermission(
                declared, Manifest.permission.ACCESS_WIFI_STATE));
        assertFalse(VirtualPermissionCompat.declaresPermission(
                null, Manifest.permission.INTERNET));
    }
}
