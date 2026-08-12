<?php
declare(strict_types=1);

require_once __DIR__ . '/../vendor/autoload.php';
require_once __DIR__ . '/../CryptoHelper.php';
require_once __DIR__ . '/../JWTHelper.php';
require_once __DIR__ . '/../IntegrityVerifier.php';

function assertTrue(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$crypto = new CryptoHelper(base64_encode(random_bytes(32)));
$payload = [
    'device_id' => '6d7f0bd1-6ca2-4eb9-9db0-ea1ad0214baf',
    'timestamp' => time(),
    'nonce' => '4eG5B9R2uLh2M4HPA8zqgQ',
    'nested' => ['value' => 'secret'],
];
$envelope = $crypto->encryptPayload($payload);
assertTrue($crypto->decryptEnvelope($envelope) === $payload, 'AES-GCM round trip failed');

$tampered = $envelope;
$tag = base64_decode($tampered['tag'], true);
$tag[0] = chr(ord($tag[0]) ^ 1);
$tampered['tag'] = base64_encode($tag);
try {
    $crypto->decryptEnvelope($tampered);
    throw new RuntimeException('Tampered AES-GCM tag was accepted');
} catch (CryptoException) {
    // Expected.
}

CryptoHelper::validateFreshness($payload, 300);
try {
    CryptoHelper::validateFreshness(array_merge($payload, ['timestamp' => time() - 301]), 300);
    throw new RuntimeException('Stale timestamp was accepted');
} catch (CryptoException) {
    // Expected.
}

$jwt = new JWTHelper(
    base64_encode(random_bytes(64)),
    'onecore-test-issuer',
    'onecore-test-audience'
);
$issued = $jwt->issue($payload['device_id'], 600);
$claims = $jwt->verify($issued['token']);
assertTrue($claims['sub'] === $payload['device_id'], 'JWT device binding failed');
assertTrue($claims['jti'] === $issued['jti'], 'JWT JTI mismatch');

$hashA = IntegrityVerifier::requestHash(
    $payload['device_id'],
    $payload['nonce'],
    $payload['timestamp']
);
$hashB = rtrim(strtr(base64_encode(hash(
    'sha256',
    $payload['device_id'] . "\n" . $payload['nonce'] . "\n" . $payload['timestamp'],
    true
)), '+/', '-_'), '=');
assertTrue(hash_equals($hashA, $hashB), 'Play Integrity request hash mismatch');

echo "All backend crypto/JWT/request-binding tests passed.\n";
