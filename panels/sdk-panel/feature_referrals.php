<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$actor=sdk_feature_require_roles($conn,['owner','admin']);
$P=get_panel_settings($conn);$uid=(int)$actor['id'];$role=(string)$actor['role'];$msg='';$err='';

if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
    try{
        $action=(string)($_POST['action']??'');
        if($action==='create'){
            $input=$_POST;
            if($role==='admin'){
                $input['role']='user';
                $input['grant_balance']='0';
            }
            $code=sdk_feature_create_referral($conn,$actor,$input);$msg='Referral created: '.$code;
        }elseif($action==='revoke'){
            $id=(int)($_POST['referral_id']??0);if($id<1)throw new RuntimeException('Invalid referral.');
            if($role==='owner'){$st=$conn->prepare('UPDATE referral_codes SET status=0,revoked_at=UTC_TIMESTAMP() WHERE id=?');$st->bind_param('i',$id);}
            else{$st=$conn->prepare('UPDATE referral_codes SET status=0,revoked_at=UTC_TIMESTAMP() WHERE id=? AND created_by=?');$st->bind_param('ii',$id,$uid);}
            $st->execute();$changed=$st->affected_rows;$st->close();if($changed<1)throw new RuntimeException('Referral not found or not owned by you.');
            sdk_feature_audit($conn,'referral_revoke','success',$uid,null,['referral_id'=>$id]);$msg='Referral revoked.';
        }
    }catch(Throwable $e){$err=$e->getMessage();}
}

if($role==='owner'){$refs=$conn->query('SELECT r.*,u.username creator_name FROM referral_codes r LEFT JOIN users u ON u.id=r.created_by ORDER BY r.id DESC LIMIT 200');}
else{$st=$conn->prepare('SELECT r.*,u.username creator_name FROM referral_codes r LEFT JOIN users u ON u.id=r.created_by WHERE r.created_by=? ORDER BY r.id DESC LIMIT 200');$st->bind_param('i',$uid);$st->execute();$refs=$st->get_result();}
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Referrals</title><style>
<?=panel_css_vars($P)?>*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1180px;margin:auto;padding:28px 16px 90px}.top{display:flex;justify-content:space-between;gap:12px;align-items:center;flex-wrap:wrap}.card{background:#0b1222;border:1px solid rgba(255,255,255,.1);border-radius:19px;padding:20px;margin-top:16px}.row{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}input,select{width:100%;padding:11px;border-radius:10px;border:1px solid rgba(255,255,255,.13);background:#050a13;color:#fff}button,.btn{padding:10px 12px;border:0;border-radius:10px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);font-weight:900;color:#07111f;text-decoration:none;cursor:pointer}.danger{background:#7f1d1d;color:#fff}.muted{color:#94a3b8}.tablewrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:900px}.table th,.table td{padding:10px 8px;border-bottom:1px solid rgba(255,255,255,.07);text-align:left}.msg,.err{padding:11px;border-radius:11px;margin-top:12px}.msg{background:#064e3b}.err{background:#4c1722}code{color:#fde68a}@media(max-width:800px){.row{grid-template-columns:1fr 1fr}}@media(max-width:480px){.row{grid-template-columns:1fr}}
</style></head><body><main class="wrap"><div class="top"><div><h1>Referral Manager</h1><p class="muted"><?=$role==='owner'?'Owner can create Admin, Reseller or User referrals.':'Admin can create User referrals only; balance grants are Owner-only.'?></p></div><a class="btn" href="<?=$role==='owner'?'owner_console.php':'dashboard.php'?>">Back</a></div>
<?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?><?php if($err):?><div class="err"><?=htmlspecialchars($err)?></div><?php endif;?>
<section class="card"><h2>Create referral</h2><form method="post"><input type="hidden" name="action" value="create"><div class="row"><div><label>Role</label><select name="role"><?php if($role==='owner'):?><option value="admin">Admin</option><option value="reseller">Reseller</option><?php endif;?><option value="user">User</option></select></div><div><label>Balance grant</label><input type="number" min="0" name="grant_balance" value="0" <?=$role==='admin'?'readonly':''?>></div><div><label>Maximum uses</label><input type="number" min="1" max="1000" name="max_uses" value="1"></div><div><label>Expiry days</label><input type="number" min="1" max="365" name="expiry_days" value="7"></div></div><label>Custom code (optional)</label><input name="code" maxlength="40" placeholder="SDK-CUSTOM-CODE"><button type="submit" style="margin-top:12px">Create referral</button></form></section>
<section class="card"><h2><?=$role==='owner'?'All referrals':'My referrals'?></h2><div class="tablewrap"><table class="table"><thead><tr><th>Code</th><th>Role</th><th>Grant</th><th>Uses</th><th>Expires</th><th>Creator</th><th>Status</th><th></th></tr></thead><tbody><?php while($r=$refs->fetch_assoc()):$available=(int)$r['status']===1&&$r['revoked_at']===null&&((int)$r['max_uses']===0||(int)$r['use_count']<(int)$r['max_uses'])&&($r['expires_at']===null||strtotime((string)$r['expires_at'].' UTC')>time());?><tr><td><code><?=htmlspecialchars((string)$r['code'])?></code></td><td><?=htmlspecialchars((string)$r['assigned_to'])?></td><td><?=number_format((int)$r['grant_balance'])?></td><td><?=number_format((int)$r['use_count'])?> / <?=number_format((int)$r['max_uses'])?></td><td><?=htmlspecialchars(sdk_feature_display_time((string)$r['expires_at']))?></td><td><?=htmlspecialchars((string)($r['creator_name']??'—'))?></td><td><?=$available?'Available':'Closed'?></td><td><?php if($available):?><form method="post"><input type="hidden" name="action" value="revoke"><input type="hidden" name="referral_id" value="<?= (int)$r['id']?>"><button class="danger">Revoke</button></form><?php endif;?></td></tr><?php endwhile;?></tbody></table></div></section>
</main></body></html>