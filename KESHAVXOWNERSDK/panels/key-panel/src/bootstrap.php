<?php

declare(strict_types=1);

namespace KESHAVXOWNERPanel;

define('PANEL_ROOT', dirname(__DIR__));

require PANEL_ROOT . '/src/Support.php';
Env::load(PANEL_ROOT . '/.env');

date_default_timezone_set(Env::get('APP_TIMEZONE', 'UTC'));
error_reporting(E_ALL);
$production = Env::get('APP_ENV', 'production') === 'production';
ini_set('display_errors', $production ? '0' : '1');
ini_set('log_errors', '1');
ini_set('error_log', PANEL_ROOT . '/runtime/php-error.log');

if (PHP_VERSION_ID < 80100) {
    http_response_code(500);
    exit('PHP 8.1 or newer is required.');
}

$remoteAddress = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
$trustedProxies = array_values(array_filter(array_map('trim', explode(',', Env::get('TRUSTED_PROXY_IPS')))));
$forwardedHttps = in_array($remoteAddress, $trustedProxies, true)
    && strtolower(trim((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? ''))) === 'https';
$secure = (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') || $forwardedHttps;
$appUrl = rtrim(Env::get('APP_URL'), '/');
if ($production && (filter_var($appUrl, FILTER_VALIDATE_URL) === false
    || strtolower((string) parse_url($appUrl, PHP_URL_SCHEME)) !== 'https')) {
    http_response_code(500);
    exit('APP_URL must be a valid HTTPS URL in production.');
}
if ($production && !$secure) {
    if (in_array($_SERVER['REQUEST_METHOD'] ?? 'GET', ['GET', 'HEAD'], true)) {
        $requestPath = (string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
        $requestPath = str_starts_with($requestPath, '/') ? $requestPath : '/';
        header('Location: ' . $appUrl . $requestPath, true, 308);
        exit;
    }
    http_response_code(400);
    exit('HTTPS is required.');
}

$sessionName = Env::get('SESSION_NAME', 'keshavxowner_panel');
if (preg_match('/^[A-Za-z0-9_-]{1,64}$/D', $sessionName) !== 1) {
    http_response_code(500);
    exit('SESSION_NAME is invalid.');
}
ini_set('session.use_strict_mode', '1');
ini_set('session.use_only_cookies', '1');
ini_set('session.use_trans_sid', '0');
ini_set('session.cookie_httponly', '1');
ini_set('session.cookie_samesite', 'Strict');
session_name($sessionName);
$cookiePath = '/' . trim(Env::get('APP_BASE_PATH'), '/');
$cookiePath = $cookiePath === '/' ? '/' : $cookiePath . '/';
session_set_cookie_params([
    'lifetime' => 0,
    'path' => $cookiePath,
    'secure' => $production || $secure,
    'httponly' => true,
    'samesite' => 'Strict',
]);
session_start();

header('Cache-Control: no-store, private, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('Cross-Origin-Opener-Policy: same-origin');
header('Cross-Origin-Resource-Policy: same-origin');
header('Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()');
header('X-Permitted-Cross-Domain-Policies: none');
header('X-Robots-Tag: noindex, nofollow, noarchive');
$csp = "default-src 'none'; style-src 'self'; img-src 'self' data:; font-src 'self'; "
    . "connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'";
header('Content-Security-Policy: ' . $csp . ($production ? '; upgrade-insecure-requests' : ''));
if ($secure) {
    header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
}

require PANEL_ROOT . '/src/Database.php';
require PANEL_ROOT . '/src/GenerationOptions.php';
require PANEL_ROOT . '/src/Security.php';
require PANEL_ROOT . '/src/ApiCrypto.php';
require PANEL_ROOT . '/src/TelegramBot.php';
require PANEL_ROOT . '/src/View.php';
require PANEL_ROOT . '/src/App.php';
