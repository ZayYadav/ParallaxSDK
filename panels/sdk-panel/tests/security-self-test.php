<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/CryptoV3.php';
require_once dirname(__DIR__) . '/MfaHelper.php';

function check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

/** @return array{key:OpenSSLAsymmetricKey,private_path:string,public_der:string} */
function testKeyPair(string $directory, string $name): array
{
    $key = openssl_pkey_new([
        'private_key_type' => OPENSSL_KEYTYPE_EC,
        'curve_name' => 'prime256v1',
    ]);
    check($key !== false, 'Could not generate test EC key.');
    $privatePem = '';
    check(openssl_pkey_export($key, $privatePem), 'Could not export test private key.');
    $path = $directory . DIRECTORY_SEPARATOR . $name . '.pem';
    check(file_put_contents($path, $privatePem) !== false, 'Could not write test private key.');
    $details = openssl_pkey_get_details($key);
    check(is_array($details) && isset($details['key']), 'Could not export test public key.');
    $publicDer = base64_decode((string) preg_replace(
        '/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/',
        '',
        (string) $details['key']
    ), true);
    check(is_string($publicDer), 'Could not decode test public key.');
    return ['key' => $key, 'private_path' => $path, 'public_der' => $publicDer];
}

$dataKey = base64_encode(random_bytes(32));
$mfa = new MfaHelper($dataKey);
$secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
check($mfa->verifyTotp($secret, '287082', 59), 'RFC 6238 TOTP vector failed.');
$encryptedSecret = $mfa->encryptSecret($secret);
check($mfa->decryptSecret($encryptedSecret) === $secret, 'MFA encryption round trip failed.');
$tampered = substr($encryptedSecret, 0, -1) . (str_ends_with($encryptedSecret, 'A') ? 'B' : 'A');
$tamperRejected = false;
try {
    $mfa->decryptSecret($tampered);
} catch (Throwable) {
    $tamperRejected = true;
}
check($tamperRejected, 'Tampered MFA secret was accepted.');

$temporary = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'sdk-panel-security-' . bin2hex(random_bytes(6));
check(mkdir($temporary, 0700), 'Could not create test directory.');
try {
    $serverEcdh = testKeyPair($temporary, 'server-ecdh');
    $serverSigning = testKeyPair($temporary, 'server-signing');
    $clientEcdh = testKeyPair($temporary, 'client-ecdh');
    $device = testKeyPair($temporary, 'device');
    $keyId = 'test-key';
    $crypto = new CryptoV3([
        $keyId => [
            'ecdh_private_key_file' => $serverEcdh['private_path'],
            'signing_private_key_file' => $serverSigning['private_path'],
        ],
    ]);

    $timestamp = time();
    $nonce = rtrim(strtr(base64_encode(random_bytes(24)), '+/', '-_'), '=');
    $clientRequestId = rtrim(strtr(base64_encode(random_bytes(24)), '+/', '-_'), '=');
    $payload = [
        'user_key' => 'SDK-TEST',
        'package_name' => 'com.example.test',
        'app_name' => 'Test',
        'device_id' => 'test-device',
        'app_signature_sha256' => str_repeat('A', 64),
        'timestamp' => $timestamp,
        'nonce' => $nonce,
        'request_id' => $clientRequestId,
        'sdk_version' => 3,
        'device_public_key' => base64_encode($device['public_der']),
    ];
    $proofCanonical = implode("\n", [
        '3', $payload['user_key'], $payload['package_name'], $payload['device_id'],
        $payload['app_signature_sha256'], (string) $timestamp, $nonce, $clientRequestId, '3',
    ]);
    $proofSignature = '';
    check(openssl_sign($proofCanonical, $proofSignature, $device['key'], OPENSSL_ALGO_SHA256), 'Device proof signing failed.');
    $payload['device_signature'] = base64_encode($proofSignature);
    $proof = CryptoV3::verifyDeviceProof($payload);
    check($proof['fingerprint'] === hash('sha256', $device['public_der']), 'Device proof fingerprint mismatch.');

    $serverPublic = openssl_pkey_get_public(
        "-----BEGIN PUBLIC KEY-----\n"
        . chunk_split(base64_encode($serverEcdh['public_der']), 64, "\n")
        . "-----END PUBLIC KEY-----\n"
    );
    check($serverPublic !== false, 'Could not load server test public key.');
    $shared = openssl_pkey_derive($serverPublic, $clientEcdh['key'], 32);
    check(is_string($shared), 'Client ECDH failed.');
    $ephemeralB64 = base64_encode($clientEcdh['public_der']);
    $salt = hash('sha256', 'sdk-panel-v3|' . $keyId, true);
    $aesKey = hash_hkdf('sha256', $shared, 32, 'request|' . $ephemeralB64, $salt);
    $requestIv = random_bytes(12);
    $requestTag = '';
    $ciphertext = openssl_encrypt(
        json_encode($payload, JSON_THROW_ON_ERROR),
        'aes-256-gcm',
        $aesKey,
        OPENSSL_RAW_DATA,
        $requestIv,
        $requestTag,
        'sdk-panel-v3|' . $keyId . '|' . $ephemeralB64,
        16
    );
    check(is_string($ciphertext), 'Test request encryption failed.');
    $opened = $crypto->openRequest([
        'version' => 3,
        'key_id' => $keyId,
        'ephemeral_key' => $ephemeralB64,
        'iv' => base64_encode($requestIv),
        'ciphertext' => base64_encode($ciphertext),
        'tag' => base64_encode($requestTag),
    ]);
    check($opened['payload']['user_key'] === 'SDK-TEST', 'V3 request round trip failed.');
    check(hash_equals($opened['aes_key'], $aesKey), 'V3 derived keys differ.');

    $serverTime = time();
    $responsePayload = [
        'status' => 'success',
        'server_time' => $serverTime,
        'app_signature_sha256' => str_repeat('A', 64),
        'authorized_package' => 'com.example.test',
        'authorized_signing_sha256' => str_repeat('A', 64),
        'device_key_fingerprint' => $proof['fingerprint'],
        'session_id' => bin2hex(random_bytes(16)),
        'request_id' => $clientRequestId,
        'lease_expires_at' => $serverTime + 600,
        'package_policy' => 'SPECIFIC',
        'signing_policy' => 'AUTO',
        'device_policy' => 'SINGLE',
        'sdk_version' => 3,
    ];

    $signingPublic = openssl_pkey_get_public(
        "-----BEGIN PUBLIC KEY-----\n"
        . chunk_split(base64_encode($serverSigning['public_der']), 64, "\n")
        . "-----END PUBLIC KEY-----\n"
    );
    check($signingPublic !== false, 'Could not load signing test public key.');

    $identityCanonical = CryptoV3::identityCanonical($responsePayload, $keyId);
    $identitySignature = $crypto->signIdentityBinding($responsePayload, $keyId);
    check(
        openssl_verify(
            $identityCanonical,
            base64_decode($identitySignature, true),
            $signingPublic,
            OPENSSL_ALGO_SHA256
        ) === 1,
        'V3 identity binding signature failed.'
    );

    $tamperedIdentity = $responsePayload;
    $tamperedIdentity['authorized_package'] = 'com.attacker.repacked';
    check(
        openssl_verify(
            CryptoV3::identityCanonical($tamperedIdentity, $keyId),
            base64_decode($identitySignature, true),
            $signingPublic,
            OPENSSL_ALGO_SHA256
        ) !== 1,
        'Tampered V3 identity binding was accepted.'
    );

    $sealed = $crypto->sealResponse($responsePayload, [
        'aes_key' => $opened['aes_key'],
        'key_id' => $opened['key_id'],
        'request_id' => $opened['request_id'],
    ]);
    $responseCanonical = implode("\n", [
        '3', $sealed['key_id'], $sealed['request_id'], $sealed['iv'],
        $sealed['ciphertext'], $sealed['tag'],
    ]);
    check(
        openssl_verify(
            $responseCanonical,
            base64_decode((string) $sealed['signature'], true),
            $signingPublic,
            OPENSSL_ALGO_SHA256
        ) === 1,
        'V3 response signature failed.'
    );
} finally {
    foreach (glob($temporary . DIRECTORY_SEPARATOR . '*') ?: [] as $file) {
        @unlink($file);
    }
    @rmdir($temporary);
}

echo "SDK panel security self-test passed.\n";
