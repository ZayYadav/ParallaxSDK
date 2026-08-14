<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));

require PANEL_ROOT . '/src/Support.php';
Env::load(PANEL_ROOT . '/.env');

date_default_timezone_set(Env::get('APP_TIMEZONE', 'UTC'));
error_reporting(E_ALL);
ini_set('display_errors', Env::get('APP_ENV', 'production') === 'development' ? '1' : '0');
ini_set('log_errors', '1');
ini_set('error_log', PANEL_ROOT . '/runtime/php-error.log');

if (PHP_VERSION_ID < 80100) {
    http_response_code(500);
    exit('PHP 8.1 or newer is required.');
}

$secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
    || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
session_name(Env::get('SESSION_NAME', 'parallax_panel'));
session_set_cookie_params([
    'lifetime' => 0,
    'path' => '/',
    'secure' => $secure,
    'httponly' => true,
    'samesite' => 'Strict',
]);
session_start();

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");

require PANEL_ROOT . '/src/Database.php';
require PANEL_ROOT . '/src/GenerationOptions.php';
require PANEL_ROOT . '/src/Security.php';
require PANEL_ROOT . '/src/ApiCrypto.php';
require PANEL_ROOT . '/src/TelegramBot.php';
require PANEL_ROOT . '/src/View.php';
require PANEL_ROOT . '/src/App.php';
