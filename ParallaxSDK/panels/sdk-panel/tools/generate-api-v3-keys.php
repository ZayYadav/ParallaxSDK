<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$target = $argv[1] ?? '';
$keyId = $argv[2] ?? ('parallax-' . gmdate('Y-m'));
if ($target === '' || preg_match('/^[A-Za-z0-9._-]{1,64}$/D', $keyId) !== 1) {
    fwrite(STDERR, "Usage: php tools/generate-api-v3-keys.php /absolute/private/directory [key-id]\n");
    exit(2);
}
$target = rtrim($target, DIRECTORY_SEPARATOR);
if (!is_dir($target) && !mkdir($target, 0700, true)) {
    fwrite(STDERR, "Unable to create private key directory.\n");
    exit(1);
}
$resolved = realpath($target);
$publicRoot = realpath(dirname(__DIR__));
if ($resolved === false || $publicRoot === false
    || str_starts_with(strtolower($resolved), strtolower($publicRoot . DIRECTORY_SEPARATOR))) {
    fwrite(STDERR, "Refusing to store private keys inside the public SDK panel directory.\n");
    exit(1);
}

/** @return array{private_path:string,public_der_b64:string} */
function generateP256Pair(string $directory, string $name): array
{
    $key = openssl_pkey_new([
        'private_key_type' => OPENSSL_KEYTYPE_EC,
        'curve_name' => 'prime256v1',
    ]);
    if ($key === false) {
        throw new RuntimeException('OpenSSL could not generate a P-256 key.');
    }
    $privatePem = '';
    if (!openssl_pkey_export($key, $privatePem)) {
        throw new RuntimeException('OpenSSL could not export the private key.');
    }
    $details = openssl_pkey_get_details($key);
    if (!is_array($details) || !isset($details['key'])) {
        throw new RuntimeException('OpenSSL could not export the public key.');
    }
    $publicPem = (string) $details['key'];
    $publicDer = base64_decode((string) preg_replace(
        '/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/',
        '',
        $publicPem
    ), true);
    if (!is_string($publicDer)) {
        throw new RuntimeException('OpenSSL returned an invalid public key.');
    }
    $privatePath = $directory . DIRECTORY_SEPARATOR . $name . '-private.pem';
    if (file_put_contents($privatePath, $privatePem, LOCK_EX) === false) {
        throw new RuntimeException('Could not write private key file.');
    }
    @chmod($privatePath, 0600);
    return ['private_path' => $privatePath, 'public_der_b64' => base64_encode($publicDer)];
}

try {
    $ecdh = generateP256Pair($resolved, 'sdk-api-v3-ecdh');
    $signing = generateP256Pair($resolved, 'sdk-api-v3-signing');
    $dataKey = base64_encode(random_bytes(32));
} catch (Throwable $throwable) {
    fwrite(STDERR, $throwable->getMessage() . "\n");
    exit(1);
}

echo "Generated private server keys. Keep these outside public_html and out of Git.\n\n";
echo "Private panel config:\n";
echo "'PANEL_DATA_KEY' => '" . $dataKey . "',\n";
echo "'API_V3_KEYS' => [\n";
echo "    '" . $keyId . "' => [\n";
echo "        'ecdh_private_key_file' => '" . str_replace('\\', '/', $ecdh['private_path']) . "',\n";
echo "        'signing_private_key_file' => '" . str_replace('\\', '/', $signing['private_path']) . "',\n";
echo "    ],\n];\n\n";
echo "Android Gradle properties (public values):\n";
echo "sdkPanelKeyId=" . $keyId . "\n";
echo "sdkPanelEcdhPublicKey=" . $ecdh['public_der_b64'] . "\n";
echo "sdkPanelSigningPublicKey=" . $signing['public_der_b64'] . "\n";
