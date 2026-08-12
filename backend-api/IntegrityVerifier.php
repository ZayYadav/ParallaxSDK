<?php
declare(strict_types=1);

use Firebase\JWT\JWT;

final class IntegrityVerificationException extends RuntimeException
{
    private int $httpStatus;
    private string $verdict;

    public function __construct(
        string $message,
        int $httpStatus = 403,
        string $verdict = 'REJECTED',
        ?Throwable $previous = null
    ) {
        parent::__construct($message, 0, $previous);
        $this->httpStatus = $httpStatus;
        $this->verdict = $verdict;
    }

    public function httpStatus(): int
    {
        return $this->httpStatus;
    }

    public function verdict(): string
    {
        return $this->verdict;
    }
}

final class IntegrityVerifier
{
    private const PLAY_INTEGRITY_SCOPE = 'https://www.googleapis.com/auth/playintegrity';
    private const OAUTH_TOKEN_URL = 'https://oauth2.googleapis.com/token';

    private array $config;
    private array $serviceAccount;

    public function __construct(array $config)
    {
        $this->config = $config;
        $this->serviceAccount = $this->loadServiceAccount($config);
    }

    public function verify(
        string $integrityToken,
        string $deviceId,
        string $nonce,
        int $timestamp,
        array $policy
    ): array {
        if ($integrityToken === '' || strlen($integrityToken) > 100_000) {
            throw new IntegrityVerificationException('Invalid integrity token');
        }

        $payload = $this->decodeIntegrityToken($integrityToken);
        $requestDetails = $payload['requestDetails'] ?? null;
        $appIntegrity = $payload['appIntegrity'] ?? null;
        $deviceIntegrity = $payload['deviceIntegrity'] ?? [];
        $accountDetails = $payload['accountDetails'] ?? [];
        if (!is_array($requestDetails) || !is_array($appIntegrity)) {
            throw new IntegrityVerificationException(
                'Integrity verdict is incomplete',
                403,
                'INCOMPLETE'
            );
        }

        $packageName = (string) ($appIntegrity['packageName']
            ?? $requestDetails['requestPackageName']
            ?? '');
        if (!hash_equals($this->config['app_package_name'], $packageName)) {
            throw new IntegrityVerificationException(
                'Package name verification failed',
                403,
                'PACKAGE_MISMATCH'
            );
        }

        $recognitionVerdict = (string) ($appIntegrity['appRecognitionVerdict'] ?? 'UNEVALUATED');
        if ($recognitionVerdict !== 'PLAY_RECOGNIZED') {
            throw new IntegrityVerificationException(
                'App is not recognized by Google Play',
                403,
                $recognitionVerdict
            );
        }

        $this->verifyRequestBinding($requestDetails, $deviceId, $nonce, $timestamp);
        $this->verifyCertificate(
            $appIntegrity['certificateSha256Digest'] ?? [],
            (string) ($policy['allowed_cert_fingerprint'] ?? '')
        );

        $requiredDeviceVerdict = (string) ($policy['required_device_verdict']
            ?? 'MEETS_DEVICE_INTEGRITY');
        $deviceVerdicts = $deviceIntegrity['deviceRecognitionVerdict'] ?? [];
        if (!is_array($deviceVerdicts)
            || !in_array($requiredDeviceVerdict, $deviceVerdicts, true)) {
            throw new IntegrityVerificationException(
                'Device integrity requirement was not met',
                403,
                'DEVICE_NOT_TRUSTED'
            );
        }

        $licensingVerdict = (string) ($accountDetails['appLicensingVerdict'] ?? 'UNEVALUATED');
        if (($policy['require_licensed'] ?? true) && $licensingVerdict !== 'LICENSED') {
            throw new IntegrityVerificationException(
                'Google Play license requirement was not met',
                403,
                $licensingVerdict
            );
        }

        return [
            'app_recognition_verdict' => $recognitionVerdict,
            'device_recognition_verdict' => array_values($deviceVerdicts),
            'app_licensing_verdict' => $licensingVerdict,
            'package_name' => $packageName,
            'version_code' => $appIntegrity['versionCode'] ?? null,
        ];
    }

    public static function requestHash(string $deviceId, string $nonce, int $timestamp): string
    {
        $digest = hash('sha256', $deviceId . "\n" . $nonce . "\n" . $timestamp, true);
        return rtrim(strtr(base64_encode($digest), '+/', '-_'), '=');
    }

    private function verifyRequestBinding(
        array $requestDetails,
        string $deviceId,
        string $nonce,
        int $timestamp
    ): void {
        $googleTimestampMillis = $requestDetails['timestampMillis'] ?? null;
        if (!is_string($googleTimestampMillis) && !is_int($googleTimestampMillis)) {
            throw new IntegrityVerificationException(
                'Integrity request timestamp is missing',
                403,
                'REQUEST_BINDING_FAILED'
            );
        }
        $googleTimestamp = intdiv((int) $googleTimestampMillis, 1000);
        if (abs(time() - $googleTimestamp) > (int) $this->config['replay_window_seconds']) {
            throw new IntegrityVerificationException(
                'Integrity verdict is stale',
                403,
                'STALE_VERDICT'
            );
        }
        if (abs($googleTimestamp - $timestamp) > (int) $this->config['replay_window_seconds']) {
            throw new IntegrityVerificationException(
                'Integrity verdict does not match the client request time',
                403,
                'REQUEST_BINDING_FAILED'
            );
        }

        if (isset($requestDetails['requestHash'])) {
            $expectedHash = self::requestHash($deviceId, $nonce, $timestamp);
            if (!hash_equals($expectedHash, (string) $requestDetails['requestHash'])) {
                throw new IntegrityVerificationException(
                    'Integrity request hash mismatch',
                    403,
                    'REQUEST_BINDING_FAILED'
                );
            }
            return;
        }

        // Classic requests return nonce instead of requestHash.
        if (isset($requestDetails['nonce'])
            && hash_equals($nonce, (string) $requestDetails['nonce'])) {
            return;
        }

        throw new IntegrityVerificationException(
            'Integrity token is not bound to this request',
            403,
            'REQUEST_BINDING_FAILED'
        );
    }

    private function verifyCertificate(array $googleDigests, string $allowedFingerprint): void
    {
        $expected = $this->decodeFingerprint($allowedFingerprint);
        if ($expected === null) {
            throw new IntegrityVerificationException(
                'Allowed signing certificate is not configured',
                503,
                'SERVER_POLICY_ERROR'
            );
        }

        foreach ($googleDigests as $digest) {
            if (!is_string($digest)) {
                continue;
            }
            $actual = $this->decodeFingerprint($digest);
            if ($actual !== null && hash_equals($expected, $actual)) {
                return;
            }
        }

        throw new IntegrityVerificationException(
            'Signing certificate verification failed',
            403,
            'CERTIFICATE_MISMATCH'
        );
    }

    private function decodeFingerprint(string $value): ?string
    {
        $value = trim($value);
        $hex = str_replace([':', ' ', '-'], '', $value);
        if (preg_match('/^[A-Fa-f0-9]{64}$/D', $hex) === 1) {
            $decoded = hex2bin($hex);
            return $decoded === false ? null : $decoded;
        }

        $padded = strtr($value, '-_', '+/');
        $padding = strlen($padded) % 4;
        if ($padding !== 0) {
            $padded .= str_repeat('=', 4 - $padding);
        }
        $decoded = base64_decode($padded, true);
        return $decoded !== false && strlen($decoded) === 32 ? $decoded : null;
    }

    private function decodeIntegrityToken(string $integrityToken): array
    {
        $accessToken = $this->getAccessToken();
        $url = sprintf(
            'https://playintegrity.googleapis.com/v1/%s:decodeIntegrityToken',
            rawurlencode($this->config['app_package_name'])
        );
        $response = $this->curlJson(
            $url,
            ['integrity_token' => $integrityToken],
            ['Authorization: Bearer ' . $accessToken]
        );
        $tokenPayload = $response['tokenPayloadExternal'] ?? null;
        if (!is_array($tokenPayload)) {
            throw new IntegrityVerificationException(
                'Google returned an invalid integrity response',
                502,
                'GOOGLE_RESPONSE_ERROR'
            );
        }
        return $tokenPayload;
    }

    private function getAccessToken(): string
    {
        $runtimeDir = $this->config['runtime_dir'];
        if (!is_dir($runtimeDir) && !mkdir($runtimeDir, 0700, true) && !is_dir($runtimeDir)) {
            throw new IntegrityVerificationException(
                'Unable to initialize OAuth cache',
                500,
                'SERVER_CONFIGURATION_ERROR'
            );
        }

        $lockHandle = fopen($runtimeDir . '/google-oauth.lock', 'c+');
        if ($lockHandle === false || !flock($lockHandle, LOCK_EX)) {
            throw new IntegrityVerificationException(
                'Unable to lock OAuth cache',
                500,
                'SERVER_CONFIGURATION_ERROR'
            );
        }

        try {
            $cachePath = $runtimeDir . '/google-oauth.json';
            if (is_file($cachePath)) {
                $cached = json_decode((string) file_get_contents($cachePath), true);
                if (is_array($cached)
                    && is_string($cached['access_token'] ?? null)
                    && (int) ($cached['expires_at'] ?? 0) > time() + 60) {
                    return $cached['access_token'];
                }
            }

            $token = $this->requestAccessToken();
            $encoded = json_encode($token, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
            if (file_put_contents($cachePath, $encoded, LOCK_EX) === false) {
                throw new IntegrityVerificationException(
                    'Unable to write OAuth cache',
                    500,
                    'SERVER_CONFIGURATION_ERROR'
                );
            }
            @chmod($cachePath, 0600);
            return $token['access_token'];
        } finally {
            flock($lockHandle, LOCK_UN);
            fclose($lockHandle);
        }
    }

    private function requestAccessToken(): array
    {
        $now = time();
        $claims = [
            'iss' => $this->serviceAccount['client_email'],
            'scope' => self::PLAY_INTEGRITY_SCOPE,
            'aud' => self::OAUTH_TOKEN_URL,
            'iat' => $now,
            'exp' => $now + 3600,
        ];
        $assertion = JWT::encode(
            $claims,
            $this->serviceAccount['private_key'],
            'RS256',
            $this->serviceAccount['private_key_id'] ?? null
        );

        $curl = curl_init(self::OAUTH_TOKEN_URL);
        if ($curl === false) {
            throw new IntegrityVerificationException('Unable to initialize OAuth request', 502);
        }
        curl_setopt_array($curl, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query([
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $assertion,
            ], '', '&', PHP_QUERY_RFC3986),
            CURLOPT_HTTPHEADER => ['Content-Type: application/x-www-form-urlencoded'],
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => $this->config['google_connect_timeout_seconds'],
            CURLOPT_TIMEOUT => $this->config['google_request_timeout_seconds'],
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        ]);

        $body = curl_exec($curl);
        $error = curl_error($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);
        if ($body === false || $status < 200 || $status >= 300) {
            throw new IntegrityVerificationException(
                'Google OAuth request failed' . ($error !== '' ? ': ' . $error : ''),
                502,
                'GOOGLE_OAUTH_ERROR'
            );
        }

        try {
            $decoded = json_decode($body, true, 32, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new IntegrityVerificationException(
                'Google OAuth response was invalid',
                502,
                'GOOGLE_OAUTH_ERROR',
                $exception
            );
        }
        if (!is_string($decoded['access_token'] ?? null)) {
            throw new IntegrityVerificationException(
                'Google OAuth access token was missing',
                502,
                'GOOGLE_OAUTH_ERROR'
            );
        }
        return [
            'access_token' => $decoded['access_token'],
            'expires_at' => $now + max(60, (int) ($decoded['expires_in'] ?? 3600)),
        ];
    }

    private function curlJson(string $url, array $payload, array $headers = []): array
    {
        $curl = curl_init($url);
        if ($curl === false) {
            throw new IntegrityVerificationException('Unable to initialize Google request', 502);
        }
        $headers[] = 'Content-Type: application/json';
        curl_setopt_array($curl, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => json_encode($payload, JSON_THROW_ON_ERROR),
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => $this->config['google_connect_timeout_seconds'],
            CURLOPT_TIMEOUT => $this->config['google_request_timeout_seconds'],
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        ]);

        $body = curl_exec($curl);
        $error = curl_error($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);
        if ($body === false || $status < 200 || $status >= 300) {
            throw new IntegrityVerificationException(
                'Google Play Integrity request failed'
                    . ($error !== '' ? ': ' . $error : '')
                    . ($status > 0 ? ' (HTTP ' . $status . ')' : ''),
                502,
                'GOOGLE_API_ERROR'
            );
        }

        try {
            $decoded = json_decode($body, true, 64, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new IntegrityVerificationException(
                'Google Play Integrity returned invalid JSON',
                502,
                'GOOGLE_RESPONSE_ERROR',
                $exception
            );
        }
        if (!is_array($decoded)) {
            throw new IntegrityVerificationException(
                'Google Play Integrity returned an invalid payload',
                502,
                'GOOGLE_RESPONSE_ERROR'
            );
        }
        return $decoded;
    }

    private function loadServiceAccount(array $config): array
    {
        $json = trim((string) $config['service_account_json']);
        if ($json === '') {
            $path = trim((string) $config['service_account_json_path']);
            if ($path === '' || !is_readable($path)) {
                throw new IntegrityVerificationException(
                    'Service account credentials are not configured',
                    500,
                    'SERVER_CONFIGURATION_ERROR'
                );
            }
            $json = (string) file_get_contents($path);
        }

        try {
            $credentials = json_decode($json, true, 32, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new IntegrityVerificationException(
                'Service account JSON is invalid',
                500,
                'SERVER_CONFIGURATION_ERROR',
                $exception
            );
        }
        if (($credentials['type'] ?? null) !== 'service_account'
            || !is_string($credentials['client_email'] ?? null)
            || !is_string($credentials['private_key'] ?? null)) {
            throw new IntegrityVerificationException(
                'Service account credentials are incomplete',
                500,
                'SERVER_CONFIGURATION_ERROR'
            );
        }
        return $credentials;
    }
}
