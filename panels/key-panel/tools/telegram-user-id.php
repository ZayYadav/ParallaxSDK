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
if (preg_match('/^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$/D', $token) !== 1 || !extension_loaded('curl')) {
    fwrite(STDERR, "Configure TELEGRAM_BOT_TOKEN and enable PHP cURL first.\n");
    exit(1);
}
$curl = curl_init('https://api.telegram.org/bot' . $token . '/getUpdates');
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => json_encode(['allowed_updates' => ['message'], 'timeout' => 0], JSON_THROW_ON_ERROR),
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT => 15,
    CURLOPT_SSL_VERIFYPEER => true,
    CURLOPT_SSL_VERIFYHOST => 2,
    CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
]);
$raw = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
curl_close($curl);
$response = is_string($raw) ? json_decode($raw, true) : null;
if ($status !== 200 || !is_array($response) || !($response['ok'] ?? false)) {
    fwrite(STDERR, "Could not fetch updates. Remove the webhook first if one is already configured.\n");
    exit(1);
}
$seen = [];
foreach ($response['result'] as $update) {
    $from = $update['message']['from'] ?? null;
    if (is_array($from) && isset($from['id'])) {
        $id = (string) $from['id'];
        if (!isset($seen[$id])) {
            $seen[$id] = true;
            echo $id . ' | ' . trim((string) (($from['first_name'] ?? '') . ' ' . ($from['last_name'] ?? ''))) . PHP_EOL;
        }
    }
}
if (!$seen) {
    echo "No messages found. Send /start to the bot, then run this tool again.\n";
}
