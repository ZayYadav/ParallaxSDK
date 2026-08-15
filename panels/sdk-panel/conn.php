<?php
declare(strict_types=1);

if (!defined('SDK_PANEL_BOOTSTRAPPED')) {
    define('SDK_PANEL_BOOTSTRAPPED', true);

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

    if ($configPath === null) {
        error_log('SDK Panel: private configuration file not found.');
        http_response_code(500);
        exit('SERVER_CONFIGURATION_ERROR');
    }

    $SDK_PANEL_CONFIG = require $configPath;
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
}

if (!function_exists('sdk_panel_schema_problems')) {
    /** @return list<string> */
    function sdk_panel_schema_problems(mysqli $connection): array
    {
        $requiredSchema = [
            'users' => ['id', 'username', 'password', 'role', 'status', 'is_online'],
            'licenses' => ['id', 'license_key', 'expiry_date', 'status', 'package_name'],
            'devices' => ['device_id', 'license_key', 'status'],
            'panel_settings' => ['setting_key', 'setting_value'],
            'server_settings' => ['setting_key', 'setting_value'],
            'api_rate_limits' => ['bucket_hash', 'window_start', 'request_count'],
            'api_audit_logs' => ['event_type', 'result', 'ip_address'],
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
