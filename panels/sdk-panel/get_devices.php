<?php
declare(strict_types=1);

session_start();
require_once __DIR__ . '/conn.php';
header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store');

if (empty($_SESSION['user_id']) || empty($_SESSION['username'])) {
    http_response_code(401);
    echo json_encode(['success' => false, 'message' => 'Not authenticated']);
    exit;
}

$licenseId = filter_input(INPUT_GET, 'license_id', FILTER_VALIDATE_INT);
if (!$licenseId || $licenseId < 1) {
    http_response_code(400);
    echo json_encode(['success' => false, 'message' => 'Valid license_id required']);
    exit;
}

$stmt = $conn->prepare('SELECT license_key, package_name FROM licenses WHERE id = ? LIMIT 1');
$stmt->bind_param('i', $licenseId);
$stmt->execute();
$license = $stmt->get_result()->fetch_assoc();
$stmt->close();
if (!$license) {
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
    'package' => (string) ($license['package_name'] ?? ''),
    'devices' => $devices,
    'count' => count($devices),
], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
