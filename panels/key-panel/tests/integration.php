<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
require PANEL_ROOT . '/src/Database.php';
require PANEL_ROOT . '/src/GenerationOptions.php';
require PANEL_ROOT . '/src/ApiCrypto.php';

$assertions = 0;
$assert = static function (bool $condition, string $message) use (&$assertions): void {
    $assertions++;
    if (!$condition) {
        throw new \RuntimeException($message);
    }
};

Database::install();
$db = Database::connection();
$assert(Database::installed(), 'Installed schema was not detected.');
$assert(GenerationOptions::games($db) === ['PUBG' => 'PUBG'], 'Default game option is missing.');
$defaultDurations = GenerationOptions::durations($db);
$assert(($defaultDurations[24] ?? null) === '1 Day', 'Default 24-hour duration is missing.');

$password = password_hash('integration-test-password', PASSWORD_DEFAULT);
$db->prepare("INSERT INTO panel_users (username,password_hash,telegram_user_id,role) VALUES (?,?,?, 'owner')")
    ->execute(['ci_owner', $password, '123456789']);
$ownerId = (int) $db->lastInsertId();
$assert($ownerId > 0, 'Owner insert failed.');

$db->prepare('INSERT INTO keys_code (game,user_key,duration,max_devices,registrator,admin_id) VALUES (?,?,?,?,?,?)')
    ->execute(['PUBG', 'CI-LEGACY-KEY', 24, 1, 'ci_owner', $ownerId]);
$keyRecord = $db->query("SELECT * FROM keys_code WHERE user_key='CI-LEGACY-KEY'")->fetch();
$assert(is_array($keyRecord) && $keyRecord['game'] === 'PUBG', 'Key insert failed.');

GenerationOptions::replace(
    $db,
    GenerationOptions::parse(GenerationOptions::GAME, "BGMI|Battlegrounds Mobile India\nPUBG|PUBG Mobile"),
    GenerationOptions::parse(GenerationOptions::DURATION, "12|12 Hours\n48|2 Days")
);
$assert(GenerationOptions::contains($db, GenerationOptions::GAME, 'BGMI'), 'Saved game option was not found.');
$assert(!GenerationOptions::contains($db, GenerationOptions::DURATION, '24'), 'Removed duration option is still active.');

$assert((int) $db->query('SELECT COUNT(*) FROM onoff WHERE id=1')->fetchColumn() === 1, 'Default server settings missing.');

ApiCrypto::ensureKeyPair();
$publicDer = base64_decode(ApiCrypto::publicKeyBase64(), true);
$assert(is_string($publicDer) && strlen($publicDer) > 256, 'API public key generation failed.');
$publicPem = "-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode($publicDer), 64, "\n") . "-----END PUBLIC KEY-----\n";
$sessionKey = random_bytes(32);
$iv = random_bytes(12);
$nonce = rtrim(strtr(base64_encode(random_bytes(18)), '+/', '-_'), '=');
$canary = rtrim(strtr(base64_encode(random_bytes(18)), '+/', '-_'), '=');
$payload = json_encode([
    'game' => 'PUBG',
    'user_key' => 'CI-LEGACY-KEY',
    'serial' => str_repeat('A', 43),
    'nonce' => $nonce,
    'canary' => $canary,
    'timestamp' => time(),
    'version_code' => 1,
    'package_name' => 'com.onecore.loader',
    'certificate_sha256' => str_repeat('A', 64),
], JSON_THROW_ON_ERROR);
$ciphertext = openssl_encrypt($payload, 'aes-256-gcm', $sessionKey, OPENSSL_RAW_DATA, $iv, $tag, ApiCrypto::REQUEST_AAD, 16);
$assert(is_string($ciphertext), 'Client AES encryption failed.');
$assert(openssl_public_encrypt($sessionKey, $wrapped, $publicPem, OPENSSL_PKCS1_OAEP_PADDING), 'Client RSA wrapping failed.');
$request = json_encode([
    'v' => 2,
    'k' => base64_encode($wrapped),
    'iv' => base64_encode($iv),
    'ct' => base64_encode($ciphertext),
    'tag' => base64_encode($tag),
], JSON_THROW_ON_ERROR);
$decrypted = ApiCrypto::decryptRequest($request);
$assert($decrypted['payload']['canary'] === $canary, 'Encrypted request canary did not round-trip.');
$assert(hash_equals($sessionKey, $decrypted['key']), 'Wrapped session key did not round-trip.');
$unexpectedEnvelope = json_decode($request, true, 16, JSON_THROW_ON_ERROR);
$unexpectedEnvelope['debug'] = true;
try {
    ApiCrypto::decryptRequest(json_encode($unexpectedEnvelope, JSON_THROW_ON_ERROR));
    $assert(false, 'Unexpected encrypted request envelope fields must fail.');
} catch (\RuntimeException) {
    $assert(true, 'Unexpected encrypted request envelope fields rejected.');
}
$response = ApiCrypto::encryptResponse(['status' => true, 'request_nonce' => $nonce], $sessionKey, $nonce);
$responseIv = base64_decode($response['iv'], true);
$responseCiphertext = base64_decode($response['ct'], true);
$responseTag = base64_decode($response['tag'], true);
$responsePlain = openssl_decrypt(
    $responseCiphertext,
    'aes-256-gcm',
    $sessionKey,
    OPENSSL_RAW_DATA,
    $responseIv,
    $responseTag,
    ApiCrypto::RESPONSE_AAD_PREFIX . $nonce
);
$assert(is_string($responsePlain) && json_decode($responsePlain, true)['request_nonce'] === $nonce, 'Encrypted response did not round-trip.');

@unlink(PANEL_ROOT . '/runtime/api-private.pem');
@unlink(PANEL_ROOT . '/runtime/api-public.b64');

echo "Standalone MySQL integration passed ($assertions assertions).\n";
