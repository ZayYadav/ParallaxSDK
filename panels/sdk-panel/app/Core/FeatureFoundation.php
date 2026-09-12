<?php
declare(strict_types=1);

/**
 * TeamDark-style management features adapted to Parallax SDK Panel.
 *
 * Contract boundary: this file must never change SDK activation payloads,
 * connect.php request fields, API v2/v3 cryptography, or license validation.
 * It only adds human-panel/account/Telegram management features.
 */

const SDK_FEATURE_REQUIRED_TABLE = 'sdk_feature_suite_meta';

function sdk_feature_installed(mysqli $conn): bool
{
    static $cache = [];
    $key = spl_object_id($conn);
    if (array_key_exists($key, $cache)) {
        return $cache[$key];
    }
    $stmt = $conn->prepare(
        'SELECT COUNT(*) c FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?'
    );
    if (!$stmt) {
        return $cache[$key] = false;
    }
    $table = SDK_FEATURE_REQUIRED_TABLE;
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    return $cache[$key] = ((int)($row['c'] ?? 0) > 0);
}

function sdk_feature_defaults(): array
{
    return [
        'panel_online' => '1',
        'registration_open' => '1',
        'generation_open' => '1',
        'maintenance_message' => 'The panel is temporarily under maintenance. Please try again later.',
        'site_announcement' => '',
        'splash_enabled' => '0',
        'splash_title' => 'PARALLAX SDK',
        'splash_subtitle' => 'Secure control plane',
        'splash_duration_ms' => '2000',
        'splash_version' => '1',
        'key_cost_per_day' => '1',
        'guest_key_enabled' => '1',
        'telegram_linked_generation_enabled' => '0',
    ];
}

function sdk_feature_settings(mysqli $conn): array
{
    $settings = sdk_feature_defaults();
    if (!sdk_feature_installed($conn)) {
        return $settings;
    }
    $res = $conn->query('SELECT setting_key,setting_value FROM panel_settings');
    if (!$res) {
        return $settings;
    }
    while ($row = $res->fetch_assoc()) {
        $key = (string)$row['setting_key'];
        if (array_key_exists($key, $settings)) {
            $settings[$key] = (string)$row['setting_value'];
        }
    }
    return $settings;
}

function sdk_feature_display_time(?string $utc, string $format = 'd M Y · h:i A'): string
{
    $utc = trim((string)$utc);
    if ($utc === '') {
        return '—';
    }
    if (class_exists('PanelTime')) {
        return PanelTime::formatUtc($utc, $format) . ' IST';
    }
    try {
        return (new DateTimeImmutable($utc, new DateTimeZone('UTC')))
            ->setTimezone(new DateTimeZone('Asia/Kolkata'))->format($format) . ' IST';
    } catch (Throwable $e) {
        return $utc;
    }
}

function sdk_feature_bool($value): bool
{
    return in_array(strtolower(trim((string)$value)), ['1', 'true', 'yes', 'on'], true);
}

function sdk_feature_save_setting(mysqli $conn, string $key, string $value, ?int $actorId): bool
{
    if (!array_key_exists($key, sdk_feature_defaults())) {
        return false;
    }
    if (strlen($value) > 1000 || preg_match('/[\x00-\x08\x0B\x0C\x0E-\x1F]/', $value)) {
        return false;
    }
    $stmt = $conn->prepare(
        'INSERT INTO panel_settings(setting_key,setting_value,updated_by) VALUES(?,?,?) '
        . 'ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value),updated_by=VALUES(updated_by),updated_at=UTC_TIMESTAMP()'
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ssi', $key, $value, $actorId);
    $ok = $stmt->execute();
    $stmt->close();
    return $ok;
}

function sdk_feature_current_user(mysqli $conn): ?array
{
    $id = (int)($_SESSION['user_id'] ?? 0);
    if ($id < 1 || !sdk_feature_installed($conn)) {
        return null;
    }
    $stmt = $conn->prepare(
        'SELECT id,username,email,role,balance,status,is_online,telegram_chat_id,telegram_2fa_enabled,auth_version,login_not_before,last_login_at,last_login_ip '
        . 'FROM users WHERE id=? LIMIT 1'
    );
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $user = $stmt->get_result()->fetch_assoc() ?: null;
    $stmt->close();
    return $user;
}

function sdk_feature_role_rank(string $role): int
{
    return match ($role) {
        'owner' => 40,
        'admin' => 30,
        'reseller' => 20,
        default => 10,
    };
}

function sdk_feature_ip(): string
{
    if (function_exists('panel_client_ip')) {
        return substr((string)panel_client_ip(), 0, 45);
    }
    $ip = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    return filter_var($ip, FILTER_VALIDATE_IP) ? $ip : '';
}

function sdk_feature_audit(
    mysqli $conn,
    string $action,
    string $result = 'success',
    ?int $actorId = null,
    ?int $targetId = null,
    array $metadata = []
): void {
    if (!sdk_feature_installed($conn)) {
        return;
    }
    $blocked = ['password', 'token', 'secret', 'otp', 'license_key', 'mfa_secret'];
    foreach ($blocked as $needle) {
        foreach (array_keys($metadata) as $key) {
            if (stripos((string)$key, $needle) !== false) {
                unset($metadata[$key]);
            }
        }
    }
    $action = substr(preg_replace('/[^A-Za-z0-9_.:-]/', '_', $action) ?: 'event', 0, 80);
    $result = substr(preg_replace('/[^A-Za-z0-9_.:-]/', '_', $result) ?: 'unknown', 0, 32);
    $ip = sdk_feature_ip();
    $json = $metadata === [] ? null : json_encode($metadata, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    $stmt = $conn->prepare(
        'INSERT INTO panel_activity_logs(actor_user_id,target_user_id,action,result,ip_address,metadata) VALUES(?,?,?,?,?,?)'
    );
    if (!$stmt) {
        return;
    }
    $stmt->bind_param('iissss', $actorId, $targetId, $action, $result, $ip, $json);
    $stmt->execute();
    $stmt->close();
}

function sdk_feature_require_roles(mysqli $conn, array $roles): array
{
    $user = sdk_feature_current_user($conn);
    if (!$user) {
        header('Location: login.php');
        exit;
    }
    if (!in_array((string)$user['role'], $roles, true)) {
        http_response_code(403);
        exit('ACCESS_DENIED');
    }
    return $user;
}

function sdk_feature_script_name(): string
{
    return strtolower((string)($_SERVER['SDK_PANEL_ROUTE_TARGET'] ?? basename((string)($_SERVER['SCRIPT_NAME'] ?? ''))));
}

function sdk_feature_is_api_request(): bool
{
    $script = sdk_feature_script_name();
    if (in_array($script, ['connect.php', 'telegram_bot.php', 'check_license.php', 'get_devices.php', 'action.php'], true)) {
        return true;
    }
    $path = strtolower((string)(parse_url((string)($_SERVER['REQUEST_URI'] ?? ''), PHP_URL_PATH) ?: ''));
    return str_ends_with(rtrim($path, '/'), '/api/connect') || str_ends_with(rtrim($path, '/'), '/telegram/webhook');
}

function sdk_feature_render_maintenance(string $message): never
{
    http_response_code(503);
    header('Content-Type: text/html; charset=UTF-8');
    $safe = htmlspecialchars($message, ENT_QUOTES, 'UTF-8');
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>Panel maintenance</title><style>'
        . 'body{margin:0;min-height:100svh;display:grid;place-items:center;background:#050810;color:#f8fafc;font-family:Inter,system-ui,sans-serif;padding:24px}'
        . '.c{max-width:560px;padding:34px;border:1px solid rgba(255,255,255,.12);border-radius:24px;background:rgba(15,23,42,.82);box-shadow:0 24px 80px rgba(0,0,0,.4)}'
        . 'h1{margin:0 0 12px;font-size:1.6rem}p{margin:0;color:#a7b0c2;line-height:1.65}</style></head><body>'
        . '<div class="c"><h1>Maintenance mode</h1><p>' . $safe . '</p></div></body></html>';
    exit;
}

function sdk_feature_bootstrap(mysqli $conn): void
{
    if (PHP_SAPI === 'cli') {
        return;
    }
    if (!sdk_feature_installed($conn)) {
        return;
    }

    $script = sdk_feature_script_name();

    // Telegram feature commands are handled before the legacy owner-only bot.
    if ($script === 'telegram_bot.php') {
        if (sdk_feature_handle_telegram_webhook($conn)) {
            exit;
        }
        return;
    }

    // Never alter SDK/API behavior.
    if (sdk_feature_is_api_request()) {
        return;
    }

    $settings = sdk_feature_settings($conn);
    $user = sdk_feature_current_user($conn);
    $GLOBALS['SDK_FEATURE_SETTINGS'] = $settings;
    $GLOBALS['SDK_FEATURE_USER'] = $user;

    // Feature security version revokes older web sessions after owner/password/2FA changes.
    if ($user) {
        $currentAuthVersion = max(1, (int)($user['auth_version'] ?? 1));
        if (isset($_SESSION['sdk_auth_version']) && (int)$_SESSION['sdk_auth_version'] !== $currentAuthVersion) {
            sdk_feature_audit($conn, 'session_auth_version', 'revoked', (int)$user['id'], (int)$user['id']);
            if (function_exists('panel_destroy_session')) { panel_destroy_session(); }
            header('Location: login.php');
            exit;
        }
        $_SESSION['sdk_auth_version'] = $currentAuthVersion;
    }

    // Disabled users lose existing sessions on their next human-panel request.
    if ($user && (int)$user['status'] !== 1) {
        sdk_feature_audit($conn, 'session_disabled_user', 'blocked', (int)$user['id'], (int)$user['id']);
        if (function_exists('panel_destroy_session')) {
            panel_destroy_session();
        }
        header('Location: login.php');
        exit;
    }

    // Panel maintenance never affects connect.php/API validation. Owner remains
    // able to sign in and restore the panel.
    if (!sdk_feature_bool($settings['panel_online']) && $user && (string)$user['role'] !== 'owner') {
        sdk_feature_render_maintenance((string)$settings['maintenance_message']);
    }

    // Registration is blocked server-side while leaving the login page usable.
    if ($script === 'register.php' && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' && isset($_POST['register'])) {
        if (!sdk_feature_bool($settings['panel_online']) || !sdk_feature_bool($settings['registration_open'])) {
            sdk_feature_render_maintenance('Registration is temporarily closed by the owner.');
        }
        sdk_feature_handle_registration($conn);
    }

    // Route reseller/user generation to the balance-aware generator without
    // touching the existing owner/admin generator implementation.
    if ($script === 'generate_ui.php' && $user && in_array((string)$user['role'], ['reseller', 'user'], true)) {
        header('Location: feature_generate.php');
        exit;
    }
    if ($script === 'generate_ui.php' && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST'
        && $user && (string)$user['role'] !== 'owner' && !sdk_feature_bool($settings['generation_open'])) {
        $_SESSION['gen_error'] = 'License generation is paused by the owner.';
        header('Location: generate_ui.php');
        exit;
    }

    // Optional Telegram 2FA is additive to the existing TOTP flow. We intercept
    // only users who explicitly enabled Telegram 2FA.
    if ($script === 'login.php') {
        sdk_feature_login_intercept($conn);
    }

    // Human-page decorator: announcement, optional splash, advanced shortcuts.
    ob_start('sdk_feature_transform_html');
}

function sdk_feature_transform_html(string $html): string
{
    if ($html === '' || stripos($html, '</body>') === false || stripos($html, '<html') === false) {
        return $html;
    }
    $settings = $GLOBALS['SDK_FEATURE_SETTINGS'] ?? null;
    if (!is_array($settings)) {
        return $html;
    }
    $user = $GLOBALS['SDK_FEATURE_USER'] ?? null;

    $parts = [];
    $announcement = trim((string)($settings['site_announcement'] ?? ''));
    if ($announcement !== '') {
        $parts[] = '<div id="sdk-feature-announcement" style="position:fixed;z-index:2147482000;top:72px;left:50%;transform:translateX(-50%);max-width:min(92vw,760px);padding:11px 16px;border:1px solid rgba(201,168,76,.35);border-radius:14px;background:rgba(8,13,25,.95);box-shadow:0 14px 40px rgba(0,0,0,.35);color:#f8fafc;font:600 13px/1.45 Inter,system-ui,sans-serif;text-align:center;backdrop-filter:blur(12px)">'
            . htmlspecialchars($announcement, ENT_QUOTES, 'UTF-8') . '</div>';
    }

    if (sdk_feature_bool($settings['splash_enabled'] ?? '0')) {
        $duration = max(1400, min(4200, (int)($settings['splash_duration_ms'] ?? 2000)));
        $version = max(1, (int)($settings['splash_version'] ?? 1));
        $title = htmlspecialchars((string)($settings['splash_title'] ?? 'PARALLAX SDK'), ENT_QUOTES, 'UTF-8');
        $subtitle = htmlspecialchars((string)($settings['splash_subtitle'] ?? 'Secure control plane'), ENT_QUOTES, 'UTF-8');
        $parts[] = '<div id="sdk-opening-splash" data-version="' . $version . '" style="position:fixed;inset:0;z-index:2147483000;display:grid;place-items:center;background:radial-gradient(circle at 50% 30%,rgba(79,142,247,.18),transparent 42%),#050810;color:#fff;font-family:Inter,system-ui,sans-serif;transition:opacity .35s ease,visibility .35s ease"><div style="text-align:center;padding:28px"><div style="font-size:clamp(1.7rem,6vw,3.4rem);font-weight:900;letter-spacing:-.04em">' . $title . '</div><div style="margin-top:8px;color:#94a3b8;font-size:.92rem;letter-spacing:.08em;text-transform:uppercase">' . $subtitle . '</div><div style="width:180px;height:3px;margin:24px auto 0;border-radius:99px;background:rgba(255,255,255,.1);overflow:hidden"><i style="display:block;width:100%;height:100%;background:linear-gradient(90deg,#c9a84c,#4f8ef7);transform-origin:left;animation:sdkSplashProgress ' . $duration . 'ms linear both"></i></div></div></div>'
            . '<style>@keyframes sdkSplashProgress{from{transform:scaleX(0)}to{transform:scaleX(1)}}@media(prefers-reduced-motion:reduce){#sdk-opening-splash{display:none!important}}</style>'
            . '<script>(()=>{const e=document.getElementById("sdk-opening-splash");if(!e)return;const k="sdk_splash_version",v=e.dataset.version;try{if(sessionStorage.getItem(k)===v){e.remove();return}sessionStorage.setItem(k,v)}catch(_){ }setTimeout(()=>{e.style.opacity="0";e.style.visibility="hidden";setTimeout(()=>e.remove(),420)},' . $duration . ')})()</script>';
    }

    if (is_array($user)) {
        $role = (string)($user['role'] ?? 'user');
        $links = '<div id="sdk-feature-launcher" style="position:fixed;z-index:2147481500;right:14px;bottom:14px;display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end;font-family:Inter,system-ui,sans-serif">'
            . '<a href="account_security.php" style="text-decoration:none;padding:9px 12px;border-radius:999px;background:rgba(15,23,42,.94);border:1px solid rgba(255,255,255,.12);color:#e2e8f0;font-size:12px;font-weight:700;box-shadow:0 10px 30px rgba(0,0,0,.3)">Account</a>';
        if (in_array($role, ['owner', 'admin'], true)) {
            $links .= '<a href="activity.php" style="text-decoration:none;padding:9px 12px;border-radius:999px;background:rgba(15,23,42,.94);border:1px solid rgba(255,255,255,.12);color:#e2e8f0;font-size:12px;font-weight:700;box-shadow:0 10px 30px rgba(0,0,0,.3)">Activity</a>';
        }
        if ($role === 'owner') {
            $links .= '<a href="owner_console.php" style="text-decoration:none;padding:9px 12px;border-radius:999px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f;font-size:12px;font-weight:900;box-shadow:0 10px 30px rgba(0,0,0,.3)">Owner Console</a>';
        }
        $links .= '</div>';
        $parts[] = $links;
    }

    if ($parts !== []) {
        $html = str_ireplace('</body>', implode("\n", $parts) . "\n</body>", $html);
    }
    return $html;
}
