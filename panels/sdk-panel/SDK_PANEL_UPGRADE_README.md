# Parallax SDK Panel - secure API v3

This panel and `BLACK-OPNE/RIYAZ-VIP` now share one fail-closed activation
contract. API v3 does not place a server secret in the APK.

## Security model

- HTTPS is mandatory and the SDK accepts only
  `https://parallaxloadersdk.parallaxserver.online/connect.php`.
- Each activation uses a fresh P-256 ECDH key and AES-256-GCM request key.
- The panel signs every encrypted response with a separate P-256 key. A swapped
  panel cannot forge an accepted response.
- Android Keystore creates a per-install proof key. The panel binds that public
  key to the device on first successful activation.
- Package verification is configured per license as `SPECIFIC` or `ANY`.
- APK signing verification is configured per license as `SPECIFIC`, `AUTO` (first secure activation), or `ANY`.
- Device binding is configured per license as disabled, single, limited, or unlimited.
- Feature flags, SDK version policy, kill switch and 10–30 minute sessions are server-controlled.
- A 60–120 second request window, single-use 192-bit nonces and independent
  request IDs block replays.
- Successful responses carry a signed 10–30 minute session and activation
  lease. Missing, expired, malformed, or unverifiable state fails closed.
- Package lock, device limit, device block, force logout, app-signature lock,
  rate limits and audit logs are all enforced on the server.
- Panel accounts support TOTP MFA and one-time recovery codes. TOTP secrets are
  encrypted with a separate server-only data key.
- Browser sessions use Secure/HttpOnly/SameSite cookies, idle and absolute
  expiry, periodic ID rotation, CSRF checks, HSTS and restrictive headers.

No client-side SDK can be made literally uncrackable: an attacker controlling a
device can patch application code. V3 removes the reusable API secret and makes
server responses cryptographically unforgeable without the private panel keys.
Security decisions no longer depend on `strlen` or another hookable C helper.

## Required deployment order

1. Back up the panel files and database.
2. Import `secure_api_migration.sql` into the existing SDK database. For a new
   database, import `database/fresh-install.sql` instead.
3. Generate keys outside `public_html`:

   ```text
   php tools/generate-api-v3-keys.php /home/ACCOUNT/private parallax-2026-09
   ```

4. Put the printed `PANEL_DATA_KEY` and `API_V3_KEYS` entries in the private
   `sdk-panel-config.php`. Never commit or upload private keys to a public path.
5. Confirm the public values printed by the tool match the four `SDK_PANEL_*`
   BuildConfig values in `BLACK-OPNE/RIYAZ-VIP/build.gradle`.
6. Upload the panel code, then run `php tools/check-schema.php`.
7. Sign in, open Settings, and enable MFA. Save the recovery codes offline.
8. Open `security_dashboard.php` and confirm API v3 and signing keys are ready.
9. Build and release the new SDK. Test activation with a disposable license.
10. Keep `LEGACY_API_ENABLED` and `API_V2_ENABLED` false. Enable them only for a
    short, controlled migration; they do not provide v3 panel-swap protection.

The panel must be deployed before distributing the v3 SDK. Old SDKs cannot
understand signed v3 envelopes.

## Key rotation

`API_V3_KEYS` is keyed by `key_id`, so the panel can hold old and new private
keys during a rollout. Add a new server key entry first, update the SDK public
keys and key id, release the SDK, then remove the retired server key after the
old client population has expired.

## License and device recovery

- An `AUTO` license's first successful v3 activation binds its signing-certificate
  fingerprint.
- For bound device modes, a device's first successful v3 activation binds its
  Android Keystore public key.
- Reinstalling an app may delete the Keystore key. An owner must remove the old
  device record before rebinding. Do not silently overwrite a different key.
- Signing-certificate rotation requires an owner to clear or replace the bound
  certificate fingerprint after verifying the new release.

## Verification

Run the PHP crypto and MFA vectors:

```text
php panels/sdk-panel/tests/security-self-test.php
```

Run the Android checks from `BLACK-OPNE`:

```text
./gradlew :ParallaxCore:testDebugUnitTest :ParallaxCore:assembleRelease
```

The panel requires PHP 8.1+ with `mysqli`, `openssl`, `json` and `mbstring`.
