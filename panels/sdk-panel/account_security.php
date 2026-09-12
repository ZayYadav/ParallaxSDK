<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';
if (!isset($_SESSION['user_id'])) { header('Location: login.php'); exit; }
if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$user = sdk_feature_current_user($conn);
if (!$user) { header('Location: login.php'); exit; }
$P = get_panel_settings($conn);
$msg='';$err='';$oneTime='';
$uid=(int)$user['id'];
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    try {
        $action=(string)($_POST['action']??'');
        if($action==='link_start'){
            $chat=trim((string)($_POST['chat_id']??''));
            if(preg_match('/^-?\d{5,20}$/D',$chat)!==1) throw new RuntimeException('Enter a valid private Telegram Chat ID.');
            $conn->query('DELETE FROM telegram_link_tokens WHERE expires_at<=UTC_TIMESTAMP() OR consumed_at IS NOT NULL');
            $token='SDKLINK-'.strtoupper(bin2hex(random_bytes(6)));$hash=hash('sha256',$token);
            $del=$conn->prepare('DELETE FROM telegram_link_tokens WHERE user_id=? AND consumed_at IS NULL');$del->bind_param('i',$uid);$del->execute();$del->close();
            $st=$conn->prepare('INSERT INTO telegram_link_tokens(user_id,requested_chat_id,token_hash,expires_at) VALUES(?,?,?,UTC_TIMESTAMP()+INTERVAL 15 MINUTE)');
            $st->bind_param('iss',$uid,$chat,$hash);$st->execute();$st->close();
            $oneTime=$token;$msg='Link code created. Send /link CODE to the configured Telegram bot from that exact Chat ID within 15 minutes.';
            sdk_feature_audit($conn,'telegram_link_code','created',$uid,$uid);
        }elseif($action==='enable_tg2fa'){
            $token=strtoupper(trim((string)($_POST['activation_code']??'')));
            if(preg_match('/^SDK2FA-[A-F0-9]{12}$/D',$token)!==1) throw new RuntimeException('Invalid Telegram 2FA activation key.');
            if(empty($user['telegram_chat_id'])) throw new RuntimeException('Link Telegram first.');
            $mfaCheck=$conn->prepare('SELECT mfa_enabled FROM users WHERE id=? LIMIT 1');$mfaCheck->bind_param('i',$uid);$mfaCheck->execute();$mfaRow=$mfaCheck->get_result()->fetch_assoc();$mfaCheck->close();
            if((int)($mfaRow['mfa_enabled']??0)===1) throw new RuntimeException('Existing TOTP MFA is active. Keep TOTP as the login factor, or disable TOTP before enabling Telegram 2FA.');
            $hash=hash('sha256',$token);
            $conn->begin_transaction();
            $st=$conn->prepare('SELECT id FROM telegram_2fa_activation_tokens WHERE user_id=? AND token_hash=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP() FOR UPDATE');$st->bind_param('is',$uid,$hash);$st->execute();$row=$st->get_result()->fetch_assoc();$st->close();
            if(!$row) throw new RuntimeException('Activation key is invalid, used or expired.');
            $id=(int)$row['id'];$up=$conn->prepare('UPDATE telegram_2fa_activation_tokens SET consumed_at=UTC_TIMESTAMP() WHERE id=?');$up->bind_param('i',$id);$up->execute();$up->close();
            $up=$conn->prepare('UPDATE users SET telegram_2fa_enabled=1,telegram_2fa_enabled_at=UTC_TIMESTAMP(),auth_version=auth_version+1 WHERE id=?');$up->bind_param('i',$uid);$up->execute();$up->close();
            $conn->commit();$_SESSION['sdk_auth_version']=(int)$user['auth_version']+1;$msg='Telegram 2FA enabled.';sdk_feature_audit($conn,'telegram_2fa','enabled',$uid,$uid);
        }elseif($action==='disable_tg2fa'){
            $password=(string)($_POST['password']??'');$st=$conn->prepare('SELECT password FROM users WHERE id=?');$st->bind_param('i',$uid);$st->execute();$row=$st->get_result()->fetch_assoc();$st->close();
            if(!$row||!password_verify($password,(string)$row['password'])) throw new RuntimeException('Current password is incorrect.');
            $up=$conn->prepare('UPDATE users SET telegram_2fa_enabled=0,telegram_2fa_enabled_at=NULL,auth_version=auth_version+1 WHERE id=?');$up->bind_param('i',$uid);$up->execute();$up->close();
            $conn->query('UPDATE telegram_login_challenges SET consumed_at=UTC_TIMESTAMP() WHERE user_id='.(int)$uid.' AND consumed_at IS NULL');$_SESSION['sdk_auth_version']=(int)$user['auth_version']+1;$msg='Telegram 2FA disabled.';sdk_feature_audit($conn,'telegram_2fa','disabled',$uid,$uid);
        }elseif($action==='unlink'){
            if((int)$user['telegram_2fa_enabled']===1) throw new RuntimeException('Disable Telegram 2FA before unlinking Telegram.');
            $up=$conn->prepare('UPDATE telegram_users SET linked_user_id=NULL WHERE linked_user_id=?');$up->bind_param('i',$uid);$up->execute();$up->close();
            $up=$conn->prepare('UPDATE users SET telegram_chat_id=NULL,auth_version=auth_version+1 WHERE id=?');$up->bind_param('i',$uid);$up->execute();$up->close();$_SESSION['sdk_auth_version']=(int)$user['auth_version']+1;$msg='Telegram unlinked.';sdk_feature_audit($conn,'telegram_unlink','success',$uid,$uid);
        }
        $user=sdk_feature_current_user($conn)?:$user;
    } catch(Throwable $e){ if($conn->errno===0 && method_exists($conn,'rollback')){ @ $conn->rollback(); } $err=$e->getMessage(); }
}
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Account Security</title><style>
<?= panel_css_vars($P) ?>*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:980px;margin:auto;padding:32px 18px 90px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.card{padding:24px;border:1px solid rgba(255,255,255,.1);border-radius:22px;background:#0b1222}input{width:100%;padding:13px;border-radius:12px;border:1px solid rgba(255,255,255,.14);background:#050a13;color:#fff;margin:7px 0 12px}button{padding:12px 15px;border:0;border-radius:12px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);font-weight:900;cursor:pointer}.danger{background:#7f1d1d;color:#fff}.msg,.err{padding:12px;border-radius:12px;margin:12px 0}.msg{background:#063d2b}.err{background:#4c1722}.muted{color:#94a3b8;line-height:1.6}code{color:#fde68a;word-break:break-all}@media(max-width:760px){.grid{grid-template-columns:1fr}}</style></head><body><main class="wrap"><a href="dashboard.php" style="color:#bfdbfe">← Dashboard</a><h1>Account & Telegram security</h1><p class="muted">Existing TOTP MFA remains authoritative and is never bypassed. Telegram linking and Telegram 2FA are optional when TOTP is not enabled.</p><?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?><?php if($err):?><div class="err"><?=htmlspecialchars($err)?></div><?php endif;?><?php if($oneTime):?><div class="msg">One-time code: <code><?=htmlspecialchars($oneTime)?></code></div><?php endif;?>
<div class="grid"><section class="card"><h2>Telegram link</h2><p class="muted">Linked Chat ID: <b><?=htmlspecialchars((string)($user['telegram_chat_id']??'Not linked'))?></b></p><form method="post"><input type="hidden" name="action" value="link_start"><label>Private Chat ID</label><input name="chat_id" inputmode="numeric" required><button>Create 15-minute link code</button></form><?php if(!empty($user['telegram_chat_id'])):?><form method="post" style="margin-top:14px"><input type="hidden" name="action" value="unlink"><button class="danger">Unlink Telegram</button></form><?php endif;?></section>
<section class="card"><h2>Telegram 2FA</h2><p>Status: <b><?=((int)$user['telegram_2fa_enabled']===1?'Enabled':'Disabled')?></b></p><?php if((int)$user['telegram_2fa_enabled']!==1):?><p class="muted">After linking, send <code>/2fa</code> to the bot and paste the one-time activation key here.</p><form method="post"><input type="hidden" name="action" value="enable_tg2fa"><input name="activation_code" placeholder="SDK2FA-XXXXXXXXXXXX" required><button>Enable Telegram 2FA</button></form><?php else:?><p class="muted">Password login will require a single-use 8-digit Telegram code.</p><form method="post"><input type="hidden" name="action" value="disable_tg2fa"><input type="password" name="password" placeholder="Current password" required><button class="danger">Disable Telegram 2FA</button></form><?php endif;?></section></div></main></body></html>
