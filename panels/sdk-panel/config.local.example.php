<?php
declare(strict_types=1);

// Copy this file to /home/ACCOUNT/private/sdk-panel-config.php (recommended)
// or to config.local.php beside conn.php. Never commit or share the real file.
return [
    'DB_HOST' => 'localhost',
    'DB_PORT' => 3306,
    'DB_NAME' => 'CHANGE_ME',
    'DB_USER' => 'CHANGE_ME',
    'DB_PASSWORD' => 'CHANGE_ME',

    // Base64 of exactly 32 random bytes. Generate with:
    // php -r "echo base64_encode(random_bytes(32)), PHP_EOL;"
    'ENCRYPTION_KEY' => 'CHANGE_TO_BASE64_32_BYTE_KEY',

    // Keep true during SDK migration. Set false after every client uses v2.
    'LEGACY_API_ENABLED' => true,
    'REQUIRE_HTTPS' => true,
    'TRUST_PROXY' => false,
    'RATE_LIMIT_PER_MINUTE' => 30,
    'SESSION_IDLE_SECONDS' => 1800,

    // Empty means licenses control their own package_name/package_lock.
    'ALLOWED_PACKAGES' => [],

    // Optional Telegram webhook integration.
    'TELEGRAM_BOT_TOKEN' => '',
    'TELEGRAM_WEBHOOK_SECRET' => '',
    'TELEGRAM_DEFAULT_ADMIN_CHAT_ID' => '',
];
