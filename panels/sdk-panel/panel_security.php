<?php
declare(strict_types=1);

function panel_config(string $key, $default = null)
{
    global $SDK_PANEL_CONFIG;
    return $SDK_PANEL_CONFIG[$key] ?? $default;
}

function panel_security_bootstrap(array $config): void
{
    static $started = false;
    if ($started) {
        return;
    }
    $started = true;

    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: no-referrer');
    header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
    header("Content-Security-Policy: frame-ancestors 'none'; base-uri 'self'; form-action 'self'");

    $https = panel_is_https($config);
    if (($config['REQUIRE_HTTPS'] ?? true) && !$https) {
        http_response_code(400);
        exit('HTTPS_REQUIRED');
    }

    if (session_status() !== PHP_SESSION_ACTIVE) {
        session_name('SDKPANELSESSID');
        session_set_cookie_params([
            'lifetime' => 0,
            'path' => '/',
            'secure' => $https,
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
        session_start();
    } elseif (session_id() !== '' && !headers_sent()) {
        setcookie(session_name(), session_id(), [
            'expires' => 0,
            'path' => '/',
            'secure' => $https,
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
    }

    $idleLimit = max(300, (int) ($config['SESSION_IDLE_SECONDS'] ?? 1800));
    if (isset($_SESSION['last_activity']) && time() - (int) $_SESSION['last_activity'] > $idleLimit) {
        panel_destroy_session();
        session_start();
    }
    $_SESSION['last_activity'] = time();

    $agentHash = hash('sha256', (string) ($_SERVER['HTTP_USER_AGENT'] ?? 'unknown'));
    if (isset($_SESSION['agent_hash']) && !hash_equals((string) $_SESSION['agent_hash'], $agentHash)) {
        panel_destroy_session();
        session_start();
    }
    $_SESSION['agent_hash'] = $agentHash;

    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    $script = strtolower(basename((string) ($_SERVER['SCRIPT_NAME'] ?? '')));
    $csrfExempt = in_array($script, ['connect.php', 'telegram_bot.php'], true);
    if (!$csrfExempt && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
        $provided = (string) ($_POST['_csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ''));
        if ($provided === '' || !hash_equals((string) $_SESSION['csrf_token'], $provided)) {
            http_response_code(419);
            exit('CSRF_VALIDATION_FAILED');
        }
    }

    if (!$csrfExempt) {
        ob_start('panel_inject_csrf_fields');
    }
}

function panel_is_https(array $config = []): bool
{
    if (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    if (($config['TRUST_PROXY'] ?? false) === true) {
        return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
    }
    return false;
}

function panel_destroy_session(): void
{
    $_SESSION = [];
    if (session_status() === PHP_SESSION_ACTIVE) {
        session_destroy();
    }
}

function panel_csrf_token(): string
{
    return (string) ($_SESSION['csrf_token'] ?? '');
}

function panel_csrf_input(): string
{
    return '<input type="hidden" name="_csrf" value="' . htmlspecialchars(panel_csrf_token(), ENT_QUOTES, 'UTF-8') . '">';
}

function panel_inject_csrf_fields(string $html): string
{
    if (stripos($html, '<form') === false) {
        return $html;
    }
    $field = panel_csrf_input();
    return (string) preg_replace_callback(
        '/<form\b[^>]*\bmethod\s*=\s*(["\']?)post\1[^>]*>/i',
        static fn(array $match): string => $match[0] . $field,
        $html
    );
}

function panel_client_ip(): string
{
    if (panel_config('TRUST_PROXY', false) === true) {
        $forwarded = trim(explode(',', (string) ($_SERVER['HTTP_X_FORWARDED_FOR'] ?? ''))[0]);
        if (filter_var($forwarded, FILTER_VALIDATE_IP)) {
            return $forwarded;
        }
    }
    $remote = (string) ($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0');
    return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
}

function panel_require_auth(): void
{
    if (empty($_SESSION['user_id']) || empty($_SESSION['username'])) {
        header('Location: login.php');
        exit;
    }
}

function panel_require_roles(mysqli $conn, array $roles): array
{
    panel_require_auth();
    $id = (int) $_SESSION['user_id'];
    $stmt = $conn->prepare('SELECT id, username, role, status FROM users WHERE id = ? LIMIT 1');
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $user = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    if (!$user || (isset($user['status']) && (int) $user['status'] !== 1)
        || !in_array(strtolower((string) $user['role']), array_map('strtolower', $roles), true)) {
        http_response_code(403);
        exit('ACCESS_DENIED');
    }
    return $user;
}

function panel_rate_limit(mysqli $conn, string $bucket, int $limit): bool
{
    $limit = max(1, min(600, $limit));
    $bucketHash = hash('sha256', $bucket);
    $stmt = $conn->prepare(
        'INSERT INTO api_rate_limits (bucket_hash, window_start, request_count)
         VALUES (?, UTC_TIMESTAMP(), 1)
         ON DUPLICATE KEY UPDATE
           request_count = IF(window_start < UTC_TIMESTAMP() - INTERVAL 60 SECOND, 1, request_count + 1),
           window_start = IF(window_start < UTC_TIMESTAMP() - INTERVAL 60 SECOND, UTC_TIMESTAMP(), window_start)'
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('s', $bucketHash);
    $stmt->execute();
    $stmt->close();

    $stmt = $conn->prepare('SELECT request_count FROM api_rate_limits WHERE bucket_hash = ?');
    $stmt->bind_param('s', $bucketHash);
    $stmt->execute();
    $count = (int) ($stmt->get_result()->fetch_assoc()['request_count'] ?? ($limit + 1));
    $stmt->close();
    return $count <= $limit;
}

function panel_consume_nonce(mysqli $conn, string $nonce, string $deviceId): bool
{
    $nonceHash = hash('sha256', $nonce);
    $stmt = $conn->prepare(
        'INSERT IGNORE INTO api_nonces (nonce_hash, device_id, expires_at)
         VALUES (?, ?, UTC_TIMESTAMP() + INTERVAL 5 MINUTE)'
    );
    $stmt->bind_param('ss', $nonceHash, $deviceId);
    $stmt->execute();
    $inserted = $stmt->affected_rows === 1;
    $stmt->close();
    if (random_int(1, 100) === 1) {
        $conn->query('DELETE FROM api_nonces WHERE expires_at < UTC_TIMESTAMP()');
    }
    return $inserted;
}

function panel_audit(mysqli $conn, string $event, string $result, string $deviceId = '', array $details = []): void
{
    $ip = panel_client_ip();
    $json = json_encode($details, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    $stmt = $conn->prepare(
        'INSERT INTO api_audit_logs (event_type, result, device_id, ip_address, details)
         VALUES (?, ?, ?, ?, ?)'
    );
    if ($stmt) {
        $stmt->bind_param('sssss', $event, $result, $deviceId, $ip, $json);
        $stmt->execute();
        $stmt->close();
    }
}
