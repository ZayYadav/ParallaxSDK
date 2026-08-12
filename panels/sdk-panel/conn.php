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
