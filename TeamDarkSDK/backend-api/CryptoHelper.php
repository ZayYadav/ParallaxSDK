<?php
declare(strict_types=1);

final class CryptoException extends RuntimeException
{
}

final class CryptoHelper
{
    private const CIPHER = 'aes-256-gcm';
    private const AAD = 'onecore-api-v1';
    private const IV_BYTES = 12;
    private const TAG_BYTES = 16;

    private string $key;

    public function __construct(string $base64Key)
    {
        $decoded = base64_decode($base64Key, true);
        if ($decoded === false || strlen($decoded) !== 32) {
            throw new CryptoException('ENCRYPTION_KEY must be base64 for exactly 32 bytes');
        }
        $this->key = $decoded;
    }

    public function decryptEnvelope(array $envelope): array
    {
        foreach (['iv', 'tag', 'ciphertext'] as $field) {
            if (!isset($envelope[$field]) || !is_string($envelope[$field])) {
                throw new CryptoException('Invalid encrypted envelope');
            }
        }

        $version = $envelope['version'] ?? 1;
        if ($version !== 1) {
            throw new CryptoException('Unsupported encrypted envelope version');
        }

        $iv = base64_decode($envelope['iv'], true);
        $tag = base64_decode($envelope['tag'], true);
        $ciphertext = base64_decode($envelope['ciphertext'], true);
        if ($iv === false || strlen($iv) !== self::IV_BYTES
            || $tag === false || strlen($tag) !== self::TAG_BYTES
            || $ciphertext === false) {
            throw new CryptoException('Malformed encrypted envelope');
        }

        $plaintext = openssl_decrypt(
            $ciphertext,
            self::CIPHER,
            $this->key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::AAD
        );
        if ($plaintext === false) {
            throw new CryptoException('Encrypted request authentication failed');
        }

        try {
            $payload = json_decode($plaintext, true, 64, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new CryptoException('Decrypted request is not valid JSON', 0, $exception);
        }
        if (!is_array($payload)) {
            throw new CryptoException('Decrypted request must be a JSON object');
        }
        return $payload;
    }

    public function encryptPayload(array $payload): array
    {
        $iv = random_bytes(self::IV_BYTES);
        $tag = '';
        $plaintext = json_encode(
            $payload,
            JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE
        );
        $ciphertext = openssl_encrypt(
            $plaintext,
            self::CIPHER,
            $this->key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::AAD,
            self::TAG_BYTES
        );
        if ($ciphertext === false) {
            throw new CryptoException('Unable to encrypt response');
        }

        return [
            'version' => 1,
            'iv' => base64_encode($iv),
            'tag' => base64_encode($tag),
            'ciphertext' => base64_encode($ciphertext),
        ];
    }

    public static function validateFreshness(array $payload, int $windowSeconds): void
    {
        if (!isset($payload['timestamp']) || !is_int($payload['timestamp'])) {
            throw new CryptoException('Integer timestamp is required');
        }
        if (abs(time() - $payload['timestamp']) > $windowSeconds) {
            throw new CryptoException('Request timestamp is outside the allowed window');
        }

        $nonce = $payload['nonce'] ?? null;
        if (!is_string($nonce)
            || preg_match('/^[A-Za-z0-9_-]{16,128}$/D', $nonce) !== 1) {
            throw new CryptoException('A 16-128 character base64url nonce is required');
        }
    }

    public static function aad(): string
    {
        return self::AAD;
    }
}
