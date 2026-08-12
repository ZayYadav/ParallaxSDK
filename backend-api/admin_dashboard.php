<?php
declare(strict_types=1);

$config = require __DIR__ . '/config.php';
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/SelfHostedVerifier.php';

if ($config['admin_api_key'] === ''
    || str_starts_with(strtoupper($config['admin_api_key']), 'REPLACE_')) {
    http_response_code(503);
    exit('Admin configuration is incomplete');
}

$isHttps = strtolower((string) ($_SERVER['HTTPS'] ?? '')) === 'on';
if (!$isHttps && $config['trust_proxy']) {
    $isHttps = strtolower(trim(explode(',', $_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')[0]))
        === 'https';
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

session_name('onecore_admin');
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

function dashboardRedirect(): void
{
    header('Location: admin_dashboard.php', true, 303);
    exit;
}

function requireCsrf(): void
{
    $provided = $_POST['csrf_token'] ?? '';
    $expected = $_SESSION['csrf_token'] ?? '';
    if (!is_string($provided) || !is_string($expected) || !hash_equals($expected, $provided)) {
        throw new RuntimeException('Invalid form token');
    }
}

if (!isset($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}

$error = null;
$notice = $_SESSION['notice'] ?? null;
$createdLicenseKey = $_SESSION['created_license_key'] ?? null;
unset($_SESSION['notice']);
unset($_SESSION['created_license_key']);

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['login'])) {
    requireCsrf();
    $provided = (string) ($_POST['api_key'] ?? '');
    if ($config['admin_api_key'] !== '' && hash_equals($config['admin_api_key'], $provided)) {
        session_regenerate_id(true);
        $_SESSION['authenticated'] = true;
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        dashboardRedirect();
    }
    $error = 'Invalid admin API key.';
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['logout'])) {
    requireCsrf();
    $_SESSION = [];
    session_destroy();
    dashboardRedirect();
}

$authenticated = ($_SESSION['authenticated'] ?? false) === true;
$database = null;
$metrics = [];
$policy = [];
$recentLogs = [];
$licenseKeys = [];
$devices = [];
$securityEvents = [];

if ($authenticated) {
    try {
        $database = new Database($config['database']);
        if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
            requireCsrf();
            $action = (string) $_POST['action'];

            if ($action === 'revoke') {
                $deviceId = trim((string) ($_POST['device_id'] ?? ''));
                $reason = trim((string) ($_POST['reason'] ?? 'Administrative revocation'));
                if (preg_match('/^[A-Za-z0-9._:-]{16,128}$/D', $deviceId) !== 1) {
                    throw new RuntimeException('Device ID format is invalid.');
                }
                if ($reason === '' || strlen($reason) > 500) {
                    throw new RuntimeException('Reason must be 1-500 characters.');
                }

                $database->transaction(function (Database $db) use ($deviceId, $reason): void {
                    $db->execute(
                        'INSERT INTO devices (device_id, status)
                         VALUES (:device_id, \'revoked\')
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
                        [
                            'device_id' => $deviceId,
                            'reason' => $reason,
                            'token_jti' => $device['current_token_jti'] ?? null,
                        ]
                    );
                });
                $_SESSION['notice'] = 'Device revoked successfully.';
                dashboardRedirect();
            }

            if ($action === 'policy') {
                $verificationMode = (string) ($_POST['verification_mode'] ?? 'self_hosted');
                if (!in_array($verificationMode, ['self_hosted', 'play_integrity'], true)) {
                    throw new RuntimeException('Verification mode is invalid.');
                }
                $expiry = filter_var($_POST['token_expiry_seconds'] ?? null, FILTER_VALIDATE_INT);
                $rate = filter_var($_POST['rate_limit_per_minute'] ?? null, FILTER_VALIDATE_INT);
                $deviceVerdict = (string) ($_POST['required_device_verdict'] ?? '');
                $allowedVerdicts = [
                    'MEETS_BASIC_INTEGRITY',
                    'MEETS_DEVICE_INTEGRITY',
                    'MEETS_STRONG_INTEGRITY',
                ];
                if ($expiry === false || $expiry < 60 || $expiry > 86_400) {
                    throw new RuntimeException('Token expiry must be 60-86400 seconds.');
                }
                if ($rate === false || $rate < 1 || $rate > 600) {
                    throw new RuntimeException('Rate limit must be 1-600 per minute.');
                }
                if (!in_array($deviceVerdict, $allowedVerdicts, true)) {
                    throw new RuntimeException('Device verdict is invalid.');
                }
                $requireLicensed = isset($_POST['require_licensed']);
                $database->transaction(function (Database $db) use (
                    $expiry,
                    $rate,
                    $deviceVerdict,
                    $requireLicensed,
                    $verificationMode
                ): void {
                    $db->setAppConfig('verification_mode', $verificationMode);
                    $db->setAppConfig('token_expiry_seconds', $expiry);
                    $db->setAppConfig('rate_limit_per_minute', $rate);
                    $db->setAppConfig('required_device_verdict', $deviceVerdict);
                    $db->setAppConfig('require_licensed', $requireLicensed);
                });
                $_SESSION['notice'] = 'Runtime policy updated.';
                dashboardRedirect();
            }

            if ($action === 'create_license') {
                $label = trim((string) ($_POST['label'] ?? 'Customer'));
                $maxDevices = filter_var($_POST['max_devices'] ?? null, FILTER_VALIDATE_INT);
                $validDays = filter_var($_POST['valid_days'] ?? null, FILTER_VALIDATE_INT);
                if ($label === '' || strlen($label) > 100) {
                    throw new RuntimeException('License label must be 1-100 characters.');
                }
                if ($maxDevices === false || $maxDevices < 1 || $maxDevices > 100) {
                    throw new RuntimeException('Max devices must be 1-100.');
                }
                if ($validDays === false || $validDays < 1 || $validDays > 3650) {
                    throw new RuntimeException('Validity must be 1-3650 days.');
                }
                $plainKey = SelfHostedVerifier::generateActivationKey();
                $expiresAt = gmdate('Y-m-d H:i:s', time() + ($validDays * 86_400));
                $database->execute(
                    'INSERT INTO license_keys
                        (key_hash, key_prefix, label, max_devices, expires_at)
                     VALUES
                        (:key_hash, :key_prefix, :label, :max_devices, :expires_at)',
                    [
                        'key_hash' => SelfHostedVerifier::activationKeyHash($plainKey),
                        'key_prefix' => substr($plainKey, 0, 12),
                        'label' => $label,
                        'max_devices' => $maxDevices,
                        'expires_at' => $expiresAt,
                    ]
                );
                $_SESSION['notice'] = 'License created. Copy it now; only its hash is stored.';
                $_SESSION['created_license_key'] = $plainKey;
                dashboardRedirect();
            }

            if ($action === 'revoke_license') {
                $licenseId = filter_var($_POST['license_id'] ?? null, FILTER_VALIDATE_INT);
                if ($licenseId === false || $licenseId < 1) {
                    throw new RuntimeException('License ID is invalid.');
                }
                $database->transaction(function (Database $db) use ($licenseId): void {
                    $db->execute(
                        'UPDATE license_keys SET status = \'revoked\' WHERE id = :id',
                        ['id' => $licenseId]
                    );
                    $db->execute(
                        'UPDATE devices d
                         INNER JOIN device_license_bindings b ON b.device_id = d.device_id
                         SET d.status = \'revoked\'
                         WHERE b.license_key_id = :id',
                        ['id' => $licenseId]
                    );
                });
                $_SESSION['notice'] = 'License and its bound devices were revoked.';
                dashboardRedirect();
            }

            if ($action === 'certificate') {
                $fingerprint = strtoupper(str_replace(
                    [':', ' ', '-'],
                    '',
                    trim((string) ($_POST['fingerprint'] ?? ''))
                ));
                if (preg_match('/^[A-F0-9]{64}$/D', $fingerprint) !== 1) {
                    throw new RuntimeException('Certificate must be a SHA-256 hex fingerprint.');
                }
                $formatted = implode(':', str_split($fingerprint, 2));
                $database->setAppConfig('allowed_cert_fingerprint', $formatted);
                $_SESSION['notice'] = 'Allowed signing certificate updated.';
                dashboardRedirect();
            }
        }

        $metrics = $database->fetchOne(
            'SELECT COUNT(*) AS total_requests,
                    COALESCE(SUM(is_success = 1), 0) AS passed,
                    COALESCE(SUM(is_success = 0), 0) AS blocked,
                    COUNT(DISTINCT device_id) AS unique_devices
             FROM integrity_logs
             WHERE timestamp >= UTC_TIMESTAMP() - INTERVAL 24 HOUR'
        ) ?? [];
        $revoked = $database->fetchOne(
            'SELECT COUNT(*) AS count FROM devices WHERE status = \'revoked\''
        );
        $metrics['revoked_devices'] = $revoked['count'] ?? 0;
        $policy = $database->getAppConfigMap();
        $recentLogs = $database->fetchAll(
            'SELECT device_id, integrity_verdict, ip_address, timestamp, is_success
             FROM integrity_logs ORDER BY timestamp DESC LIMIT 50'
        );
        $licenseKeys = $database->fetchAll(
            'SELECT k.id, k.key_prefix, k.label, k.status, k.max_devices,
                    k.expires_at, k.last_used_at, k.created_at,
                    COUNT(b.id) AS bound_devices
             FROM license_keys k
             LEFT JOIN device_license_bindings b ON b.license_key_id = k.id
             GROUP BY k.id, k.key_prefix, k.label, k.status, k.max_devices,
                      k.expires_at, k.last_used_at, k.created_at
             ORDER BY k.created_at DESC LIMIT 100'
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
    } catch (Throwable $throwable) {
        $error = $throwable->getMessage();
    }
}
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>OneCore Integrity Admin</title>
    <style>
        :root{color-scheme:dark;--bg:#08101e;--card:#111c2e;--gold:#f5c451;--muted:#9cabc1;--bad:#ff6b6b;--good:#42d392}
        *{box-sizing:border-box}body{margin:0;background:linear-gradient(145deg,#050a13,#0d1830);color:#f7f9fc;font:15px system-ui,sans-serif;min-height:100vh}
        main{width:min(1120px,92vw);margin:40px auto}.top{display:flex;align-items:center;justify-content:space-between;gap:16px}.brand{color:var(--gold);letter-spacing:.08em}
        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:16px}.card{background:rgba(17,28,46,.95);border:1px solid #263855;border-radius:18px;padding:20px;box-shadow:0 14px 40px #0005;margin-bottom:16px}
        .metric{font-size:32px;color:var(--gold);font-weight:750}.muted{color:var(--muted)}label{display:block;margin:12px 0 6px}input,select{width:100%;padding:11px 12px;border-radius:10px;border:1px solid #344765;background:#0a1424;color:#fff}
        button{border:0;border-radius:10px;padding:11px 16px;background:var(--gold);color:#201700;font-weight:750;cursor:pointer}.danger{background:var(--bad);color:#250000}.ghost{background:#263855;color:#fff}
        .notice,.error,.secret{border-radius:12px;padding:12px 15px;margin:14px 0}.notice{background:#123c2d;color:#a9f3d1}.error{background:#471e27;color:#ffd4da}.secret{background:#382e0f;color:#ffe398;border:1px solid #8d7122;font:600 15px ui-monospace,monospace;overflow-wrap:anywhere}
        table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:left;padding:10px;border-bottom:1px solid #273650;word-break:break-word}.ok{color:var(--good)}.no{color:var(--bad)}
        .login{max-width:440px;margin:12vh auto}.inline{display:flex;gap:10px;align-items:center}.inline input{width:auto}.actions{margin-top:16px}@media(max-width:700px){table{display:block;overflow-x:auto}.top{align-items:flex-start;flex-direction:column}}
    </style>
</head>
<body><main>
<?php if (!$authenticated): ?>
    <section class="card login">
        <h1 class="brand">ONECORE INTEGRITY</h1>
        <p class="muted">Enter the server-side admin API key. It is kept only in this secure session.</p>
        <?php if ($error): ?><div class="error"><?= h($error) ?></div><?php endif; ?>
        <form method="post">
            <input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>">
            <label for="api_key">Admin API key</label>
            <input id="api_key" name="api_key" type="password" required autocomplete="current-password">
            <div class="actions"><button name="login" value="1">Sign in</button></div>
        </form>
    </section>
<?php else: ?>
    <header class="top">
        <div><h1 class="brand">ONECORE INTEGRITY</h1><p class="muted">Live licensing and policy control</p></div>
        <form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><button class="ghost" name="logout" value="1">Sign out</button></form>
    </header>
    <?php if ($notice): ?><div class="notice"><?= h($notice) ?></div><?php endif; ?>
    <?php if ($createdLicenseKey): ?><div class="secret">New activation key: <?= h($createdLicenseKey) ?></div><?php endif; ?>
    <?php if ($error): ?><div class="error"><?= h($error) ?></div><?php endif; ?>
    <section class="grid">
        <div class="card"><div class="muted">Requests / 24h</div><div class="metric"><?= h($metrics['total_requests'] ?? 0) ?></div></div>
        <div class="card"><div class="muted">Passed</div><div class="metric"><?= h($metrics['passed'] ?? 0) ?></div></div>
        <div class="card"><div class="muted">Blocked</div><div class="metric"><?= h($metrics['blocked'] ?? 0) ?></div></div>
        <div class="card"><div class="muted">Revoked devices</div><div class="metric"><?= h($metrics['revoked_devices'] ?? 0) ?></div></div>
    </section>
    <section class="grid">
        <form class="card" method="post">
            <h2>Create activation key</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="create_license">
            <label>Customer / label</label><input name="label" maxlength="100" required value="Customer">
            <label>Maximum devices</label><input name="max_devices" type="number" min="1" max="100" required value="1">
            <label>Valid for days</label><input name="valid_days" type="number" min="1" max="3650" required value="30">
            <div class="actions"><button>Create key</button></div>
        </form>
        <form class="card" method="post">
            <h2>Revoke device</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="revoke">
            <label>Device ID</label><input name="device_id" minlength="16" maxlength="128" required>
            <label>Reason</label><input name="reason" maxlength="500" required value="Fraud detected">
            <div class="actions"><button class="danger">Revoke immediately</button></div>
        </form>
        <form class="card" method="post">
            <h2>Runtime policy</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="policy">
            <label>Verification mode</label><select name="verification_mode"><option value="self_hosted" <?= ($policy['verification_mode'] ?? 'self_hosted') === 'self_hosted' ? 'selected' : '' ?>>Self-hosted (no Play Console)</option><option value="play_integrity" <?= ($policy['verification_mode'] ?? '') === 'play_integrity' ? 'selected' : '' ?>>Google Play Integrity</option></select>
            <label>Token expiry (seconds)</label><input name="token_expiry_seconds" type="number" min="60" max="86400" required value="<?= h($policy['token_expiry_seconds'] ?? 600) ?>">
            <label>Rate limit / minute</label><input name="rate_limit_per_minute" type="number" min="1" max="600" required value="<?= h($policy['rate_limit_per_minute'] ?? 10) ?>">
            <label>Required device verdict</label><select name="required_device_verdict"><?php foreach (['MEETS_BASIC_INTEGRITY','MEETS_DEVICE_INTEGRITY','MEETS_STRONG_INTEGRITY'] as $option): ?><option value="<?= h($option) ?>" <?= ($policy['required_device_verdict'] ?? '') === $option ? 'selected' : '' ?>><?= h($option) ?></option><?php endforeach; ?></select>
            <label class="inline"><input type="checkbox" name="require_licensed" <?= ($policy['require_licensed'] ?? true) ? 'checked' : '' ?>> Require LICENSED verdict</label>
            <div class="actions"><button>Update policy</button></div>
        </form>
        <form class="card" method="post">
            <h2>Signing certificate</h2><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="certificate">
            <label>Allowed release signing SHA-256</label><input name="fingerprint" required value="<?= h($policy['allowed_cert_fingerprint'] ?? '') ?>">
            <div class="actions"><button>Update certificate</button></div>
        </form>
    </section>
    <section class="card"><h2>Activation keys</h2><table><thead><tr><th>Prefix</th><th>Label</th><th>Devices</th><th>Expires</th><th>Status</th><th>Action</th></tr></thead><tbody><?php foreach ($licenseKeys as $key): ?><tr><td><?= h($key['key_prefix']) ?>…</td><td><?= h($key['label']) ?></td><td><?= h($key['bound_devices']) ?>/<?= h($key['max_devices']) ?></td><td><?= h($key['expires_at']) ?></td><td class="<?= $key['status'] === 'active' ? 'ok' : 'no' ?>"><?= h(strtoupper($key['status'])) ?></td><td><?php if ($key['status'] === 'active'): ?><form method="post"><input type="hidden" name="csrf_token" value="<?= h($_SESSION['csrf_token']) ?>"><input type="hidden" name="action" value="revoke_license"><input type="hidden" name="license_id" value="<?= h($key['id']) ?>"><button class="danger">Revoke</button></form><?php endif; ?></td></tr><?php endforeach; ?></tbody></table></section>
    <section class="card"><h2>Registered devices</h2><table><thead><tr><th>Device ID</th><th>License</th><th>Last verified</th><th>Token expires</th><th>Status</th></tr></thead><tbody><?php foreach ($devices as $device): ?><tr><td><?= h($device['device_id']) ?></td><td><?= h(($device['key_prefix'] ?? '—') . ($device['label'] ? ' · ' . $device['label'] : '')) ?></td><td><?= h($device['last_verified_at']) ?></td><td><?= h($device['token_expires_at']) ?></td><td class="<?= $device['status'] === 'active' ? 'ok' : 'no' ?>"><?= h(strtoupper($device['status'])) ?></td></tr><?php endforeach; ?></tbody></table></section>
    <section class="card"><h2>Recent integrity requests</h2><table><thead><tr><th>Time (UTC)</th><th>Device</th><th>Verdict</th><th>IP</th><th>Result</th></tr></thead><tbody><?php foreach ($recentLogs as $log): ?><tr><td><?= h($log['timestamp']) ?></td><td><?= h($log['device_id']) ?></td><td><?= h($log['integrity_verdict']) ?></td><td><?= h($log['ip_address']) ?></td><td class="<?= $log['is_success'] ? 'ok' : 'no' ?>"><?= $log['is_success'] ? 'PASS' : 'BLOCK' ?></td></tr><?php endforeach; ?></tbody></table></section>
    <section class="card"><h2>Security events</h2><table><thead><tr><th>Time (UTC)</th><th>Device</th><th>Event</th><th>Severity</th><th>IP</th></tr></thead><tbody><?php foreach ($securityEvents as $event): ?><tr><td><?= h($event['created_at']) ?></td><td><?= h($event['device_id']) ?></td><td><?= h($event['event_type']) ?></td><td class="<?= $event['severity'] === 'info' ? 'ok' : 'no' ?>"><?= h(strtoupper($event['severity'])) ?></td><td><?= h($event['ip_address']) ?></td></tr><?php endforeach; ?></tbody></table></section>
<?php endif; ?>
</main></body></html>
