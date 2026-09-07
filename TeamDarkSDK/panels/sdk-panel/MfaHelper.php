<?php
declare(strict_types=1);

final class MfaHelper
{
    private const AAD = 'sdk-panel-mfa-v1';
    private const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    private string $dataKey;

    public function __construct(string $base64DataKey)
    {
        $decoded = base64_decode($base64DataKey, true);
        if (!is_string($decoded) || strlen($decoded) !== 32) {
            throw new RuntimeException('PANEL_DATA_KEY must be base64 of exactly 32 random bytes.');
        }
        $this->dataKey = $decoded;
    }

    public function generateSecret(): string
    {
        return self::base32Encode(random_bytes(20));
    }

    public function encryptSecret(string $secret): string
    {
        $iv = random_bytes(12);
        $tag = '';
        $ciphertext = openssl_encrypt(
            $secret,
            'aes-256-gcm',
            $this->dataKey,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::AAD,
            16
        );
        if (!is_string($ciphertext) || strlen($tag) !== 16) {
            throw new RuntimeException('Could not encrypt MFA secret.');
        }
        return 'v1.' . base64_encode($iv) . '.' . base64_encode($ciphertext) . '.' . base64_encode($tag);
    }

    public function decryptSecret(string $encrypted): string
    {
        $parts = explode('.', $encrypted);
        if (count($parts) !== 4 || $parts[0] !== 'v1') {
            throw new RuntimeException('Invalid encrypted MFA secret.');
        }
        [, $ivB64, $ciphertextB64, $tagB64] = $parts;
        $iv = base64_decode($ivB64, true);
        $ciphertext = base64_decode($ciphertextB64, true);
        $tag = base64_decode($tagB64, true);
        if (!is_string($iv) || strlen($iv) !== 12 || !is_string($ciphertext)
            || !is_string($tag) || strlen($tag) !== 16) {
            throw new RuntimeException('Invalid encrypted MFA secret.');
        }
        $secret = openssl_decrypt(
            $ciphertext,
            'aes-256-gcm',
            $this->dataKey,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            self::AAD
        );
        if (!is_string($secret)) {
            throw new RuntimeException('MFA secret authentication failed.');
        }
        return $secret;
    }

    public function verifyTotp(string $secret, string $code, ?int $timestamp = null): bool
    {
        $code = preg_replace('/\D/', '', $code) ?? '';
        if (strlen($code) !== 6) {
            return false;
        }
        $counter = intdiv($timestamp ?? time(), 30);
        for ($offset = -1; $offset <= 1; $offset++) {
            if (hash_equals(self::totpAt($secret, $counter + $offset), $code)) {
                return true;
            }
        }
        return false;
    }

    /** @return list<string> */
    public function generateRecoveryCodes(int $count = 10): array
    {
        $codes = [];
        for ($i = 0; $i < $count; $i++) {
            $raw = strtoupper(bin2hex(random_bytes(6)));
            $codes[] = substr($raw, 0, 6) . '-' . substr($raw, 6, 6);
        }
        return $codes;
    }

    public static function normalizeRecoveryCode(string $code): string
    {
        return strtoupper((string) preg_replace('/[^A-Fa-f0-9]/', '', $code));
    }

    public static function provisioningUri(string $secret, string $username, string $issuer): string
    {
        $label = rawurlencode($issuer . ':' . $username);
        return 'otpauth://totp/' . $label
            . '?secret=' . rawurlencode($secret)
            . '&issuer=' . rawurlencode($issuer)
            . '&algorithm=SHA1&digits=6&period=30';
    }

    private static function totpAt(string $secret, int $counter): string
    {
        $key = self::base32Decode($secret);
        $high = intdiv($counter, 0x100000000);
        $low = $counter % 0x100000000;
        $binaryCounter = pack('N2', $high, $low);
        $hash = hash_hmac('sha1', $binaryCounter, $key, true);
        $offset = ord($hash[19]) & 0x0f;
        $value = ((ord($hash[$offset]) & 0x7f) << 24)
            | ((ord($hash[$offset + 1]) & 0xff) << 16)
            | ((ord($hash[$offset + 2]) & 0xff) << 8)
            | (ord($hash[$offset + 3]) & 0xff);
        return str_pad((string) ($value % 1_000_000), 6, '0', STR_PAD_LEFT);
    }

    private static function base32Encode(string $bytes): string
    {
        $bits = '';
        foreach (str_split($bytes) as $byte) {
            $bits .= str_pad(decbin(ord($byte)), 8, '0', STR_PAD_LEFT);
        }
        $encoded = '';
        foreach (str_split($bits, 5) as $chunk) {
            $encoded .= self::ALPHABET[bindec(str_pad($chunk, 5, '0', STR_PAD_RIGHT))];
        }
        return $encoded;
    }

    private static function base32Decode(string $encoded): string
    {
        $encoded = strtoupper((string) preg_replace('/[^A-Z2-7]/', '', $encoded));
        $bits = '';
        foreach (str_split($encoded) as $character) {
            $position = strpos(self::ALPHABET, $character);
            if ($position === false) {
                throw new RuntimeException('Invalid base32 secret.');
            }
            $bits .= str_pad(decbin($position), 5, '0', STR_PAD_LEFT);
        }
        $decoded = '';
        foreach (str_split($bits, 8) as $chunk) {
            if (strlen($chunk) === 8) {
                $decoded .= chr(bindec($chunk));
            }
        }
        return $decoded;
    }
}
