<?php
declare(strict_types=1);

$config = require __DIR__ . '/config.php';
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/SelfHostedVerifier.php';
require_once __DIR__ . '/AccountManager.php';

$isHttps = strtolower((string) ($_SERVER['HTTPS'] ?? '')) === 'on';
if (!$isHttps && $config['trust_proxy']) {
    $isHttps = strtolower(trim(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')[0])) === 'https';
}
if ($config['require_https'] && !$isHttps) {
    http_response_code(400);
    exit('HTTPS is required');
}

header('Cache-Control: no-store, private');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");

session_name('onecore_dashboard');
session_set_cookie_params([
    'lifetime' => 0,
    'path' => '/',
    'secure' => $isHttps,
    'httponly' => true,
    'samesite' => 'Strict',
]);
session_start();

function h(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function redirectDashboard(string $suffix = ''): void
{
    header('Location: admin_dashboard.php' . $suffix, true, 303);
    exit;
}

function requireCsrf(): void
{
    $provided = $_POST['csrf_token'] ?? '';
    $expected = $_SESSION['csrf_token'] ?? '';
    if (!is_string($provided) || !is_string($expected) || !hash_equals($expected, $provided)) {
        throw new RuntimeException('Invalid form token. Refresh the page and try again.');
    }
}

function positiveInt(string $name, int $minimum, int $maximum): int
{
    $value = filter_var($_POST[$name] ?? null, FILTER_VALIDATE_INT);
    if ($value === false || $value < $minimum || $value > $maximum) {
        throw new RuntimeException("$name must be $minimum-$maximum.");
    }
    return $value;
}

function dashboardBaseUrl(array $config): string
{
    if ($config['api_base_url'] !== '') {
        return rtrim($config['api_base_url'], '/');
    }
    $host = preg_replace('/[^A-Za-z0-9.:-]/', '', (string) ($_SERVER['HTTP_HOST'] ?? ''));
    return 'https://' . $host;
}

$_SESSION['csrf_token'] ??= bin2hex(random_bytes(32));
$error = null;
$notice = $_SESSION['notice'] ?? null;
$createdLicenseKey = $_SESSION['created_license_key'] ?? null;
$createdInviteUrl = $_SESSION['created_invite_url'] ?? null;
unset($_SESSION['notice'], $_SESSION['created_license_key'], $_SESSION['created_invite_url']);

$database = null;
$accounts = null;
$currentUser = null;
$setupRequired = false;
$inviteToken = trim((string) ($_GET['invite'] ?? $_POST['invite_token'] ?? ''));

try {
    $database = new Database($config['database']);
    $accounts = new AccountManager($database);
    $setupRequired = !$accounts->hasUsers();

    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['bootstrap_owner'])) {
        requireCsrf();
        if (!$setupRequired) {
            throw new RuntimeException('Owner setup has already been completed.');
        }
        $apiKey = (string) ($_POST['api_key'] ?? '');
        if ($config['admin_api_key'] === ''
            || str_starts_with(strtoupper($config['admin_api_key']), 'REPLACE_')
            || !hash_equals($config['admin_api_key'], $apiKey)) {
            throw new RuntimeException('Admin API key is invalid or not configured.');
        }
        $password = (string) ($_POST['password'] ?? '');
        if (!hash_equals($password, (string) ($_POST['password_confirm'] ?? ''))) {
            throw new RuntimeException('Passwords do not match.');
        }
        $owner = $accounts->bootstrapOwner((string) ($_POST['username'] ?? ''), $password);
        session_regenerate_id(true);
        $_SESSION['user_id'] = $owner['id'];
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        $_SESSION['notice'] = 'Owner account created. Save its password securely.';
        redirectDashboard();
    }

    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['register'])) {
        requireCsrf();
        $password = (string) ($_POST['password'] ?? '');
        if (!hash_equals($password, (string) ($_POST['password_confirm'] ?? ''))) {
            throw new RuntimeException('Passwords do not match.');
        }
        $user = $accounts->register(
            (string) ($_POST['invite_token'] ?? ''),
            (string) ($_POST['username'] ?? ''),
            $password
        );
        session_regenerate_id(true);
        $_SESSION['user_id'] = $user['id'];
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        $_SESSION['notice'] = 'Registration completed successfully.';
        redirectDashboard();
    }

    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['login'])) {
        requireCsrf();
        $username = (string) ($_POST['username'] ?? '');
        $ip = (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
        $limit = $database->consumeRateLimit($ip . '|' . strtolower($username), 'dashboard-login', 10);
        if (!$limit['allowed']) {
            throw new RuntimeException('Too many login attempts. Try again in one minute.');
        }
        $user = $accounts->authenticate($username, (string) ($_POST['password'] ?? ''));
        if ($user === null) {
            throw new RuntimeException('Invalid username or password.');
        }
        session_regenerate_id(true);
        $_SESSION['user_id'] = $user['id'];
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        redirectDashboard();
    }

    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['logout'])) {
        requireCsrf();
        $_SESSION = [];
        session_destroy();
        redirectDashboard();
    }

    if (isset($_SESSION['user_id'])) {
        $currentUser = $accounts->userById((int) $_SESSION['user_id']);
        if ($currentUser['status'] !== 'active') {
            $_SESSION = [];
            session_destroy();
            throw new RuntimeException('Dashboard account is suspended.');
        }
    }

    if ($currentUser !== null && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
        requireCsrf();
        $action = (string) $_POST['action'];

        if ($action === 'create_license') {
            $result = $accounts->createLicense(
                $currentUser,
                (string) ($_POST['label'] ?? ''),
                (string) ($_POST['key_prefix'] ?? $database->getAppConfig('default_key_prefix', '5OC')),
                positiveInt('max_devices', 1, 100),
                positiveInt('valid_days', 1, 3650)
            );
            $_SESSION['created_license_key'] = $result['key'];
            $_SESSION['notice'] = 'Key created. Cost: ' . $result['cost']
                . ' credit(s). Copy it now; only its hash is stored.';
            redirectDashboard();
        }

        if ($action === 'revoke_license') {
            $accounts->revokeLicense($currentUser, positiveInt('license_id', 1, PHP_INT_MAX));
            $_SESSION['notice'] = 'Key and its bound devices were revoked.';
            redirectDashboard();
        }

        if ($action === 'create_invite') {
            $token = $accounts->createInvite(
                $currentUser,
                (string) ($_POST['assigned_role'] ?? 'user'),
                positiveInt('initial_balance', 0, 1_000_000),
                positiveInt('valid_hours', 1, 720),
                positiveInt('max_uses', 1, 100)
            );
            $_SESSION['created_invite_url'] = dashboardBaseUrl($config)
                . '/admin_dashboard.php?invite=' . rawurlencode($token);
            $_SESSION['notice'] = 'Referral registration link created.';
            redirectDashboard();
        }

        if ($action === 'adjust_balance') {
            $targetUserId = positiveInt('user_id', 1, PHP_INT_MAX);
            $delta = filter_var($_POST['delta'] ?? null, FILTER_VALIDATE_INT);
            if ($delta === false) {
                throw new RuntimeException('Balance adjustment is invalid.');
            }
            $balance = $accounts->adjustBalance(
                $currentUser,
                $targetUserId,
                $delta,
                (string) ($_POST['reason'] ?? '')
            );
            $_SESSION['notice'] = 'Balance updated to ' . $balance . ' credits.';
            redirectDashboard();
        }

        if ($action === 'user_status') {
            $accounts->setUserStatus(
                $currentUser,
                positiveInt('user_id', 1, PHP_INT_MAX),
                (string) ($_POST['status'] ?? '')
            );
            $_SESSION['notice'] = 'Account status updated.';
            redirectDashboard();
        }

        if ($action === 'user_role') {
            $accounts->setUserRole(
                $currentUser,
                positiveInt('user_id', 1, PHP_INT_MAX),
                (string) ($_POST['role'] ?? '')
            );
            $_SESSION['notice'] = 'Account role updated.';
            redirectDashboard();
        }

        if ($action === 'revoke_invite') {
            $accounts->revokeInvite(
                $currentUser,
                positiveInt('invite_id', 1, PHP_INT_MAX)
            );
            $_SESSION['notice'] = 'Registration referral revoked.';
            redirectDashboard();
        }

        if ($action === 'key_settings') {
            if (!AccountManager::isOwner($currentUser)) {
                throw new RuntimeException('Only an owner can change key pricing.');
            }
            $cost = positiveInt('key_cost_credits', 0, 1_000_000);
            $prefix = SelfHostedVerifier::normalizeActivationPrefix(
                (string) ($_POST['default_key_prefix'] ?? '5OC')
            );
            $database->transaction(function (Database $db) use ($cost, $prefix): void {
                $db->setAppConfig('key_cost_credits', $cost);
                $db->setAppConfig('default_key_prefix', $prefix);
            });
            $_SESSION['notice'] = 'Key pricing and default prefix updated.';
            redirectDashboard();
        }

        if (!AccountManager::canOperate($currentUser)) {
            throw new RuntimeException('This account can only manage its own activation keys.');
        }

        if ($action === 'revoke_device') {
            $deviceId = trim((string) ($_POST['device_id'] ?? ''));
            $reason = trim((string) ($_POST['reason'] ?? 'Administrative revocation'));
            if (preg_match('/^[A-Za-z0-9._:-]{16,128}$/D', $deviceId) !== 1
                || $reason === '' || strlen($reason) > 500) {
                throw new RuntimeException('Device ID or reason is invalid.');
            }
            $database->transaction(function (Database $db) use ($deviceId, $reason): void {
                $db->execute(
                    'INSERT INTO devices (device_id, status) VALUES (:device_id, \'revoked\')
                     ON DUPLICATE KEY UPDATE status = \'revoked\'',
                    ['device_id' => $deviceId]
                );
                $device = $db->fetchOne(
                    'SELECT current_token_jti FROM devices WHERE device_id = :device_id FOR UPDATE',
                    ['device_id' => $deviceId]
                );
                $db->execute(
                    'INSERT INTO revoked_tokens (device_id, reason, token_jti)
                     VALUES (:device_id, :reason, :token_jti)',
                    ['device_id' => $deviceId, 'reason' => $reason,
                        'token_jti' => $device['current_token_jti'] ?? null]
                );
            });
            $_SESSION['notice'] = 'Device revoked successfully.';
            redirectDashboard();
        }

        if ($action === 'policy') {
            $mode = (string) ($_POST['verification_mode'] ?? 'self_hosted');
            if (!in_array($mode, ['self_hosted', 'play_integrity'], true)) {
                throw new RuntimeException('Verification mode is invalid.');
            }
            $expiry = positiveInt('token_expiry_seconds', 60, 86_400);
            $rate = positiveInt('rate_limit_per_minute', 1, 600);
            $verdict = (string) ($_POST['required_device_verdict'] ?? '');
            if (!in_array($verdict, [
                'MEETS_BASIC_INTEGRITY', 'MEETS_DEVICE_INTEGRITY', 'MEETS_STRONG_INTEGRITY'
            ], true)) {
                throw new RuntimeException('Device verdict is invalid.');
            }
            $database->transaction(function (Database $db) use ($mode, $expiry, $rate, $verdict): void {
                $db->setAppConfig('verification_mode', $mode);
                $db->setAppConfig('token_expiry_seconds', $expiry);
                $db->setAppConfig('rate_limit_per_minute', $rate);
                $db->setAppConfig('required_device_verdict', $verdict);
                $db->setAppConfig('require_licensed', isset($_POST['require_licensed']));
            });
            $_SESSION['notice'] = 'Runtime policy updated.';
            redirectDashboard();
        }

        if ($action === 'certificate') {
            $fingerprint = strtoupper(str_replace(
                [':', ' ', '-'], '', trim((string) ($_POST['fingerprint'] ?? ''))
            ));
            if (preg_match('/^[A-F0-9]{64}$/D', $fingerprint) !== 1) {
                throw new RuntimeException('Certificate must be a SHA-256 hex fingerprint.');
            }
            $database->setAppConfig('allowed_cert_fingerprint', implode(':', str_split($fingerprint, 2)));
            $_SESSION['notice'] = 'Allowed signing certificate updated.';
            redirectDashboard();
        }
    }
} catch (Throwable $throwable) {
    $error = $throwable->getMessage();
}

$metrics = $policy = $recentLogs = $licenseKeys = $devices = $securityEvents = [];
$users = $invites = $balanceLedger = [];
if ($database !== null && $currentUser !== null) {
    try {
        $policy = $database->getAppConfigMap();
        $mayOperate = AccountManager::canOperate($currentUser);
        $owner = AccountManager::isOwner($currentUser);
        $licenseWhere = $mayOperate ? '' : 'WHERE k.created_by_user_id = :creator';
        $licenseParams = $mayOperate ? [] : ['creator' => $currentUser['id']];
        $licenseKeys = $database->fetchAll(
            'SELECT k.id, k.key_prefix, k.label, k.status, k.max_devices,
                    k.expires_at, k.last_used_at, k.created_at, k.created_by_user_id,
                    u.username AS creator_username, COUNT(b.id) AS bound_devices
             FROM license_keys k
             LEFT JOIN dashboard_users u ON u.id = k.created_by_user_id
             LEFT JOIN device_license_bindings b ON b.license_key_id = k.id '
             . $licenseWhere . '
             GROUP BY k.id, k.key_prefix, k.label, k.status, k.max_devices,
                      k.expires_at, k.last_used_at, k.created_at,
                      k.created_by_user_id, u.username
             ORDER BY k.created_at DESC LIMIT 200',
            $licenseParams
        );
        if ($mayOperate) {
            $metrics = $database->fetchOne(
                'SELECT COUNT(*) AS total_requests,
                        COALESCE(SUM(is_success = 1), 0) AS passed,
                        COALESCE(SUM(is_success = 0), 0) AS blocked,
                        COUNT(DISTINCT device_id) AS unique_devices
                 FROM integrity_logs WHERE timestamp >= UTC_TIMESTAMP() - INTERVAL 24 HOUR'
            ) ?? [];
            $metrics['revoked_devices'] = ($database->fetchOne(
                'SELECT COUNT(*) AS count FROM devices WHERE status = \'revoked\''
            )['count'] ?? 0);
            $recentLogs = $database->fetchAll(
                'SELECT device_id, integrity_verdict, ip_address, timestamp, is_success
                 FROM integrity_logs ORDER BY timestamp DESC LIMIT 50'
            );
            $devices = $database->fetchAll(
                'SELECT d.device_id, d.status, d.last_verified_at, d.token_expires_at,
                        k.key_prefix, k.label
                 FROM devices d
                 LEFT JOIN device_license_bindings b ON b.device_id = d.device_id
                 LEFT JOIN license_keys k ON k.id = b.license_key_id
                 ORDER BY d.updated_at DESC LIMIT 100'
            );
            $securityEvents = $database->fetchAll(
                'SELECT device_id, event_type, severity, ip_address, created_at
                 FROM security_events ORDER BY created_at DESC LIMIT 50'
            );
        }
        if ($owner) {
            $users = $database->fetchAll(
                'SELECT u.id, u.username, u.role, u.balance_credits, u.referral_code,
                        u.status, u.last_login_at, u.created_at, r.username AS referrer
                 FROM dashboard_users u
                 LEFT JOIN dashboard_users r ON r.id = u.referred_by_user_id
                 ORDER BY u.created_at DESC LIMIT 200'
            );
            $invites = $database->fetchAll(
                'SELECT i.id, i.token_prefix, i.assigned_role, i.initial_balance,
                        i.max_uses, i.use_count, i.expires_at, i.status,
                        u.username AS creator
                 FROM registration_invites i
                 JOIN dashboard_users u ON u.id = i.created_by_user_id
                 ORDER BY i.created_at DESC LIMIT 100'
            );
            $balanceLedger = $database->fetchAll(
                'SELECT t.delta_credits, t.balance_after, t.reason, t.created_at,
                        u.username, a.username AS actor
                 FROM balance_transactions t
                 JOIN dashboard_users u ON u.id = t.user_id
                 LEFT JOIN dashboard_users a ON a.id = t.actor_user_id
                 ORDER BY t.created_at DESC LIMIT 100'
            );
        }
    } catch (Throwable $throwable) {
        $error = $throwable->getMessage();
    }
}
?><!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>OneCore Integrity</title><style>
:root{color-scheme:dark;--bg:#060b14;--card:#101b2d;--card2:#15233a;--gold:#f6c453;--muted:#9caec8;--bad:#ff7078;--good:#4ade9c;--line:#293c59}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at 10% 0,#17284a 0,transparent 34%),linear-gradient(145deg,#050912,#0a1426);color:#f7f9fd;font:15px system-ui,sans-serif}main{width:min(1320px,94vw);margin:30px auto}.top{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:18px}.brand{color:var(--gold);letter-spacing:.1em;margin-bottom:4px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}.card{background:linear-gradient(145deg,rgba(21,35,58,.96),rgba(13,24,42,.96));border:1px solid var(--line);border-radius:18px;padding:20px;box-shadow:0 18px 50px #0006;margin-bottom:16px}.metric{font-size:32px;color:var(--gold);font-weight:800}.muted{color:var(--muted)}.pill{display:inline-block;padding:5px 10px;border-radius:999px;background:#243653;color:var(--gold);font-weight:700;text-transform:uppercase;font-size:12px}label{display:block;margin:12px 0 6px}input,select{width:100%;padding:11px 12px;border-radius:10px;border:1px solid #39506f;background:#081426;color:#fff}button{border:0;border-radius:10px;padding:11px 16px;background:var(--gold);color:#251a00;font-weight:800;cursor:pointer}.danger{background:var(--bad);color:#280006}.ghost{background:#293d5e;color:#fff}.notice,.error,.secret{border-radius:12px;padding:13px 15px;margin:14px 0}.notice{background:#123d2e;color:#b7f7d9}.error{background:#4a1e29;color:#ffd6dc}.secret{background:#3b300f;color:#ffe69a;border:1px solid #927525;font:650 14px ui-monospace,monospace;overflow-wrap:anywhere}table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:left;padding:10px;border-bottom:1px solid var(--line);word-break:break-word}.ok{color:var(--good)}.no{color:var(--bad)}.login{max-width:480px;margin:10vh auto}.inline{display:flex;gap:8px;align-items:center}.inline input{width:auto}.actions{margin-top:16px}.compact{display:flex;gap:6px;align-items:center}.compact input{min-width:100px}.section-title{margin-top:30px}.scroll{overflow:auto}h2{font-size:20px}@media(max-width:760px){.top{align-items:flex-start;flex-direction:column}.compact{align-items:stretch;flex-direction:column}.card{padding:16px}}
</style></head><body><main>
<?php if ($currentUser === null): ?>
<section class="card login"><h1 class="brand">ONECORE INTEGRITY</h1>
<?php if ($error): ?><div class="error"><?= h($error) ?></div><?php endif; ?>
<?php if ($setupRequired): ?>
<p class="muted">First-run owner setup. The server ADMIN_API_KEY is required once.</p>
<form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>">
<label>Admin API key</label><input name="api_key" type="password" required autocomplete="off">
<label>Owner username</label><input name="username" required minlength="3" maxlength="32" autocomplete="username">
<label>Password</label><input name="password" type="password" required minlength="10" autocomplete="new-password">
<label>Confirm password</label><input name="password_confirm" type="password" required minlength="10" autocomplete="new-password">
<div class="actions"><button name="bootstrap_owner" value="1">Create owner</button></div></form>
<?php elseif ($inviteToken !== ''): ?>
<p class="muted">Create your invited dashboard account.</p>
<form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="invite_token" value="<?= h($inviteToken) ?>">
<label>Username</label><input name="username" required minlength="3" maxlength="32" autocomplete="username">
<label>Password</label><input name="password" type="password" required minlength="10" autocomplete="new-password">
<label>Confirm password</label><input name="password_confirm" type="password" required minlength="10" autocomplete="new-password">
<div class="actions"><button name="register" value="1">Register</button> <a class="muted" href="admin_dashboard.php">Sign in</a></div></form>
<?php else: ?>
<p class="muted">Sign in with your owner, admin, or key-generator account.</p>
<form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>">
<label>Username</label><input name="username" required autocomplete="username">
<label>Password</label><input name="password" type="password" required autocomplete="current-password">
<div class="actions"><button name="login" value="1">Sign in</button></div></form>
<?php endif; ?></section>
<?php else: $mayOperate = AccountManager::canOperate($currentUser); $owner = AccountManager::isOwner($currentUser); ?>
<header class="top"><div><h1 class="brand">ONECORE INTEGRITY</h1><div class="muted">Signed in as <?= h($currentUser['username']) ?> <span class="pill"><?= h($currentUser['role']) ?></span></div></div><form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><button class="ghost" name="logout" value="1">Sign out</button></form></header>
<?php if ($notice): ?><div class="notice"><?= h($notice) ?></div><?php endif; ?>
<?php if ($createdLicenseKey): ?><div class="secret">New activation key: <?= h($createdLicenseKey) ?></div><?php endif; ?>
<?php if ($createdInviteUrl): ?><div class="secret">Registration referral: <?= h($createdInviteUrl) ?></div><?php endif; ?>
<?php if ($error): ?><div class="error"><?= h($error) ?></div><?php endif; ?>
<section class="grid">
<div class="card"><div class="muted">Balance</div><div class="metric"><?= $owner ? '∞' : h($currentUser['balance_credits']) ?></div><div class="muted"><?= $owner ? 'Owner bypass' : 'credits' ?></div></div>
<?php if ($mayOperate): ?><div class="card"><div class="muted">Requests / 24h</div><div class="metric"><?= h($metrics['total_requests'] ?? 0) ?></div></div><div class="card"><div class="muted">Passed</div><div class="metric"><?= h($metrics['passed'] ?? 0) ?></div></div><div class="card"><div class="muted">Blocked</div><div class="metric"><?= h($metrics['blocked'] ?? 0) ?></div></div><?php endif; ?>
</section>
<section class="grid">
<form class="card" method="post"><h2>Create activation key</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="create_license"><label>Custom name</label><input name="label" maxlength="100" required placeholder="Customer name"><label>Key prefix</label><input name="key_prefix" pattern="[A-Za-z0-9]{2,8}" maxlength="8" required value="<?= h($policy['default_key_prefix'] ?? '5OC') ?>"><label>Maximum devices</label><input name="max_devices" type="number" min="1" max="100" required value="1"><label>Valid for days</label><input name="valid_days" type="number" min="1" max="3650" required value="30"><div class="actions"><button>Create key · <?= $owner ? 'free' : h($policy['key_cost_credits'] ?? 1) . ' credit(s)' ?></button></div></form>
<?php if ($owner): ?><form class="card" method="post"><h2>Create referral</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="create_invite"><label>Role</label><select name="assigned_role"><option value="user">Normal user</option><option value="admin">Admin</option><option value="owner">Owner</option></select><label>Starting balance</label><input name="initial_balance" type="number" min="0" max="1000000" value="0" required><label>Valid hours</label><input name="valid_hours" type="number" min="1" max="720" value="24" required><label>Maximum registrations</label><input name="max_uses" type="number" min="1" max="100" value="1" required><div class="actions"><button>Create registration link</button></div></form>
<form class="card" method="post"><h2>Key pricing</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="key_settings"><label>Default prefix</label><input name="default_key_prefix" pattern="[A-Za-z0-9]{2,8}" maxlength="8" value="<?= h($policy['default_key_prefix'] ?? '5OC') ?>" required><label>Cost per key</label><input name="key_cost_credits" type="number" min="0" max="1000000" value="<?= h($policy['key_cost_credits'] ?? 1) ?>" required><div class="actions"><button>Update pricing</button></div></form><?php endif; ?>
<?php if ($mayOperate): ?><form class="card" method="post"><h2>Revoke device</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="revoke_device"><label>Device ID</label><input name="device_id" minlength="16" maxlength="128" required><label>Reason</label><input name="reason" maxlength="500" required value="Administrative revocation"><div class="actions"><button class="danger">Revoke device</button></div></form><?php endif; ?>
</section>
<section class="card scroll"><h2><?= $mayOperate ? 'All activation keys' : 'My activation keys' ?></h2><table><thead><tr><th>Prefix</th><th>Name</th><?php if ($mayOperate): ?><th>Creator</th><?php endif; ?><th>Devices</th><th>Expires</th><th>Status</th><th>Action</th></tr></thead><tbody><?php foreach ($licenseKeys as $key): ?><tr><td><?= h($key['key_prefix']) ?>…</td><td><?= h($key['label']) ?></td><?php if ($mayOperate): ?><td><?= h($key['creator_username'] ?? 'legacy') ?></td><?php endif; ?><td><?= h($key['bound_devices']) ?>/<?= h($key['max_devices']) ?></td><td><?= h($key['expires_at']) ?></td><td class="<?= $key['status'] === 'active' ? 'ok' : 'no' ?>"><?= h(strtoupper($key['status'])) ?></td><td><?php if ($key['status'] === 'active'): ?><form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="revoke_license"><input type="hidden" name="license_id" value="<?= h($key['id']) ?>"><button class="danger">Revoke</button></form><?php endif; ?></td></tr><?php endforeach; ?></tbody></table></section>
<?php if ($owner): ?><h2 class="section-title">Owner control</h2><section class="card scroll"><h2>Accounts and balances</h2><table><thead><tr><th>User</th><th>Role</th><th>Balance</th><th>Referrer</th><th>Status</th><th>Balance adjustment</th><th>Account</th></tr></thead><tbody><?php foreach ($users as $user): ?><tr><td><?= h($user['username']) ?></td><td><?= h($user['role']) ?></td><td><?= h($user['balance_credits']) ?></td><td><?= h($user['referrer'] ?? '—') ?></td><td class="<?= $user['status'] === 'active' ? 'ok' : 'no' ?>"><?= h($user['status']) ?></td><td><form class="compact" method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="adjust_balance"><input type="hidden" name="user_id" value="<?= h($user['id']) ?>"><input name="delta" type="number" min="-1000000" max="1000000" placeholder="+/- credits" required><input name="reason" maxlength="200" placeholder="Reason" required><button>Apply</button></form></td><td><?php if ((int)$user['id'] !== (int)$currentUser['id']): ?><form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="user_status"><input type="hidden" name="user_id" value="<?= h($user['id']) ?>"><input type="hidden" name="status" value="<?= $user['status'] === 'active' ? 'suspended' : 'active' ?>"><button class="<?= $user['status'] === 'active' ? 'danger' : 'ghost' ?>"><?= $user['status'] === 'active' ? 'Suspend' : 'Activate' ?></button></form><?php endif; ?></td></tr><?php endforeach; ?></tbody></table></section>
<section class="grid"><div class="card"><h2>Role assignments</h2><?php foreach ($users as $user): ?><?php if ((int)$user['id'] !== (int)$currentUser['id']): ?><form class="compact" method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="user_role"><input type="hidden" name="user_id" value="<?= h($user['id']) ?>"><strong><?= h($user['username']) ?></strong><select name="role"><?php foreach(['owner','admin','user'] as $role): ?><option value="<?= h($role) ?>" <?= $user['role'] === $role ? 'selected' : '' ?>><?= h($role) ?></option><?php endforeach; ?></select><button>Set role</button></form><?php endif; ?><?php endforeach; ?></div><div class="card"><h2>Referral controls</h2><?php foreach ($invites as $invite): ?><?php if ($invite['status'] === 'active'): ?><form class="compact" method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="revoke_invite"><input type="hidden" name="invite_id" value="<?= h($invite['id']) ?>"><span><?= h($invite['token_prefix']) ?>… · <?= h($invite['assigned_role']) ?></span><button class="danger">Revoke</button></form><?php endif; ?><?php endforeach; ?></div></section>
<section class="grid"><div class="card scroll"><h2>Referral invitations</h2><table><thead><tr><th>Prefix</th><th>Role</th><th>Balance</th><th>Uses</th><th>Expires</th><th>Status</th></tr></thead><tbody><?php foreach ($invites as $invite): ?><tr><td><?= h($invite['token_prefix']) ?>…</td><td><?= h($invite['assigned_role']) ?></td><td><?= h($invite['initial_balance']) ?></td><td><?= h($invite['use_count']) ?>/<?= h($invite['max_uses']) ?></td><td><?= h($invite['expires_at']) ?></td><td><?= h($invite['status']) ?></td></tr><?php endforeach; ?></tbody></table></div><div class="card scroll"><h2>Balance ledger</h2><table><thead><tr><th>Time</th><th>User</th><th>Change</th><th>Balance</th><th>Reason</th></tr></thead><tbody><?php foreach ($balanceLedger as $entry): ?><tr><td><?= h($entry['created_at']) ?></td><td><?= h($entry['username']) ?></td><td class="<?= $entry['delta_credits'] >= 0 ? 'ok' : 'no' ?>"><?= h($entry['delta_credits']) ?></td><td><?= h($entry['balance_after']) ?></td><td><?= h($entry['reason']) ?></td></tr><?php endforeach; ?></tbody></table></div></section><?php endif; ?>
<?php if ($mayOperate): ?><h2 class="section-title">Operations</h2><section class="grid"><form class="card" method="post"><h2>Runtime policy</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="policy"><label>Verification mode</label><select name="verification_mode"><option value="self_hosted" <?= ($policy['verification_mode'] ?? '') === 'self_hosted' ? 'selected' : '' ?>>Self-hosted</option><option value="play_integrity" <?= ($policy['verification_mode'] ?? '') === 'play_integrity' ? 'selected' : '' ?>>Play Integrity</option></select><label>Token expiry seconds</label><input name="token_expiry_seconds" type="number" min="60" max="86400" value="<?= h($policy['token_expiry_seconds'] ?? 600) ?>" required><label>Rate limit / minute</label><input name="rate_limit_per_minute" type="number" min="1" max="600" value="<?= h($policy['rate_limit_per_minute'] ?? 10) ?>" required><label>Device verdict</label><select name="required_device_verdict"><?php foreach(['MEETS_BASIC_INTEGRITY','MEETS_DEVICE_INTEGRITY','MEETS_STRONG_INTEGRITY'] as $verdict): ?><option <?= ($policy['required_device_verdict'] ?? '') === $verdict ? 'selected' : '' ?>><?= h($verdict) ?></option><?php endforeach; ?></select><label class="inline"><input name="require_licensed" type="checkbox" <?= ($policy['require_licensed'] ?? false) ? 'checked' : '' ?>> Require licensed verdict</label><div class="actions"><button>Update policy</button></div></form><form class="card" method="post"><h2>Signing certificate</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="certificate"><label>Release SHA-256</label><input name="fingerprint" value="<?= h($policy['allowed_cert_fingerprint'] ?? '') ?>" required><div class="actions"><button>Update certificate</button></div></form></section>
<section class="card scroll"><h2>Registered devices</h2><table><thead><tr><th>Device</th><th>License</th><th>Last verified</th><th>Token expiry</th><th>Status</th></tr></thead><tbody><?php foreach ($devices as $device): ?><tr><td><?= h($device['device_id']) ?></td><td><?= h(($device['key_prefix'] ?? '—') . (($device['label'] ?? '') !== '' ? ' · ' . $device['label'] : '')) ?></td><td><?= h($device['last_verified_at']) ?></td><td><?= h($device['token_expires_at']) ?></td><td><?= h($device['status']) ?></td></tr><?php endforeach; ?></tbody></table></section><section class="grid"><div class="card scroll"><h2>Recent verification</h2><table><thead><tr><th>Time</th><th>Device</th><th>Verdict</th><th>Result</th></tr></thead><tbody><?php foreach ($recentLogs as $log): ?><tr><td><?= h($log['timestamp']) ?></td><td><?= h($log['device_id']) ?></td><td><?= h($log['integrity_verdict']) ?></td><td class="<?= $log['is_success'] ? 'ok' : 'no' ?>"><?= $log['is_success'] ? 'PASS' : 'BLOCK' ?></td></tr><?php endforeach; ?></tbody></table></div><div class="card scroll"><h2>Security events</h2><table><thead><tr><th>Time</th><th>Device</th><th>Event</th><th>Severity</th></tr></thead><tbody><?php foreach ($securityEvents as $event): ?><tr><td><?= h($event['created_at']) ?></td><td><?= h($event['device_id']) ?></td><td><?= h($event['event_type']) ?></td><td><?= h($event['severity']) ?></td></tr><?php endforeach; ?></tbody></table></div></section><?php endif; ?>
<?php endif; ?></main></body></html>
