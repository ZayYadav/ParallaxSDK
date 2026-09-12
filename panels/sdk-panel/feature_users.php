<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$actor=sdk_feature_require_roles($conn,['admin']);
$P=get_panel_settings($conn);
$q=trim((string)($_GET['q']??''));$where="role<>'owner'";$params=[];$types='';
if($q!==''){$where.=' AND (username LIKE CONCAT(\'%\',?,\'%\') OR email LIKE CONCAT(\'%\',?,\'%\'))';$params=[$q,$q];$types='ss';}
$sql='SELECT id,username,email,role,balance,status,created_at FROM users WHERE '.$where.' ORDER BY id DESC LIMIT 200';
$st=$conn->prepare($sql);if($params){$st->bind_param($types,...$params);} $st->execute();$users=$st->get_result();
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>User Directory</title><style>
<?=panel_css_vars($P)?>*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1100px;margin:auto;padding:28px 16px 90px}.top{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}.card{background:#0b1222;border:1px solid rgba(255,255,255,.1);border-radius:19px;padding:20px;margin-top:16px}.muted{color:#94a3b8}input{padding:11px 12px;border-radius:10px;border:1px solid rgba(255,255,255,.13);background:#050a13;color:#fff}.btn{display:inline-flex;padding:10px 12px;border-radius:10px;background:#172033;color:#e2e8f0;text-decoration:none;font-weight:800}.tablewrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:760px}.table th,.table td{padding:10px 8px;border-bottom:1px solid rgba(255,255,255,.07);text-align:left}.table th{font-size:.72rem;color:#94a3b8;text-transform:uppercase}
</style></head><body><main class="wrap"><div class="top"><div><h1>User Directory</h1><p class="muted">Admin view is read-only. Owner accounts are intentionally excluded.</p></div><a class="btn" href="dashboard.php">Dashboard</a></div><section class="card"><form method="get"><input name="q" value="<?=htmlspecialchars($q)?>" placeholder="Search username/email"><button class="btn" type="submit">Search</button></form><div class="tablewrap"><table class="table"><thead><tr><th>ID</th><th>Username</th><th>Email</th><th>Role</th><th>Balance</th><th>Status</th><th>Created (IST)</th></tr></thead><tbody><?php while($u=$users->fetch_assoc()):?><tr><td>#<?= (int)$u['id']?></td><td><?=htmlspecialchars((string)$u['username'])?></td><td><?=htmlspecialchars((string)($u['email']??''))?></td><td><?=htmlspecialchars((string)$u['role'])?></td><td><?=number_format((int)$u['balance'])?></td><td><?=((int)$u['status']===1?'Active':'Disabled')?></td><td><?=htmlspecialchars(sdk_feature_display_time((string)$u['created_at']))?></td></tr><?php endwhile;$st->close();?></tbody></table></div></section></main></body></html>
