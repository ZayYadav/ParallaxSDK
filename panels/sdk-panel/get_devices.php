<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';
header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store');

$uid = (int)($_SESSION['user_id'] ?? 0);
if ($uid < 1 || empty($_SESSION['username']) || empty($_SESSION['auth_v3'])) {
    http_response_code(401);
    echo json_encode(['success' => false, 'message' => 'Not authenticated']);
    exit;
}

$userStmt = $conn->prepare('SELECT role,status FROM users WHERE id=? LIMIT 1');
$userStmt->bind_param('i', $uid);
$userStmt->execute();
$user = $userStmt->get_result()->fetch_assoc();
$userStmt->close();
if (!$user || (int)$user['status'] !== 1) {
    http_response_code(403);
    echo json_encode(['success' => false, 'message' => 'Account unavailable']);
    exit;
}
$role = strtolower((string)$user['role']);
$isManager = in_array($role, ['owner', 'admin'], true);

$licenseId = filter_input(INPUT_GET, 'license_id', FILTER_VALIDATE_INT);
if (!$licenseId || $licenseId < 1) {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Valid license_id required']);
    exit;
}

$stmt = $conn->prepare('SELECT license_key,package_name,owner_user_id FROM licenses WHERE id=? LIMIT 1');
$stmt->bind_param('i', $licenseId);
$stmt->execute();
$license = $stmt->get_result()->fetch_assoc();
$stmt->close();
if (!$license || (!$isManager && (int)($license['owner_user_id'] ?? 0) !== $uid)) {
    // Return 404 for ownership failures so numeric license IDs cannot be used as
    // an account/device enumeration oracle.
    http_response_code(404);
    echo json_encode(['success' => false, 'message' => 'License not found']);
    exit;
}

$stmt = $conn->prepare(
    "SELECT device_id, ip_address, status,
            DATE_FORMAT(last_seen, '%Y-%m-%dT%H:%i:%sZ') AS last_seen
     FROM devices WHERE license_key = ? ORDER BY last_seen DESC LIMIT 200"
);
$stmt->bind_param('s', $license['license_key']);
$stmt->execute();
$result = $stmt->get_result();
$devices = [];
while ($row = $result->fetch_assoc()) {
    $devices[] = $row;
}
$stmt->close();

echo json_encode([
    'success' => true,
    'package' => (string)($license['package_name'] ?? ''),
    'devices' => $devices,
    'count' => count($devices),
], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
