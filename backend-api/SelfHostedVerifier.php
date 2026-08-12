<?php
declare(strict_types=1);

final class SelfHostedVerificationException extends RuntimeException
{
    public function __construct(
        string $message,
        public int $httpStatus = 403,
        public string $verdict = 'SELF_HOSTED_REJECTED'
    ) {
        parent::__construct($message);
    }
}

/**
 * Verifies app metadata and a per-install Android Keystore public-key proof.
 *
 * The first successful activation binds the non-exportable device key to the
 * license. Later requests must be signed by the same key. This is independent
 * of Google Play Console and is intended for directly distributed APKs.
 */
final class SelfHostedVerifier
{
    private const PROOF_CONTEXT = 'onecore-device-proof-v1';

    private array $config;

    public function __construct(array $config)
    {
        $this->config = $config;
    }

    public function verifyActivation(
        array $payload,
        string $deviceId,
        string $nonce,
        int $timestamp,
        string $activationKey,
        string $allowedFingerprint
    ): array {
        $packageName = trim((string) ($payload['app_package_name'] ?? ''));
        if ($packageName === ''
            || !hash_equals($this->config['app_package_name'], $packageName)) {
            throw new SelfHostedVerificationException(
                'Package name verification failed',
                403,
                'PACKAGE_MISMATCH'
            );
        }

        $certificate = $this->normalizedFingerprint(
            (string) ($payload['app_certificate_sha256'] ?? '')
        );
        $expectedCertificate = $this->normalizedFingerprint($allowedFingerprint);
        if ($certificate === null || $expectedCertificate === null) {
            throw new SelfHostedVerificationException(
                'Release signing certificate is not configured',
                503,
                'SERVER_POLICY_ERROR'
            );
        }
        if (!hash_equals($expectedCertificate, $certificate)) {
            throw new SelfHostedVerificationException(
                'Signing certificate verification failed',
                403,
                'CERTIFICATE_MISMATCH'
            );
        }

        $publicKeyDer = $this->decodePublicKey((string) ($payload['device_public_key'] ?? ''));
        $derivedDeviceId = self::deviceIdForPublicKey($publicKeyDer);
        if (!hash_equals($derivedDeviceId, $deviceId)) {
            throw new SelfHostedVerificationException(
                'Device identity does not match its public key',
                403,
                'DEVICE_IDENTITY_MISMATCH'
            );
        }

        $bindingHash = hash('sha256', self::normalizeActivationKey($activationKey));
        $this->verifyProof(
            'activate',
            $deviceId,
            $nonce,
            $timestamp,
            $bindingHash,
            $publicKeyDer,
            (string) ($payload['device_proof'] ?? '')
        );

        $versionCode = filter_var(
            $payload['app_version_code'] ?? null,
            FILTER_VALIDATE_INT,
            ['options' => ['min_range' => 1]]
        );
        if ($versionCode === false) {
            throw new SelfHostedVerificationException(
                'App version is invalid',
                422,
                'INVALID_APP_METADATA'
            );
        }

        return [
            'package_name' => $packageName,
            'certificate_sha256' => $certificate,
            'public_key_der' => $publicKeyDer,
            'public_key_base64' => base64_encode($publicKeyDer),
            'public_key_sha256' => hash('sha256', $publicKeyDer),
            'version_code' => $versionCode,
        ];
    }

    public function verifyLicenseProof(
        string $deviceId,
        string $nonce,
        int $timestamp,
        string $licenseToken,
        string $publicKeyBase64,
        string $proof
    ): void {
        $publicKeyDer = $this->decodePublicKey($publicKeyBase64);
        if (!hash_equals(self::deviceIdForPublicKey($publicKeyDer), $deviceId)) {
            throw new SelfHostedVerificationException(
                'Stored device identity is invalid',
                403,
                'DEVICE_IDENTITY_MISMATCH'
            );
        }
        $this->verifyProof(
            'validate',
            $deviceId,
            $nonce,
            $timestamp,
            hash('sha256', $licenseToken),
            $publicKeyDer,
            $proof
        );
    }

    public function verifySecurityProof(
        string $deviceId,
        string $nonce,
        int $timestamp,
        string $eventType,
        string $publicKeyBase64,
        string $proof
    ): void {
        $publicKeyDer = $this->decodePublicKey($publicKeyBase64);
        if (!hash_equals(self::deviceIdForPublicKey($publicKeyDer), $deviceId)) {
            throw new SelfHostedVerificationException(
                'Security event device identity is invalid',
                403,
                'DEVICE_IDENTITY_MISMATCH'
            );
        }
        $this->verifyProof(
            'security',
            $deviceId,
            $nonce,
            $timestamp,
            hash('sha256', $eventType),
            $publicKeyDer,
            $proof
        );
    }

    public static function normalizeActivationKey(string $key): string
    {
        return strtoupper(trim($key));
    }

    public static function activationKeyHash(string $key): string
    {
        return hash('sha256', self::normalizeActivationKey($key));
    }

    public static function generateActivationKey(): string
    {
        $hex = strtoupper(bin2hex(random_bytes(16)));
        return 'OC-' . implode('-', str_split($hex, 4));
    }

    public static function deviceIdForPublicKey(string $publicKeyDer): string
    {
        return rtrim(strtr(base64_encode(hash('sha256', $publicKeyDer, true)), '+/', '-_'), '=');
    }

    public static function proofMessage(
        string $purpose,
        string $deviceId,
        string $nonce,
        int $timestamp,
        string $bindingHash
    ): string {
        return implode("\n", [
            self::PROOF_CONTEXT,
            $purpose,
            $deviceId,
            $nonce,
            (string) $timestamp,
            strtolower($bindingHash),
        ]);
    }

    private function verifyProof(
        string $purpose,
        string $deviceId,
        string $nonce,
        int $timestamp,
        string $bindingHash,
        string $publicKeyDer,
        string $proofBase64
    ): void {
        $proof = base64_decode($proofBase64, true);
        if ($proof === false || strlen($proof) < 48 || strlen($proof) > 144) {
            throw new SelfHostedVerificationException(
                'Device proof is malformed',
                403,
                'DEVICE_PROOF_INVALID'
            );
        }

        $pem = "-----BEGIN PUBLIC KEY-----\n"
            . chunk_split(base64_encode($publicKeyDer), 64, "\n")
            . "-----END PUBLIC KEY-----\n";
        $key = openssl_pkey_get_public($pem);
        if ($key === false) {
            throw new SelfHostedVerificationException(
                'Device public key is invalid',
                403,
                'DEVICE_PROOF_INVALID'
            );
        }
        $message = self::proofMessage(
            $purpose,
            $deviceId,
            $nonce,
            $timestamp,
            $bindingHash
        );
        $valid = openssl_verify($message, $proof, $key, OPENSSL_ALGO_SHA256);
        if ($valid !== 1) {
            throw new SelfHostedVerificationException(
                'Device proof verification failed',
                403,
                'DEVICE_PROOF_INVALID'
            );
        }
    }

    private function decodePublicKey(string $value): string
    {
        $decoded = base64_decode(trim($value), true);
        if ($decoded === false || strlen($decoded) < 64 || strlen($decoded) > 256) {
            throw new SelfHostedVerificationException(
                'Device public key is malformed',
                403,
                'DEVICE_PROOF_INVALID'
            );
        }
        return $decoded;
    }

    private function normalizedFingerprint(string $value): ?string
    {
        $hex = strtoupper(str_replace([':', ' ', '-'], '', trim($value)));
        return preg_match('/^[A-F0-9]{64}$/D', $hex) === 1 ? $hex : null;
    }
}
