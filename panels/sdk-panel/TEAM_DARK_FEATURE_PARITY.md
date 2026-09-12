# TeamDark-style feature parity for Parallax SDK Panel

This upgrade adds TeamDark-style management features while deliberately preserving the existing Parallax SDK activation contract.

## Contract boundary

The upgrade does **not** replace or redesign `connect.php`, API v2/v3 request fields, response envelopes, P-256/AES-GCM signing/encryption, package/signing/device validation, SDK version policy, or the Loader/SDK contract.

Database/API calculations continue in UTC. Human-facing panel dates are rendered using `DISPLAY_TIMEZONE=Asia/Kolkata` (IST, UTC+05:30). Activity date filters convert India-day boundaries back to UTC before querying, and feature-rendered IST timestamps are protected against accidental second timezone conversion.

## Added management features

- Owner / Admin / Reseller / User roles.
- Owner unlimited generation; balance-aware generation for non-owner self-service accounts.
- Database-backed license ownership (`owner_user_id`) so Reseller/User pages can only read/manage their own keys and devices.
- Multi-use, expiring and revocable referrals with signup role/balance grants. Admin-created referrals are forced to User role with zero balance grant; balance minting remains Owner-only.
- Server-enforced 15-second registration review before first login.
- Owner user directory with role/status, balance adjustment, password reset and Telegram reset.
- Read-only Admin user directory; Owner accounts are excluded from Admin management.
- Owner step-up password confirmation for sensitive controls (5-minute window).
- Panel maintenance, registration and generation switches. `/connect` and SDK activation validation are explicitly excluded from human-panel maintenance.
- Separate Owner SDK server control synchronizes the legacy `server_status` and authoritative `server_mode` settings without changing the SDK response contract.
- Site announcement and optional versioned opening splash.
- Owner/Admin activity log with actor/target/IP/result/metadata and India-time date filtering.
- Telegram user registry with guest/linked states.
- Secure Telegram account linking using 15-minute one-time codes bound to an exact Chat ID. Linking rotates `auth_version` and prevents one Telegram identity from silently moving between panel accounts.
- Guest Telegram keys are tagged to the Telegram identity and automatically assigned to the panel account when that guest securely links later.
- Optional Telegram 2FA with 8-digit, single-use, 5-minute login codes. Existing TOTP/recovery-code MFA remains authoritative and is never bypassed.
- Telegram 2FA fails closed if `PANEL_DATA_KEY` is missing or Telegram cannot deliver the OTP; incorrect OTPs allow up to five attempts within the same challenge.
- Telegram webhook replay protection plus per-chat rate limiting for non-admin chats.
- Guest Telegram 2-hour / one-device key once every 7 days.
- Optional linked Telegram generation for 1/7/30 days, charged against panel balance.
- Read-only Telegram admin views and a separate Owner mutation authority. New balance/user-status/announcement mutation commands require `TELEGRAM_OWNER_CHAT_ID`, or an active panel Owner account securely linked to that Telegram chat.
- Announcement broadcast queue with atomic recipient claims, three-attempt retry handling and stale-claim recovery to prevent duplicate concurrent delivery.
- Disabled users and `auth_version` changes invalidate human-panel and panel-AJAX sessions; the SDK `/connect` contract remains excluded.

TeamDark-specific components that would replace the Parallax SDK license/API contract (for example TeamDark's alternate Loader app API/auth model) are intentionally not copied. Their management ideas are adapted around the existing Parallax SDK schema instead.

## Install / upgrade

1. Back up the panel database and panel files.
2. Keep the existing `.env`; confirm `DISPLAY_TIMEZONE=Asia/Kolkata`.
3. Import `database/teamdark-feature-parity.sql` into the same SDK panel database.
4. Immediately import `database/teamdark-feature-parity-hardening.sql`. Both files are idempotent and the CI runs each twice against MySQL 8.4.
5. Keep `TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET` if Telegram features are used.
6. `TELEGRAM_DEFAULT_ADMIN_CHAT_ID` is the legacy/read-only admin chat. Set `TELEGRAM_OWNER_CHAT_ID` for explicit Telegram mutation authority. If the Owner chat setting is empty, an active panel Owner securely linked to Telegram is accepted as the Owner mutation authority.
7. Sign in as Owner and open `/owner-console` (or `owner_console.php`). Confirm the Owner password to unlock sensitive controls.
8. Use `/server/control` for SDK online / maintenance / offline mode; it keeps `server_mode` and `server_status` synchronized.

The base feature migration writes the feature sentinel only after its work completes. The hardening migration raises the feature version to `3` and adds ownership/broadcast recovery structures. Do not deploy the new PHP files without both migrations.

## Hardened routes

- `/owner-console` — Owner command center
- `/activity` — Owner/Admin audit history
- `/telegram-users` — Owner Telegram user registry
- `/account` — account + Telegram security
- `/dashboard/my` — ownership-aware Reseller/User dashboard
- `/licenses/mine` — ownership-aware Reseller/User license management
- `/licenses/self-service` — balance-aware generation
- `/users/directory` — read-only Admin directory
- `/referrals/advanced` — role-safe Owner/Admin referrals
- `/server/control` — Owner-only SDK server mode
- `/registration-review` — registration countdown/review

Legacy URLs remain available where required; access-control preflight routes lower roles away from legacy global-data pages.

## Automated audit

`.github/workflows/sdk-panel-feature-parity-check.yml` verifies:

- every SDK-panel PHP file passes PHP 8.2 syntax lint;
- `connect.php` has no diff from `main`;
- feature access-control wiring and ownership checks exist;
- fresh schema + base feature migration + hardening migration import on MySQL 8.4;
- both migrations can be run a second time;
- feature version is `3`;
- `licenses.owner_user_id`, its index and foreign key exist;
- broadcast `claimed_at` recovery state exists;
- historical `generated_by=username` license rows backfill deterministically to `owner_user_id`.
