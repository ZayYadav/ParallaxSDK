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
