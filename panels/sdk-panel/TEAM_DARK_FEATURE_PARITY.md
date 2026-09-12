# TeamDark-style feature parity for Parallax SDK Panel

This upgrade adds TeamDark-style management features while deliberately preserving the existing Parallax SDK activation contract.

## Contract boundary

The upgrade does **not** replace or redesign `connect.php`, API v2/v3 request fields, response envelopes, P-256/AES-GCM signing/encryption, package/signing/device validation, SDK version policy, or existing license/device records.

Database/API calculations continue in UTC. Human-facing panel dates are rendered by `PanelTime` using `DISPLAY_TIMEZONE=Asia/Kolkata` (IST, UTC+05:30). Telegram-facing date output also uses the same India-time renderer.

## Added management features

- Owner / Admin / Reseller / User roles.
- Owner unlimited generation; balance-aware generation for every non-owner role.
- Multi-use, expiring and revocable referrals with signup role/balance grants.
- Server-enforced 15-second registration review before first login.
- Owner user directory with role/status, balance adjustment, password reset and Telegram reset.
- Owner step-up password confirmation for sensitive controls (5-minute window).
- Panel maintenance, registration and generation switches. `/connect` and SDK API validation are explicitly excluded from panel maintenance.
- Site announcement and optional versioned opening splash.
- Owner/Admin activity log with actor/target/IP/result/metadata.
- Telegram user registry with guest/linked states.
- Secure Telegram account linking using 15-minute one-time codes bound to a Chat ID.
- Optional Telegram 2FA with 8-digit, single-use, 5-minute login codes. Existing TOTP/recovery-code MFA remains authoritative and is never bypassed.
- Telegram webhook replay protection plus per-chat rate limiting for non-owner chats.
- Guest Telegram 2-hour / one-device key once every 7 days.
- Optional linked Telegram generation for 1/7/30 days, charged against panel balance.
- Owner Telegram read-only console plus balance, user status and announcement commands; the existing license-management bot commands remain available.
- Announcement broadcast queue for panel, linked Telegram and guest Telegram audiences.

TeamDark-specific components that would replace the Parallax SDK license/API contract (for example TeamDark's alternate app API/auth model) are intentionally not copied. Their management ideas are adapted around the existing Parallax SDK schema instead.

## Install / upgrade

1. Back up the panel database.
2. Keep the existing `.env`; confirm `DISPLAY_TIMEZONE=Asia/Kolkata`.
3. Import `database/teamdark-feature-parity.sql` once into the same SDK panel database.
4. Keep the existing Telegram settings if Telegram features are used: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, and `TELEGRAM_DEFAULT_ADMIN_CHAT_ID`.
5. Sign in as Owner and open `/owner-console` (or `owner_console.php`). Confirm the owner password to unlock sensitive controls.

The feature runtime stays disabled until the migration writes the final `sdk_feature_suite_meta` sentinel table, so a partial SQL import does not leave half-enabled PHP behavior.

## New routes

- `/owner-console`
- `/activity`
- `/telegram-users`
- `/account`
- `/licenses/self-service`
- `/registration-review`

Legacy `.php` URLs for these pages also work.
