<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/CryptoHelper.php';
require_once __DIR__ . '/CryptoV3.php';

header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');

$contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
$requestedVersion = (int) ($_SERVER['HTTP_X_API_VERSION'] ?? 0);
$apiVersion = $requestedVersion === 3 ? 3 : (($contentType === 'application/json' || $requestedVersion === 2) ? 2 : 1);
$isV2 = $apiVersion === 2;
$crypto = null;
$cryptoV3 = null;
$v3Context = null;

$send = static function (array $payload, int $httpStatus = 200) use (
    &$apiVersion,
    &$isV2,
    &$crypto,
    &$cryptoV3,
    &$v3Context
): never {
    // The installed v1 SDK only parses JSON on HTTP 200. Preserve that wire
    // contract during migration while v2 receives accurate HTTP status codes.
    $wireStatus = $apiVersion === 1 && !in_array($httpStatus, [405, 500], true) ? 200 : $httpStatus;
    http_response_code($wireStatus);
    $payload['server_time'] = time();
    try {
        if ($apiVersion === 3 && $cryptoV3 instanceof CryptoV3 && is_array($v3Context)) {
            $body = $cryptoV3->sealResponse($payload, $v3Context);
        } elseif ($isV2 && $crypto instanceof CryptoHelper) {
            $body = $crypto->encryptPayload($payload);
        } else {
            $body = $payload;
        }
        echo json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    } catch (Throwable $throwable) {
        error_log('SDK API response failure: ' . $throwable->getMessage());
        http_response_code(500);
        echo '{"status":"fail","message":"SERVER_RESPONSE_ERROR"}';
    }
    exit;
};

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: POST');
    $send(['status' => 'fail', 'message' => 'METHOD_NOT_ALLOWED'], 405);
}

if ($apiVersion === 3) {
    try {
        $cryptoV3 = new CryptoV3((array) panel_config('API_V3_KEYS', []));
    } catch (Throwable $throwable) {
        error_log('SDK API v3 configuration error: ' . $throwable->getMessage());
        $send(['status' => 'fail', 'message' => 'SERVER_CONFIGURATION_ERROR'], 500);
    }
} elseif ($isV2) {
    if (panel_config('API_V2_ENABLED', false) !== true) {
        $send(['status' => 'fail', 'message' => 'API_VERSION_DISABLED'], 426);
    }
    try {
        $crypto = new CryptoHelper((string) panel_config('ENCRYPTION_KEY', ''));
    } catch (Throwable $throwable) {
        error_log('SDK API encryption configuration error: ' . $throwable->getMessage());
        $send(['status' => 'fail', 'message' => 'SERVER_CONFIGURATION_ERROR'], 500);
    }
}

if (!panel_rate_limit(
    $conn,
    'activate|' . panel_client_ip(),
    (int) panel_config('RATE_LIMIT_PER_MINUTE', 30)
)) {
    $send(['status' => 'fail', 'message' => 'RATE_LIMITED'], 429);
}

$payload = [];
if ($apiVersion === 3) {
    try {
        $raw = file_get_contents('php://input', false, null, 0, 32769);
        if (!is_string($raw) || $raw === '' || strlen($raw) > 32768) {
            throw new RuntimeException('Invalid request size.');
        }
        $envelope = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
        if (!is_array($envelope)) {
            throw new RuntimeException('Invalid request envelope.');
        }
        $opened = $cryptoV3->openRequest($envelope);
        $payload = $opened['payload'];
        $v3Context = [
            'aes_key' => $opened['aes_key'],
            'key_id' => $opened['key_id'],
            'request_id' => $opened['request_id'],
        ];
    } catch (Throwable $throwable) {
        error_log('SDK API v3 request rejected: ' . $throwable->getMessage());
        panel_audit($conn, 'activation', 'v3_decrypt_failed');
        $send(['status' => 'fail', 'message' => 'INVALID_ENCRYPTED_REQUEST'], 400);
    }
} elseif ($isV2) {
    try {
        $raw = file_get_contents('php://input', false, null, 0, 32769);
        if (!is_string($raw) || $raw === '' || strlen($raw) > 32768) {
            throw new RuntimeException('Invalid request size.');
        }
        $envelope = json_decode($raw, true, 16, JSON_THROW_ON_ERROR);
        if (!is_array($envelope)) {
            throw new RuntimeException('Invalid request envelope.');
        }
        $payload = $crypto->decryptEnvelope($envelope);
    } catch (Throwable $throwable) {
        error_log('SDK API encrypted request rejected: ' . $throwable->getMessage());
        panel_audit($conn, 'activation', 'decrypt_failed');
        $send(['status' => 'fail', 'message' => 'INVALID_ENCRYPTED_REQUEST'], 400);
    }
} else {
    if (panel_config('LEGACY_API_ENABLED', true) !== true) {
        $send(['status' => 'fail', 'message' => 'ENCRYPTION_REQUIRED'], 426);
    }
    $payload = $_POST;
}

$licenseKey = strtoupper(trim((string) ($payload['user_key'] ?? '')));
$packageName = trim((string) ($payload['package_name'] ?? ''));
$deviceId = trim((string) ($payload['device_id'] ?? ''));
$appName = trim((string) ($payload['app_name'] ?? ''));
$appSignature = strtoupper(trim((string) ($payload['app_signature_sha256'] ?? '')));
$clientRequestId = trim((string) ($payload['request_id'] ?? ''));
$sdkVersion = (int) ($payload['sdk_version'] ?? ($apiVersion === 3 ? 0 : 1));
$deviceProof = null;

if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $licenseKey) !== 1
    || preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $packageName) !== 1
    || preg_match('/^[A-Za-z0-9._:-]{3,128}$/D', $deviceId) !== 1
    || strlen($appName) > 120) {
    panel_audit($conn, 'activation', 'invalid_input', substr($deviceId, 0, 128));
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'INVALID_REQUEST'], 400);
}

if ($apiVersion >= 2) {
    $timestamp = filter_var($payload['timestamp'] ?? null, FILTER_VALIDATE_INT);
    $nonce = (string) ($payload['nonce'] ?? '');
    $replayWindow = max(60, min(120, (int) panel_config('API_REPLAY_WINDOW_SECONDS', 120)));
    if ($timestamp === false || abs(time() - $timestamp) > $replayWindow
        || preg_match('/^[A-Za-z0-9_-]{22,128}$/D', $nonce) !== 1) {
        panel_audit($conn, 'activation', 'replay_window_failed', $deviceId);
        $send(['status' => 'fail', 'message' => 'STALE_OR_INVALID_REQUEST'], 400);
    }
    if (!panel_consume_nonce($conn, $nonce, $deviceId)) {
        panel_audit($conn, 'activation', 'replay_blocked', $deviceId);
        $send(['status' => 'fail', 'message' => 'REPLAY_DETECTED'], 409);
    }
    if ($apiVersion === 3
        && (preg_match('/^[A-Za-z0-9_-]{22,96}$/D', $clientRequestId) !== 1
            || !panel_consume_request_id($conn, $clientRequestId, $deviceId))) {
        panel_audit($conn, 'activation', 'duplicate_request_id', $deviceId, ['request_id' => $clientRequestId]);
        $send(['status' => 'fail', 'message' => 'REPLAY_DETECTED'], 409);
    }
}

if ($apiVersion === 3) {
    if (preg_match('/^[A-F0-9]{64}$/D', $appSignature) !== 1 || $sdkVersion < 1 || $sdkVersion > 1000000) {
        panel_audit($conn, 'activation', 'invalid_app_signature', $deviceId);
        $send(['status' => 'fail', 'message' => 'APP_SIGNATURE_REQUIRED'], 400);
    }
    try {
        $deviceProof = CryptoV3::verifyDeviceProof($payload);
    } catch (Throwable $throwable) {
        error_log('SDK device proof rejected: ' . $throwable->getMessage());
        panel_audit($conn, 'activation', 'device_proof_failed', $deviceId);
        $send(['status' => 'fail', 'message' => 'DEVICE_PROOF_INVALID'], 403);
    }
}

if (!panel_rate_limit($conn, 'license|' . hash('sha256', $licenseKey), 60)
    || !panel_rate_limit($conn, 'device|' . hash('sha256', $deviceId), 45)) {
    panel_audit($conn, 'activation', 'rate_limited', $deviceId, [
        'package' => $packageName,
        'sdk_version' => $sdkVersion,
        'request_id' => $clientRequestId,
    ]);
    $send(['status' => 'fail', 'message' => 'RATE_LIMITED'], 429);
}

$allowedPackages = panel_config('ALLOWED_PACKAGES', []);
if (is_array($allowedPackages) && $allowedPackages !== []
    && !in_array($packageName, $allowedPackages, true)) {
    panel_audit($conn, 'activation', 'package_not_allowed', $deviceId, ['package' => $packageName]);
    $send(['status' => 'fail', 'message' => 'PACKAGE_NOT_ALLOWED'], 403);
}

$getSetting = static function (mysqli $conn, string $key, string $fallback = ''): string {
    $stmt = $conn->prepare('SELECT setting_value FROM server_settings WHERE setting_key = ? LIMIT 1');
    $stmt->bind_param('s', $key);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    return isset($row['setting_value']) ? (string) $row['setting_value'] : $fallback;
};

$serverMode = strtolower($getSetting($conn, 'server_mode', ''));
if ($serverMode === '') {
    $serverMode = strtolower($getSetting($conn, 'server_status', 'online'));
}
if (!in_array($serverMode, ['online', 'maintenance', 'offline'], true)) {
    $serverMode = 'offline';
}
if ($serverMode !== 'online') {
    $message = $getSetting(
        $conn,
        'maintenance_message',
        $serverMode === 'maintenance' ? 'Server is under maintenance.' : 'Server is offline.'
    );
    panel_audit($conn, 'activation', 'server_' . $serverMode, $deviceId);
    $send(['status' => 'fail', 'server_mode' => $serverMode, 'message' => $message], 503);
}

$blockedApp = $conn->prepare('SELECT 1 FROM blocked_apps WHERE package_name = ? LIMIT 1');
if ($blockedApp) {
    $blockedApp->bind_param('s', $packageName);
    $blockedApp->execute();
    $isBlockedApp = (bool) $blockedApp->get_result()->fetch_row();
    $blockedApp->close();
    if ($isBlockedApp) {
        panel_audit($conn, 'activation', 'package_blocked', $deviceId, ['package' => $packageName]);
        $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'PACKAGE_BLOCKED'], 403);
    }
}

$stmt = $conn->prepare(
    'SELECT id, client_name, expiry_date, daemon, hide_root, toggle_expiry, package_name,
            package_lock, package_mode, max_devices, force_logout, signing_lock,
            signing_mode, signing_cert_sha256, device_mode, java_native_auth,
            feature_policy, minimum_sdk_version, latest_sdk_version, force_update,
            blocked_versions, session_lifetime_seconds, kill_switch
     FROM licenses WHERE license_key = ? AND status = 1 LIMIT 1'
);
$stmt->bind_param('s', $licenseKey);
$stmt->execute();
$license = $stmt->get_result()->fetch_assoc();
$stmt->close();

if (!$license) {
    panel_audit($conn, 'activation', 'license_not_found', $deviceId, ['package' => $packageName]);
    $send([
        'status' => 'fail',
        'server_mode' => 'online',
        'message' => 'INVALID_LICENSE',
    ], 403);
}

if ((int) ($license['force_logout'] ?? 0) === 1 || (int) ($license['kill_switch'] ?? 0) === 1) {
    panel_audit($conn, 'activation', 'license_force_logout', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_REVOKED'], 403);
}

$packageMode = strtoupper((string) ($license['package_mode'] ?? ((int) ($license['package_lock'] ?? 1) === 1 ? 'SPECIFIC' : 'ANY')));
if ($packageMode === 'SPECIFIC'
    && !hash_equals((string) ($license['package_name'] ?? ''), $packageName)) {
    panel_audit($conn, 'activation', 'package_mismatch', $deviceId, ['package' => $packageName]);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'PACKAGE_MISMATCH'], 403);
}

$expiryValue = trim((string) ($license['expiry_date'] ?? ''));
$expiryTimestamp = $expiryValue !== '' ? strtotime($expiryValue . (strlen($expiryValue) === 10 ? ' 23:59:59 UTC' : ' UTC')) : false;
if ($expiryValue === '' || $expiryTimestamp === false) {
    panel_audit($conn, 'activation', 'invalid_license_expiry', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_CONFIGURATION_INVALID'], 500);
}

if ($apiVersion === 3) {
    $blockedVersions = json_decode((string) ($license['blocked_versions'] ?? '[]'), true);
    if (!is_array($blockedVersions)) {
        $blockedVersions = [];
    }
    $blockedVersions = array_map('intval', $blockedVersions);
    $minimumVersion = max(1, (int) ($license['minimum_sdk_version'] ?? 3));
    $latestVersion = max($minimumVersion, (int) ($license['latest_sdk_version'] ?? $minimumVersion));
    if (in_array($sdkVersion, $blockedVersions, true)) {
        panel_audit($conn, 'activation', 'sdk_version_blocked', $deviceId, ['sdk_version' => $sdkVersion]);
        $send(['status' => 'fail', 'message' => 'SDK_VERSION_BLOCKED'], 426);
    }
    if ($sdkVersion < $minimumVersion
        || ((int) ($license['force_update'] ?? 0) === 1 && $sdkVersion < $latestVersion)) {
        panel_audit($conn, 'activation', 'sdk_update_required', $deviceId, ['sdk_version' => $sdkVersion]);
        $send([
            'status' => 'fail',
            'message' => 'UPDATE_REQUIRED',
            'minimum_sdk_version' => $minimumVersion,
            'latest_sdk_version' => $latestVersion,
        ], 426);
    }
}
if ($expiryTimestamp < time()) {
    panel_audit($conn, 'activation', 'license_expired', $deviceId);
    $send([
        'status' => 'fail',
        'server_mode' => 'online',
        'message' => 'LICENSE_EXPIRED',
        'expiry' => $expiryValue,
        'toggle_expiry' => (int) ($license['toggle_expiry'] ?? 0),
        'feature1' => (int) ($license['daemon'] ?? 0),
        'feature2' => (int) ($license['hide_root'] ?? 0),
    ], 403);
}

$conn->begin_transaction();
$lockLicense = $conn->prepare(
    'SELECT signing_lock, signing_mode, signing_cert_sha256, kill_switch
     FROM licenses WHERE id = ? AND status = 1 FOR UPDATE'
);
$licenseId = (int) $license['id'];
$lockLicense->bind_param('i', $licenseId);
$lockLicense->execute();
$lockedLicense = $lockLicense->get_result()->fetch_assoc();
$lockLicense->close();
if (!$lockedLicense) {
    $conn->rollback();
    panel_audit($conn, 'activation', 'license_changed', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_REVOKED'], 403);
}

if ((int) ($lockedLicense['kill_switch'] ?? 0) === 1) {
    $conn->rollback();
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_REVOKED'], 403);
}

$signingMode = strtoupper((string) ($lockedLicense['signing_mode']
    ?? ((int) ($lockedLicense['signing_lock'] ?? 1) === 1 ? 'AUTO' : 'ANY')));
if ($apiVersion === 3 && $signingMode !== 'ANY') {
    $boundSignature = strtoupper(trim((string) ($lockedLicense['signing_cert_sha256'] ?? '')));
    if ($signingMode === 'AUTO' && $boundSignature === '') {
        $bindSignature = $conn->prepare(
            "UPDATE licenses SET signing_cert_sha256 = ?
             WHERE id = ? AND (signing_cert_sha256 IS NULL OR signing_cert_sha256 = '')"
        );
        $bindSignature->bind_param('si', $appSignature, $licenseId);
        $bindSignature->execute();
        $bindSignature->close();
        $boundSignature = $appSignature;
    }
    if ($signingMode === 'SPECIFIC' && $boundSignature === '') {
        $conn->rollback();
        panel_audit($conn, 'activation', 'signing_policy_invalid', $deviceId);
        $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_CONFIGURATION_INVALID'], 500);
    }
    if (!hash_equals($boundSignature, $appSignature)) {
        $conn->rollback();
        panel_audit($conn, 'activation', 'app_signature_mismatch', $deviceId, ['package' => $packageName]);
        $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'SIGNATURE_MISMATCH'], 403);
    }
}

$deviceCheck = $conn->prepare(
    'SELECT blocked, license_key, client_key_fingerprint
     FROM devices WHERE license_key = ? AND device_id = ? LIMIT 1 FOR UPDATE'
);
$deviceCheck->bind_param('ss', $licenseKey, $deviceId);
$deviceCheck->execute();
$existingDevice = $deviceCheck->get_result()->fetch_assoc();
$deviceCheck->close();
if ((int) ($existingDevice['blocked'] ?? 0) === 1) {
    $conn->rollback();
    panel_audit($conn, 'activation', 'device_blocked', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'DEVICE_BLOCKED'], 403);
}
$deviceMode = strtoupper((string) ($license['device_mode'] ?? 'SINGLE'));
if ($apiVersion === 3 && $deviceMode !== 'DISABLED' && is_array($deviceProof)) {
    $boundFingerprint = strtolower(trim((string) ($existingDevice['client_key_fingerprint'] ?? '')));
    if ($boundFingerprint !== '' && !hash_equals($boundFingerprint, $deviceProof['fingerprint'])) {
        $conn->rollback();
        panel_audit($conn, 'activation', 'device_key_mismatch', $deviceId);
        $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'DEVICE_KEY_MISMATCH'], 403);
    }
}

$maxDevices = $deviceMode === 'SINGLE'
    ? 1
    : max(1, min(100, (int) ($license['max_devices'] ?? 1)));
$countStmt = $conn->prepare(
    'SELECT COUNT(DISTINCT device_id) AS total
     FROM devices WHERE license_key = ? AND device_id <> ? AND status <> \'blocked\''
);
$countStmt->bind_param('ss', $licenseKey, $deviceId);
$countStmt->execute();
$otherDevices = (int) ($countStmt->get_result()->fetch_assoc()['total'] ?? 0);
$countStmt->close();
$alreadyBoundHere = $existingDevice
    && hash_equals((string) ($existingDevice['license_key'] ?? ''), $licenseKey);
if (in_array($deviceMode, ['SINGLE', 'LIMITED'], true)
    && !$alreadyBoundHere && $otherDevices >= $maxDevices) {
    $conn->rollback();
    panel_audit($conn, 'activation', 'device_limit', $deviceId, ['max_devices' => $maxDevices]);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'DEVICE_LIMIT_EXCEEDED'], 403);
}

$ip = panel_client_ip();
$devicePublicKey = $apiVersion === 3 && $deviceMode !== 'DISABLED' && is_array($deviceProof)
    ? $deviceProof['public_key_pem'] : null;
$deviceFingerprint = $apiVersion === 3 && $deviceMode !== 'DISABLED' && is_array($deviceProof)
    ? $deviceProof['fingerprint'] : null;
$deviceStmt = $conn->prepare(
    "INSERT INTO devices
        (device_id, package_name, app_name, license_key, ip_address, status,
         client_public_key, client_key_fingerprint, last_seen, connected_at)
     VALUES (?, ?, ?, ?, ?, 'connected', ?, ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
     ON DUPLICATE KEY UPDATE
        package_name = VALUES(package_name), app_name = VALUES(app_name),
        ip_address = VALUES(ip_address), status = 'connected',
        client_public_key = COALESCE(client_public_key, VALUES(client_public_key)),
        client_key_fingerprint = COALESCE(client_key_fingerprint, VALUES(client_key_fingerprint)),
        last_seen = UTC_TIMESTAMP()"
);
$deviceStmt->bind_param(
    'sssssss',
    $deviceId,
    $packageName,
    $appName,
    $licenseKey,
    $ip,
    $devicePublicKey,
    $deviceFingerprint
);
$deviceStmt->execute();
$deviceStmt->close();
$sessionId = null;
$sessionToken = null;
$leaseExpiresAt = null;
if ($apiVersion === 3 && is_array($deviceProof)) {
    $leaseSeconds = max(600, min(1800, (int) ($license['session_lifetime_seconds'] ?? 600)));
    $leaseExpiresAt = min(time() + $leaseSeconds, $expiryTimestamp);
    if ($leaseExpiresAt <= time()) {
        $conn->rollback();
        $send(['status' => 'fail', 'message' => 'LICENSE_EXPIRED'], 403);
    }
    $sessionId = bin2hex(random_bytes(16));
    $sessionToken = rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
    $tokenHash = hash('sha256', $sessionToken);
    $revokeSessions = $conn->prepare(
        'UPDATE api_sessions SET revoked = 1
         WHERE license_id = ? AND device_id = ? AND revoked = 0'
    );
    $revokeSessions->bind_param('is', $licenseId, $deviceId);
    $revokeSessions->execute();
    $revokeSessions->close();
    $sessionStmt = $conn->prepare(
        'INSERT INTO api_sessions
            (session_id, token_hash, license_id, device_id, package_name,
             signing_sha256, device_key_fingerprint, sdk_version, expires_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(?))'
    );
    $proofFingerprint = (string) $deviceProof['fingerprint'];
    $sessionStmt->bind_param(
        'ssissssii',
        $sessionId,
        $tokenHash,
        $licenseId,
        $deviceId,
        $packageName,
        $appSignature,
        $proofFingerprint,
        $sdkVersion,
        $leaseExpiresAt
    );
    $sessionStmt->execute();
    $sessionStmt->close();
}
$conn->commit();

$featurePolicy = json_decode((string) ($license['feature_policy'] ?? '{}'), true);
if (!is_array($featurePolicy)) {
    $featurePolicy = [];
}
$featurePolicy = array_filter(
    $featurePolicy,
    static fn($value, $key): bool => is_string($key)
        && preg_match('/^[a-zA-Z0-9_.-]{1,64}$/D', $key) === 1
        && (is_bool($value) || is_int($value) || is_string($value)),
    ARRAY_FILTER_USE_BOTH
);
$response = [
    'status' => 'success',
    'server_mode' => 'online',
    'expiry' => $expiryValue,
    'toggle_expiry' => (int) ($license['toggle_expiry'] ?? 0),
    'feature1' => (int) ($license['daemon'] ?? 0),
    'feature2' => (int) ($license['hide_root'] ?? 0),
    'features' => (object) $featurePolicy,
    'message' => 'KESHAVXOWNER Access Active',
];
if ($apiVersion === 3) {
    $response['lease_expires_at'] = $leaseExpiresAt;
    $response['session_id'] = $sessionId;
    $response['session_token'] = $sessionToken;
    $response['issued_at'] = time();
    $response['expires_at'] = $leaseExpiresAt;
    $response['app_signature_sha256'] = $appSignature;
    $response['authorized_package'] = $packageName;
    $response['authorized_signing_sha256'] = $appSignature;
    $response['device_key_fingerprint'] = $deviceProof['fingerprint'];
    $response['package_policy'] = $packageMode;
    $response['signing_policy'] = $signingMode;
    $response['device_policy'] = $deviceMode;
    $response['java_native_auth'] = (int) ($license['java_native_auth'] ?? 1);
    $response['sdk_version'] = $sdkVersion;
    $response['request_id'] = $clientRequestId;
}

$notificationJson = $getSetting($conn, 'server_notification_json', '');
if ($notificationJson !== '') {
    $notification = json_decode($notificationJson, true);
    if (is_array($notification) && (int) ($notification['enabled'] ?? 0) === 1) {
        $response['server_notification'] = [
            'enabled' => 1,
            'title' => mb_substr((string) ($notification['title'] ?? 'System notice'), 0, 80),
            'message' => mb_substr((string) ($notification['message'] ?? ''), 0, 500),
            'iconType' => mb_substr((string) ($notification['iconType'] ?? 'event'), 0, 20),
        ];
    }
}

panel_audit($conn, 'activation', 'success', $deviceId, [
    'license_id' => $licenseId,
    'client_name' => (string) ($license['client_name'] ?? ''),
    'package' => $packageName,
    'signing_sha256' => $appSignature,
    'sdk_version' => $sdkVersion,
    'request_id' => $clientRequestId,
    'api_version' => $apiVersion,
]);
$send($response);
