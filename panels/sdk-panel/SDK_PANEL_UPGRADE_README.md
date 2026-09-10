# Parallax SDK Panel - secure API v3 + UI v3

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
- Browser panel POSTs also validate request origin/fetch metadata and reject
  oversized requests before form handlers run.
- Apache deployments deny direct access to `.env`, local config, private keys,
  SQL dumps, logs and Markdown upgrade notes.

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

4. Copy `.env.example` to `.env` on the server and fill database, v2/v3, session
   and Telegram settings. Existing private `sdk-panel-config.php` files still
   work, and `.env` values override them.
5. Confirm the public key id and public keys printed by the tool match the three
   `SDK_PANEL_*` BuildConfig trust anchors in `BLACK-OPNE/RIYAZ-VIP/build.gradle`.
   The exact HTTPS endpoint is masked in native code and independently
   integrity-checked by the Java/Kotlin activation client.
6. Upload the panel code, then run `php tools/check-schema.php`.
7. Sign in, open Settings, and enable MFA. Save the recovery codes offline.
8. Open `/security` or `security_dashboard.php` and confirm API v3 and signing
   keys are ready.
9. Build and release the new SDK. Test activation with a disposable license.
10. Keep `LEGACY_API_ENABLED` and `API_V2_ENABLED` false. Enable them only for a
    short, controlled migration; they do not provide v3 panel-swap protection.

The panel must be deployed before distributing the v3 SDK. Old SDKs cannot
understand signed v3 envelopes.

## Folder routing

The old `.php` files remain valid for SDK and admin compatibility. New installs
can use the folder routes below when Apache rewrite is enabled:

| Folder route | Legacy file |
|---|---|
| `/dashboard` | `dashboard.php` |
| `/licenses` | `license_list.php` |
| `/licenses/generate` | `generate_ui.php` |
| `/licenses/check` | `check_license.php` |
| `/security` | `security_dashboard.php` |
| `/users` | `manage_users.php` |
| `/referrals` | `manage_referrals.php` |
| `/server` | `online_server.php` |
| `/appearance` | `panel_customizer.php` |
| `/api/connect` and `/connect` | `connect.php` |
| `/telegram/webhook` | `telegram_bot.php` |

Do not remove `connect.php` unless every shipped SDK has been updated to the
new path. The request and response contract inside `connect.php` is unchanged.

## UI v3

The new visual system is injected from `panel_css_vars()` so every existing page
receives the same modern shell without rewriting business logic. It upgrades
buttons, tables, cards, dropdowns, dialogs, loading states, sidebar behavior,
mobile layouts and reduced-motion handling while keeping form names, POST
targets and endpoint payloads intact.

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
