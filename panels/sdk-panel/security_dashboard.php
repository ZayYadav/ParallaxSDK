<?php
declare(strict_types=1);

session_start();
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';
$currentUser = panel_require_roles($conn, ['owner', 'admin']);
$role_dash = strtolower((string) $currentUser['role']);
$active_page = 'security_dashboard.php';
$P = get_panel_settings($conn);

$metricsResult = $conn->query(
    "SELECT COUNT(*) AS total,
            COALESCE(SUM(result = 'success'), 0) AS passed,
            COALESCE(SUM(result <> 'success'), 0) AS blocked,
            COUNT(DISTINCT NULLIF(device_id, '')) AS devices
     FROM api_audit_logs WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 24 HOUR"
);
$metrics = $metricsResult ? ($metricsResult->fetch_assoc() ?: []) : [];
$recent = $conn->query(
    'SELECT event_type, result, device_id, ip_address, details, created_at
     FROM api_audit_logs ORDER BY id DESC LIMIT 100'
);
$encryptedReady = false;
try {
    $key = base64_decode((string) panel_config('ENCRYPTION_KEY', ''), true);
    $encryptedReady = $key !== false && strlen($key) === 32;
} catch (Throwable $ignored) {
}
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>API Security - <?= htmlspecialchars($P['panel_name'] ?? 'SDK Panel', ENT_QUOTES, 'UTF-8') ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>
*{box-sizing:border-box}body{margin:0;min-height:100vh;color:#eef2ff;font-family:Inter,system-ui,sans-serif;background:radial-gradient(circle at 10% 0%,rgba(201,168,76,.13),transparent 32rem),radial-gradient(circle at 95% 15%,rgba(79,142,247,.12),transparent 32rem),var(--p-bg1)}.wrap{width:min(1380px,100%);margin:auto;padding:32px 20px 60px}.top{display:flex;align-items:end;justify-content:space-between;gap:20px;flex-wrap:wrap}.eyebrow{color:var(--p-primary);font-size:.72rem;font-weight:800;letter-spacing:.18em;text-transform:uppercase}h1{margin:.4rem 0;font-size:clamp(2rem,5vw,3.4rem);font-weight:800}.muted{color:#94a3b8}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:28px}.cardx{border:1px solid rgba(148,163,184,.14);border-radius:20px;background:rgba(15,23,42,.72);box-shadow:0 25px 80px rgba(0,0,0,.25);backdrop-filter:blur(18px)}.metric{padding:22px}.metric strong{display:block;margin-top:8px;font-size:2rem}.badge2{display:inline-flex;padding:.35rem .65rem;border-radius:999px;font-size:.72rem;font-weight:800}.ok{color:#a7f3d0;background:rgba(16,185,129,.13)}.bad{color:#fecdd3;background:rgba(244,63,94,.13)}.table-card{margin-top:20px;overflow:hidden}.head{padding:20px 22px;border-bottom:1px solid rgba(148,163,184,.12);display:flex;justify-content:space-between;gap:15px;align-items:center}.table{--bs-table-bg:transparent;--bs-table-color:#cbd5e1;margin:0}.table th{padding:14px 18px;color:#64748b;font-size:.68rem;letter-spacing:.12em;text-transform:uppercase;border-color:rgba(148,163,184,.1)}.table td{padding:14px 18px;border-color:rgba(148,163,184,.08);vertical-align:middle}.actions{display:flex;gap:10px;flex-wrap:wrap}.btnx{display:inline-flex;align-items:center;gap:8px;padding:11px 15px;border:1px solid rgba(148,163,184,.18);border-radius:12px;color:#e2e8f0;background:rgba(30,41,59,.7);text-decoration:none;font-weight:700;font-size:.84rem}.btnx:hover{color:#fff;border-color:rgba(201,168,76,.45)}code{color:#f8d786}@media(max-width:900px){.grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:540px){.wrap{padding:22px 12px 50px}.grid{grid-template-columns:1fr}.table-card{overflow-x:auto}.metric{padding:18px}}
</style>
</head>
<body>
<main class="wrap">
    <div class="top">
        <div><div class="eyebrow">Runtime protection</div><h1>API security center</h1><p class="muted mb-0">Encrypted activation monitoring, replay blocks and request audit.</p></div>
        <div class="actions"><a class="btnx" href="dashboard.php"><i class="fas fa-arrow-left"></i>Dashboard</a><a class="btnx" href="license_list.php"><i class="fas fa-key"></i>Licenses</a></div>
    </div>

    <section class="grid">
        <article class="cardx metric"><span class="muted">Requests / 24h</span><strong><?= number_format((int)($metrics['total'] ?? 0)) ?></strong></article>
        <article class="cardx metric"><span class="muted">Passed</span><strong style="color:#6ee7b7"><?= number_format((int)($metrics['passed'] ?? 0)) ?></strong></article>
        <article class="cardx metric"><span class="muted">Blocked / failed</span><strong style="color:#fda4af"><?= number_format((int)($metrics['blocked'] ?? 0)) ?></strong></article>
        <article class="cardx metric"><span class="muted">Unique devices</span><strong style="color:#93c5fd"><?= number_format((int)($metrics['devices'] ?? 0)) ?></strong></article>
    </section>

    <section class="cardx table-card">
        <div class="head"><div><div class="eyebrow">Configuration</div><strong>Transport status</strong></div><span class="badge2 <?= $encryptedReady ? 'ok' : 'bad' ?>"><?= $encryptedReady ? 'AES-256-GCM READY' : 'ENCRYPTION KEY INVALID' ?></span></div>
        <div class="p-4 muted small">Legacy compatibility: <strong class="text-white"><?= panel_config('LEGACY_API_ENABLED', true) ? 'Enabled' : 'Disabled' ?></strong> &nbsp; - &nbsp; Rate limit: <strong class="text-white"><?= (int)panel_config('RATE_LIMIT_PER_MINUTE', 30) ?>/minute</strong>. Disable legacy mode only after every installed SDK uses API v2.</div>
    </section>

    <section class="cardx table-card">
        <div class="head"><div><div class="eyebrow">Audit log</div><strong>Recent activation events</strong></div><span class="muted small">UTC</span></div>
        <div class="table-responsive">
        <table class="table align-middle"><thead><tr><th>Time</th><th>Event</th><th>Result</th><th>Device</th><th>IP</th></tr></thead><tbody>
        <?php if (!$recent || $recent->num_rows === 0): ?><tr><td colspan="5" class="text-center muted py-5">No API events recorded yet.</td></tr><?php else: while ($row = $recent->fetch_assoc()): ?>
            <tr><td class="text-nowrap"><?= htmlspecialchars($row['created_at'], ENT_QUOTES, 'UTF-8') ?></td><td><?= htmlspecialchars($row['event_type'], ENT_QUOTES, 'UTF-8') ?></td><td><span class="badge2 <?= $row['result']==='success'?'ok':'bad' ?>"><?= htmlspecialchars(strtoupper($row['result']), ENT_QUOTES, 'UTF-8') ?></span></td><td><code><?= htmlspecialchars($row['device_id'] ?: '-', ENT_QUOTES, 'UTF-8') ?></code></td><td><?= htmlspecialchars($row['ip_address'], ENT_QUOTES, 'UTF-8') ?></td></tr>
        <?php endwhile; endif; ?>
        </tbody></table></div>
    </section>
</main>
</body>
</html>
