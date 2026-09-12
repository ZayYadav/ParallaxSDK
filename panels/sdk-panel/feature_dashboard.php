<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$actor = sdk_feature_require_roles($conn, ['reseller', 'user']);
$P = get_panel_settings($conn);
$uid = (int)$actor['id'];

$statsStmt = $conn->prepare(
    "SELECT COUNT(*) total,
            COALESCE(SUM(status=1 AND expiry_date>UTC_TIMESTAMP()),0) active,
            COALESCE(SUM(status<>1 OR expiry_date<=UTC_TIMESTAMP()),0) inactive
     FROM licenses WHERE owner_user_id=?"
);
$statsStmt->bind_param('i', $uid);
$statsStmt->execute();
$stats = $statsStmt->get_result()->fetch_assoc() ?: [];
$statsStmt->close();

$deviceStmt = $conn->prepare(
    'SELECT COUNT(DISTINCT d.device_id) c FROM licenses l LEFT JOIN devices d ON d.license_key=l.license_key WHERE l.owner_user_id=?'
);
$deviceStmt->bind_param('i', $uid);
$deviceStmt->execute();
$deviceCount = (int)($deviceStmt->get_result()->fetch_assoc()['c'] ?? 0);
$deviceStmt->close();

$recentStmt = $conn->prepare(
    'SELECT l.id,l.license_key,l.client_name,l.expiry_date,l.status,l.max_devices,COUNT(DISTINCT d.device_id) device_count '
    . 'FROM licenses l LEFT JOIN devices d ON d.license_key=l.license_key '
    . 'WHERE l.owner_user_id=? GROUP BY l.id ORDER BY l.id DESC LIMIT 10'
);
$recentStmt->bind_param('i', $uid);
$recentStmt->execute();
$recent = $recentStmt->get_result();
$unread = count_unread_announcements($conn, $uid);
?>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><?=htmlspecialchars($P['dashboard_title'] ?? 'Parallax SDK')?> · My Dashboard</title><style>
<?=panel_css_vars($P)?>
*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1180px;margin:auto;padding:28px 16px 90px}.top{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap}.actions{display:flex;gap:9px;flex-wrap:wrap}.btn{display:inline-flex;padding:10px 13px;border-radius:11px;background:#172033;color:#e2e8f0;text-decoration:none;font-weight:800;border:1px solid rgba(255,255,255,.1)}.primary{background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:20px 0}.stat,.card{background:#0b1222;border:1px solid rgba(255,255,255,.1);border-radius:19px}.stat{padding:18px}.stat b{display:block;font-size:1.8rem}.muted{color:#94a3b8}.card{padding:20px}.tablewrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:760px}.table th,.table td{padding:11px 9px;border-bottom:1px solid rgba(255,255,255,.07);text-align:left}.table th{color:#94a3b8;font-size:.73rem;text-transform:uppercase}code{color:#fde68a}.badge{display:inline-flex;padding:5px 9px;border-radius:999px;background:rgba(255,255,255,.07)}@media(max-width:800px){.stats{grid-template-columns:1fr 1fr}}@media(max-width:480px){.stats{grid-template-columns:1fr}}
</style></head><body><main class="wrap">
<div class="top"><div><span class="badge"><?=htmlspecialchars(strtoupper((string)$actor['role']))?></span><h1>My Dashboard</h1><p class="muted">Only licenses owned by your account are shown here.</p></div><div class="actions"><a class="btn primary" href="feature_generate.php">Generate</a><a class="btn" href="feature_licenses.php">My Licenses</a><a class="btn" href="account_security.php">Account</a><a class="btn" href="announcements.php">Announcements<?=$unread>0?' ('.$unread.')':''?></a></div></div>
<div class="stats"><div class="stat"><b><?=number_format((int)($stats['total']??0))?></b><span class="muted">My licenses</span></div><div class="stat"><b><?=number_format((int)($stats['active']??0))?></b><span class="muted">Active</span></div><div class="stat"><b><?=number_format((int)($stats['inactive']??0))?></b><span class="muted">Inactive / expired</span></div><div class="stat"><b><?=number_format($deviceCount)?></b><span class="muted">Known devices</span></div></div>
<section class="card"><h2>Recent licenses</h2><div class="tablewrap"><table class="table"><thead><tr><th>Key</th><th>Client</th><th>Expiry (IST)</th><th>Status</th><th>Devices</th></tr></thead><tbody>
<?php while($row=$recent->fetch_assoc()): $active=(int)$row['status']===1 && strtotime((string)$row['expiry_date'].' UTC')>time(); ?>
<tr><td><code><?=htmlspecialchars((string)$row['license_key'])?></code></td><td><?=htmlspecialchars((string)$row['client_name'])?></td><td><?=htmlspecialchars(sdk_feature_display_time((string)$row['expiry_date']))?></td><td><?=$active?'Active':'Inactive'?></td><td><?=number_format((int)$row['device_count'])?> / <?=number_format((int)$row['max_devices'])?></td></tr>
<?php endwhile; $recentStmt->close(); ?>
</tbody></table></div></section>
</main></body></html>
