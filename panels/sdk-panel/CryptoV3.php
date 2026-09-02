<?php
declare(strict_types=1);

/**
 * API v3 uses ephemeral P-256 ECDH so the APK contains public keys only.
 * Responses are encrypted with the derived request key and signed by a
 * separate P-256 server key. Repointing the client to another panel therefore
 * cannot produce an accepted activation response.
 */
final class CryptoV3
{
    private const REQUEST_AAD_PREFIX = 'sdk-panel-v3';
    private const RESPONSE_AAD_PREFIX = 'sdk-panel-v3-response';
    private const IDENTITY_PREFIX = 'sdk-panel-v3-identity';

    /** @var array<string,array{ecdh_private_key_file:string,signing_private_key_file:string}> */
    private array $keys;

    public function __construct(array $keys)
    {
        if ($keys === []) {
            throw new RuntimeException('API_V3_KEYS is not configured.');
        }
        $this->keys = $keys;
    }

    /**
     * @return array{payload:array<string,mixed>,aes_key:string,key_id:string,request_id:string}
     */
    public function openRequest(array $envelope): array
    {
        if (($envelope['version'] ?? null) !== 3) {
            throw new RuntimeException('Unsupported API envelope version.');
        }
        $keyId = (string) ($envelope['key_id'] ?? '');
        if (preg_match('/^[A-Za-z0-9._-]{1,64}$/D', $keyId) !== 1 || !isset($this->keys[$keyId])) {
            throw new RuntimeException('Unknown API key id.');
        }
        foreach (['ephemeral_key', 'iv', 'ciphertext', 'tag'] as $field) {
            if (!isset($envelope[$field]) || !is_string($envelope[$field])) {
                throw new RuntimeException('Invalid v3 request envelope.');
            }
        }

        $ephemeralB64 = $envelope['ephemeral_key'];
        $ephemeralDer = self::decodeBase64($ephemeralB64, 65, 256);
        $iv = self::decodeBase64($envelope['iv'], 12, 12);
        $ciphertext = self::decodeBase64($envelope['ciphertext'], 1, 32768);
        $tag = self::decodeBase64($envelope['tag'], 16, 16);

        $clientPublic = openssl_pkey_get_public(self::derToPem($ephemeralDer, 'PUBLIC KEY'));
        if ($clientPublic === false) {
            throw new RuntimeException('Invalid ephemeral public key.');
        }
        $details = openssl_pkey_get_details($clientPublic);
        if (!is_array($details)
            || ($details['type'] ?? null) !== OPENSSL_KEYTYPE_EC
            || ($details['bits'] ?? 0) !== 256
            || (($details['ec']['curve_name'] ?? '') !== 'prime256v1')) {
            throw new RuntimeException('Ephemeral key must be P-256.');
        }

        $private = $this->loadPrivateKey($this->keys[$keyId]['ecdh_private_key_file'] ?? '');
        $sharedSecret = openssl_pkey_derive($clientPublic, $private, 32);
        if (!is_string($sharedSecret) || strlen($sharedSecret) < 16) {
            throw new RuntimeException('ECDH key agreement failed.');
        }
        $salt = hash('sha256', self::REQUEST_AAD_PREFIX . '|' . $keyId, true);
        $aesKey = hash_hkdf('sha256', $sharedSecret, 32, 'request|' . $ephemeralB64, $salt);
        if (strlen($aesKey) !== 32) {
            throw new RuntimeException('Request key derivation failed.');
        }
        $aad = self::REQUEST_AAD_PREFIX . '|' . $keyId . '|' . $ephemeralB64;
        $plaintext = openssl_decrypt(
            $ciphertext,
            'aes-256-gcm',
            $aesKey,
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            $aad
        );
        if (!is_string($plaintext)) {
            throw new RuntimeException('V3 request authentication failed.');
        }
        $payload = json_decode($plaintext, true, 32, JSON_THROW_ON_ERROR);
        if (!is_array($payload)) {
            throw new RuntimeException('V3 request payload must be an object.');
        }
        $nonce = (string) ($payload['nonce'] ?? '');
        if ($nonce === '') {
            throw new RuntimeException('V3 request nonce is missing.');
        }

        return [
            'payload' => $payload,
            'aes_key' => $aesKey,
            'key_id' => $keyId,
            'request_id' => hash('sha256', $nonce),
        ];
    }

    /**
     * @param array{aes_key:string,key_id:string,request_id:string} $context
     * @return array<string,int|string>
     */
    public function sealResponse(array $payload, array $context): array
    {
        $keyId = $context['key_id'];
        if (!isset($this->keys[$keyId])) {
            throw new RuntimeException('Unknown response key id.');
        }

        if (($payload['status'] ?? '') === 'success') {
            $payload['identity_signature'] = $this->signIdentityBinding($payload, $keyId);
        }

        $requestId = $context['request_id'];
        $iv = random_bytes(12);
        $tag = '';
        $aad = self::RESPONSE_AAD_PREFIX . '|' . $keyId . '|' . $requestId;
        $plaintext = json_encode(
            $payload,
            JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR
        );
        $ciphertext = openssl_encrypt(
            $plaintext,
            'aes-256-gcm',
            $context['aes_key'],
            OPENSSL_RAW_DATA,
            $iv,
            $tag,
            $aad,
            16
        );
        if (!is_string($ciphertext) || strlen($tag) !== 16) {
            throw new RuntimeException('V3 response encryption failed.');
        }

        $ivB64 = base64_encode($iv);
        $ciphertextB64 = base64_encode($ciphertext);
        $tagB64 = base64_encode($tag);
        $canonical = implode("\n", ['3', $keyId, $requestId, $ivB64, $ciphertextB64, $tagB64]);
        $signingKey = $this->loadPrivateKey($this->keys[$keyId]['signing_private_key_file'] ?? '');
        $signature = '';
        if (!openssl_sign($canonical, $signature, $signingKey, OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('V3 response signing failed.');
        }

        return [
            'version' => 3,
            'key_id' => $keyId,
            'request_id' => $requestId,
            'iv' => $ivB64,
            'ciphertext' => $ciphertextB64,
            'tag' => $tagB64,
            'signature' => base64_encode($signature),
        ];
    }

    /**
     * Signs the security-critical plaintext identity fields separately from the
     * encrypted envelope. Native code can therefore bind the running APK to the
     * exact package/signing/lease values accepted by the panel instead of
     * trusting Java-passed plaintext alone.
     */
    public function signIdentityBinding(array $payload, string $keyId): string
    {
        if (!isset($this->keys[$keyId])) {
            throw new RuntimeException('Unknown identity-signing key id.');
        }
        $canonical = self::identityCanonical($payload, $keyId);
        $signingKey = $this->loadPrivateKey($this->keys[$keyId]['signing_private_key_file'] ?? '');
        $signature = '';
        if (!openssl_sign($canonical, $signature, $signingKey, OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('V3 identity signing failed.');
        }
        return base64_encode($signature);
    }

    public static function identityCanonical(array $payload, string $keyId): string
    {
        return implode("\n", [
            self::IDENTITY_PREFIX,
            $keyId,
            (string) ($payload['app_signature_sha256'] ?? ''),
            (string) ($payload['authorized_package'] ?? ''),
            (string) ($payload['authorized_signing_sha256'] ?? ''),
            (string) ($payload['device_key_fingerprint'] ?? ''),
            (string) ($payload['session_id'] ?? ''),
            (string) ($payload['request_id'] ?? ''),
            (string) ($payload['lease_expires_at'] ?? ''),
            (string) ($payload['server_time'] ?? ''),
            (string) ($payload['package_policy'] ?? ''),
            (string) ($payload['signing_policy'] ?? ''),
            (string) ($payload['device_policy'] ?? ''),
            (string) ($payload['sdk_version'] ?? ''),
        ]);
    }

    /**
     * Verifies possession of a per-install Android Keystore P-256 key.
     *
     * @return array{public_key_pem:string,fingerprint:string}
     */
    public static function verifyDeviceProof(array $payload): array
    {
        $publicDer = self::decodeBase64((string) ($payload['device_public_key'] ?? ''), 65, 256);
        $signature = self::decodeBase64((string) ($payload['device_signature'] ?? ''), 64, 160);
        $publicPem = self::derToPem($publicDer, 'PUBLIC KEY');
        $publicKey = openssl_pkey_get_public($publicPem);
        if ($publicKey === false) {
            throw new RuntimeException('Invalid device proof key.');
        }
        $details = openssl_pkey_get_details($publicKey);
        if (!is_array($details)
            || ($details['type'] ?? null) !== OPENSSL_KEYTYPE_EC
            || ($details['bits'] ?? 0) !== 256
            || (($details['ec']['curve_name'] ?? '') !== 'prime256v1')) {
            throw new RuntimeException('Device proof key must be P-256.');
        }

        $canonical = implode("\n", [
            '3',
            (string) ($payload['user_key'] ?? ''),
            (string) ($payload['package_name'] ?? ''),
            (string) ($payload['device_id'] ?? ''),
            (string) ($payload['app_signature_sha256'] ?? ''),
            (string) ($payload['timestamp'] ?? ''),
            (string) ($payload['nonce'] ?? ''),
            (string) ($payload['request_id'] ?? ''),
            (string) ($payload['sdk_version'] ?? ''),
        ]);
        if (openssl_verify($canonical, $signature, $publicKey, OPENSSL_ALGO_SHA256) !== 1) {
            throw new RuntimeException('Device proof signature is invalid.');
        }
        return [
            'public_key_pem' => $publicPem,
            'fingerprint' => hash('sha256', $publicDer),
        ];
    }

    private function loadPrivateKey(string $path): OpenSSLAsymmetricKey
    {
        if ($path === '' || !is_file($path) || !is_readable($path)) {
            throw new RuntimeException('API v3 private key file is unavailable.');
        }
        $contents = file_get_contents($path);
        $key = is_string($contents) ? openssl_pkey_get_private($contents) : false;
        if ($key === false) {
            throw new RuntimeException('API v3 private key is invalid.');
        }
        return $key;
    }

    private static function decodeBase64(string $value, int $minimum, int $maximum): string
    {
        if ($value === '' || strlen($value) > (int) ceil($maximum * 4 / 3) + 8) {
            throw new RuntimeException('Invalid base64 field length.');
        }
        $decoded = base64_decode($value, true);
        if (!is_string($decoded) || strlen($decoded) < $minimum || strlen($decoded) > $maximum) {
            throw new RuntimeException('Invalid base64 field.');
        }
        return $decoded;
    }

    private static function derToPem(string $der, string $label): string
    {
        return "-----BEGIN {$label}-----\n"
            . chunk_split(base64_encode($der), 64, "\n")
            . "-----END {$label}-----\n";
    }
}
