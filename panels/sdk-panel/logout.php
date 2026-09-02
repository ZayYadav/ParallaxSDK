<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';
if (!empty($_SESSION['user_id'])) {
    $userId = (int) $_SESSION['user_id'];
    $stmt = $conn->prepare('UPDATE users SET is_online = 0 WHERE id = ?');
    $stmt->bind_param('i', $userId);
    $stmt->execute();
    $stmt->close();
}
panel_destroy_session();
header('Location: login.php');
exit;
