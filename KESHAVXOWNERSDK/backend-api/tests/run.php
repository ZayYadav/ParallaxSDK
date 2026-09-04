<?php
declare(strict_types=1);

require_once __DIR__ . '/../vendor/autoload.php';
require_once __DIR__ . '/../CryptoHelper.php';
require_once __DIR__ . '/../JWTHelper.php';
require_once __DIR__ . '/../IntegrityVerifier.php';
require_once __DIR__ . '/../SelfHostedVerifier.php';
require_once __DIR__ . '/../Database.php';
require_once __DIR__ . '/../AccountManager.php';

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

$activationKey = SelfHostedVerifier::generateActivationKey();
assertTrue(
    preg_match('/^5OC-(?:[A-F0-9]{4}-){7}[A-F0-9]{4}$/D', $activationKey) === 1,
    'Activation key format is invalid'
);
assertTrue(
    SelfHostedVerifier::isActivationKeyFormat(
        SelfHostedVerifier::generateActivationKey('PARALLAX')
    ),
    'Custom activation-key prefix was rejected'
);
assertTrue(
    AccountManager::normalizeUsername('  Key.Seller_1 ') === 'key.seller_1',
    'Dashboard username normalization failed'
);
AccountManager::validatePassword('SecurePass123');
try {
    AccountManager::validatePassword('short');
    throw new RuntimeException('Weak dashboard password was accepted');
} catch (AccountException) {
    // Expected.
}
$deviceKey = openssl_pkey_new([
    'private_key_type' => OPENSSL_KEYTYPE_EC,
    'curve_name' => 'prime256v1',
]);
assertTrue($deviceKey !== false, 'Unable to generate device test key');
$details = openssl_pkey_get_details($deviceKey);
assertTrue(is_array($details) && is_string($details['key'] ?? null), 'Device public key missing');
$publicKeyDer = base64_decode((string) preg_replace(
    '/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/',
    '',
    $details['key']
), true);
assertTrue(is_string($publicKeyDer), 'Unable to decode device public key');
$deviceId = SelfHostedVerifier::deviceIdForPublicKey($publicKeyDer);
$timestamp = time();
$nonce = 'selfHostedNonceForTests123';
$proofMessage = SelfHostedVerifier::proofMessage(
    'activate',
    $deviceId,
    $nonce,
    $timestamp,
    SelfHostedVerifier::activationKeyHash($activationKey)
);
$signature = '';
assertTrue(
    openssl_sign($proofMessage, $signature, $deviceKey, OPENSSL_ALGO_SHA256),
    'Unable to sign device proof'
);
$certificate = str_repeat('AB', 32);
$selfHosted = new SelfHostedVerifier(['app_package_name' => 'com.onecore.loader']);
$selfHostedVerdict = $selfHosted->verifyActivation([
    'app_package_name' => 'com.onecore.loader',
    'app_certificate_sha256' => $certificate,
    'app_version_code' => 42,
    'device_public_key' => base64_encode($publicKeyDer),
    'device_proof' => base64_encode($signature),
], $deviceId, $nonce, $timestamp, $activationKey, $certificate);
assertTrue(
    $selfHostedVerdict['public_key_sha256'] === hash('sha256', $publicKeyDer),
    'Self-hosted public-key binding failed'
);
try {
    $tamperedProof = $signature;
    $tamperedProof[5] = chr(ord($tamperedProof[5]) ^ 1);
    $selfHosted->verifyActivation([
        'app_package_name' => 'com.onecore.loader',
        'app_certificate_sha256' => $certificate,
        'app_version_code' => 42,
        'device_public_key' => base64_encode($publicKeyDer),
        'device_proof' => base64_encode($tamperedProof),
    ], $deviceId, $nonce, $timestamp, $activationKey, $certificate);
    throw new RuntimeException('Tampered device proof was accepted');
} catch (SelfHostedVerificationException) {
    // Expected.
}

echo "All backend crypto/JWT/request-binding/device-proof tests passed.\n";
