<?php
declare(strict_types=1);

final class ApiException extends RuntimeException
{
    public function __construct(
        string $message,
        public int $httpStatus = 400,
        public string $errorCode = 'BAD_REQUEST'
    ) {
        parent::__construct($message);
    }
}

$config = require __DIR__ . '/config.php';
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/CryptoHelper.php';

$autoload = __DIR__ . '/vendor/autoload.php';
if (!is_file($autoload)) {
    http_response_code(503);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'DEPENDENCIES_NOT_INSTALLED']);
    exit;
}
require_once $autoload;
require_once __DIR__ . '/JWTHelper.php';
require_once __DIR__ . '/IntegrityVerifier.php';
require_once __DIR__ . '/SelfHostedVerifier.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, private');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

applyCors($config);
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

$crypto = null;
$database = null;
$requestNonce = null;
$requestDeviceId = 'unknown';
$clientIp = clientIp($config);
$route = routePath();

try {
    validateServerConfig($config);
    enforceHttps($config);
    $crypto = new CryptoHelper($config['encryption_key']);
    $database = new Database($config['database']);
    $jwt = new JWTHelper(
        $config['jwt_secret'],
        $config['jwt_issuer'],
        $config['jwt_audience']
    );

    $method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
    if ($route === '/security/event' && $method === 'POST') {
        applyRateLimit($database, $clientIp, $route);
        $payload = encryptedPayload($crypto, $database, $config);
        $requestNonce = $payload['nonce'];
        $requestDeviceId = validatedDeviceId($payload['device_id'] ?? null);
        $eventType = strtoupper(trim((string) ($payload['event_type'] ?? '')));
        $severity = strtolower(trim((string) ($payload['severity'] ?? 'warning')));
        if (preg_match('/^[A-Z0-9_]{3,64}$/D', $eventType) !== 1
            || !in_array($severity, ['info', 'warning', 'critical'], true)) {
            throw new ApiException('Security event is invalid', 422, 'VALIDATION_ERROR');
        }
        try {
            (new SelfHostedVerifier($config))->verifySecurityProof(
                $requestDeviceId,
                $requestNonce,
                $payload['timestamp'],
                $eventType,
                (string) ($payload['device_public_key'] ?? ''),
                (string) ($payload['device_proof'] ?? '')
            );
        } catch (SelfHostedVerificationException $exception) {
            throw new ApiException(
                $exception->getMessage(),
                $exception->httpStatus,
                'DEVICE_PROOF_INVALID'
            );
        }
        $details = $payload['details'] ?? null;
        if ($details !== null && !is_array($details)) {
            throw new ApiException('Security event details must be an object', 422, 'VALIDATION_ERROR');
        }
        $database->execute(
            'INSERT INTO security_events
                (device_id, event_type, severity, ip_address, details)
             VALUES (:device_id, :event_type, :severity, :ip_address, :details)',
            [
                'device_id' => $requestDeviceId,
                'event_type' => $eventType,
                'severity' => $severity,
                'ip_address' => $clientIp,
                'details' => $details === null
                    ? null
                    : json_encode($details, JSON_THROW_ON_ERROR),
            ]
        );
        encryptedResponse($crypto, ['status' => 'recorded'], 202, $requestNonce);
    }

    if ($route === '/verify' && $method === 'POST') {
        $rateLimit = applyRateLimit($database, $clientIp, $route);
        $payload = encryptedPayload($crypto, $database, $config);
        $requestNonce = $payload['nonce'];
        $requestDeviceId = validatedDeviceId($payload['device_id'] ?? null);

        $existing = $database->fetchOne(
            'SELECT status FROM devices WHERE device_id = :device_id',
            ['device_id' => $requestDeviceId]
        );
        if (($existing['status'] ?? null) === 'revoked') {
            logIntegrity($database, $requestDeviceId, 'DEVICE_REVOKED', $clientIp, false);
            throw new ApiException('Device access has been revoked', 403, 'DEVICE_REVOKED');
        }

        $policy = $database->getAppConfigMap();
        $verificationMode = validatedVerificationMode($policy['verification_mode'] ?? 'self_hosted');
        $verdict = [];
        $successVerdict = 'SELF_HOSTED_ACCEPTED';
        if ($verificationMode === 'self_hosted') {
            $activationKey = $payload['activation_key'] ?? null;
            if (!is_string($activationKey)
                || !SelfHostedVerifier::isActivationKeyFormat($activationKey)) {
                throw new ApiException('A valid activation_key is required', 422, 'VALIDATION_ERROR');
            }
            $verifier = new SelfHostedVerifier($config);
            try {
                $verdict = $verifier->verifyActivation(
                    $payload,
                    $requestDeviceId,
                    $requestNonce,
                    $payload['timestamp'],
                    $activationKey,
                    (string) ($policy['allowed_cert_fingerprint'] ?? '')
                );
                bindSelfHostedLicense(
                    $database,
                    $requestDeviceId,
                    $activationKey,
                    $verdict
                );
            } catch (SelfHostedVerificationException $exception) {
                logIntegrity(
                    $database,
                    $requestDeviceId,
                    $exception->verdict,
                    $clientIp,
                    false
                );
                throw new ApiException(
                    $exception->getMessage(),
                    $exception->httpStatus,
                    'LICENSE_REJECTED'
                );
            }
        } else {
            $integrityToken = $payload['integrity_token'] ?? null;
            if (!is_string($integrityToken) || $integrityToken === '') {
                throw new ApiException('integrity_token is required', 422, 'VALIDATION_ERROR');
            }
            $verifier = new IntegrityVerifier($config);
            try {
                $verdict = $verifier->verify(
                    $integrityToken,
                    $requestDeviceId,
                    $requestNonce,
                    $payload['timestamp'],
                    $policy
                );
                $successVerdict = $verdict['app_recognition_verdict'];
            } catch (IntegrityVerificationException $exception) {
                logIntegrity(
                    $database,
                    $requestDeviceId,
                    $exception->verdict(),
                    $clientIp,
                    false
                );
                throw new ApiException(
                    $exception->getMessage(),
                    $exception->httpStatus(),
                    'INTEGRITY_REJECTED'
                );
            }
        }

        $expirySeconds = validatedExpiry($policy['token_expiry_seconds'] ?? 600);
        $license = $jwt->issue($requestDeviceId, $expirySeconds);
        $tokenHash = hash('sha256', $license['token']);

        $database->transaction(function (Database $db) use (
            $requestDeviceId,
            $license,
            $tokenHash
        ): void {
            $device = $db->fetchOne(
                'SELECT status FROM devices WHERE device_id = :device_id FOR UPDATE',
                ['device_id' => $requestDeviceId]
            );
            if (($device['status'] ?? null) === 'revoked') {
                throw new ApiException('Device access has been revoked', 403, 'DEVICE_REVOKED');
            }

            if ($device === null) {
                $db->execute(
                    'INSERT INTO devices
                        (device_id, last_verified_at, status, current_token_hash,
                         current_token_jti, token_expires_at)
                     VALUES
                        (:device_id, UTC_TIMESTAMP(6), \'active\', :token_hash,
                         :token_jti, FROM_UNIXTIME(:expires_at))',
                    [
                        'device_id' => $requestDeviceId,
                        'token_hash' => $tokenHash,
                        'token_jti' => $license['jti'],
                        'expires_at' => $license['expires_at'],
                    ]
                );
                return;
            }

            $db->execute(
                'UPDATE devices SET
                    last_verified_at = UTC_TIMESTAMP(6),
                    current_token_hash = :token_hash,
                    current_token_jti = :token_jti,
                    token_expires_at = FROM_UNIXTIME(:expires_at)
                 WHERE device_id = :device_id AND status = \'active\'',
                [
                    'device_id' => $requestDeviceId,
                    'token_hash' => $tokenHash,
                    'token_jti' => $license['jti'],
                    'expires_at' => $license['expires_at'],
                ]
            );
        });

        logIntegrity($database, $requestDeviceId, $successVerdict, $clientIp, true, [
            'verification_mode' => $verificationMode,
            'device_verdicts' => $verdict['device_recognition_verdict'] ?? [],
            'licensing_verdict' => $verdict['app_licensing_verdict'] ?? 'SELF_HOSTED',
            'version_code' => $verdict['version_code'] ?? null,
        ]);
        encryptedResponse($crypto, [
            'status' => 'success',
            'license_token' => $license['token'],
            'expires_in' => $license['expires_in'],
            'expires_at' => $license['expires_at'],
            'verification_mode' => $verificationMode,
            'rate_limit_remaining' => $rateLimit['remaining'],
        ], 200, $requestNonce);
    }

    if ($route === '/license/validate' && $method === 'POST') {
        applyRateLimit($database, $clientIp, $route);
        $payload = encryptedPayload($crypto, $database, $config);
        $requestNonce = $payload['nonce'];
        $requestDeviceId = validatedDeviceId($payload['device_id'] ?? null);
        $token = $payload['license_token'] ?? null;
        if (!is_string($token) || $token === '') {
            throw new ApiException('license_token is required', 422, 'VALIDATION_ERROR');
        }

        try {
            $claims = $jwt->verify($token);
        } catch (LicenseTokenException $exception) {
            throw new ApiException($exception->getMessage(), 401, 'INVALID_LICENSE');
        }
        if (!hash_equals($requestDeviceId, $claims['sub'])) {
            throw new ApiException('License device binding failed', 403, 'DEVICE_MISMATCH');
        }

        $device = $database->fetchOne(
            'SELECT status, current_token_hash, current_token_jti, token_expires_at,
                    device_public_key
             FROM devices WHERE device_id = :device_id',
            ['device_id' => $requestDeviceId]
        );
        $policy = $database->getAppConfigMap();
        if (validatedVerificationMode($policy['verification_mode'] ?? 'self_hosted') === 'self_hosted') {
            if (!is_string($device['device_public_key'] ?? null)
                || !is_string($payload['device_proof'] ?? null)) {
                throw new ApiException('Device proof is required', 403, 'DEVICE_PROOF_REQUIRED');
            }
            try {
                (new SelfHostedVerifier($config))->verifyLicenseProof(
                    $requestDeviceId,
                    $requestNonce,
                    $payload['timestamp'],
                    $token,
                    $device['device_public_key'],
                    $payload['device_proof']
                );
            } catch (SelfHostedVerificationException $exception) {
                throw new ApiException(
                    $exception->getMessage(),
                    $exception->httpStatus,
                    'DEVICE_PROOF_INVALID'
                );
            }
        }
        $valid = $device !== null
            && $device['status'] === 'active'
            && is_string($device['current_token_hash'])
            && hash_equals($device['current_token_hash'], hash('sha256', $token))
            && hash_equals((string) $device['current_token_jti'], $claims['jti'])
            && strtotime((string) $device['token_expires_at']) > time();
        if (!$valid) {
            throw new ApiException('License is revoked or superseded', 403, 'LICENSE_REVOKED');
        }

        encryptedResponse($crypto, [
            'status' => 'valid',
            'device_id' => $requestDeviceId,
            'expires_at' => $claims['exp'],
        ], 200, $requestNonce);
    }

    if (str_starts_with($route, '/admin/')) {
        requireAdminKey($config);

        if ($route === '/admin/revoke' && $method === 'POST') {
            $payload = encryptedPayload($crypto, $database, $config);
            $requestNonce = $payload['nonce'];
            $requestDeviceId = validatedDeviceId($payload['device_id'] ?? null);
            $reason = trim((string) ($payload['reason'] ?? 'Administrative revocation'));
            if ($reason === '' || strlen($reason) > 500) {
                throw new ApiException('reason must be 1-500 characters', 422, 'VALIDATION_ERROR');
            }

            $database->transaction(function (Database $db) use ($requestDeviceId, $reason): void {
                $db->execute(
                    'INSERT INTO devices (device_id, status)
                     VALUES (:device_id, \'revoked\')
                     ON DUPLICATE KEY UPDATE status = \'revoked\'',
                    ['device_id' => $requestDeviceId]
                );
                $device = $db->fetchOne(
                    'SELECT current_token_jti FROM devices WHERE device_id = :device_id FOR UPDATE',
                    ['device_id' => $requestDeviceId]
                );
                $db->execute(
                    'INSERT INTO revoked_tokens (device_id, reason, token_jti)
                     VALUES (:device_id, :reason, :token_jti)',
                    [
                        'device_id' => $requestDeviceId,
                        'reason' => $reason,
                        'token_jti' => $device['current_token_jti'] ?? null,
                    ]
                );
            });

            encryptedResponse($crypto, ['status' => 'revoked'], 200, $requestNonce);
        }

        if ($route === '/admin/policy' && $method === 'PUT') {
            $payload = encryptedPayload($crypto, $database, $config);
            $requestNonce = $payload['nonce'];
            unset($payload['nonce'], $payload['timestamp']);
            $updates = validatedPolicyUpdates($payload);
            if ($updates === []) {
                throw new ApiException('No supported policy values supplied', 422, 'VALIDATION_ERROR');
            }
            $database->transaction(function (Database $db) use ($updates): void {
                foreach ($updates as $key => $value) {
                    $db->setAppConfig($key, $value);
                }
            });
            encryptedResponse($crypto, [
                'status' => 'updated',
                'new_config' => $database->getAppConfigMap(),
            ], 200, $requestNonce);
        }

        if ($route === '/admin/metrics' && $method === 'GET') {
            $metrics = $database->fetchOne(
                'SELECT
                    COUNT(*) AS total_requests,
                    COALESCE(SUM(is_success = 1), 0) AS passed,
                    COALESCE(SUM(is_success = 0), 0) AS blocked,
                    COUNT(DISTINCT device_id) AS unique_devices
                 FROM integrity_logs
                 WHERE timestamp >= UTC_TIMESTAMP() - INTERVAL 24 HOUR'
            ) ?? [];
            $revoked = $database->fetchOne(
                'SELECT COUNT(*) AS count FROM devices WHERE status = \'revoked\''
            );
            $total = (int) ($metrics['total_requests'] ?? 0);
            $passed = (int) ($metrics['passed'] ?? 0);
            encryptedResponse($crypto, [
                'total_requests' => $total,
                'passed' => $passed,
                'blocked' => (int) ($metrics['blocked'] ?? 0),
                'unique_devices' => (int) ($metrics['unique_devices'] ?? 0),
                'revoked_devices' => (int) ($revoked['count'] ?? 0),
                'success_rate' => $total > 0 ? round(($passed / $total) * 100, 2) : 0.0,
                'window_hours' => 24,
            ]);
        }

        if ($route === '/admin/whitelist/cert' && $method === 'PUT') {
            $payload = encryptedPayload($crypto, $database, $config);
            $requestNonce = $payload['nonce'];
            $fingerprint = normalizedConfiguredFingerprint($payload['new_fingerprint'] ?? null);
            $database->setAppConfig('allowed_cert_fingerprint', $fingerprint);
            encryptedResponse($crypto, [
                'status' => 'updated',
                'allowed_cert_fingerprint' => $fingerprint,
            ], 200, $requestNonce);
        }

        throw new ApiException('Admin endpoint not found', 404, 'NOT_FOUND');
    }

    throw new ApiException('Endpoint not found', 404, 'NOT_FOUND');
} catch (ApiException $exception) {
    if ($crypto instanceof CryptoHelper) {
        encryptedResponse($crypto, [
            'status' => 'error',
            'error' => $exception->errorCode,
            'message' => $exception->getMessage(),
        ], $exception->httpStatus, $requestNonce);
    }
    plainBootstrapError($exception->errorCode, $exception->httpStatus);
} catch (CryptoException $exception) {
    if ($crypto instanceof CryptoHelper) {
        encryptedResponse($crypto, [
            'status' => 'error',
            'error' => 'INVALID_ENCRYPTED_REQUEST',
            'message' => $exception->getMessage(),
        ], 400, null);
    }
    plainBootstrapError('SERVER_CONFIGURATION_ERROR', 500);
} catch (Throwable $throwable) {
    error_log('OneCore API error: ' . $throwable->getMessage());
    if ($crypto instanceof CryptoHelper) {
        encryptedResponse($crypto, [
            'status' => 'error',
            'error' => 'INTERNAL_ERROR',
            'message' => 'The request could not be completed',
        ], 500, $requestNonce);
    }
    plainBootstrapError('SERVER_CONFIGURATION_ERROR', 500);
}

function encryptedPayload(CryptoHelper $crypto, Database $database, array $config): array
{
    $contentType = strtolower(trim(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0]));
    if ($contentType !== 'application/json') {
        throw new ApiException('Content-Type must be application/json', 415, 'UNSUPPORTED_MEDIA_TYPE');
    }
    $contentLength = (int) ($_SERVER['CONTENT_LENGTH'] ?? 0);
    if ($contentLength > $config['max_request_bytes']) {
        throw new ApiException('Encrypted request is too large', 413, 'PAYLOAD_TOO_LARGE');
    }
    $raw = file_get_contents('php://input', false, null, 0, $config['max_request_bytes'] + 1);
    if ($raw === false || $raw === '' || strlen($raw) > $config['max_request_bytes']) {
        throw new ApiException('Encrypted JSON body is required', 400, 'INVALID_REQUEST');
    }
    try {
        $envelope = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        throw new ApiException('Request envelope must be valid JSON', 400, 'INVALID_REQUEST');
    }
    if (!is_array($envelope)) {
        throw new ApiException('Request envelope must be a JSON object', 400, 'INVALID_REQUEST');
    }
    $payload = $crypto->decryptEnvelope($envelope);
    CryptoHelper::validateFreshness($payload, $config['replay_window_seconds']);
    $deviceForNonce = isset($payload['device_id']) && is_string($payload['device_id'])
        ? substr($payload['device_id'], 0, 128)
        : 'admin';
    if (!$database->claimNonce(
        $payload['nonce'],
        $deviceForNonce,
        $config['replay_window_seconds']
    )) {
        throw new ApiException('Request nonce has already been used', 409, 'REPLAY_DETECTED');
    }
    return $payload;
}

function encryptedResponse(
    CryptoHelper $crypto,
    array $payload,
    int $status = 200,
    ?string $requestNonce = null
): void {
    $payload['server_timestamp'] = time();
    if ($requestNonce !== null) {
        $payload['request_nonce'] = $requestNonce;
    }
    http_response_code($status);
    echo json_encode(
        $crypto->encryptPayload($payload),
        JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES
    );
    exit;
}

function applyRateLimit(Database $database, string $clientIp, string $endpoint): array
{
    $limit = (int) $database->getAppConfig('rate_limit_per_minute', 10);
    $result = $database->consumeRateLimit($clientIp, $endpoint, $limit);
    header('X-RateLimit-Limit: ' . $result['limit']);
    header('X-RateLimit-Remaining: ' . $result['remaining']);
    if (!$result['allowed']) {
        header('Retry-After: ' . $result['retry_after']);
        throw new ApiException('Rate limit exceeded', 429, 'RATE_LIMITED');
    }
    return $result;
}

function validatedDeviceId(mixed $value): string
{
    if (!is_string($value)
        || preg_match('/^[A-Za-z0-9._:-]{16,128}$/D', $value) !== 1) {
        throw new ApiException(
            'device_id must be 16-128 safe characters',
            422,
            'VALIDATION_ERROR'
        );
    }
    return $value;
}

function validatedExpiry(mixed $value): int
{
    $expiry = filter_var($value, FILTER_VALIDATE_INT);
    if ($expiry === false || $expiry < 60 || $expiry > 86_400) {
        return 600;
    }
    return $expiry;
}

function validatedPolicyUpdates(array $payload): array
{
    $updates = [];
    if (array_key_exists('verification_mode', $payload)) {
        $updates['verification_mode'] = validatedVerificationMode($payload['verification_mode']);
    }
    if (array_key_exists('token_expiry_seconds', $payload)) {
        $value = filter_var($payload['token_expiry_seconds'], FILTER_VALIDATE_INT);
        if ($value === false || $value < 60 || $value > 86_400) {
            throw new ApiException('token_expiry_seconds must be 60-86400', 422, 'VALIDATION_ERROR');
        }
        $updates['token_expiry_seconds'] = $value;
    }
    if (array_key_exists('rate_limit_per_minute', $payload)) {
        $value = filter_var($payload['rate_limit_per_minute'], FILTER_VALIDATE_INT);
        if ($value === false || $value < 1 || $value > 600) {
            throw new ApiException('rate_limit_per_minute must be 1-600', 422, 'VALIDATION_ERROR');
        }
        $updates['rate_limit_per_minute'] = $value;
    }
    if (array_key_exists('required_device_verdict', $payload)) {
        $allowed = ['MEETS_BASIC_INTEGRITY', 'MEETS_DEVICE_INTEGRITY', 'MEETS_STRONG_INTEGRITY'];
        if (!is_string($payload['required_device_verdict'])
            || !in_array($payload['required_device_verdict'], $allowed, true)) {
            throw new ApiException('required_device_verdict is invalid', 422, 'VALIDATION_ERROR');
        }
        $updates['required_device_verdict'] = $payload['required_device_verdict'];
    }
    if (array_key_exists('require_licensed', $payload)) {
        if (!is_bool($payload['require_licensed'])) {
            throw new ApiException('require_licensed must be boolean', 422, 'VALIDATION_ERROR');
        }
        $updates['require_licensed'] = $payload['require_licensed'];
    }
    return $updates;
}

function validatedVerificationMode(mixed $value): string
{
    if (!is_string($value) || !in_array($value, ['self_hosted', 'play_integrity'], true)) {
        throw new ApiException('verification_mode is invalid', 422, 'VALIDATION_ERROR');
    }
    return $value;
}

function bindSelfHostedLicense(
    Database $database,
    string $deviceId,
    string $activationKey,
    array $verdict
): void {
    $keyHash = SelfHostedVerifier::activationKeyHash($activationKey);
    $database->transaction(function (Database $db) use (
        $deviceId,
        $keyHash,
        $verdict
    ): void {
        $licenseKey = $db->fetchOne(
            'SELECT id, status, max_devices, expires_at
             FROM license_keys WHERE key_hash = :key_hash FOR UPDATE',
            ['key_hash' => $keyHash]
        );
        if ($licenseKey === null
            || $licenseKey['status'] !== 'active'
            || ($licenseKey['expires_at'] !== null
                && strtotime((string) $licenseKey['expires_at']) <= time())) {
            throw new SelfHostedVerificationException(
                'Activation key is invalid, revoked, or expired',
                403,
                'ACTIVATION_KEY_DENIED'
            );
        }

        $device = $db->fetchOne(
            'SELECT status, device_public_key_sha256
             FROM devices WHERE device_id = :device_id FOR UPDATE',
            ['device_id' => $deviceId]
        );
        if (($device['status'] ?? null) === 'revoked') {
            throw new SelfHostedVerificationException(
                'Device access has been revoked',
                403,
                'DEVICE_REVOKED'
            );
        }
        if ($device !== null
            && is_string($device['device_public_key_sha256'])
            && !hash_equals($device['device_public_key_sha256'], $verdict['public_key_sha256'])) {
            throw new SelfHostedVerificationException(
                'Device key binding mismatch',
                403,
                'DEVICE_IDENTITY_MISMATCH'
            );
        }

        if ($device === null) {
            $db->execute(
                'INSERT INTO devices
                    (device_id, device_public_key, device_public_key_sha256,
                     last_verified_at, status)
                 VALUES
                    (:device_id, :public_key, :public_key_sha256,
                     UTC_TIMESTAMP(6), \'active\')',
                [
                    'device_id' => $deviceId,
                    'public_key' => $verdict['public_key_base64'],
                    'public_key_sha256' => $verdict['public_key_sha256'],
                ]
            );
        } else {
            $db->execute(
                'UPDATE devices SET
                    device_public_key = :public_key,
                    device_public_key_sha256 = :public_key_sha256,
                    last_verified_at = UTC_TIMESTAMP(6)
                 WHERE device_id = :device_id',
                [
                    'device_id' => $deviceId,
                    'public_key' => $verdict['public_key_base64'],
                    'public_key_sha256' => $verdict['public_key_sha256'],
                ]
            );
        }

        $binding = $db->fetchOne(
            'SELECT id FROM device_license_bindings
             WHERE license_key_id = :license_key_id AND device_id = :device_id',
            [
                'license_key_id' => $licenseKey['id'],
                'device_id' => $deviceId,
            ]
        );
        if ($binding === null) {
            $count = $db->fetchOne(
                'SELECT COUNT(*) AS count FROM device_license_bindings
                 WHERE license_key_id = :license_key_id',
                ['license_key_id' => $licenseKey['id']]
            );
            if ((int) ($count['count'] ?? 0) >= (int) $licenseKey['max_devices']) {
                throw new SelfHostedVerificationException(
                    'Activation key device limit has been reached',
                    403,
                    'DEVICE_LIMIT_REACHED'
                );
            }
            $db->execute(
                'INSERT INTO device_license_bindings
                    (license_key_id, device_id, last_verified_at)
                 VALUES (:license_key_id, :device_id, UTC_TIMESTAMP(6))',
                [
                    'license_key_id' => $licenseKey['id'],
                    'device_id' => $deviceId,
                ]
            );
        } else {
            $db->execute(
                'UPDATE device_license_bindings SET last_verified_at = UTC_TIMESTAMP(6)
                 WHERE id = :id',
                ['id' => $binding['id']]
            );
        }
        $db->execute(
            'UPDATE license_keys SET last_used_at = UTC_TIMESTAMP(6) WHERE id = :id',
            ['id' => $licenseKey['id']]
        );
    });
}

function normalizedConfiguredFingerprint(mixed $value): string
{
    if (!is_string($value)) {
        throw new ApiException('new_fingerprint is required', 422, 'VALIDATION_ERROR');
    }
    $hex = strtoupper(str_replace([':', ' ', '-'], '', trim($value)));
    if (preg_match('/^[A-F0-9]{64}$/D', $hex) !== 1) {
        throw new ApiException('Fingerprint must be a SHA-256 hex digest', 422, 'VALIDATION_ERROR');
    }
    return implode(':', str_split($hex, 2));
}

function logIntegrity(
    Database $database,
    string $deviceId,
    string $verdict,
    string $ipAddress,
    bool $success,
    array $details = []
): void {
    $database->execute(
        'INSERT INTO integrity_logs
            (device_id, integrity_verdict, ip_address, is_success, details)
         VALUES (:device_id, :verdict, :ip_address, :is_success, :details)',
        [
            'device_id' => $deviceId,
            'verdict' => substr($verdict, 0, 64),
            'ip_address' => $ipAddress,
            'is_success' => $success ? 1 : 0,
            'details' => $details === [] ? null : json_encode($details, JSON_THROW_ON_ERROR),
        ]
    );
}

function requireAdminKey(array $config): void
{
    $provided = $_SERVER['HTTP_X_API_KEY'] ?? '';
    if (!is_string($provided)
        || $provided === ''
        || !hash_equals($config['admin_api_key'], $provided)) {
        throw new ApiException('Admin authentication failed', 401, 'UNAUTHORIZED');
    }
}

function validateServerConfig(array $config): void
{
    foreach (['encryption_key', 'jwt_secret', 'admin_api_key'] as $key) {
        $value = $config[$key] ?? null;
        if (!is_string($value)
            || strlen($value) < 32
            || str_starts_with(strtoupper($value), 'REPLACE_')) {
            throw new RuntimeException($key . ' is not securely configured');
        }
    }
}

function enforceHttps(array $config): void
{
    if (!$config['require_https']) {
        return;
    }
    $https = strtolower((string) ($_SERVER['HTTPS'] ?? '')) === 'on';
    if (!$https && $config['trust_proxy']) {
        $forwarded = strtolower(trim(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')[0]));
        $https = $forwarded === 'https';
    }
    if (!$https) {
        throw new ApiException('HTTPS is required', 400, 'HTTPS_REQUIRED');
    }
}

function clientIp(array $config): string
{
    $ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
    if ($config['trust_proxy'] && isset($_SERVER['HTTP_X_FORWARDED_FOR'])) {
        $candidate = trim(explode(',', $_SERVER['HTTP_X_FORWARDED_FOR'])[0]);
        if (filter_var($candidate, FILTER_VALIDATE_IP)) {
            $ip = $candidate;
        }
    }
    return filter_var($ip, FILTER_VALIDATE_IP) ? $ip : '0.0.0.0';
}

function routePath(): string
{
    $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
    $scriptDirectory = str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/index.php'));
    if ($scriptDirectory !== '/' && $scriptDirectory !== '.'
        && str_starts_with($path, $scriptDirectory)) {
        $path = substr($path, strlen($scriptDirectory));
    }
    $path = '/' . trim($path, '/');
    return $path === '//' ? '/' : $path;
}

function applyCors(array $config): void
{
    $origin = $_SERVER['HTTP_ORIGIN'] ?? null;
    if (!is_string($origin) || $origin === '') {
        return;
    }
    if (!in_array($origin, $config['cors_allowed_origins'], true)) {
        http_response_code(403);
        exit;
    }
    header('Access-Control-Allow-Origin: ' . $origin);
    header('Vary: Origin');
    header('Access-Control-Allow-Headers: Content-Type, X-API-Key');
    header('Access-Control-Allow-Methods: GET, POST, PUT, OPTIONS');
    header('Access-Control-Max-Age: 600');
}

function plainBootstrapError(string $code, int $status): void
{
    http_response_code($status);
    echo json_encode(['error' => $code]);
    exit;
}
