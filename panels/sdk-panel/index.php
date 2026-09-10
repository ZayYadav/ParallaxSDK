<?php
declare(strict_types=1);

$requestPath = (string) parse_url((string) ($_SERVER['REQUEST_URI'] ?? '/'), PHP_URL_PATH);
$scriptDir = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? ''))), '/');
if ($scriptDir !== '' && $scriptDir !== '/') {
    if ($requestPath === $scriptDir) {
        $requestPath = '/';
    } elseif (str_starts_with($requestPath, $scriptDir . '/')) {
        $requestPath = substr($requestPath, strlen($scriptDir));
    }
}
$requestPath = trim($requestPath, '/');
if ($requestPath !== '' && basename($requestPath) !== 'index.php') {
    require_once __DIR__ . '/app/Core/Router.php';
    $routes = require __DIR__ . '/app/routes/web.php';
    (new Router(__DIR__, $routes))->dispatch();
}

require_once __DIR__ . '/conn.php';

if (!empty($_SESSION['user_id']) && !empty($_SESSION['username'])) {
    header('Location: dashboard.php');
    exit;
}

header('Location: login.php');
exit;
