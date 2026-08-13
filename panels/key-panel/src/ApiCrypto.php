<?php

declare(strict_types=1);

namespace ParallaxPanel;

use JsonException;
use RuntimeException;

final class ApiCrypto
{
    public const VERSION = 2;
    public const REQUEST_AAD = 'parallax-license-v2-request';
    public const RESPONSE_AAD_PREFIX = 'parallax-license-v2-response:';
    private const TAG_BYTES = 16;
    private const MAX_ENVELOPE_BYTES = 32768;

    public static function ensureKeyPair(): void
    {
        [$privatePath, $publicPath] = self::keyPaths();
        if (is_file($privatePath) && is_file($publicPath)) {
            return;
        }
        if (!extension_loaded('openssl')) {
            throw new RuntimeException('OpenSSL PHP extension is required.');
        }
        $key = openssl_pkey_new([
            'private_key_type' => OPENSSL_KEYTYPE_RSA,
            'private_key_bits' => 3072,
        ]);
        if ($key === false || !openssl_pkey_export($key, $privatePem)) {
            throw new RuntimeException('Could not generate the API private key.');
        }
        $details = openssl_pkey_get_details($key);
        if (!is_array($details) || !isset($details['key'])) {
            throw new RuntimeException('Could not export the API public key.');
        }
        $publicDer = self::pemToDer((string) $details['key']);
        if (!is_dir(dirname($privatePath)) && !mkdir(dirname($privatePath), 0700, true)) {
            throw new RuntimeException('Could not create the protected runtime folder.');
        }
        if (file_put_contents($privatePath, $privatePem, LOCK_EX) === false
            || file_put_contents($publicPath, base64_encode($publicDer), LOCK_EX) === false) {
            throw new RuntimeException('Could not save the API key pair.');
        }
        @chmod($privatePath, 0600);
        @chmod($publicPath, 0640);
    }

    public static function publicKeyBase64(): string
    {
        self::ensureKeyPair();
        [, $publicPath] = self::keyPaths();
        $value = trim((string) file_get_contents($publicPath));
        if ($value === '' || base64_decode($value, true) === false) {
            throw new RuntimeException('API public key is invalid.');
        }
        return $value;
    }

    /** @return array{payload:array<string,mixed>,key:string,nonce:string} */
    public static function decryptRequest(string $raw): array
    {
        if ($raw === '' || strlen($raw) > self::MAX_ENVELOPE_BYTES) {
            throw new RuntimeException('Invalid encrypted request.');
        }
        try {
            $envelope = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            throw new RuntimeException('Invalid encrypted request.');
        }
        if (!is_array($envelope) || (int) ($envelope['v'] ?? 0) !== self::VERSION) {
            throw new RuntimeException('Unsupported encrypted request.');
        }
        $wrappedKey = self::decode((string) ($envelope['k'] ?? ''), null, 256, 512);
        $iv = self::decode((string) ($envelope['iv'] ?? ''), 12);
        $ciphertext = self::decode((string) ($envelope['ct'] ?? ''), null, 1, 16384);
        $tag = self::decode((string) ($envelope['tag'] ?? ''), self::TAG_BYTES);
        [$privatePath] = self::keyPaths();
        self::ensureKeyPair();
        $privatePem = file_get_contents($privatePath);
        if (!is_string($privatePem)
            || !openssl_private_decrypt($wrappedKey, $sessionKey, $privatePem, OPENSSL_PKCS1_OAEP_PADDING)
            || strlen($sessionKey) !== 32) {
            throw new RuntimeException('Encrypted session key was rejected.');
        }
        $plain = openssl_decrypt(
            $ciphertext,
            'aes-256-gcm',
            $sessionKey,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::REQUEST_AAD
        );
        if (!is_string($plain)) {
            throw new RuntimeException('Encrypted request authentication failed.');
        }
        try {
            $payload = json_decode($plain, true, 16, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            throw new RuntimeException('Encrypted request payload is invalid.');
        }
        if (!is_array($payload)) {
            throw new RuntimeException('Encrypted request payload is invalid.');
        }
        $nonce = (string) ($payload['nonce'] ?? '');
        if (preg_match('/^[A-Za-z0-9_-]{22,64}$/D', $nonce) !== 1) {
            throw new RuntimeException('Encrypted request binding is invalid.');
        }
        return ['payload' => $payload, 'key' => $sessionKey, 'nonce' => $nonce];
    }

    /** @param array<string,mixed> $payload */
    public static function encryptResponse(array $payload, string $sessionKey, string $requestNonce): array
    {
        if (strlen($sessionKey) !== 32) {
            throw new RuntimeException('Invalid response session key.');
        }
        $iv = random_bytes(12);
        $plain = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        $ciphertext = openssl_encrypt(
            $plain,
            'aes-256-gcm',
            $sessionKey,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::RESPONSE_AAD_PREFIX . $requestNonce,
            self::TAG_BYTES
        );
        if (!is_string($ciphertext) || strlen($tag) !== self::TAG_BYTES) {
            throw new RuntimeException('Could not encrypt API response.');
        }
        return [
            'v' => self::VERSION,
            'iv' => base64_encode($iv),
            'ct' => base64_encode($ciphertext),
            'tag' => base64_encode($tag),
        ];
    }

    /** @return array{0:string,1:string} */
    private static function keyPaths(): array
    {
        $private = self::safeRuntimePath(Env::get('API_PRIVATE_KEY_PATH', 'runtime/api-private.pem'));
        $public = self::safeRuntimePath(Env::get('API_PUBLIC_KEY_PATH', 'runtime/api-public.b64'));
        return [$private, $public];
    }

    private static function safeRuntimePath(string $relative): string
    {
        $normalized = str_replace('\\', '/', trim($relative));
        if ($normalized === '' || str_contains($normalized, '..')
            || !str_starts_with($normalized, 'runtime/')) {
            throw new RuntimeException('API key paths must stay inside runtime/.');
        }
        return PANEL_ROOT . '/' . $normalized;
    }

    private static function pemToDer(string $pem): string
    {
        $value = preg_replace('/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/', '', $pem);
        $der = base64_decode((string) $value, true);
        if ($der === false) {
            throw new RuntimeException('Could not encode the API public key.');
        }
        return $der;
    }

    private static function decode(string $value, ?int $exact, int $minimum = 0, int $maximum = 4096): string
    {
        $decoded = base64_decode($value, true);
        $length = $decoded === false ? -1 : strlen($decoded);
        if ($decoded === false || ($exact !== null && $length !== $exact)
            || $length < $minimum || $length > $maximum) {
            throw new RuntimeException('Encrypted request fields are invalid.');
        }
        return $decoded;
    }
}
