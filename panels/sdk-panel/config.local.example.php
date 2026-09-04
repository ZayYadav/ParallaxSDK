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

    // Legacy API v2 only. Base64 of exactly 32 random bytes. Generate with:
    // php -r "echo base64_encode(random_bytes(32)), PHP_EOL;"
    'ENCRYPTION_KEY' => 'CHANGE_TO_BASE64_32_BYTE_KEY',

    // Server-only key for encrypting TOTP secrets at rest. Never reuse the v2
    // API key because older APKs may contain that value.
    'PANEL_DATA_KEY' => 'CHANGE_TO_A_DIFFERENT_BASE64_32_BYTE_KEY',

    // Generate these outside public_html with tools/generate-api-v3-keys.php.
    // The matching public keys are compiled into the SDK; private keys never
    // leave the server.
    'API_V3_KEYS' => [
        'parallax-2026-09' => [
            'ecdh_private_key_file' => '/home/ACCOUNT/private/sdk-api-v3-ecdh-private.pem',
            'signing_private_key_file' => '/home/ACCOUNT/private/sdk-api-v3-signing-private.pem',
        ],
    ],

    // Keep true during SDK migration. Set false after every client uses v2.
    'LEGACY_API_ENABLED' => false,
    'API_V2_ENABLED' => false,
    'REQUIRE_HTTPS' => true,

    // Leave false when PHP is directly internet-facing. When a reverse proxy
    // terminates TLS, set TRUST_PROXY=true and list only that proxy's REMOTE_ADDR
    // values below. Forwarded headers are ignored unless the immediate peer is
    // explicitly allowlisted.
    'TRUST_PROXY' => false,
    'TRUSTED_PROXIES' => [],

    'RATE_LIMIT_PER_MINUTE' => 30,
    'API_REPLAY_WINDOW_SECONDS' => 120,
    'SESSION_IDLE_SECONDS' => 1800,
    'SESSION_ABSOLUTE_SECONDS' => 28800,

    // Empty means licenses control their own package_name/package_lock.
    'ALLOWED_PACKAGES' => [],

    // Optional Telegram webhook integration.
    'TELEGRAM_BOT_TOKEN' => '',
    'TELEGRAM_WEBHOOK_SECRET' => '',
    'TELEGRAM_DEFAULT_ADMIN_CHAT_ID' => '',
];
