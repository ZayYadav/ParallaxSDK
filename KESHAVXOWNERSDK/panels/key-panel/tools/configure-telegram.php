<?php

declare(strict_types=1);

namespace KESHAVXOWNERPanel;

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
Env::load(PANEL_ROOT . '/.env');

$token = Env::get('TELEGRAM_BOT_TOKEN');
$secret = Env::get('TELEGRAM_WEBHOOK_SECRET');
$appUrl = rtrim(Env::get('APP_URL'), '/');
$basePath = '/' . trim(Env::get('APP_BASE_PATH'), '/');
$basePath = $basePath === '/' ? '' : $basePath;
if (preg_match('/^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$/D', $token) !== 1
    || preg_match('/^[A-Za-z0-9_-]{32,256}$/D', $secret) !== 1
    || !str_starts_with($appUrl, 'https://')) {
    fwrite(STDERR, "Configure TELEGRAM_BOT_TOKEN, TELEGRAM_WEBHOOK_SECRET, and HTTPS APP_URL first.\n");
    exit(1);
}
if (!extension_loaded('curl')) {
    fwrite(STDERR, "PHP cURL extension is required.\n");
    exit(1);
}
$payload = json_encode([
    'url' => $appUrl . $basePath . '/telegram/webhook',
    'secret_token' => $secret,
    'allowed_updates' => ['message', 'callback_query'],
    'drop_pending_updates' => true,
    'max_connections' => 10,
], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
$curl = curl_init('https://api.telegram.org/bot' . $token . '/setWebhook');
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $payload,
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 5,
    CURLOPT_TIMEOUT => 15,
    CURLOPT_SSL_VERIFYPEER => true,
    CURLOPT_SSL_VERIFYHOST => 2,
    CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
]);
$response = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
$error = curl_error($curl);
curl_close($curl);
$decoded = is_string($response) ? json_decode($response, true) : null;
if ($status !== 200 || !is_array($decoded) || !($decoded['ok'] ?? false)) {
    fwrite(STDERR, 'Webhook setup failed: ' . ($error ?: (string) ($decoded['description'] ?? 'HTTP ' . $status)) . PHP_EOL);
    exit(1);
}
echo "Telegram webhook configured successfully.\n";
