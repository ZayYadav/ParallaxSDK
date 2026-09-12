<?php
declare(strict_types=1);

if (!defined('SDK_PANEL_BOOTSTRAPPED')) {
    define('SDK_PANEL_BOOTSTRAPPED', true);

    require_once __DIR__ . DIRECTORY_SEPARATOR . 'app' . DIRECTORY_SEPARATOR . 'Core' . DIRECTORY_SEPARATOR . 'Env.php';
    Env::load(__DIR__ . DIRECTORY_SEPARATOR . '.env');

    // Keep all backend/API calculations deterministic in UTC. Human-facing
    // panel output is converted separately by PanelTime to Asia/Kolkata.
    date_default_timezone_set('UTC');
    require_once __DIR__ . DIRECTORY_SEPARATOR . 'app' . DIRECTORY_SEPARATOR . 'Core' . DIRECTORY_SEPARATOR . 'PanelTime.php';
    PanelTime::bootstrapForRequest();

    $envConfig = Env::panelConfigFromEnvironment();

    $configuredPath = getenv('SDK_PANEL_CONFIG') ?: '';
    $candidates = array_filter([
        $configuredPath,
        dirname(__DIR__) . DIRECTORY_SEPARATOR . 'private' . DIRECTORY_SEPARATOR . 'sdk-panel-config.php',
        __DIR__ . DIRECTORY_SEPARATOR . 'config.local.php',
    ]);

    $configPath = null;
    foreach ($candidates as $candidate) {
        if (is_file($candidate) && is_readable($candidate)) {
            $configPath = $candidate;
            break;
        }
    }

    if ($configPath === null && $envConfig === []) {
        error_log('SDK Panel: private configuration file not found.');
        http_response_code(500);
        exit('SERVER_CONFIGURATION_ERROR');
    }

    $fileConfig = [];
    if ($configPath !== null) {
        $fileConfig = require $configPath;
    }
    if (!is_array($fileConfig)) {
        error_log('SDK Panel: configuration must return an array.');
        http_response_code(500);
        exit('SERVER_CONFIGURATION_ERROR');
    }

    $SDK_PANEL_CONFIG = array_replace($fileConfig, $envConfig);
    if (!is_array($SDK_PANEL_CONFIG)) {
        error_log('SDK Panel: configuration must return an array.');
        http_response_code(500);
        exit('SERVER_CONFIGURATION_ERROR');
    }

    foreach (['DB_HOST', 'DB_NAME', 'DB_USER', 'DB_PASSWORD'] as $requiredKey) {
        if (!isset($SDK_PANEL_CONFIG[$requiredKey]) || trim((string) $SDK_PANEL_CONFIG[$requiredKey]) === '') {
            error_log('SDK Panel: missing configuration key ' . $requiredKey);
            http_response_code(500);
            exit('SERVER_CONFIGURATION_ERROR');
        }
    }

    mysqli_report(MYSQLI_REPORT_OFF);
    $conn = mysqli_connect(
        (string) $SDK_PANEL_CONFIG['DB_HOST'],
        (string) $SDK_PANEL_CONFIG['DB_USER'],
        (string) $SDK_PANEL_CONFIG['DB_PASSWORD'],
        (string) $SDK_PANEL_CONFIG['DB_NAME'],
        (int) ($SDK_PANEL_CONFIG['DB_PORT'] ?? 3306)
    );

    if (!$conn) {
        error_log('SDK Panel database connection failed: ' . mysqli_connect_error());
        http_response_code(500);
        exit('SERVER_DATABASE_ERROR');
    }

    mysqli_set_charset($conn, 'utf8mb4');
    $conn->query("SET time_zone = '+00:00'");

    require_once __DIR__ . DIRECTORY_SEPARATOR . 'panel_security.php';
    panel_security_bootstrap($SDK_PANEL_CONFIG);

    // Add TeamDark-style panel management features around the existing SDK
    // contract. The feature bootstrap explicitly excludes connect.php/API
    // validation paths, so native request/response and crypto behavior stay
    // unchanged. Until its additive SQL migration completes, this layer is a
    // no-op and the existing panel behaves exactly as before.
    require_once __DIR__ . DIRECTORY_SEPARATOR . 'app' . DIRECTORY_SEPARATOR . 'Core' . DIRECTORY_SEPARATOR . 'FeatureSuite.php';

    // Admins, resellers and users use the balance-aware generator; Owner keeps
    // the original advanced generator with unlimited management access.
    if (sdk_feature_installed($conn) && sdk_feature_script_name() === 'generate_ui.php') {
        $featureActor = sdk_feature_current_user($conn);
        if ($featureActor && in_array((string) $featureActor['role'], ['admin', 'reseller', 'user'], true)) {
            header('Location: feature_generate.php');
            exit;
        }
    }

    sdk_feature_bootstrap($conn);
}

if (!function_exists('sdk_panel_schema_problems')) {
    /** @return list<string> */
    function sdk_panel_schema_problems(mysqli $connection): array
    {
        $requiredSchema = [
            'users' => ['id', 'username', 'password', 'role', 'status', 'is_online', 'mfa_enabled', 'mfa_secret_enc'],
            'licenses' => [
                'id', 'license_key', 'client_name', 'expiry_date', 'status', 'package_name',
                'package_mode', 'signing_mode', 'signing_cert_sha256', 'device_mode',
                'feature_policy', 'minimum_sdk_version', 'latest_sdk_version',
                'blocked_versions', 'session_lifetime_seconds', 'kill_switch',
            ],
            'devices' => [
                'device_id', 'license_key', 'status', 'client_public_key', 'client_key_fingerprint',
            ],
            'user_recovery_codes' => ['user_id', 'code_hash', 'used_at'],
            'panel_settings' => ['setting_key', 'setting_value'],
            'server_settings' => ['setting_key', 'setting_value', 'broadcast_version'],
            'api_rate_limits' => ['bucket_hash', 'window_start', 'request_count'],
            'api_request_ids' => ['request_id_hash', 'device_id', 'expires_at'],
            'api_sessions' => ['session_id', 'token_hash', 'license_id', 'device_id', 'expires_at'],
            'api_audit_logs' => [
                'event_type', 'result', 'ip_address', 'license_id', 'client_name',
                'package_name', 'signing_sha256', 'sdk_version', 'request_id',
            ],
        ];
        $problems = [];
        foreach ($requiredSchema as $table => $columns) {
            $quotedTable = '`' . str_replace('`', '``', $table) . '`';
            $result = $connection->query('SHOW COLUMNS FROM ' . $quotedTable);
            if (!$result) {
                $problems[] = $table . ' (table missing)';
                continue;
            }
            $available = [];
            while ($row = $result->fetch_assoc()) {
                $available[(string) $row['Field']] = true;
            }
            $missing = array_values(array_filter(
                $columns,
                static fn(string $column): bool => !isset($available[$column])
            ));
            if ($missing !== []) {
                $problems[] = $table . ' (missing: ' . implode(', ', $missing) . ')';
            }
        }
        return $problems;
    }
}
