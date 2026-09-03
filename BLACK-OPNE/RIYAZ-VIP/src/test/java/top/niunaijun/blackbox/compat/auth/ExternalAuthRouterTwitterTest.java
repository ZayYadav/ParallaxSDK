package top.niunaijun.blackbox.compat.auth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalAuthRouterTwitterTest {
    @Test
    public void acceptsInteractiveOAuthOneEndpoints() {
        assertTrue(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/oauth/authenticate", "oauth_token=request-token"));
        assertTrue(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/oauth/authorize/", "oauth_token=request-token"));
    }

    @Test
    public void acceptsInteractiveOAuthTwoEndpoints() {
        assertTrue(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/i/oauth2/authorize", "client_id=game-client&state=state-value"));
    }

    @Test
    public void rejectsTokenApiEndpointsThatCauseNativeError9999() {
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/oauth/request_token", "oauth_token=request-token"));
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/oauth/access_token", "oauth_token=request-token"));
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/2/oauth2/token", "client_id=game-client"));
    }

    @Test
    public void rejectsNonInteractiveTwitterLinksAndIncompleteAuthorizationUrls() {
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/home", "oauth_token=request-token"));
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(
                "/oauth/authenticate", "state=state-value"));
        assertFalse(ExternalAuthRouter.isInteractiveTwitterOAuthRequest(null, null));
    }
}
