<?php
declare(strict_types=1);

final class CryptoHelper
{
    private const CIPHER = 'aes-256-gcm';
    private const AAD = 'sdk-panel-v2';
    private string $key;

    public function __construct(string $base64Key)
    {
        $decoded = base64_decode($base64Key, true);
        if ($decoded === false || strlen($decoded) !== 32) {
            throw new RuntimeException('ENCRYPTION_KEY must be base64 of exactly 32 random bytes.');
        }
        $this->key = $decoded;
    }

    public function decryptEnvelope(array $envelope): array
    {
        foreach (['iv', 'ciphertext', 'tag'] as $field) {
            if (!isset($envelope[$field]) || !is_string($envelope[$field])) {
                throw new RuntimeException('Invalid encrypted envelope.');
            }
        }
        $iv = base64_decode($envelope['iv'], true);
        $ciphertext = base64_decode($envelope['ciphertext'], true);
        $tag = base64_decode($envelope['tag'], true);
        if ($iv === false || strlen($iv) !== 12 || $ciphertext === false || $tag === false || strlen($tag) !== 16) {
            throw new RuntimeException('Invalid encrypted envelope encoding.');
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
            throw new RuntimeException('Encrypted request authentication failed.');
        }
        $payload = json_decode($plaintext, true, 32, JSON_THROW_ON_ERROR);
        if (!is_array($payload)) {
            throw new RuntimeException('Decrypted payload must be an object.');
        }
        return $payload;
    }

    public function encryptPayload(array $payload): array
    {
        $iv = random_bytes(12);
        $tag = '';
        $plaintext = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        $ciphertext = openssl_encrypt(
            $plaintext,
            self::CIPHER,
            $this->key,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::AAD,
            16
        );
        if ($ciphertext === false || strlen($tag) !== 16) {
            throw new RuntimeException('Response encryption failed.');
        }
        return [
            'version' => 2,
            'iv' => base64_encode($iv),
            'ciphertext' => base64_encode($ciphertext),
            'tag' => base64_encode($tag),
        ];
    }
}
