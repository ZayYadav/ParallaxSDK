<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$config = require __DIR__ . '/config.php';
require_once __DIR__ . '/CryptoHelper.php';
require_once __DIR__ . '/IntegrityVerifier.php';

$crypto = new CryptoHelper($config['encryption_key']);
$command = $argv[1] ?? '';

if ($command === 'encrypt') {
    $file = $argv[2] ?? '';
    if ($file === '' || !is_readable($file)) {
        fwrite(STDERR, "Usage: php example_client.php encrypt payload.json\n");
        exit(1);
    }
    $payload = json_decode((string) file_get_contents($file), true, 64, JSON_THROW_ON_ERROR);
    echo json_encode($crypto->encryptPayload($payload), JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES), PHP_EOL;
    exit;
}

if ($command === 'decrypt') {
    $file = $argv[2] ?? '';
    if ($file === '' || !is_readable($file)) {
        fwrite(STDERR, "Usage: php example_client.php decrypt response.json\n");
        exit(1);
    }
    $envelope = json_decode((string) file_get_contents($file), true, 16, JSON_THROW_ON_ERROR);
    echo json_encode($crypto->decryptEnvelope($envelope), JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES), PHP_EOL;
    exit;
}

if ($command === 'request-hash') {
    if (count($argv) !== 5) {
        fwrite(STDERR, "Usage: php example_client.php request-hash DEVICE_ID NONCE TIMESTAMP\n");
        exit(1);
    }
    echo IntegrityVerifier::requestHash($argv[2], $argv[3], (int) $argv[4]), PHP_EOL;
    exit;
}

fwrite(STDERR, "Commands: encrypt, decrypt, request-hash\n");
exit(1);
