# OneCore Play Integrity Licensing API

Production-oriented PHP 8 + MySQL backend for the `com.onecore.loader` Android
app. It decodes Google Play Integrity standard/classic tokens on Google's
servers, applies runtime policy, issues short-lived device-bound JWT licenses,
and supports immediate administrative revocation.

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

The AES key is a shared app secret and can ultimately be recovered from a
client APK. It is defense in depth, not a replacement for TLS or Play Integrity.
Keep entitlement decisions, revocations, and valuable secrets on this server.

## Requirements

- PHP 8.0+ with `curl`, `json`, `openssl`, `pdo`, and `pdo_mysql`
- MySQL 8.0+ (or a compatible MariaDB version with JSON support)
- Composer 2
- Apache with `mod_rewrite` and `mod_headers`, or equivalent Nginx rules
- A valid HTTPS certificate

## Files

- `install.sql` - schema, indexes, foreign key, nonce and rate-limit tables
- `config.php` - environment/local configuration loader
- `Database.php` - strict PDO wrapper and DB-backed replay/rate limiting
- `CryptoHelper.php` - AES-256-GCM envelopes and freshness validation
- `JWTHelper.php` - HS256 license issue/verification
- `IntegrityVerifier.php` - service-account OAuth and Google token decoding
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
mysql -u root -p < install.sql
```

Create a dedicated least-privilege MySQL user for this database. Do not use the
hosting root account in `config.local.php`.

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

## 4. Configure Google Play Integrity

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

## 5. Configure deployment

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
| `POST` | `/verify` | Decode integrity token and issue a license |
| `POST` | `/license/validate` | Confirm a license remains current and active |

Decrypted `/verify` payload:

```json
{
  "integrity_token": "TOKEN_FROM_PLAY_INTEGRITY",
  "device_id": "6d7f0bd1-6ca2-4eb9-9db0-ea1ad0214baf",
  "timestamp": 1786500000,
  "nonce": "4eG5B9R2uLh2M4HPA8zqgQ"
}
```

Successful decrypted response:

```json
{
  "status": "success",
  "license_token": "eyJ...",
  "expires_in": 600,
  "rate_limit_remaining": 9,
  "server_timestamp": 1786500001,
  "request_nonce": "4eG5B9R2uLh2M4HPA8zqgQ"
}
```

Use a random installation UUID stored with Android Keystore as `device_id`; do
not use IMEI, serial number, or another restricted hardware identifier.

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

- `token_expiry_seconds`: 60-86400
- `rate_limit_per_minute`: 1-600
- `required_device_verdict`: `MEETS_BASIC_INTEGRITY`,
  `MEETS_DEVICE_INTEGRITY`, or `MEETS_STRONG_INTEGRITY`
- `require_licensed`: boolean

Dashboard: `https://your-host.example/api/admin_dashboard.php`

## Curl test workflow

First create `verify-payload.json` with a current timestamp, unique nonce,
device ID, and a real Play Integrity token. Encrypt it:

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

Before requesting a standard Play Integrity token:

1. Generate `timestamp`, `nonce`, and installation `device_id`.
2. Compute the request hash with the exact formula documented above. You can
   verify a test vector with:

   ```bash
   php example_client.php request-hash DEVICE_ID NONCE TIMESTAMP
   ```

3. Pass that result to `StandardIntegrityTokenRequest.setRequestHash()`.
4. Send the returned integrity token in the encrypted `/verify` payload.
5. Validate that the encrypted response echoes `request_nonce`, then store the
   license token in Android Keystore-backed encrypted storage.

The Android base URL is intentionally not hardcoded yet. Add the final HTTPS API
URL only after hosting and Google Play configuration are complete.

## Operations

- Rotate `ADMIN_API_KEY` immediately if exposed.
- Rotating `JWT_SECRET` invalidates all existing licenses.
- Rotating `ENCRYPTION_KEY` requires an Android client update unless you add a
  key-version migration window.
- Keep service-account JSON outside the web root and rotate it periodically.
- Back up MySQL, monitor blocked verdicts, and prune old integrity logs according
  to your retention policy.
- Use the admin dashboard/API to revoke a device immediately. Offline clients can
  remain active only until their short JWT expiry; keep expiry low for rapid
  revocation.
