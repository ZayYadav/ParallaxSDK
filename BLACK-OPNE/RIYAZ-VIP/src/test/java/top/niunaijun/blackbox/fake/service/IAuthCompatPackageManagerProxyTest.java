package top.niunaijun.blackbox.fake.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IAuthCompatPackageManagerProxyTest {

    @Test
    public void acceptsOnlyTheRealLegacyTwitterKitSsoContract() {
        assertTrue(IAuthCompatPackageManagerProxy.isWireCompatibleTwitterSsoClass(
                "com.twitter.android.SingleSignOnActivity"));

        assertFalse(IAuthCompatPackageManagerProxy.isWireCompatibleTwitterSsoClass(
                "com.twitter.android.AuthorizeAppActivity"));
        assertFalse(IAuthCompatPackageManagerProxy.isWireCompatibleTwitterSsoClass(
                "com.twitter.app.authorizeapp.AppAuthorizationActivity"));
        assertFalse(IAuthCompatPackageManagerProxy.isWireCompatibleTwitterSsoClass(
                "com.x.android.deeplink.XUrlInterpreterActivity"));
    }
}
