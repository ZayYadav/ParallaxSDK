# Twitter/X login root cause — 2026-09-04

## Observed failure

The `TWITTER-.txt` capture shows the following sequence inside the virtual BGMI process:

1. `TwitterWrapper: Twitter initialize success`
2. Legacy Twitter Kit probes `com.twitter.android.SingleSignOnActivity`.
3. The installed current X build does not expose that legacy Activity, so Twitter Kit logs `SSO auth activity not found`.
4. Twitter Kit falls back to its embedded OAuth1 `OAuthActivity`.
5. The request-token exchange reaches X/Twitter and receives HTTP 403 with error code 415:
   `Callback URL not approved for this client application.`
6. Twitter Kit reports `Failed to get request token`, and IMSDK returns `twitter login failure`.

This is not a TLS, DNS, WebView, Activity-result, or loader-network failure. The request reaches the provider successfully and is rejected by the provider's OAuth application policy.

## Current X application capability

The captured/decompiled current `com.twitter.android` manifest exposes:

- `com.twitter.android.platform.TwitterAuthenticationService` as an AccountAuthenticator service.
- `com.x.android.deeplink.XUrlInterpreterActivity` as an exported URL/deep-link handler for `twitter.com`, `x.com`, `twitter:` and `x:` links.

It does **not** expose:

- `com.twitter.android.SingleSignOnActivity`
- `com.twitter.android.AuthorizeAppActivity`

`XUrlInterpreterActivity` cannot safely replace the old Twitter Kit SSO Activity. The legacy contract is a URL-less Activity request carrying `ck`/`cs` and expecting `tk`/`ts` account-token result extras. The modern URL interpreter consumes URLs and does not implement that wire contract.

## SDK change in this branch

`IAuthCompatPackageManagerProxy` now accepts only the real legacy `SingleSignOnActivity` as a Twitter Kit SSO match. It no longer treats removed/assumed successor Activities as wire-compatible. Modern OAuth2 authorization URLs remain handled by the existing `TwitterNativeAuthBridgeActivity`/X URL interpreter path.

## What is required for the legacy BGMI Twitter Kit flow

Because the embedded game SDK is an OAuth1 Twitter Kit client, one of these provider-supported conditions must be true:

1. The X developer application used by that game client must allow the exact OAuth callback URI sent by Twitter Kit, or
2. The game/client SDK must be updated to a currently supported X OAuth flow (for example OAuth 2.0 Authorization Code + PKCE) with an approved redirect URI.

A loader/virtualization SDK cannot legitimately make X accept a callback URI that the provider rejects, and it should not fabricate OAuth tokens, provider signatures, or authentication results.

## Regression guard

The unit test now rejects `AuthorizeAppActivity` and `XUrlInterpreterActivity` as legacy Twitter Kit SSO substitutes while still accepting the genuine `SingleSignOnActivity` contract when an older X/Twitter build actually provides it.
