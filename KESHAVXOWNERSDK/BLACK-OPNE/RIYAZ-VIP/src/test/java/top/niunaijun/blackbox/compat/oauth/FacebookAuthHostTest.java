package top.niunaijun.blackbox.compat.oauth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FacebookAuthHostTest {
    @Test
    public void acceptsOnlyFacebookHosts() {
        assertTrue(FacebookAuthHost.matches("facebook.com"));
        assertTrue(FacebookAuthHost.matches("WWW.FACEBOOK.COM"));
        assertTrue(FacebookAuthHost.matches("login.facebook.com"));

        assertFalse(FacebookAuthHost.matches((String) null));
        assertFalse(FacebookAuthHost.matches("notfacebook.com"));
        assertFalse(FacebookAuthHost.matches("facebook.com.example.org"));
        assertFalse(FacebookAuthHost.matches("example.org"));
    }
}
