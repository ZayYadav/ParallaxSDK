# Parallax SDK Panel - Secure v2 Upgrade

This package is a full, sanitized replacement for the supplied standalone PHP
panel. It preserves the existing panel features and the legacy SDK response
fields while adding an encrypted API v2 and fixing the highest-risk panel and
license-management defects.

## Main upgrades

- AES-256-GCM encrypted activation requests **and responses** (`connect.php`).
- Authenticated encryption uses a 12-byte random IV, 16-byte tag and fixed AAD
  `sdk-panel-v2`.
- Five-minute timestamp window and single-use 192-bit nonce stop replay attacks.
- Database-backed IP rate limiting and a searchable activation audit trail.
- Package allowlist, per-license package lock, global blocked-app check, device
  block, force logout, expiry and maximum-device enforcement.
- Backward-compatible legacy form mode during migration; it can be disabled.
- New owner/admin `security_dashboard.php` showing API health and recent events.
- License usage now comes from the actual `devices` table rather than the
  unrelated `activated_packages` table.
- Device view is scoped by license ID/key, fixing mixed counts when several
  licenses use the same package.
- Cryptographically strong 96-bit-random license keys and configurable device
  limits on the generation page.
- Central POST CSRF protection, hardened session cookies, idle timeout, browser
  security headers and removal of the insecure username login cookie bypass.
- Prepared statements for authentication, registration and critical mutations.
- Registration referral claiming is transactional, preventing double use.
- State changes formerly performed by GET are now POST-only.
- Hardcoded database and Telegram credentials have been removed.
- Telegram webhook requests require Telegram's secret-token header.
- Mobile blocking was removed; the responsive panel works on phones and PCs.
- Duplicate license inventory page now redirects to the secured canonical view.

## Deployment order

### Fresh installation

For a new/empty SDK panel database, do not import only
`secure_api_migration.sql`. In phpMyAdmin select the database configured in the
private SDK panel config and import:

```text
database/fresh-install.sql
```

Then create the first owner from cPanel Terminal:

```bash
php tools/check-schema.php
php tools/create-owner.php YOUR_OWNER_USERNAME
```

The tool asks for a 12+ character password without storing a default password
in SQL or source control. If the panel returns `SERVER_DATABASE_SCHEMA_ERROR`,
check the PHP error log for the exact missing table/column and confirm that
`DB_NAME` points to the SDK panel database rather than the separate key-panel
database.

### Existing installation upgrade

1. Back up the current website and database.
2. In phpMyAdmin, select the SDK panel database and import
   `secure_api_migration.sql` once. The statements preserve existing data.
3. Copy `config.local.example.php` to this recommended private path:

   `/home/YOUR_CPANEL_ACCOUNT/private/sdk-panel-config.php`

   The `private` directory must be outside the public website document root.
   Fill the current database values and new security settings there.
4. Generate the encryption key in cPanel Terminal:

   ```bash
   php -r "echo base64_encode(random_bytes(32)), PHP_EOL;"
   ```

   Paste the result into `ENCRYPTION_KEY` in the private config. Do not paste it
   into SQL, JavaScript, a public PHP file, GitHub, screenshots, or chat.
5. Upload this package over the current SDK panel directory. The package does
   not contain a live `config.local.php` and cannot overwrite the private file.
6. Ensure PHP 8.1+ has `mysqli`, `openssl`, `json` and `mbstring` enabled.
7. Sign in as owner and open `security_dashboard.php`. It should show
   `AES-256-GCM READY`.
8. Create a test license with one device, activate the current SDK in legacy
   mode, and confirm the license/device count changes to `1 / 1`.
9. Integrate `android-client/SecureSdkApiClient.kt` into the Android SDK and use
   the same encryption key through a private build configuration value.
10. Test v2 activation. After every active SDK build has migrated, change:

    ```php
    'LEGACY_API_ENABLED' => false,
    ```

## API v2 contract

Send `POST /connect.php` with:

- `Content-Type: application/json`
- `X-API-Version: 2`
- HTTPS only

Encrypted envelope:

```json
{
  "version": 2,
  "iv": "base64-12-byte-iv",
  "ciphertext": "base64-ciphertext",
  "tag": "base64-16-byte-tag"
}
```

The AES-GCM plaintext object is:

```json
{
  "user_key": "SDK-...",
  "package_name": "com.example.app",
  "app_name": "Example",
  "device_id": "stable-install-id",
  "timestamp": 1786531200,
  "nonce": "base64url-random-24-bytes"
}
```

Every response, including normal license failures after encryption is
initialized, uses the same encrypted envelope. The decrypted response retains
the original fields: `status`, `server_mode`, `expiry`, `toggle_expiry`,
`feature1`, `feature2`, `message`, and optional `server_notification`.

## Android integration

`android-client/SecureSdkApiClient.kt` supports Android API 24+ and returns the
decrypted response as `JSONObject`. Replace the old URL-encoded request in
`RemoteManager.activateSdk()` with `SecureSdkApiClient.activate(...)`, then keep
the existing response handling.

Do not send both plaintext and encrypted activation requests. Keep the legacy
server switch enabled only while old installed builds still exist.

AES with a key embedded in an APK is defense-in-depth, not an unextractable
secret. HTTPS, server-side license/device enforcement, key rotation and short
rollout windows remain necessary.

## Telegram (optional)

Put the rotated token, a random webhook secret and admin chat ID in the private
config. Register the webhook with Telegram using its `secret_token` parameter.
Leave all Telegram values empty to keep the endpoint disabled.

## Security checklist

- Rotate the database password and Telegram bot token found in the old ZIP.
- Keep `REQUIRE_HTTPS` enabled.
- Set `TRUST_PROXY` only when requests really pass through a trusted proxy.
- Remove `hit.txt` and other old exports if they contain operational data.
- Never upload the real private config into the panel directory.
- Test on a staging subdomain before replacing production.
- Review `api_audit_logs` and the security dashboard after rollout.
