<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';

if (!empty($_SESSION['user_id']) && !empty($_SESSION['username'])) {
    header('Location: dashboard.php');
    exit;
}

header('Location: login.php');
exit;
