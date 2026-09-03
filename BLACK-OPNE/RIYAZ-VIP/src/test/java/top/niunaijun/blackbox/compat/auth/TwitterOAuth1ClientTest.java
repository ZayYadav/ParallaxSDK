package top.niunaijun.blackbox.compat.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class TwitterOAuth1ClientTest {

    @Test
    public void percentEncodeUsesOAuthRfc3986Rules() {
        assertEquals("a%20b%2Ac~d%2Fe", TwitterOAuth1Client.percentEncode("a b*c~d/e"));
    }

    @Test
    public void requestTokenHeaderHasDeterministicHmacSha1Signature() throws Exception {
        String header = TwitterOAuth1Client.createAuthorizationHeader(
                "POST",
                "https://api.twitter.com/oauth/request_token",
                "abc",
                "secret",
                null,
                null,
                "twittersdk://callback?version=3.3.0&app=abc",
                "nonce-1",
                "1700000000");

        assertTrue(header.startsWith("OAuth "));
        assertTrue(header.contains("oauth_consumer_key=\"abc\""));
        assertTrue(header.contains("oauth_signature=\"LNdHw6ceOVpG3fsrpAVXO6ajhz0%3D\""));
        assertTrue(header.contains("oauth_callback=\"twittersdk%3A%2F%2Fcallback%3Fversion%3D3.3.0%26app%3Dabc\""));
    }

    @Test
    public void parseFormDecodesTokenFieldsWithoutLoggingThem() throws Exception {
        Map<String, String> values = TwitterOAuth1Client.parseForm(
                "oauth_token=a%2Fb&oauth_token_secret=s%2Bt&screen_name=one%20core&user_id=42");

        assertEquals("a/b", values.get("oauth_token"));
        assertEquals("s+t", values.get("oauth_token_secret"));
        assertEquals("one core", values.get("screen_name"));
        assertEquals("42", values.get("user_id"));
    }
}
