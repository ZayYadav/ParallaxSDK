<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$actor = sdk_feature_require_roles($conn, ['reseller', 'user']);
$P = get_panel_settings($conn);
$uid = (int)$actor['id'];
$msg='';$err='';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $id = (int)($_POST['license_id'] ?? 0);
    $action = (string)($_POST['action'] ?? '');
    if ($id < 1 || !in_array($action, ['toggle','reset_devices','delete'], true)) {
        $err = 'Invalid license action.';
    } else {
        $conn->begin_transaction();
        try {
            $st=$conn->prepare('SELECT id,license_key,status FROM licenses WHERE id=? AND owner_user_id=? FOR UPDATE');
            $st->bind_param('ii',$id,$uid);$st->execute();$lic=$st->get_result()->fetch_assoc();$st->close();
            if(!$lic) throw new RuntimeException('License not found.');
            $key=(string)$lic['license_key'];
            if($action==='toggle'){
                $status=(int)$lic['status']===1?0:1;
                $st=$conn->prepare('UPDATE licenses SET status=? WHERE id=? AND owner_user_id=?');
                $st->bind_param('iii',$status,$id,$uid);$st->execute();$st->close();
                $msg=$status===1?'License enabled.':'License disabled.';
            }elseif($action==='reset_devices'){
                $st=$conn->prepare('DELETE FROM devices WHERE license_key=?');
                $st->bind_param('s',$key);$st->execute();$st->close();
                $msg='Bound devices reset.';
            }else{
                $st=$conn->prepare('DELETE FROM licenses WHERE id=? AND owner_user_id=?');
                $st->bind_param('ii',$id,$uid);$st->execute();$st->close();
                $msg='License deleted.';
            }
            $conn->commit();
            sdk_feature_audit($conn,'self_license_'.$action,'success',$uid,$uid,['license_id'=>$id]);
        }catch(Throwable $e){$conn->rollback();$err=$e->getMessage();}
    }
}

$st=$conn->prepare(
    'SELECT l.id,l.license_key,l.client_name,l.package_name,l.expiry_date,l.status,l.max_devices,l.created_at,COUNT(DISTINCT d.device_id) device_count '
    . 'FROM licenses l LEFT JOIN devices d ON d.license_key=l.license_key WHERE l.owner_user_id=? '
    . 'GROUP BY l.id ORDER BY l.id DESC'
);
$st->bind_param('i',$uid);$st->execute();$licenses=$st->get_result();
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>My Licenses</title><style>
<?=panel_css_vars($P)?>*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1200px;margin:auto;padding:28px 16px 90px}.top{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}.btn,button{display:inline-flex;padding:9px 12px;border-radius:10px;border:1px solid rgba(255,255,255,.1);background:#172033;color:#e2e8f0;text-decoration:none;font-weight:800;cursor:pointer}.primary{background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f}.danger{background:#7f1d1d;color:#fff}.card{margin-top:18px;background:#0b1222;border:1px solid rgba(255,255,255,.1);border-radius:19px;padding:18px}.muted{color:#94a3b8}.tablewrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:980px}.table th,.table td{padding:10px 8px;border-bottom:1px solid rgba(255,255,255,.07);text-align:left;vertical-align:top}.table th{font-size:.72rem;color:#94a3b8;text-transform:uppercase}code{color:#fde68a}.acts{display:flex;gap:7px;flex-wrap:wrap}.acts form{display:inline}.msg,.err{padding:11px;border-radius:11px;margin-top:14px}.msg{background:#064e3b}.err{background:#4c1722}
</style></head><body><main class="wrap"><div class="top"><div><h1>My Licenses</h1><p class="muted">Only licenses owned by your account are visible and manageable here.</p></div><div><a class="btn" href="feature_dashboard.php">Dashboard</a> <a class="btn primary" href="feature_generate.php">Generate</a></div></div>
<?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?><?php if($err):?><div class="err"><?=htmlspecialchars($err)?></div><?php endif;?>
<section class="card"><div class="tablewrap"><table class="table"><thead><tr><th>Key</th><th>Client / package</th><th>Expiry (IST)</th><th>Status</th><th>Devices</th><th>Created</th><th>Actions</th></tr></thead><tbody>
<?php while($r=$licenses->fetch_assoc()):$active=(int)$r['status']===1&&strtotime((string)$r['expiry_date'].' UTC')>time();?>
<tr><td><code><?=htmlspecialchars((string)$r['license_key'])?></code></td><td><?=htmlspecialchars((string)$r['client_name'])?><br><span class="muted"><?=htmlspecialchars((string)($r['package_name']?:'ANY'))?></span></td><td><?=htmlspecialchars(sdk_feature_display_time((string)$r['expiry_date']))?></td><td><?=$active?'Active':((int)$r['status']===1?'Expired':'Disabled')?></td><td><?=number_format((int)$r['device_count'])?> / <?=number_format((int)$r['max_devices'])?></td><td><?=htmlspecialchars(sdk_feature_display_time((string)$r['created_at']))?></td><td><div class="acts"><form method="post"><input type="hidden" name="license_id" value="<?= (int)$r['id']?>"><input type="hidden" name="action" value="toggle"><button><?=$active?'Disable':'Enable'?></button></form><form method="post"><input type="hidden" name="license_id" value="<?= (int)$r['id']?>"><input type="hidden" name="action" value="reset_devices"><button>Reset devices</button></form><form method="post" onsubmit="return confirm('Delete this license?')"><input type="hidden" name="license_id" value="<?= (int)$r['id']?>"><input type="hidden" name="action" value="delete"><button class="danger">Delete</button></form></div></td></tr>
<?php endwhile;$st->close();?>
</tbody></table></div></section></main></body></html>
