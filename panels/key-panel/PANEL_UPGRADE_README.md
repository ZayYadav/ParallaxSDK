# Parallax Panel - OneCore Modern Upgrade

This is a safe overlay for the existing CodeIgniter panel. It modernizes the
dashboard and key screens, fixes the incomplete legacy-license query, and adds
a dedicated OneCore activation-key inventory for the current Android APK.

## What changed

- Responsive dark/gold UI for desktop and mobile.
- Complete legacy license view: game, key, devices, duration, expiry, status,
  owner and controls are loaded and displayed.
- Modern legacy create/edit/list screens with batch generation and correct
  quantity pricing.
- State-changing cleanup, reset, delete and revoke actions now use POST + CSRF.
- New owner-only `/licenses` page for APK-compatible `OC-...` keys.
- OneCore keys use 128 bits of cryptographic randomness. Only SHA-256 hashes
  are stored; plaintext keys are displayed once immediately after creation.
- Revoking a OneCore key also revokes devices bound to that license.
- `/connect` now has a public activation portal while preserving its legacy
  POST response for older clients.
- Database credentials were removed from `app/Config/Database.php`; both
  connections are loaded from the private `.env` file.
- Raw SQL was removed from the upgraded dashboard and `/connect` flow.

## Deploy (recommended overlay method)

1. Back up the website files and both databases.
2. Extract the upgrade ZIP over the existing panel document root. The archive
   does **not** contain `.env`, so your current private configuration remains.
3. Open the existing `.env` and append the `database.integrity.*` values shown
   in `.env.onecore.example`. Use the database/user/password belonging to the
   live OneCore Integrity API.
4. Add `ONECORE_LEGACY_TOKEN_SECRET` to `.env`. Use a new random 64-character
   hex value only when every legacy client can be updated; otherwise first copy
   the current legacy secret so existing clients remain compatible.
5. If the Integrity database does not already contain `license_keys`,
   `devices`, and `device_license_bindings`, import
   `onecore-license-schema.sql` into that database. Do not re-import or replace
   an existing production schema.
6. Ensure the server uses PHP 8.1 or newer with `mysqli`, `mbstring`, `intl`,
   `json` and `openssl` enabled.
7. Sign in as the owner and open `/licenses`. Create one test key, copy it,
   activate the Android app, then verify that device usage becomes `1/1`.

## Important license-view behavior

Existing OneCore plaintext keys cannot be displayed again. This is intentional:
the database stores only a SHA-256 hash and a short prefix. The full key is
shown once when it is created. If a customer loses it, revoke the old license
and issue a new one.

## Routes

| Route | Access | Purpose |
|---|---|---|
| `/dashboard` | Signed-in users | Modern metrics and recent activity |
| `/licenses` | Owner only | Current APK activation inventory |
| `/keys` | Signed-in users | Legacy key inventory |
| `/keys/generate` | Signed-in users | Legacy key generation |
| `/connect` | Public GET / legacy POST | Activation help and old client API |

## Security after deployment

- Keep `.env` outside backups that are publicly downloadable.
- Delete any public plaintext key exports left by the old panel, such as
  `public/Newkeys.txt`, after taking a private backup if they are still needed.
- The original project contains old standalone `conn.php` files. Migrate any
  remaining pages away from those files before removing them from production.
- Rotate database passwords or access tokens that were previously pasted into
  chat, logs, source archives, or screenshots.
- Test on a staging subdomain before replacing a live production panel.
