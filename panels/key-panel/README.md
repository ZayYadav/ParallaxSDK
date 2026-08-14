# Parallax Standalone PHP Control Panel

Complete shared-hosting control panel with no CodeIgniter, Composer, Laravel,
Node.js, or vendor dependency.

## Included

- First-run installer and MySQL schema
- Owner, admin, and reseller accounts with linked Telegram user IDs
- Password hashing, secure sessions, one-time login CAPTCHA, login throttling,
  CSRF protection, audit log, security headers, and outline-style web controls
- Legacy key duration/device enforcement and encrypted loader API
- Owner-managed game and duration lists used by both the web key form and the
  Telegram key-generation flow
- OneCore hashed activation keys, expiry, device count, and revocation
- Telegram webhook bot with inline keyboards for dashboard, recent keys,
  maintenance, users, and key generation
- HTTPS validation, TLS certificate pinning in the loader, AES-256-GCM request
  and response encryption, RSA-OAEP session-key wrapping, nonce replay defense,
  and encrypted canary binding

Telegram controls use the native Telegram inline-keyboard appearance. Telegram
does not expose custom CSS/button borders to bots; the web panel uses the
requested outline button design.

## Hosting requirements

- PHP 8.1 or newer
- MySQL 8.0+ or MariaDB 10.5+
- PHP extensions: `PDO`, `pdo_mysql`, `session`, `json`, `openssl`, `curl`
- Apache or LiteSpeed with `mod_rewrite` and `.htaccess` enabled
- A public HTTPS domain or subdomain
- PHP write permission for `runtime/` (normally `0755`)

## 1. Create the Telegram bot

1. Open the official `@BotFather` chat.
2. Run `/newbot`, choose the bot name and username, and copy the bot token.
3. Do not send the token in chat, screenshots, source files, or Git commits.
4. Send `/start` to the new bot.

The webhook follows Telegram's official `setWebhook` flow and validates the
`X-Telegram-Bot-Api-Secret-Token` header. Only private-chat updates from IDs in
both the `.env` allowlist and an active linked owner/admin account are accepted.

## 2. Upload and configure

1. Create an empty database and database user in cPanel. Grant all privileges
   on that database.
2. Upload this folder's contents into the domain document root. It must contain
   `index.php`, `.htaccess`, `assets/`, `database/`, `src/`, `tools/`, and
   `runtime/`.
3. Copy `.env.example` to a hidden file named `.env`.
4. Generate private configuration values:

   ```bash
   php tools/generate-secrets.php
   ```

5. Fill the `.env` values:

   ```dotenv
   APP_ENV=production
   APP_URL=https://panel.example.com
   APP_BASE_PATH=
   APP_TIMEZONE=UTC
   SESSION_NAME=parallax_panel
   SETUP_TOKEN=<generated value>

   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=<database name>
   DB_USER=<database user>
   DB_PASSWORD=<database password>

   ENABLE_LEGACY_CONNECT=false
   ONECORE_LEGACY_TOKEN_SECRET=<only needed for an old /connect migration window>
   TELEGRAM_BOT_TOKEN=<BotFather token>
   TELEGRAM_BOT_USERNAME=<bot username without @>
   TELEGRAM_WEBHOOK_SECRET=<generated value>
   TELEGRAM_ALLOWED_USER_IDS=<numeric Telegram IDs separated by commas>
   API_PRIVATE_KEY_PATH=runtime/api-private.pem
   API_PUBLIC_KEY_PATH=runtime/api-public.b64
   EXPECTED_ANDROID_PACKAGE=com.onecore.loader
   EXPECTED_ANDROID_CERT_SHA256=<64-hex release signing certificate digest>
   ```

For a subfolder URL such as `https://example.com/panel`, set
`APP_URL=https://example.com` and `APP_BASE_PATH=/panel`.

To discover your numeric Telegram ID before enabling the webhook, send `/start`
to the bot and run:

```bash
php tools/telegram-user-id.php
```

## 3. First setup

1. Open `https://your-domain/setup`.
2. Enter `SETUP_TOKEN`, owner username, a 12+ character password, and the
   owner's numeric Telegram ID.
3. Setup creates the database tables and a 3072-bit RSA transport key pair.
4. After successful setup, remove or rotate `SETUP_TOKEN` in `.env`.
5. Sign in. The CAPTCHA is one-time and expires after five minutes. Accounts
   with a linked Telegram ID must also enter that ID at login.
6. Open **Settings -> Key generation lists**. The owner can edit the game and
   duration dropdowns with one `VALUE|Display label` entry per line, for example
   `BGMI|Battlegrounds Mobile India` and `168|7 Days`. The current loader submits
   game ID `PUBG`, so keep `PUBG` in the list unless that loader is rebuilt to
   submit another game ID.

The private API key is stored at `runtime/api-private.pem`. Back it up privately.
Never upload it to GitHub. Rotating it requires copying the new public key to
GitHub and rebuilding the loader.

## 4. Configure the encrypted loader

Open panel **Settings** and copy the displayed API public key. In GitHub open:

`Repository Settings -> Secrets and variables -> Actions -> Variables`

Create:

- `PARALLAX_API_PUBLIC_KEY_B64`: public key copied from panel Settings
- `PARALLAX_TLS_PINS`: comma-separated OkHttp pins for the live licensing host

On hosting, `EXPECTED_ANDROID_PACKAGE` and
`EXPECTED_ANDROID_CERT_SHA256` bind encrypted requests to the intended package
and release signing identity. Comma-separated certificate digests are supported
for a planned signing-key rotation.

Get the current leaf-certificate pin from the hosting terminal:

```bash
php tools/tls-pin.php parallaxserver.online
```

Example format:

```text
sha256/Ina/GY9COYngSpB7Ht+asGkJ5LV99jpBHYz11jXRSWM=
```

The example is a point-in-time value and must be verified at deployment. A TLS
certificate/key rotation changes a leaf pin. Add a planned backup certificate
pin before switching certificates, then rebuild the loader. Do not pin a random
CA intermediate merely to avoid maintenance; that widens trust to unrelated
certificates.

The repository variables are public configuration. The RSA private key remains
only on hosting. The loader no longer embeds `ONECORE_LEGACY_TOKEN_SECRET`, and
the legacy plaintext `/connect` route is disabled by default.

## 5. Enable Telegram controls

Confirm the owner/admin numeric IDs are:

1. saved on the corresponding panel accounts, and
2. listed in `TELEGRAM_ALLOWED_USER_IDS`.

Then run:

```bash
php tools/configure-telegram.php
```

This registers the HTTPS webhook, limits updates to messages/callback queries,
adds the webhook secret header, drops stale pending updates, and limits webhook
connections. Open the bot and send `/start` to show the control keyboard.

Available bot controls:

- Dashboard metrics
- Recent legacy keys with device reset, block/enable, and confirmed deletion
- Generate a legacy key by choosing from the owner-managed game and duration
  lists
- Generate a 30-day OneCore key and revoke it with bound devices
- View linked panel users
- Confirmed maintenance-mode on/off switch

Key material sent by Telegram is visible in the recipient's Telegram account.
Use a protected private chat and enable Telegram account two-step verification.

Existing standalone installations create and seed the
`key_generation_options` table automatically on the first request after the
updated files are deployed. Editing a list affects new key generation only;
already-issued keys keep their stored game and duration.

## 6. GitHub release configuration

The Actions workflow requires these repository variables for push/manual
release builds:

- `PARALLAX_API_PUBLIC_KEY_B64`
- `PARALLAX_TLS_PINS`

Existing Android signing secrets remain required for a stable production
identity:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

After the first release build, download the `loader-signing-certificate`
artifact, copy its SHA-256 certificate digest to
`EXPECTED_ANDROID_CERT_SHA256` on hosting, and keep the same signing key for
future releases.

Pull requests use throwaway transport configuration only for compile/tests and
do not publish loader APK artifacts. Production APK artifacts are uploaded only
when real encrypted transport configuration is present.

## Security model and limitations

The loader uses ordinary verified HTTPS plus OkHttp certificate pinning. Inside
that channel, every licensing request and response is separately encrypted with
AES-256-GCM. A fresh AES key is wrapped to the server's RSA public key, request
nonces are single-use, timestamps are short-lived, and a random canary must be
returned inside the authenticated encrypted response. The loader also verifies
its signing identity, while the server checks the claimed package and signing
digest, and fails closed on response tampering, replay, redirect,
oversize payload, invalid expiry, certificate mismatch, or canary mismatch.

No client-side protection can make a secret or decrypted value impossible to
inspect on a fully compromised/rooted device. These controls substantially
raise the cost of passive sniffing and common proxy/MITM attacks; they are not a
claim of absolute anti-dump protection. Keep server authorization and expiry as
the final source of truth.

## Backup and recovery

Back up together:

- MySQL database
- private `.env`
- `runtime/api-private.pem`
- Android signing keystore

Do not store these four items in public GitHub artifacts. A lost API private key
requires a new pair and loader rebuild. A lost Android signing key changes the
app identity and can invalidate signature pinning.

## Tests

```bash
find . -name '*.php' -print0 | xargs -0 -n1 php -l
php tests/run.php
# With DB_* variables pointing to an empty test database:
php tests/integration.php
```

CI imports the schema into MySQL, exercises the RSA/AES encrypted transport,
runs replay/binding tests, packages a deployable panel ZIP, and compiles/tests
the Android loader.
