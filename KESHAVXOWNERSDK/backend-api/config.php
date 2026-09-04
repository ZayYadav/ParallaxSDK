<?php
declare(strict_types=1);

date_default_timezone_set('UTC');

/**
 * Production secrets must be supplied through environment variables or the
 * ignored config.local.php file. Never commit real keys to source control.
 */

$local = [];
$localPath = __DIR__ . '/config.local.php';
if (is_file($localPath)) {
    $loaded = require $localPath;
    if (!is_array($loaded)) {
        throw new RuntimeException('config.local.php must return an array');
    }
    $local = $loaded;
}

$read = static function (string $name, mixed $default = null) use ($local): mixed {
    if (array_key_exists($name, $local)) {
        return $local[$name];
    }
    $value = getenv($name);
    return $value === false ? $default : $value;
};

$readBool = static function (string $name, bool $default) use ($read): bool {
    $value = $read($name, $default);
    if (is_bool($value)) {
        return $value;
    }
    $parsed = filter_var($value, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE);
    return $parsed ?? $default;
};

$readList = static function (string $name, array $default = []) use ($read): array {
    $value = $read($name, $default);
    if (is_array($value)) {
        return array_values(array_filter(array_map('trim', $value)));
    }
    return array_values(array_filter(array_map('trim', explode(',', (string) $value))));
};

return [
    'app_env' => (string) $read('APP_ENV', 'production'),
    'app_package_name' => (string) $read('APP_PACKAGE_NAME', 'com.onecore.loader'),
    'api_base_url' => rtrim((string) $read('API_BASE_URL', ''), '/'),
    'require_https' => $readBool('REQUIRE_HTTPS', true),
    'trust_proxy' => $readBool('TRUST_PROXY', false),
    'cors_allowed_origins' => $readList('CORS_ALLOWED_ORIGINS', []),

    'database' => [
        'host' => (string) $read('DB_HOST', '127.0.0.1'),
        'port' => (int) $read('DB_PORT', 3306),
        'name' => (string) $read('DB_NAME', 'onecore_integrity'),
        'user' => (string) $read('DB_USER', 'onecore_api'),
        'password' => (string) $read('DB_PASSWORD', ''),
    ],

    // Base64 of exactly 32 random bytes; used for AES-256-GCM envelopes.
    'encryption_key' => (string) $read('ENCRYPTION_KEY', ''),
    // Base64 of at least 32 independent random bytes; used only for HS256 JWTs.
    'jwt_secret' => (string) $read('JWT_SECRET', ''),
    'jwt_issuer' => (string) $read('JWT_ISSUER', 'onecore-integrity-api'),
    'jwt_audience' => (string) $read('JWT_AUDIENCE', 'onecore-engine-android'),
    'admin_api_key' => (string) $read('ADMIN_API_KEY', ''),

    'google_cloud_project_number' => (string) $read('GOOGLE_CLOUD_PROJECT_NUMBER', ''),
    'service_account_json_path' => (string) $read('SERVICE_ACCOUNT_JSON_PATH', ''),
    'service_account_json' => (string) $read('GOOGLE_SERVICE_ACCOUNT_JSON', ''),
    'google_connect_timeout_seconds' => (int) $read('GOOGLE_CONNECT_TIMEOUT_SECONDS', 5),
    'google_request_timeout_seconds' => (int) $read('GOOGLE_REQUEST_TIMEOUT_SECONDS', 20),

    'replay_window_seconds' => 300,
    'max_request_bytes' => 1_048_576,
    'runtime_dir' => (string) $read('RUNTIME_DIR', __DIR__ . '/runtime'),
];
