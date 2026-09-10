<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/app/Core/Env.php';

function routing_env_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$routes = require dirname(__DIR__) . '/app/routes/web.php';
routing_env_check(($routes['connect'] ?? null) === 'connect.php', 'Clean connect route does not preserve connect.php.');
routing_env_check(($routes['api/connect'] ?? null) === 'connect.php', 'API connect route does not target connect.php.');
routing_env_check(($routes['licenses/generate'] ?? null) === 'generate_ui.php', 'License generate route missing.');

$temporary = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'sdk-panel-env-' . bin2hex(random_bytes(6));
routing_env_check(mkdir($temporary, 0700), 'Could not create env test directory.');
$envPath = $temporary . DIRECTORY_SEPARATOR . '.env';
try {
    routing_env_check(file_put_contents($envPath, implode("\n", [
        'DB_HOST=localhost',
        'DB_PORT=3307',
        'DB_NAME=panel',
        'DB_USER=panel_user',
        'DB_PASSWORD="secret value"',
        'LEGACY_API_ENABLED=false',
        'API_V2_ENABLED=true',
        'TRUSTED_PROXIES=10.0.0.1, 10.0.0.2',
        'ALLOWED_PACKAGES=com.example.one,com.example.two',
        'API_V3_KEY_ID=test-key',
        'API_V3_ECDH_PRIVATE_KEY_FILE=/private/ecdh.pem',
        'API_V3_SIGNING_PRIVATE_KEY_FILE=/private/signing.pem',
    ])) !== false, 'Could not write test env file.');

    Env::load($envPath);
    $config = Env::panelConfigFromEnvironment();
    routing_env_check($config['DB_PORT'] === 3307, 'DB_PORT was not parsed as integer.');
    routing_env_check($config['DB_PASSWORD'] === 'secret value', 'Quoted env value was not parsed.');
    routing_env_check($config['LEGACY_API_ENABLED'] === false, 'Boolean false env value failed.');
    routing_env_check($config['API_V2_ENABLED'] === true, 'Boolean true env value failed.');
    routing_env_check($config['TRUSTED_PROXIES'] === ['10.0.0.1', '10.0.0.2'], 'CSV proxy env failed.');
    routing_env_check($config['ALLOWED_PACKAGES'] === ['com.example.one', 'com.example.two'], 'CSV package env failed.');
    routing_env_check(isset($config['API_V3_KEYS']['test-key']), 'File-based API v3 env config failed.');
} finally {
    @unlink($envPath);
    @rmdir($temporary);
}

echo "SDK panel routing/env self-test passed.\n";
