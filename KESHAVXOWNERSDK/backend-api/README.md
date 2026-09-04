# OneCore Hosted Licensing API

Production-oriented PHP 8 + MySQL backend for the `com.onecore.loader` Android
app. Its default `self_hosted` mode needs no Play Console account: the dashboard
creates activation keys, each install binds a non-exportable Android Keystore
public key, and the server issues short-lived device-bound JWT licenses. Google
Play Integrity remains available as an optional policy mode.

## Security model

- HTTPS is mandatory in production.
- Request and response bodies use an additional AES-256-GCM authenticated
  envelope with a fresh 96-bit IV and the AAD value `onecore-api-v1`.
- Every encrypted request contains a Unix `timestamp` and unique base64url
  `nonce`. The API enforces a five-minute window and stores the nonce hash in
  MySQL to reject replays.
- Standard Play Integrity tokens are bound to the action using:

  ```text
  base64url(SHA-256(device_id + "\n" + nonce + "\n" + timestamp))
  ```

  The Android app must supply this value to `setRequestHash()`.
- The server requires `PLAY_RECOGNIZED`, the configured Play App Signing
  SHA-256 certificate, a configurable device verdict, and (by default) the
  `LICENSED` account verdict.
- JWTs are short lived, device bound, and stored only as SHA-256 hashes in the
  database. `/license/validate` checks the signature, current token hash,
  device state, JTI, and expiry.
- Admin REST routes require the constant-time checked `X-API-Key` header. The
  dashboard uses the same key to create a secure, CSRF-protected session.

In self-hosted mode, activation keys are stored only as SHA-256 hashes. Each key
has an expiry and device limit. Revoking a key also revokes its bound devices.
Each installation creates an EC P-256 Android Keystore key and signs its request,
so an exported JWT cannot simply be moved to another phone.

The AES key is a shared app secret and can ultimately be recovered from a
client APK. It is defense in depth, not a replacement for TLS or Play Integrity.
Keep entitlement decisions, revocations, and valuable secrets on this server.
Self-hosted app metadata can be patched by a determined attacker, so this mode
does not claim to be equivalent to Google-signed Play app recognition.

## Requirements

- PHP 8.0+ with `curl`, `json`, `openssl`, `pdo`, and `pdo_mysql`
- MySQL 8.0+ (or a compatible MariaDB version with JSON support)
- Composer 2
- Apache with `mod_rewrite` and `mod_headers`, or equivalent Nginx rules
- A valid HTTPS certificate

## Files

- `install.sql` - complete schema for a fresh database
- `migrate_self_hosted.sql` - one-time self-hosted licensing upgrade
- `migrate_rbac.sql` - one-time owner/admin/user and balance upgrade
- `config.php` - environment/local configuration loader
- `Database.php` - strict PDO wrapper and DB-backed replay/rate limiting
- `CryptoHelper.php` - AES-256-GCM envelopes and freshness validation
- `JWTHelper.php` - HS256 license issue/verification
- `IntegrityVerifier.php` - service-account OAuth and Google token decoding
- `SelfHostedVerifier.php` - device public-key proof and release-signing checks
- `AccountManager.php` - role authorization, referrals, balances, and key ownership
- `index.php` - encrypted REST router
- `admin_dashboard.php` - session/CSRF-protected admin UI
- `generate_secrets.php` - CLI-only cryptographic secret generator
- `example_client.php` - CLI envelope/request-hash test helper
- `.htaccess` - URL rewriting and sensitive file protection

## 1. Install PHP dependencies

Place this complete folder at `public_html/api/`, then run inside it:

```bash
composer install --no-dev --optimize-autoloader
```

If shared hosting has no SSH/Composer, run this command locally and upload the
generated `vendor/` folder with the PHP files.

## 2. Create the database

Import `install.sql` from phpMyAdmin, or run:

```bash
mysql -u root -p onecore_integrity < install.sql
```

Create a dedicated least-privilege MySQL user for this database. Do not use the
hosting root account in `config.local.php`.

For a database that was already created from an older ZIP, select that database
in phpMyAdmin and import `migrate_self_hosted.sql`, then `migrate_rbac.sql`, once
each. Do not re-import `install.sql` over a live deployment.

## 3. Generate secrets

Run this once:

```bash
php generate_secrets.php
```

It prints three independent random values:

- `ENCRYPTION_KEY`: base64 of exactly 32 random bytes
- `JWT_SECRET`: base64 of 64 random bytes
- `ADMIN_API_KEY`: 64-character random hex value

Copy `config.local.example.php` to `config.local.php`, paste these generated
values, and fill the database values. `config.local.php` is ignored by Git.
Alternatively configure the same names as hosting environment variables.

Do not keep example/AI-generated shared secrets in production. Generate them on
your own deployment and never commit or send the admin/JWT/service-account keys
to the Android client. Only the AES envelope key is needed by the Android app.

## 4. Configure self-hosted mode (no Play Console)

Self-hosted mode is the default. It does not need a Google project, service
account, D-U-N-S number, or Play Console fee.

1. Build the APK with the same `ENCRYPTION_KEY` used by this server. Configure
   the GitHub Actions secret `ONECORE_API_ENCRYPTION_KEY`, or pass the Gradle
   property `-PonecoreApiEncryptionKey=...`. Never commit this value.
2. Configure a persistent release keystore in GitHub Actions secrets:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   and `ANDROID_KEY_PASSWORD`. The CI fallback key is for testing only and
   changes on a fresh runner.
3. Build the signed release APK and obtain its signing SHA-256. Gradle derives
   the same fingerprint into `BuildConfig.EXPECTED_SIGNATURE_SHA256`.
4. Open `admin_dashboard.php`. On first run, prove server control with
   `ADMIN_API_KEY` and create the initial owner username/password.
5. Enter the release SHA-256 under **Signing certificate**, and keep
   **Verification mode** set to **Self-hosted**.
6. Create a unique `5OC-…` activation key. The plaintext key is shown once;
   only its SHA-256 hash remains in MySQL.
7. Enter that activation key in the Android loader. The dashboard will show the
   bound device, request result, IP address, token expiry, and revocation state.

The Android API URL is already configured as:

```text
https://onintigirty.parallaxserver.online
```

## Dashboard roles, referrals, and balances

- **Owner** has full policy, account, referral, balance, key, and device control.
  Owner key generation does not consume balance.
- **Admin** can monitor activity and manage keys, devices, certificates, and
  policy, but cannot create referrals or change account balances.
- **User** can generate, view, and revoke only their own activation keys.
- Registration is closed by default. An owner creates an expiring referral URL
  with an assigned role, starting balance, maximum uses, and expiry.
- Each non-owner key generation atomically debits `key_cost_credits`. Only an
  owner can add/remove credits, and every balance change is recorded.
- Owners may use a 2-8 character alphanumeric branded key prefix. Keys remain
  independently random and are stored only as SHA-256 hashes.

## 5. Configure Google Play Integrity (optional)

1. In Google Cloud Console, create or select a project, open **APIs & Services**,
   search for **Play Integrity API**, and enable it.
2. In Play Console select the OneCore app, open **Protected with Play -> Play
   Integrity API**, and link that Cloud project.
3. Copy the numeric **project number** (not project ID) into
   `GOOGLE_CLOUD_PROJECT_NUMBER`. The Android token provider also uses this
   numeric project number.
4. Create a dedicated service account in that project and download a JSON key.
   Store it outside `public_html` with restrictive permissions, then set its
   absolute path as `SERVICE_ACCOUNT_JSON_PATH`. If the host provides Google
   workload identity, prefer that over a long-lived key; this implementation
   also accepts JSON through `GOOGLE_SERVICE_ACCOUNT_JSON`.
5. In **Play Console -> App integrity -> App signing**, copy the **SHA-256
   certificate fingerprint** of the Play App Signing certificate. Replace the
   `allowed_cert_fingerprint` value through the dashboard or
   `PUT /admin/whitelist/cert`.

Google setup references:

- https://developer.android.com/google/play/integrity/setup
- https://developer.android.com/google/play/integrity/standard
- https://cloud.google.com/iam/docs/keys-create-delete

`PLAY_RECOGNIZED` and `LICENSED` are normally available for builds installed by
Google Play. Sideloaded debug APKs will be rejected by the production policy.

## 6. Configure deployment

Example `config.local.php` fields:

```php
<?php
return [
    'DB_HOST' => 'localhost',
    'DB_PORT' => 3306,
    'DB_NAME' => 'onecore_integrity',
    'DB_USER' => 'onecore_api',
    'DB_PASSWORD' => 'your-private-db-password',
    'ENCRYPTION_KEY' => 'output-from-generate-secrets',
    'JWT_SECRET' => 'different-output-from-generate-secrets',
    'ADMIN_API_KEY' => 'admin-output-from-generate-secrets',
    'GOOGLE_CLOUD_PROJECT_NUMBER' => '123456789012',
    'SERVICE_ACCOUNT_JSON_PATH' => '/home/account/private/play-integrity.json',
    'APP_PACKAGE_NAME' => 'com.onecore.loader',
    'API_BASE_URL' => 'https://api.example.com/api',
    'REQUIRE_HTTPS' => true,
    'TRUST_PROXY' => false,
    'CORS_ALLOWED_ORIGINS' => [],
];
```

Set `TRUST_PROXY=true` only when a trusted reverse proxy overwrites
`X-Forwarded-Proto` and `X-Forwarded-For`. Android does not require CORS. Leave
the origin list empty unless a separate web frontend calls the REST API.

For Nginx, deny access to `config*.php`, `install.sql`, `composer.*`, and the
`runtime/` directory, then route missing paths to `index.php`.

## Encrypted envelope

Every POST/PUT body has this wire format:

```json
{
  "version": 1,
  "iv": "base64-12-byte-IV",
  "tag": "base64-16-byte-GCM-tag",
  "ciphertext": "base64-ciphertext"
}
```

The decrypted plaintext is a JSON object. Every encrypted response uses the
same envelope with a fresh IV. Even error responses are encrypted after the
server has loaded its key. Bootstrap errors such as missing Composer packages
or invalid server secrets are intentionally generic plaintext errors.

## Public endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/verify` | Bind activation/device proof and issue a license |
| `POST` | `/license/validate` | Confirm a license remains current and active |

Decrypted `/verify` payload:

```json
{
  "activation_key": "5OC-....",
  "device_id": "base64url-sha256-of-device-public-key",
  "timestamp": 1786500000,
  "nonce": "4eG5B9R2uLh2M4HPA8zqgQ",
  "app_package_name": "com.onecore.loader",
  "app_certificate_sha256": "64_HEX_CHARACTERS",
  "app_version_code": 15,
  "device_public_key": "BASE64_X509_EC_PUBLIC_KEY",
  "device_proof": "BASE64_ECDSA_SIGNATURE"
}
```

Successful decrypted response:

```json
{
  "status": "success",
  "license_token": "eyJ...",
  "expires_in": 600,
  "expires_at": 1786500601,
  "verification_mode": "self_hosted",
  "rate_limit_remaining": 9,
  "server_timestamp": 1786500001,
  "request_nonce": "4eG5B9R2uLh2M4HPA8zqgQ"
}
```

The bundled Android client computes `device_id` from its per-install Android
Keystore public key. It does not use IMEI, serial number, or another restricted
hardware identifier.

## Admin endpoints

| Method | Path | Decrypted input |
| --- | --- | --- |
| `POST` | `/admin/revoke` | `device_id`, `reason`, `timestamp`, `nonce` |
| `PUT` | `/admin/policy` | policy fields, `timestamp`, `nonce` |
| `GET` | `/admin/metrics` | no request body; response is encrypted |
| `PUT` | `/admin/whitelist/cert` | `new_fingerprint`, `timestamp`, `nonce` |

All require:

```http
X-API-Key: YOUR_ADMIN_API_KEY
```

Policy fields supported without redeployment:

- `verification_mode`: `self_hosted` or `play_integrity`
- `token_expiry_seconds`: 60-86400
- `rate_limit_per_minute`: 1-600
- `required_device_verdict`: `MEETS_BASIC_INTEGRITY`,
  `MEETS_DEVICE_INTEGRITY`, or `MEETS_STRONG_INTEGRITY`
- `require_licensed`: boolean

Dashboard: `https://onintigirty.parallaxserver.online/admin_dashboard.php`

## Curl test workflow

The Android app constructs the signed self-hosted payload automatically. The
following envelope workflow remains useful for diagnostics after creating a
valid signed payload:

```bash
php example_client.php encrypt verify-payload.json > verify-envelope.json
```

Send it:

```bash
curl --fail-with-body \
  -X POST 'https://your-host.example/api/verify' \
  -H 'Content-Type: application/json' \
  --data-binary @verify-envelope.json \
  --output response-envelope.json
```

Decrypt the response:

```bash
php example_client.php decrypt response-envelope.json
```

Admin metrics (encrypted response, API key required):

```bash
curl --fail-with-body \
  'https://your-host.example/api/admin/metrics' \
  -H 'X-API-Key: YOUR_ADMIN_API_KEY' \
  --output metrics-envelope.json

php example_client.php decrypt metrics-envelope.json
```

For revoke/policy/certificate calls, create a JSON payload including a current
`timestamp` and new `nonce`, encrypt it with `example_client.php`, then use
`curl -X POST` or `curl -X PUT` with `Content-Type: application/json`,
`X-API-Key`, and `--data-binary @envelope.json`.

## Android integration contract

`HostedLicenseClient` is included in the loader and performs the complete flow:

1. Create or load an EC P-256 signing key from Android Keystore.
2. Derive the device ID from SHA-256 of its X.509 public key.
3. Bind the activation key, timestamp, nonce, and device identity with ECDSA.
4. Send the signed metadata in an AES-256-GCM envelope over verified HTTPS.
5. Verify the echoed nonce and store the short-lived JWT and expiry using
   Android Keystore-backed `SecurePreferences`.

The release build must receive the server's `ENCRYPTION_KEY` as the Gradle
property `onecoreApiEncryptionKey` or environment variable
`ONECORE_API_ENCRYPTION_KEY`. The API base URL is already set to the deployed
shared-hosting domain and can be overridden with `onecoreApiBaseUrl`.

## Operations

- Rotate `ADMIN_API_KEY` immediately if exposed.
- Rotating `JWT_SECRET` invalidates all existing licenses.
- Rotating `ENCRYPTION_KEY` requires an Android client update unless you add a
  key-version migration window.
- If optional Play mode is used, keep service-account JSON outside the web root
  and rotate it periodically.
- Back up MySQL, monitor blocked verdicts, and prune old integrity logs according
  to your retention policy.
- Use the admin dashboard/API to revoke a device immediately. Offline clients can
  remain active only until their short JWT expiry; keep expiry low for rapid
  revocation.
