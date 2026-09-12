<?php
declare(strict_types=1);

/**
 * Runtime access boundary for TeamDark-style management features.
 *
 * Important: connect.php and the Telegram webhook remain outside this human-panel
 * access layer so the existing SDK request/response contract is never changed.
 */

function sdk_feature_json_error(int $status, string $message): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');
    echo json_encode(['success' => false, 'message' => $message], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function sdk_feature_access_denied(string $message = 'Access denied.'): never
{
    http_response_code(403);
    header('Content-Type: text/html; charset=UTF-8');
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>Access denied</title><style>body{margin:0;min-height:100svh;display:grid;place-items:center;background:#050810;color:#f8fafc;font-family:Inter,system-ui;padding:24px}'
        . '.c{max-width:540px;padding:30px;border-radius:22px;background:#0b1222;border:1px solid rgba(255,255,255,.12)}a{color:#93c5fd}</style></head><body>'
        . '<div class="c"><h2>Access denied</h2><p>' . htmlspecialchars($message, ENT_QUOTES, 'UTF-8') . '</p><a href="dashboard.php">Return to dashboard</a></div></body></html>';
    exit;
}

/**
 * PanelTime already converts raw UTC timestamps. Feature pages sometimes render
 * an already-converted local string such as "12 Sep 2026 · 02:30 PM IST".
 * Replace that separator before the outer PanelTime buffer runs so it cannot be
 * interpreted as UTC and shifted a second time.
 */
function sdk_feature_protect_local_time_html(string $html): string
{
    if ($html === '' || stripos($html, 'IST') === false) {
        return $html;
    }
    return (string)preg_replace(
        '/\b(\d{2} [A-Z][a-z]{2} \d{4})\s*·\s*(\d{2}:\d{2}(?::\d{2})?)(\s+(?:AM|PM))?\s+IST\b/',
        '$1 • $2$3 IST',
        $html
    );
}

function sdk_feature_panel_endpoint_is_json(string $script): bool
{
    return in_array($script, ['get_devices.php'], true)
        || ($script === 'check_license.php' && strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET')) === 'POST');
}

function sdk_feature_is_owner_chat(mysqli $conn, string $chatId): bool
{
    $chatId = trim($chatId);
    if ($chatId === '') {
        return false;
    }

    $configured = trim((string)(function_exists('panel_config') ? panel_config('TELEGRAM_OWNER_CHAT_ID', '') : ''));
    if ($configured !== '') {
        return hash_equals($configured, $chatId);
    }

    // Safe fallback: a verified panel Owner may use the Telegram account linked
    // to that Owner account. Generic admin chat IDs remain read-only.
    $stmt = $conn->prepare(
        "SELECT id FROM users WHERE role='owner' AND status=1 AND telegram_chat_id=? LIMIT 1"
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('s', $chatId);
    $stmt->execute();
    $ok = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $ok;
}

function sdk_feature_settings_security_post(mysqli $conn, array $user): void
{
    if (sdk_feature_script_name() !== 'settings.php'
        || strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET')) !== 'POST') {
        return;
    }

    $uid = (int)$user['id'];

    // The legacy settings page previously accepted any >=3-character username,
    // while login.php accepts only this safe alphabet. Intercept the change so
    // an account cannot save a username that can never be used to sign in.
    if (isset($_POST['change_username'])) {
        $username = trim((string)($_POST['new_username'] ?? ''));
        if (preg_match('/^[A-Za-z0-9_.-]{3,32}$/D', $username) !== 1) {
            $_SESSION['sdk_settings_error'] = 'Username must be 3-32 characters using letters, numbers, dot, underscore or hyphen.';
            header('Location: settings.php', true, 303);
            exit;
        }
        $check = $conn->prepare('SELECT id FROM users WHERE username=? AND id<>? LIMIT 1');
        $check->bind_param('si', $username, $uid);
        $check->execute();
        $exists = (bool)$check->get_result()->fetch_assoc();
        $check->close();
        if ($exists) {
            $_SESSION['sdk_settings_error'] = 'Username already exists.';
            header('Location: settings.php', true, 303);
            exit;
        }
        $update = $conn->prepare('UPDATE users SET username=? WHERE id=?');
        $update->bind_param('si', $username, $uid);
        if (!$update->execute() || $update->affected_rows !== 1) {
            $update->close();
            $_SESSION['sdk_settings_error'] = 'Username could not be updated.';
            header('Location: settings.php', true, 303);
            exit;
        }
        $update->close();
        $_SESSION['username'] = $username;
        sdk_feature_audit($conn, 'account_username_change', 'success', $uid, $uid);
        $_SESSION['sdk_settings_message'] = 'Username updated successfully.';
        header('Location: settings.php', true, 303);
        exit;
    }

    // Password changes are security-sensitive: rotate auth_version so every
    // older browser session is invalidated. The current session is intentionally
    // destroyed too, requiring a clean sign-in with the new password/MFA.
    if (isset($_POST['change_password'])) {
        $current = (string)($_POST['current_password'] ?? '');
        $new = (string)($_POST['new_password'] ?? '');
        $confirm = (string)($_POST['confirm_password'] ?? '');
        $q = $conn->prepare('SELECT password FROM users WHERE id=? LIMIT 1');
        $q->bind_param('i', $uid);
        $q->execute();
        $row = $q->get_result()->fetch_assoc();
        $q->close();
        if (!$row || !password_verify($current, (string)$row['password'])) {
            $_SESSION['sdk_settings_error'] = 'Current password is incorrect.';
            header('Location: settings.php', true, 303);
            exit;
        }
        if (strlen($new) < 12 || strlen($new) > 128 || $new !== $confirm) {
            $_SESSION['sdk_settings_error'] = 'New password must be 12-128 characters and both entries must match.';
            header('Location: settings.php', true, 303);
            exit;
        }
        $hash = password_hash($new, PASSWORD_DEFAULT);
        $update = $conn->prepare('UPDATE users SET password=?,auth_version=auth_version+1 WHERE id=?');
        $update->bind_param('si', $hash, $uid);
        if (!$update->execute() || $update->affected_rows !== 1) {
            $update->close();
            $_SESSION['sdk_settings_error'] = 'Password could not be updated.';
            header('Location: settings.php', true, 303);
            exit;
        }
        $update->close();
        sdk_feature_audit($conn, 'account_password_change', 'success', $uid, $uid);
        if (function_exists('panel_destroy_session')) {
            panel_destroy_session();
        }
        header('Location: login.php?password_changed=1', true, 303);
        exit;
    }
}

function sdk_feature_access_preflight(mysqli $conn): void
{
    if (PHP_SAPI === 'cli' || !sdk_feature_installed($conn)) {
        return;
    }

    $script = sdk_feature_script_name();

    // Never alter the SDK activation endpoint or Telegram webhook contract.
    if (in_array($script, ['connect.php', 'telegram_bot.php'], true)) {
        return;
    }

    static $bufferStarted = false;
    if (!$bufferStarted) {
        $bufferStarted = true;
        ob_start('sdk_feature_protect_local_time_html');
    }

    $sessionUserId = (int)($_SESSION['user_id'] ?? 0);
    $user = $sessionUserId > 0 ? sdk_feature_current_user($conn) : null;
    $jsonEndpoint = sdk_feature_panel_endpoint_is_json($script);

    if ($sessionUserId > 0 && !$user) {
        if (function_exists('panel_destroy_session')) {
            panel_destroy_session();
        }
        if ($jsonEndpoint) {
            sdk_feature_json_error(401, 'Session is no longer valid.');
        }
        header('Location: login.php');
        exit;
    }

    if (!$user) {
        return;
    }

    $authVersion = max(1, (int)($user['auth_version'] ?? 1));
    if (isset($_SESSION['sdk_auth_version']) && (int)$_SESSION['sdk_auth_version'] !== $authVersion) {
        sdk_feature_audit($conn, 'session_auth_version', 'revoked', (int)$user['id'], (int)$user['id']);
        if (function_exists('panel_destroy_session')) {
            panel_destroy_session();
        }
        if ($jsonEndpoint) {
            sdk_feature_json_error(401, 'Session security changed. Sign in again.');
        }
        header('Location: login.php');
        exit;
    }
    $_SESSION['sdk_auth_version'] = $authVersion;

    if ((int)($user['status'] ?? 0) !== 1) {
        sdk_feature_audit($conn, 'session_disabled_user', 'blocked', (int)$user['id'], (int)$user['id']);
        if (function_exists('panel_destroy_session')) {
            panel_destroy_session();
        }
        if ($jsonEndpoint) {
            sdk_feature_json_error(403, 'Account disabled.');
        }
        header('Location: login.php');
        exit;
    }

    $settings = sdk_feature_settings($conn);
    $role = strtolower((string)($user['role'] ?? 'user'));
    if (!sdk_feature_bool($settings['panel_online'] ?? '1') && $role !== 'owner') {
        if ($jsonEndpoint) {
            sdk_feature_json_error(503, 'Panel is under maintenance.');
        }
        sdk_feature_render_maintenance((string)($settings['maintenance_message'] ?? 'Maintenance in progress.'));
    }

    sdk_feature_settings_security_post($conn, $user);

    // The legacy pages were designed for owner/admin global data. Route lower
    // roles to ownership-aware pages instead of merely hiding buttons.
    if ($script === 'dashboard.php' && in_array($role, ['reseller', 'user'], true)) {
        header('Location: feature_dashboard.php', true, 303);
        exit;
    }
    if ($script === 'license_list.php' && in_array($role, ['reseller', 'user'], true)) {
        header('Location: feature_licenses.php', true, 303);
        exit;
    }
    if ($script === 'announcements.php' && in_array($role, ['reseller', 'user'], true)) {
        header('Location: feature_announcements.php', true, 303);
        exit;
    }
    if ($script === 'manage_users.php') {
        if ($role === 'owner') {
            header('Location: owner_console.php', true, 303);
            exit;
        }
        if ($role === 'admin') {
            header('Location: feature_users.php', true, 303);
            exit;
        }
        sdk_feature_access_denied('Only Owner/Admin accounts can view the user directory.');
    }
    if ($script === 'manage_referrals.php') {
        if (in_array($role, ['owner', 'admin'], true)) {
            header('Location: feature_referrals.php', true, 303);
            exit;
        }
        sdk_feature_access_denied('Only Owner/Admin accounts can manage referral invites.');
    }
    if ($script === 'online_server.php') {
        if ($role === 'owner') {
            header('Location: feature_server.php', true, 303);
            exit;
        }
        sdk_feature_access_denied('Only an Owner can control SDK server mode.');
    }
    if ($script === 'check_license.php' && !in_array($role, ['owner', 'admin'], true)) {
        if (strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET')) === 'POST') {
            sdk_feature_json_error(403, 'Use My Licenses for your own license status.');
        }
        header('Location: feature_licenses.php', true, 303);
        exit;
    }
}

if (isset($GLOBALS['conn']) && $GLOBALS['conn'] instanceof mysqli) {
    sdk_feature_access_preflight($GLOBALS['conn']);
}
