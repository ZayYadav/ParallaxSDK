<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/panel_security.php';

function proxy_test_check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$originalServer = $_SERVER;
$originalConfig = $SDK_PANEL_CONFIG ?? null;

try {
    $SDK_PANEL_CONFIG = [
        'TRUST_PROXY' => true,
        'TRUSTED_PROXIES' => ['10.0.0.10'],
    ];

    unset($_SERVER['HTTPS']);
    $_SERVER['HTTP_X_FORWARDED_FOR'] = '203.0.113.45';
    $_SERVER['HTTP_X_FORWARDED_PROTO'] = 'https';

    // An untrusted internet peer must not make forwarded headers authoritative.
    $_SERVER['REMOTE_ADDR'] = '198.51.100.20';
    proxy_test_check(panel_client_ip() === '198.51.100.20', 'Untrusted X-Forwarded-For was accepted.');
    proxy_test_check(!panel_is_https($SDK_PANEL_CONFIG), 'Untrusted X-Forwarded-Proto was accepted.');

    // The same headers become authoritative only behind the configured proxy.
    $_SERVER['REMOTE_ADDR'] = '10.0.0.10';
    proxy_test_check(panel_client_ip() === '203.0.113.45', 'Trusted proxy client IP was not accepted.');
    proxy_test_check(panel_is_https($SDK_PANEL_CONFIG), 'Trusted proxy HTTPS state was not accepted.');

    // TRUST_PROXY without an allowlist must fail closed.
    $SDK_PANEL_CONFIG['TRUSTED_PROXIES'] = [];
    proxy_test_check(panel_client_ip() === '10.0.0.10', 'Empty proxy allowlist did not fail closed.');
    proxy_test_check(!panel_is_https($SDK_PANEL_CONFIG), 'Empty proxy allowlist trusted forwarded HTTPS.');
} finally {
    $_SERVER = $originalServer;
    if ($originalConfig === null) {
        unset($SDK_PANEL_CONFIG);
    } else {
        $SDK_PANEL_CONFIG = $originalConfig;
    }
}

echo "SDK panel proxy trust self-test passed.\n";
