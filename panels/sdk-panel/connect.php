<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/CryptoHelper.php';

header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');

$contentType = strtolower(trim(explode(';', (string) ($_SERVER['CONTENT_TYPE'] ?? ''))[0]));
$isV2 = $contentType === 'application/json'
    || (string) ($_SERVER['HTTP_X_API_VERSION'] ?? '') === '2';
$crypto = null;

$send = static function (array $payload, int $httpStatus = 200) use (&$isV2, &$crypto): never {
    // The installed v1 SDK only parses JSON on HTTP 200. Preserve that wire
    // contract during migration while v2 receives accurate HTTP status codes.
    $wireStatus = !$isV2 && !in_array($httpStatus, [405, 500], true) ? 200 : $httpStatus;
    http_response_code($wireStatus);
    $payload['server_time'] = time();
    try {
        $body = $isV2 && $crypto instanceof CryptoHelper
            ? $crypto->encryptPayload($payload)
            : $payload;
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

if ($isV2) {
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
if ($isV2) {
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

if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $licenseKey) !== 1
    || preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $packageName) !== 1
    || preg_match('/^[A-Za-z0-9._:-]{3,128}$/D', $deviceId) !== 1
    || strlen($appName) > 120) {
    panel_audit($conn, 'activation', 'invalid_input', substr($deviceId, 0, 128));
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'INVALID_REQUEST'], 400);
}

if ($isV2) {
    $timestamp = filter_var($payload['timestamp'] ?? null, FILTER_VALIDATE_INT);
    $nonce = (string) ($payload['nonce'] ?? '');
    if ($timestamp === false || abs(time() - $timestamp) > 300
        || preg_match('/^[A-Za-z0-9_-]{22,128}$/D', $nonce) !== 1) {
        panel_audit($conn, 'activation', 'replay_window_failed', $deviceId);
        $send(['status' => 'fail', 'message' => 'STALE_OR_INVALID_REQUEST'], 400);
    }
    if (!panel_consume_nonce($conn, $nonce, $deviceId)) {
        panel_audit($conn, 'activation', 'replay_blocked', $deviceId);
        $send(['status' => 'fail', 'message' => 'REPLAY_DETECTED'], 409);
    }
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

$ownerContact = $getSetting($conn, 'owner_contact', 'support');
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
    'SELECT id, expiry_date, daemon, hide_root, toggle_expiry, package_name,
            package_lock, max_devices, force_logout
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
        'message' => 'Invalid or inactive license. Contact ' . $ownerContact,
    ], 403);
}

if ((int) ($license['force_logout'] ?? 0) === 1) {
    panel_audit($conn, 'activation', 'license_force_logout', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'LICENSE_REVOKED'], 403);
}

if ((int) ($license['package_lock'] ?? 1) === 1
    && !hash_equals((string) ($license['package_name'] ?? ''), $packageName)) {
    panel_audit($conn, 'activation', 'package_mismatch', $deviceId, ['package' => $packageName]);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'PACKAGE_MISMATCH'], 403);
}

$expiryValue = trim((string) ($license['expiry_date'] ?? ''));
$expiryTimestamp = $expiryValue !== '' ? strtotime($expiryValue . (strlen($expiryValue) === 10 ? ' 23:59:59 UTC' : ' UTC')) : false;
if ($expiryTimestamp !== false && $expiryTimestamp < time()) {
    panel_audit($conn, 'activation', 'license_expired', $deviceId);
    $send([
        'status' => 'fail',
        'server_mode' => 'online',
        'message' => 'License expired. Contact ' . $ownerContact,
        'expiry' => $expiryValue,
        'toggle_expiry' => (int) ($license['toggle_expiry'] ?? 0),
        'feature1' => (int) ($license['daemon'] ?? 0),
        'feature2' => (int) ($license['hide_root'] ?? 0),
    ], 403);
}

$deviceCheck = $conn->prepare('SELECT blocked, license_key FROM devices WHERE device_id = ? LIMIT 1');
$deviceCheck->bind_param('s', $deviceId);
$deviceCheck->execute();
$existingDevice = $deviceCheck->get_result()->fetch_assoc();
$deviceCheck->close();
if ((int) ($existingDevice['blocked'] ?? 0) === 1) {
    panel_audit($conn, 'activation', 'device_blocked', $deviceId);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'DEVICE_BLOCKED'], 403);
}

$maxDevices = max(1, min(100, (int) ($license['max_devices'] ?? 1)));
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
if (!$alreadyBoundHere && $otherDevices >= $maxDevices) {
    panel_audit($conn, 'activation', 'device_limit', $deviceId, ['max_devices' => $maxDevices]);
    $send(['status' => 'fail', 'server_mode' => 'online', 'message' => 'MAX_DEVICE_REACHED'], 403);
}

$ip = panel_client_ip();
$deviceStmt = $conn->prepare(
    "INSERT INTO devices
        (device_id, package_name, license_key, ip_address, status, last_seen, connected_at)
     VALUES (?, ?, ?, ?, 'connected', UTC_TIMESTAMP(), UTC_TIMESTAMP())
     ON DUPLICATE KEY UPDATE
        package_name = VALUES(package_name), license_key = VALUES(license_key),
        ip_address = VALUES(ip_address), status = 'connected', last_seen = UTC_TIMESTAMP()"
);
$deviceStmt->bind_param('ssss', $deviceId, $packageName, $licenseKey, $ip);
$deviceStmt->execute();
$deviceStmt->close();

$response = [
    'status' => 'success',
    'server_mode' => 'online',
    'expiry' => $expiryValue,
    'toggle_expiry' => (int) ($license['toggle_expiry'] ?? 0),
    'feature1' => (int) ($license['daemon'] ?? 0),
    'feature2' => (int) ($license['hide_root'] ?? 0),
    'message' => 'PARALLAX Access Active',
];

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
    'package' => $packageName,
    'api_version' => $isV2 ? 2 : 1,
]);
$send($response);
