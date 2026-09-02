<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$panelRoot = dirname(__DIR__);
$trustPath = $panelRoot . '/sdk-v3-public-trust.json';
if (!is_file($trustPath) || !is_readable($trustPath)) {
    fwrite(STDERR, "[FAIL] Missing sdk-v3-public-trust.json\n");
    exit(2);
}

try {
    $trust = json_decode((string) file_get_contents($trustPath), true, 16, JSON_THROW_ON_ERROR);
} catch (Throwable $throwable) {
    fwrite(STDERR, "[FAIL] Invalid public trust JSON: {$throwable->getMessage()}\n");
    exit(2);
}
if (!is_array($trust) || (int) ($trust['contract_version'] ?? 0) !== 3) {
    fwrite(STDERR, "[FAIL] Public trust contract_version must be 3.\n");
    exit(2);
}

$configCandidates = [];
$explicitConfig = trim((string) getenv('SDK_PANEL_CONFIG'));
if ($explicitConfig !== '') {
    $configCandidates[] = $explicitConfig;
}
$configCandidates[] = dirname($panelRoot) . '/private/sdk-panel-config.php';
$configCandidates[] = $panelRoot . '/config.local.php';

$configPath = null;
foreach ($configCandidates as $candidate) {
    if (is_file($candidate) && is_readable($candidate)) {
        $configPath = $candidate;
        break;
    }
}
if ($configPath === null) {
    fwrite(STDERR, "[FAIL] Private SDK panel config is not readable. Set SDK_PANEL_CONFIG if it is stored elsewhere.\n");
    exit(2);
}

$config = require $configPath;
if (!is_array($config)) {
    fwrite(STDERR, "[FAIL] Private SDK panel config must return an array.\n");
    exit(2);
}

$keyId = trim((string) ($trust['key_id'] ?? ''));
$ecdhExpected = trim((string) ($trust['ecdh_public_key_b64'] ?? ''));
$signingExpected = trim((string) ($trust['signing_public_key_b64'] ?? ''));
$endpoint = trim((string) ($trust['endpoint'] ?? ''));
$endpointSha = strtolower(trim((string) ($trust['endpoint_sha256'] ?? '')));

if (preg_match('/^[A-Za-z0-9._-]{1,64}$/D', $keyId) !== 1) {
    fwrite(STDERR, "[FAIL] Invalid key_id in public trust contract.\n");
    exit(2);
}
foreach (['ECDH' => $ecdhExpected, 'SIGNING' => $signingExpected] as $label => $encoded) {
    $der = base64_decode($encoded, true);
    if (!is_string($der) || strlen($der) < 80 || strlen($der) > 160) {
        fwrite(STDERR, "[FAIL] {$label} public key in trust contract is invalid Base64/DER.\n");
        exit(2);
    }
}
if ($ecdhExpected === $signingExpected) {
    fwrite(STDERR, "[FAIL] ECDH and response-signing public keys must be different.\n");
    exit(2);
}
if ($endpoint === '' || preg_match('/^[a-f0-9]{64}$/D', $endpointSha) !== 1
    || !hash_equals($endpointSha, hash('sha256', $endpoint))) {
    fwrite(STDERR, "[FAIL] Endpoint SHA-256 in public trust contract is inconsistent.\n");
    exit(2);
}

$v3Keys = $config['API_V3_KEYS'] ?? [];
if (!is_array($v3Keys) || !isset($v3Keys[$keyId]) || !is_array($v3Keys[$keyId])) {
    fwrite(STDERR, "[FAIL] API_V3_KEYS does not contain key id '{$keyId}'.\n");
    exit(3);
}
$keyConfig = $v3Keys[$keyId];

/** @return array{b64:string,sha256:string} */
function publicFromPrivateFile(string $path, string $label): array
{
    if ($path === '' || !is_file($path) || !is_readable($path)) {
        throw new RuntimeException("{$label} private key file is unavailable: {$path}");
    }
    $private = openssl_pkey_get_private((string) file_get_contents($path));
    if ($private === false) {
        throw new RuntimeException("{$label} private key is invalid.");
    }
    $details = openssl_pkey_get_details($private);
    if (!is_array($details)
        || ($details['type'] ?? null) !== OPENSSL_KEYTYPE_EC
        || ($details['bits'] ?? 0) !== 256
        || (($details['ec']['curve_name'] ?? '') !== 'prime256v1')
        || !isset($details['key'])
        || !is_string($details['key'])) {
        throw new RuntimeException("{$label} private key must be P-256/prime256v1.");
    }
    $body = preg_replace('/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/', '', $details['key']);
    $der = is_string($body) ? base64_decode($body, true) : false;
    if (!is_string($der) || $der === '') {
        throw new RuntimeException("Could not derive {$label} public DER.");
    }
    return [
        'b64' => base64_encode($der),
        'sha256' => hash('sha256', $der),
    ];
}

try {
    $ecdhActual = publicFromPrivateFile(
        trim((string) ($keyConfig['ecdh_private_key_file'] ?? '')),
        'ECDH'
    );
    $signingActual = publicFromPrivateFile(
        trim((string) ($keyConfig['signing_private_key_file'] ?? '')),
        'SIGNING'
    );
} catch (Throwable $throwable) {
    fwrite(STDERR, "[FAIL] {$throwable->getMessage()}\n");
    exit(3);
}

$ecdhExpectedDer = base64_decode($ecdhExpected, true);
$signingExpectedDer = base64_decode($signingExpected, true);
$ecdhExpectedSha = hash('sha256', (string) $ecdhExpectedDer);
$signingExpectedSha = hash('sha256', (string) $signingExpectedDer);

$ok = true;
printf("SDK V3 contract version : 3\n");
printf("Key ID                  : %s\n", $keyId);
printf("Endpoint SHA-256         : %s\n", $endpointSha);
printf("ECDH expected SHA-256    : %s\n", $ecdhExpectedSha);
printf("ECDH live PEM SHA-256    : %s\n", $ecdhActual['sha256']);
printf("SIGN expected SHA-256    : %s\n", $signingExpectedSha);
printf("SIGN live PEM SHA-256    : %s\n", $signingActual['sha256']);

if (!hash_equals($ecdhExpected, $ecdhActual['b64'])) {
    fwrite(STDERR, "[FAIL] SDK ECDH public trust anchor does not match the live ECDH private PEM.\n");
    $ok = false;
}
if (!hash_equals($signingExpected, $signingActual['b64'])) {
    fwrite(STDERR, "[FAIL] SDK response-signing public trust anchor does not match the live signing private PEM.\n");
    $ok = false;
}
if (hash_equals($ecdhActual['b64'], $signingActual['b64'])) {
    fwrite(STDERR, "[FAIL] Live ECDH and signing private keys resolve to the same public key.\n");
    $ok = false;
}

if (!$ok) {
    fwrite(STDERR, "\nActivation V3 cannot be trusted until these public/private key pairs match.\n");
    fwrite(STDERR, "Do not copy or expose either private PEM. Rotate/update public trust values instead.\n");
    exit(4);
}

fwrite(STDOUT, "[OK] Live panel private keys and SDK V3 public trust anchors match exactly.\n");
fwrite(STDOUT, "[OK] SPECIFIC/AUTO/ANY signing policy remains server-controlled and unchanged.\n");
exit(0);
