<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

define('SDK_PANEL_OWNER_TOOL', true);
require dirname(__DIR__) . DIRECTORY_SEPARATOR . 'conn.php';

$schemaProblems = sdk_panel_schema_problems($conn);
if ($schemaProblems !== []) {
    fwrite(STDERR, "Database schema is incomplete:\n- " . implode("\n- ", $schemaProblems) . "\n");
    fwrite(STDERR, "Import database/fresh-install.sql into the configured SDK panel database first.\n");
    exit(1);
}

$username = trim((string) ($argv[1] ?? ''));
if (preg_match('/^[A-Za-z0-9_.-]{3,64}$/D', $username) !== 1) {
    fwrite(STDERR, "Usage: php tools/create-owner.php OWNER_USERNAME\n");
    fwrite(STDERR, "Username must be 3-64 letters, numbers, dot, underscore or hyphen.\n");
    exit(1);
}

fwrite(STDOUT, 'Enter a new owner password (12+ characters): ');
$hidden = DIRECTORY_SEPARATOR === '/' && function_exists('shell_exec');
if ($hidden) {
    shell_exec('stty -echo');
}
$password = rtrim((string) fgets(STDIN), "\r\n");
if ($hidden) {
    shell_exec('stty echo');
    fwrite(STDOUT, PHP_EOL);
}
if (strlen($password) < 12 || strlen($password) > 256) {
    fwrite(STDERR, "Password must contain 12-256 characters.\n");
    exit(1);
}

$hash = password_hash($password, PASSWORD_DEFAULT);
if (!is_string($hash)) {
    fwrite(STDERR, "Password hashing failed.\n");
    exit(1);
}

$statement = $conn->prepare(
    "INSERT INTO users (username,password,role,status,is_online)
     VALUES (?,?,'owner',1,0)
     ON DUPLICATE KEY UPDATE password=VALUES(password),role='owner',status=1,is_online=0"
);
if (!$statement) {
    error_log('SDK Panel owner statement failed: ' . $conn->error);
    fwrite(STDERR, "Owner creation failed. Check the PHP error log.\n");
    exit(1);
}
$statement->bind_param('ss', $username, $hash);
if (!$statement->execute()) {
    error_log('SDK Panel owner creation failed: ' . $statement->error);
    fwrite(STDERR, "Owner creation failed. Check the PHP error log.\n");
    exit(1);
}
$statement->close();

fwrite(STDOUT, "Owner account created or securely reset.\n");
