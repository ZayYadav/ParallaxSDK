# Parallax Standalone PHP Key Panel

This folder is a complete, dependency-free PHP application. It does not use
CodeIgniter, Composer, Laravel, Node.js, or a vendor directory.

## Hosting requirements

- PHP 8.1 or newer
- MySQL 8.0+ or MariaDB 10.5+
- PHP extensions: PDO, pdo_mysql, session, json, openssl
- Apache or LiteSpeed with `mod_rewrite` and `.htaccess` enabled
- HTTPS on the public domain

## Fresh cPanel deployment

1. Create an empty MySQL database and database user in cPanel. Grant the user
   all privileges on that database.
2. Upload the contents of this folder into the domain/subdomain document root.
   The document root must contain `index.php`, `.htaccess`, `src`, `assets`, and
   `database`.
3. Copy `.env.example` to a hidden file named `.env`.
4. Fill `APP_URL`, database credentials, `SETUP_TOKEN`, and
   `ONECORE_LEGACY_TOKEN_SECRET`. Generate the two private values with:

   ```bash
   php tools/generate-secrets.php
   ```

   cPanel users without Terminal can generate two independent 64-character hex
   values with a trusted password generator. Never reuse a database password.
5. If installed in a subfolder such as `https://example.com/panel`, set
   `APP_BASE_PATH=/panel`. Leave it empty for a domain root.
6. Ensure `runtime` is writable by PHP (normally permission `0755`).
7. Open `/setup`, enter the `SETUP_TOKEN`, and create the first owner. Setup
   automatically imports `database/schema.sql`.
8. After setup, rotate or remove `SETUP_TOKEN` from `.env`.
9. Add the exact same `ONECORE_LEGACY_TOKEN_SECRET` value as a GitHub Actions
   repository secret so release APKs validate this server's responses.

## Existing database migration

Back up the site and database first. Point `.env` at the existing database and
open `/setup`. The installer uses `CREATE TABLE IF NOT EXISTS`, so it preserves
existing `keys_code`, `license_keys`, and device data. If an old table has a
different column layout, migrate it on staging first instead of modifying the
production database in place.

## Included features

- First-run database installer and owner creation
- Owner, admin, and reseller sessions with password changes and login throttling
- CSRF protection, secure cookies, password hashing, audit log, and security headers
- Legacy key creation, device reset, blocking, deletion, duration, and device limits
- Reseller credit accounting (one credit per generated legacy key)
- Rate-limited `/connect` checker compatible with the loader
- Hashed OneCore activation keys with expiry, device counts, and revocation
- Maintenance mode and feature/server settings
- No plaintext OneCore keys stored after their one-time display

## Security notes

- Keep `.env`, `src`, `database`, `tests`, `tools`, and `runtime` inaccessible
  from the web. The supplied `.htaccess` blocks sensitive paths on Apache and
  LiteSpeed. For Nginx, add equivalent deny rules.
- Do not commit or send the completed `.env` through chat.
- Use a dedicated database account, HTTPS, and daily off-site backups.
- Test the `/connect` endpoint and one short-duration key on a staging subdomain
  before moving the loader to production.

## Local checks

```bash
find . -name '*.php' -print0 | xargs -0 -n1 php -l
php tests/run.php
# With DB_* variables pointing to an empty test database:
php tests/integration.php
```
