# Parallax Panel Sources

This directory contains the sanitized source packages for the two Parallax
licensing panels. Live credentials, runtime sessions, generated exports, and
uploaded files are intentionally excluded from version control.

## Packages

- `key-panel/` — standalone dependency-free PHP control panel whose web UI,
  Telegram bot, and encrypted Loader API share the `keys_code` inventory.
- `sdk-panel/` — standalone PHP SDK panel with the secure encrypted API v2,
  responsive administration UI, device controls, audit logs, and migration
  scripts.

Read each package's deployment README before applying it to a live server:

- `key-panel/PANEL_UPGRADE_README.md`
- `sdk-panel/SDK_PANEL_UPGRADE_README.md`

## Configuration safety

Only placeholder examples belong in Git. Keep the completed `.env`,
`config.local.php`, database passwords, encryption keys, Telegram tokens, and
webhook secrets private on the server. Rotate any credential that has already
appeared in chat, screenshots, logs, or old source archives.
