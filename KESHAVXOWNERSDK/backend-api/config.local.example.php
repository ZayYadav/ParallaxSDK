<?php
declare(strict_types=1);

// Copy to config.local.php, replace every placeholder, and keep that file private.
return [
    'DB_HOST' => '127.0.0.1',
    'DB_PORT' => 3306,
    'DB_NAME' => 'onecore_integrity',
    'DB_USER' => 'onecore_api',
    'DB_PASSWORD' => 'REPLACE_WITH_A_LONG_DATABASE_PASSWORD',
    'ENCRYPTION_KEY' => 'REPLACE_WITH_BASE64_OF_32_RANDOM_BYTES',
    'JWT_SECRET' => 'REPLACE_WITH_BASE64_OF_AT_LEAST_32_RANDOM_BYTES',
    'ADMIN_API_KEY' => 'REPLACE_WITH_AT_LEAST_32_RANDOM_BYTES',
    'GOOGLE_CLOUD_PROJECT_NUMBER' => '123456789012',
    'SERVICE_ACCOUNT_JSON_PATH' => '/absolute/private/path/service-account.json',
    'APP_PACKAGE_NAME' => 'com.onecore.loader',
    'API_BASE_URL' => 'https://api.example.com/api',
    'CORS_ALLOWED_ORIGINS' => [],
    'REQUIRE_HTTPS' => true,
    'TRUST_PROXY' => false,
];
