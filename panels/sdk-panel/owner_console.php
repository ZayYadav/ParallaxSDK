<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$owner = sdk_feature_require_roles($conn, ['owner']);
$P = get_panel_settings($conn);
$settings = sdk_feature_settings($conn);
$ownerId = (int)$owner['id'];
$msg = '';
$err = '';

function sdk_owner_fresh(): bool { return (int)($_SESSION['sdk_owner_fresh_at'] ?? 0) >= time() - 300; }
function sdk_owner_require_fresh(): void { if (!sdk_owner_fresh()) throw new RuntimeException('Confirm your owner password first. Sensitive controls stay unlocked for 5 minutes.'); }
function sdk_owner_active_count(mysqli $conn): int { $r=$conn->query("SELECT COUNT(*) c FROM users WHERE role='owner' AND status=1"); return (int)($r->fetch_assoc()['c'] ?? 0); }

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    try {
        $action = (string)($_POST['action'] ?? '');
        if ($action === 'owner_unlock') {
            $password = (string)($_POST['current_password'] ?? '');
            $st = $conn->prepare('SELECT password FROM users WHERE id=? LIMIT 1');
            $st->bind_param('i', $ownerId); $st->execute(); $row=$st->get_result()->fetch_assoc(); $st->close();
            if (!$row || !password_verify($password, (string)$row['password'])) throw new RuntimeException('Owner password is incorrect.');
            $_SESSION['sdk_owner_fresh_at'] = time();
            sdk_feature_audit($conn, 'owner_stepup', 'success', $ownerId, $ownerId);
            $msg = 'Sensitive controls unlocked for 5 minutes.';
        } else {
            sdk_owner_require_fresh();
            if ($action === 'settings') {
                $old = $settings;
                $values = [
                    'panel_online' => isset($_POST['panel_online']) ? '1' : '0',
                    'registration_open' => isset($_POST['registration_open']) ? '1' : '0',
                    'generation_open' => isset($_POST['generation_open']) ? '1' : '0',
                    'guest_key_enabled' => isset($_POST['guest_key_enabled']) ? '1' : '0',
                    'telegram_linked_generation_enabled' => isset($_POST['telegram_linked_generation_enabled']) ? '1' : '0',
                    'splash_enabled' => isset($_POST['splash_enabled']) ? '1' : '0',
                    'maintenance_message' => trim((string)($_POST['maintenance_message'] ?? '')),
                    'site_announcement' => trim((string)($_POST['site_announcement'] ?? '')),
                    'splash_title' => trim((string)($_POST['splash_title'] ?? '')),
                    'splash_subtitle' => trim((string)($_POST['splash_subtitle'] ?? '')),
                    'splash_duration_ms' => (string)(int)($_POST['splash_duration_ms'] ?? 2000),
                    'key_cost_per_day' => (string)max(0, (int)($_POST['key_cost_per_day'] ?? 1)),
                ];
                if (strlen($values['maintenance_message']) > 500 || strlen($values['site_announcement']) > 1000 || strlen($values['splash_title']) > 80 || strlen($values['splash_subtitle']) > 160) throw new RuntimeException('One of the text settings is too long.');
                if (!in_array((int)$values['splash_duration_ms'], [1400,2000,2400,3200,4200], true)) throw new RuntimeException('Invalid splash duration.');
                if ($values['maintenance_message'] === '') $values['maintenance_message'] = sdk_feature_defaults()['maintenance_message'];
                if ($values['splash_title'] === '') $values['splash_title'] = 'PARALLAX SDK';
                foreach ($values as $key => $value) if (!sdk_feature_save_setting($conn, $key, $value, $ownerId)) throw new RuntimeException('Could not save ' . $key . '.');
                $splashChanged = false;
                foreach (['splash_enabled','splash_title','splash_subtitle','splash_duration_ms'] as $key) if ((string)($old[$key] ?? '') !== (string)$values[$key]) $splashChanged = true;
                if ($splashChanged) sdk_feature_save_setting($conn, 'splash_version', (string)(max(1,(int)($old['splash_version'] ?? 1))+1), $ownerId);
                sdk_feature_audit($conn, 'panel_settings', 'updated', $ownerId, $ownerId, ['panel_online'=>$values['panel_online'],'registration_open'=>$values['registration_open'],'generation_open'=>$values['generation_open'],'guest_key_enabled'=>$values['guest_key_enabled']]);
                $msg = 'Owner controls saved.';
            } elseif ($action === 'user_update') {
                $target=(int)($_POST['user_id'] ?? 0); $role=strtolower(trim((string)($_POST['role'] ?? 'user'))); $status=(int)($_POST['status'] ?? 1);
                if ($target < 1 || !in_array($role,['owner','admin','reseller','user'],true) || !in_array($status,[0,1],true)) throw new RuntimeException('Invalid user update.');
                $st=$conn->prepare('SELECT id,role,status FROM users WHERE id=? LIMIT 1'); $st->bind_param('i',$target); $st->execute(); $before=$st->get_result()->fetch_assoc(); $st->close();
                if (!$before) throw new RuntimeException('User not found.');
                if ($target === $ownerId && ($role !== 'owner' || $status !== 1)) throw new RuntimeException('You cannot remove or disable your current owner session.');
                if ((string)$before['role'] === 'owner' && ((string)$role !== 'owner' || $status !== 1) && sdk_owner_active_count($conn) <= 1) throw new RuntimeException('At least one active Owner must remain.');
                $st=$conn->prepare('UPDATE users SET role=?,status=?,auth_version=auth_version+1 WHERE id=?'); $st->bind_param('sii',$role,$status,$target); $st->execute(); $st->close();
                sdk_feature_audit($conn,'owner_user_update','success',$ownerId,$target,['role'=>$role,'status'=>$status]); $msg='User updated.';
            } elseif ($action === 'balance') {
                $target=(int)($_POST['user_id'] ?? 0); $delta=(int)($_POST['delta'] ?? 0); if ($target < 1 || $delta === 0) throw new RuntimeException('Enter a valid user and non-zero balance change.');
                $next=sdk_feature_adjust_balance($conn,$target,$delta,$ownerId,'Owner console adjustment'); $msg='Balance updated to '.$next.'.';
            } elseif ($action === 'password_reset') {
                $target=(int)($_POST['user_id'] ?? 0); $password=(string)($_POST['new_password'] ?? '');
                if ($target < 1 || strlen($password) < 12 || strlen($password) > 128) throw new RuntimeException('New password must be 12-128 characters.');
                $hash=password_hash($password,PASSWORD_DEFAULT); $st=$conn->prepare('UPDATE users SET password=?,auth_version=auth_version+1 WHERE id=?'); $st->bind_param('si',$hash,$target); $st->execute(); $changed=$st->affected_rows; $st->close();
                if ($changed < 1) throw new RuntimeException('User not found.');
                sdk_feature_audit($conn,'owner_password_reset','success',$ownerId,$target); $msg='Password reset and feature-aware sessions revoked.';
            } elseif ($action === 'telegram_reset') {
                $target=(int)($_POST['user_id'] ?? 0); if ($target < 1) throw new RuntimeException('Invalid user.');
                $conn->begin_transaction();
                $st=$conn->prepare('UPDATE telegram_users SET linked_user_id=NULL WHERE linked_user_id=?'); $st->bind_param('i',$target); $st->execute(); $st->close();
                $st=$conn->prepare('UPDATE users SET telegram_chat_id=NULL,telegram_2fa_enabled=0,telegram_2fa_enabled_at=NULL,auth_version=auth_version+1 WHERE id=?'); $st->bind_param('i',$target); $st->execute(); $st->close();
                $conn->query('UPDATE telegram_login_challenges SET consumed_at=UTC_TIMESTAMP() WHERE user_id='.(int)$target.' AND consumed_at IS NULL');
                $conn->commit(); sdk_feature_audit($conn,'owner_telegram_reset','success',$ownerId,$target); $msg='Telegram link and Telegram 2FA reset.';
            } elseif ($action === 'referral_create') {
                $code=sdk_feature_create_referral($conn,$owner,$_POST); $msg='Referral created: '.$code;
            } elseif ($action === 'referral_revoke') {
                $id=(int)($_POST['referral_id'] ?? 0); if ($id < 1) throw new RuntimeException('Invalid referral.');
                $st=$conn->prepare('UPDATE referral_codes SET status=0,revoked_at=UTC_TIMESTAMP() WHERE id=?'); $st->bind_param('i',$id); $st->execute(); $st->close();
                sdk_feature_audit($conn,'referral_revoke','success',$ownerId,null,['referral_id'=>$id]); $msg='Referral revoked.';
            } elseif ($action === 'broadcast') {
                $bid=sdk_feature_queue_broadcast($conn,$ownerId,(string)($_POST['message'] ?? ''),isset($_POST['panel']),isset($_POST['linked']),isset($_POST['guest']));
                $r=sdk_feature_process_broadcast_batch($conn,$bid,25); $msg='Announcement queued. Sent now: '.$r['sent'].', failed: '.$r['failed'].', remaining: '.$r['remaining'].'.';
            } elseif ($action === 'process_broadcast') {
                $bid=(int)($_POST['broadcast_id'] ?? 0); $r=sdk_feature_process_broadcast_batch($conn,$bid,50); $msg='Broadcast batch processed. Sent: '.$r['sent'].', failed: '.$r['failed'].', remaining: '.$r['remaining'].'.';
            }
        }
    } catch (Throwable $e) {
        @ $conn->rollback();
        $err = $e->getMessage();
    }
    $settings=sdk_feature_settings($conn); $owner=sdk_feature_current_user($conn) ?: $owner;
}

$q=trim((string)($_GET['q'] ?? '')); $roleFilter=strtolower(trim((string)($_GET['role'] ?? ''))); $statusFilter=(string)($_GET['status'] ?? '');
$where=['1=1'];
if ($q !== '') { $e=$conn->real_escape_string($q); $where[]="(username LIKE '%$e%' OR email LIKE '%$e%')"; }
if (in_array($roleFilter,['owner','admin','reseller','user'],true)) { $e=$conn->real_escape_string($roleFilter); $where[]="role='$e'"; }
if (in_array($statusFilter,['0','1'],true)) $where[]='status='.(int)$statusFilter;
$users=$conn->query('SELECT id,username,email,role,balance,status,telegram_chat_id,telegram_2fa_enabled,last_login_at,last_login_ip,created_at FROM users WHERE '.implode(' AND ',$where).' ORDER BY id DESC LIMIT 100');
$refs=$conn->query('SELECT r.*,u.username creator_name FROM referral_codes r LEFT JOIN users u ON u.id=r.created_by ORDER BY r.id DESC LIMIT 30');
$broadcasts=$conn->query('SELECT * FROM announcement_broadcasts ORDER BY id DESC LIMIT 15');
$stats=[];
foreach (['users'=>'SELECT COUNT(*) c FROM users','licenses'=>'SELECT COUNT(*) c FROM licenses','active'=>'SELECT COUNT(*) c FROM licenses WHERE status=1 AND expiry_date>UTC_TIMESTAMP()','telegram'=>'SELECT COUNT(*) c FROM telegram_users'] as $key=>$sql) { $r=$conn->query($sql); $stats[$key]=(int)($r->fetch_assoc()['c'] ?? 0); }
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Owner Console</title><style>
<?= panel_css_vars($P) ?>
*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1480px;margin:auto;padding:28px 16px 100px}.head{display:flex;justify-content:space-between;gap:14px;align-items:center;flex-wrap:wrap}.muted{color:#94a3b8;line-height:1.5}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}.stat,.card{background:#0b1222;border:1px solid rgba(255,255,255,.1);border-radius:20px}.stat{padding:18px}.stat b{display:block;font-size:1.8rem}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.card{padding:20px;margin-bottom:16px}.row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}input,select,textarea{width:100%;padding:11px 12px;border-radius:11px;border:1px solid rgba(255,255,255,.13);background:#050a13;color:#fff;font:inherit;margin:6px 0 10px}textarea{min-height:92px}button,.btn{display:inline-block;padding:10px 13px;border:0;border-radius:11px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f;font-weight:900;cursor:pointer;text-decoration:none}.danger{background:#7f1d1d;color:#fff}.soft{background:#172033;color:#e2e8f0}.msg,.err{padding:12px;border-radius:12px;margin:12px 0}.msg{background:#064e3b}.err{background:#4c1722}.scroll{overflow:auto}.table{width:100%;border-collapse:collapse;font-size:.84rem}.table th,.table td{padding:10px 8px;border-bottom:1px solid rgba(255,255,255,.07);vertical-align:top;text-align:left}.checks{display:flex;gap:14px;flex-wrap:wrap}.checks label{display:flex;gap:7px;align-items:center}.checks input{width:auto;margin:0}details summary{cursor:pointer;color:#bfdbfe}code{color:#fde68a}@media(max-width:980px){.grid{grid-template-columns:1fr}.stats{grid-template-columns:1fr 1fr}}@media(max-width:620px){.stats,.row{grid-template-columns:1fr}}
</style></head><body><main class="wrap">
<div class="head"><div><h1>Owner Command Center</h1><div class="muted">TeamDark-style management features adapted to the existing SDK panel contract. Human dates render in IST.</div></div><div><a class="btn soft" href="dashboard.php">Dashboard</a> <a class="btn soft" href="activity.php">Activity</a> <a class="btn soft" href="telegram_users.php">Telegram Users</a></div></div>
<?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?><?php if($err):?><div class="err"><?=htmlspecialchars($err)?></div><?php endif;?>
<div class="stats"><div class="stat"><b><?=$stats['users']?></b><span class="muted">Panel users</span></div><div class="stat"><b><?=$stats['licenses']?></b><span class="muted">Total licenses</span></div><div class="stat"><b><?=$stats['active']?></b><span class="muted">Active licenses</span></div><div class="stat"><b><?=$stats['telegram']?></b><span class="muted">Telegram users</span></div></div>
<section class="card"><h2>Sensitive-control step-up</h2><p class="muted">Mutations require your current owner password and remain unlocked for 5 minutes.</p><form method="post" class="row"><input type="hidden" name="action" value="owner_unlock"><input type="password" name="current_password" placeholder="Current owner password" required><button><?=sdk_owner_fresh()?'Unlocked · Refresh 5 min':'Unlock controls'?></button></form></section>
<div class="grid"><section class="card"><h2>Panel controls</h2><form method="post"><input type="hidden" name="action" value="settings"><div class="checks"><label><input type="checkbox" name="panel_online" <?=sdk_feature_bool($settings['panel_online'])?'checked':''?>>Panel online</label><label><input type="checkbox" name="registration_open" <?=sdk_feature_bool($settings['registration_open'])?'checked':''?>>Registration</label><label><input type="checkbox" name="generation_open" <?=sdk_feature_bool($settings['generation_open'])?'checked':''?>>Generation</label><label><input type="checkbox" name="guest_key_enabled" <?=sdk_feature_bool($settings['guest_key_enabled'])?'checked':''?>>Guest key</label><label><input type="checkbox" name="telegram_linked_generation_enabled" <?=sdk_feature_bool($settings['telegram_linked_generation_enabled'])?'checked':''?>>Linked TG generation</label><label><input type="checkbox" name="splash_enabled" <?=sdk_feature_bool($settings['splash_enabled'])?'checked':''?>>Splash</label></div><label>Maintenance message</label><textarea name="maintenance_message"><?=htmlspecialchars($settings['maintenance_message'])?></textarea><label>Site announcement</label><textarea name="site_announcement"><?=htmlspecialchars($settings['site_announcement'])?></textarea><div class="row"><div><label>Splash title</label><input name="splash_title" value="<?=htmlspecialchars($settings['splash_title'])?>"></div><div><label>Splash subtitle</label><input name="splash_subtitle" value="<?=htmlspecialchars($settings['splash_subtitle'])?>"></div><div><label>Splash duration</label><select name="splash_duration_ms"><?php foreach([1400,2000,2400,3200,4200] as $d):?><option value="<?=$d?>" <?=(int)$settings['splash_duration_ms']===$d?'selected':''?>><?=$d/1000?> sec</option><?php endforeach;?></select></div><div><label>Key cost / day</label><input type="number" min="0" name="key_cost_per_day" value="<?=(int)$settings['key_cost_per_day']?>"></div></div><button>Save controls</button></form></section>
<section class="card"><h2>Announcement broadcast</h2><form method="post"><input type="hidden" name="action" value="broadcast"><textarea name="message" maxlength="1000" placeholder="Official announcement" required></textarea><div class="checks"><label><input type="checkbox" name="panel" checked>Panel</label><label><input type="checkbox" name="linked">Linked TG</label><label><input type="checkbox" name="guest">Guest TG</label></div><button style="margin-top:12px">Publish & process batch</button></form><h3>Recent broadcasts</h3><div class="scroll"><table class="table"><tr><th>ID</th><th>Status</th><th>Sent / failed</th><th></th></tr><?php while($b=$broadcasts->fetch_assoc()):?><tr><td>#<?=(int)$b['id']?></td><td><?=htmlspecialchars($b['status'])?></td><td><?=(int)$b['sent_count']?> / <?=(int)$b['failed_count']?></td><td><?php if($b['status']!=='complete'):?><form method="post"><input type="hidden" name="action" value="process_broadcast"><input type="hidden" name="broadcast_id" value="<?=(int)$b['id']?>"><button class="soft">Process</button></form><?php endif;?></td></tr><?php endwhile;?></table></div></section></div>
<section class="card"><div class="head"><h2>User directory</h2><form method="get" style="display:flex;gap:8px;flex-wrap:wrap"><input name="q" placeholder="Search user/email" value="<?=htmlspecialchars($q)?>"><select name="role"><option value="">All roles</option><?php foreach(['owner','admin','reseller','user'] as $r):?><option value="<?=$r?>" <?=$roleFilter===$r?'selected':''?>><?=$r?></option><?php endforeach;?></select><select name="status"><option value="">All status</option><option value="1" <?=$statusFilter==='1'?'selected':''?>>Active</option><option value="0" <?=$statusFilter==='0'?'selected':''?>>Disabled</option></select><button>Filter</button></form></div><div class="scroll"><table class="table"><tr><th>User</th><th>Role / status</th><th>Balance</th><th>Telegram</th><th>Last login</th><th>Controls</th></tr><?php while($u=$users->fetch_assoc()):?><tr><td><b><?=htmlspecialchars($u['username'])?></b><br><span class="muted">#<?=(int)$u['id']?> · <?=htmlspecialchars((string)($u['email']??''))?></span></td><td><?=htmlspecialchars($u['role'])?> · <?=((int)$u['status']===1?'active':'disabled')?></td><td><?=(int)$u['balance']?></td><td><?=empty($u['telegram_chat_id'])?'—':'linked'?><?=((int)$u['telegram_2fa_enabled']===1?' · TG 2FA':'')?></td><td><?=htmlspecialchars((string)($u['last_login_at']??'—'))?><br><span class="muted"><?=htmlspecialchars((string)($u['last_login_ip']??''))?></span></td><td><details><summary>Manage</summary><form method="post"><input type="hidden" name="action" value="user_update"><input type="hidden" name="user_id" value="<?=(int)$u['id']?>"><select name="role"><?php foreach(['owner','admin','reseller','user'] as $r):?><option value="<?=$r?>" <?=$u['role']===$r?'selected':''?>><?=$r?></option><?php endforeach;?></select><select name="status"><option value="1" <?=(int)$u['status']===1?'selected':''?>>Active</option><option value="0" <?=(int)$u['status']===0?'selected':''?>>Disabled</option></select><button>Update</button></form><form method="post"><input type="hidden" name="action" value="balance"><input type="hidden" name="user_id" value="<?=(int)$u['id']?>"><input type="number" name="delta" placeholder="+100 or -50" required><button class="soft">Balance</button></form><form method="post"><input type="hidden" name="action" value="password_reset"><input type="hidden" name="user_id" value="<?=(int)$u['id']?>"><input type="password" name="new_password" placeholder="New password (12+)" required><button class="danger">Reset password</button></form><form method="post"><input type="hidden" name="action" value="telegram_reset"><input type="hidden" name="user_id" value="<?=(int)$u['id']?>"><button class="danger">Reset Telegram</button></form></details></td></tr><?php endwhile;?></table></div></section>
<div class="grid"><section class="card"><h2>Create referral</h2><form method="post"><input type="hidden" name="action" value="referral_create"><div class="row"><select name="role"><option value="user">User</option><option value="reseller">Reseller</option><option value="admin">Admin</option></select><input type="number" min="0" name="grant_balance" value="0" placeholder="Signup balance"><input type="number" min="1" max="1000" name="max_uses" value="1"><input type="number" min="1" max="365" name="expiry_days" value="7"></div><input name="code" placeholder="Optional custom referral code"><button>Create referral</button></form></section><section class="card"><h2>Recent referrals</h2><div class="scroll"><table class="table"><tr><th>Code</th><th>Role</th><th>Uses</th><th>Expires</th><th></th></tr><?php while($r=$refs->fetch_assoc()):?><tr><td><code><?=htmlspecialchars($r['code'])?></code><br><span class="muted"><?=htmlspecialchars((string)($r['creator_name']??''))?></span></td><td><?=htmlspecialchars($r['assigned_to'])?></td><td><?=(int)$r['use_count']?>/<?=(int)$r['max_uses']?></td><td><?=htmlspecialchars((string)($r['expires_at']??'—'))?></td><td><?php if((int)$r['status']===1&&$r['revoked_at']===null):?><form method="post"><input type="hidden" name="action" value="referral_revoke"><input type="hidden" name="referral_id" value="<?=(int)$r['id']?>"><button class="danger">Revoke</button></form><?php else:?>Closed<?php endif;?></td></tr><?php endwhile;?></table></div></section></div>
</main></body></html>
