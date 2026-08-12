<?php
declare(strict_types=1);

use Firebase\JWT\JWT;
use Firebase\JWT\Key;

final class LicenseTokenException extends RuntimeException
{
}

final class JWTHelper
{
    private string $secret;
    private string $issuer;
    private string $audience;

    public function __construct(string $base64Secret, string $issuer, string $audience)
    {
        $decoded = base64_decode($base64Secret, true);
        if ($decoded === false || strlen($decoded) < 32) {
            throw new LicenseTokenException(
                'JWT_SECRET must be base64 for at least 32 random bytes'
            );
        }
        $this->secret = $decoded;
        $this->issuer = $issuer;
        $this->audience = $audience;
    }

    public function issue(string $deviceId, int $expirySeconds): array
    {
        $now = time();
        $expirySeconds = max(60, min($expirySeconds, 86_400));
        $jti = bin2hex(random_bytes(16));
        $claims = [
            'iss' => $this->issuer,
            'aud' => $this->audience,
            'sub' => $deviceId,
            'jti' => $jti,
            'iat' => $now,
            'nbf' => $now - 5,
            'exp' => $now + $expirySeconds,
            'typ' => 'license',
        ];

        return [
            'token' => JWT::encode($claims, $this->secret, 'HS256'),
            'jti' => $jti,
            'issued_at' => $now,
            'expires_at' => $now + $expirySeconds,
            'expires_in' => $expirySeconds,
        ];
    }

    public function verify(string $token): array
    {
        try {
            JWT::$leeway = 10;
            $claims = (array) JWT::decode($token, new Key($this->secret, 'HS256'));
        } catch (Throwable $throwable) {
            throw new LicenseTokenException('Invalid or expired license token', 0, $throwable);
        }

        if (($claims['iss'] ?? null) !== $this->issuer
            || ($claims['aud'] ?? null) !== $this->audience
            || ($claims['typ'] ?? null) !== 'license'
            || !is_string($claims['sub'] ?? null)
            || !is_string($claims['jti'] ?? null)) {
            throw new LicenseTokenException('License token claims are invalid');
        }
        return $claims;
    }
}
