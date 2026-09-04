<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$host = $argv[1] ?? 'keshavxownerserver.online';
if (preg_match('/^[A-Za-z0-9.-]{1,253}$/D', $host) !== 1) {
    fwrite(STDERR, "Invalid hostname.\n");
    exit(1);
}
$context = stream_context_create(['ssl' => [
    'capture_peer_cert' => true,
    'verify_peer' => true,
    'verify_peer_name' => true,
    'peer_name' => $host,
    'SNI_enabled' => true,
]]);
$socket = @stream_socket_client(
    'ssl://' . $host . ':443',
    $errorNumber,
    $errorMessage,
    15,
    STREAM_CLIENT_CONNECT,
    $context
);
if (!is_resource($socket)) {
    fwrite(STDERR, "TLS connection failed: $errorMessage ($errorNumber)\n");
    exit(1);
}
$options = stream_context_get_options($context);
$certificate = $options['ssl']['peer_certificate'] ?? null;
$public = $certificate ? openssl_pkey_get_public($certificate) : false;
$details = $public ? openssl_pkey_get_details($public) : false;
if (!is_array($details) || !isset($details['key'])) {
    fwrite(STDERR, "Could not read the certificate public key.\n");
    exit(1);
}
$base64 = preg_replace('/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/', '', (string) $details['key']);
$der = base64_decode((string) $base64, true);
if ($der === false) {
    fwrite(STDERR, "Could not encode the certificate public key.\n");
    exit(1);
}
echo 'sha256/' . base64_encode(hash('sha256', $der, true)) . PHP_EOL;
