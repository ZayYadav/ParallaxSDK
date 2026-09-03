<?php
declare(strict_types=1);

session_start();
require_once __DIR__ . '/conn.php';
panel_require_roles($conn, ['owner', 'admin']);

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    exit('METHOD_NOT_ALLOWED');
}

$id = filter_input(INPUT_POST, 'id', FILTER_VALIDATE_INT);
$action = (string) ($_POST['action'] ?? '');
if (!$id || !in_array($action, ['toggle', 'delete'], true)) {
    http_response_code(400);
    exit('INVALID_REQUEST');
}

if ($action === 'toggle') {
    $stmt = $conn->prepare('UPDATE licenses SET status = IF(status = 1, 0, 1) WHERE id = ?');
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $stmt->close();
} else {
    $conn->begin_transaction();
    $keyStmt = $conn->prepare('SELECT license_key FROM licenses WHERE id = ? FOR UPDATE');
    $keyStmt->bind_param('i', $id); $keyStmt->execute();
    $row = $keyStmt->get_result()->fetch_assoc(); $keyStmt->close();
    if ($row) {
        $deviceStmt = $conn->prepare('DELETE FROM devices WHERE license_key = ?');
        $deviceStmt->bind_param('s', $row['license_key']); $deviceStmt->execute(); $deviceStmt->close();
        $stmt = $conn->prepare('DELETE FROM licenses WHERE id = ?');
        $stmt->bind_param('i', $id); $stmt->execute(); $stmt->close();
    }
    $conn->commit();
}

header('Location: dashboard.php');
exit;
