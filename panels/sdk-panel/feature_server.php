<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$owner=sdk_feature_require_roles($conn,['owner']);
$P=get_panel_settings($conn);$uid=(int)$owner['id'];$msg='';$err='';

$getSetting=static function(mysqli $conn,string $key,string $default=''):string{
    $st=$conn->prepare('SELECT setting_value FROM server_settings WHERE setting_key=? LIMIT 1');$st->bind_param('s',$key);$st->execute();$r=$st->get_result()->fetch_assoc();$st->close();return $r?(string)$r['setting_value']:$default;
};
$mode=strtolower($getSetting($conn,'server_mode',$getSetting($conn,'server_status','online')));
if(!in_array($mode,['online','maintenance','offline'],true))$mode='offline';
$maintenance=$getSetting($conn,'maintenance_message','Maintenance in progress');

if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
    try{
        $password=(string)($_POST['current_password']??'');
        $st=$conn->prepare('SELECT password FROM users WHERE id=? LIMIT 1');$st->bind_param('i',$uid);$st->execute();$row=$st->get_result()->fetch_assoc();$st->close();
        if(!$row||!password_verify($password,(string)$row['password']))throw new RuntimeException('Owner password is incorrect.');
        $newMode=strtolower(trim((string)($_POST['server_mode']??'')));
        $newMessage=trim((string)($_POST['maintenance_message']??''));
        if(!in_array($newMode,['online','maintenance','offline'],true))throw new RuntimeException('Invalid server mode.');
        if($newMessage===''||strlen($newMessage)>500)throw new RuntimeException('Maintenance message must be 1-500 characters.');
        $conn->begin_transaction();
        $up=$conn->prepare('INSERT INTO server_settings(setting_key,setting_value,broadcast_version) VALUES(?,?,0) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)');
        foreach([['server_mode',$newMode],['server_status',$newMode],['maintenance_message',$newMessage]] as [$k,$v]){$up->bind_param('ss',$k,$v);if(!$up->execute())throw new RuntimeException('Could not update server settings.');}
        $up->close();$conn->commit();$mode=$newMode;$maintenance=$newMessage;
        sdk_feature_audit($conn,'sdk_server_mode','updated',$uid,$uid,['mode'=>$mode]);$msg='SDK server mode updated. server_mode and server_status are synchronized.';
    }catch(Throwable $e){@$conn->rollback();$err=$e->getMessage();}
}
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>SDK Server Control</title><style>
<?=panel_css_vars($P)?>*{box-sizing:border-box}body{margin:0;min-height:100svh;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:760px;margin:auto;padding:34px 16px 90px}.card{padding:24px;border-radius:22px;background:#0b1222;border:1px solid rgba(255,255,255,.1)}.badge{display:inline-flex;padding:7px 11px;border-radius:999px;background:rgba(255,255,255,.07);font-weight:900}.muted{color:#94a3b8;line-height:1.55}select,input,textarea{width:100%;padding:12px;border-radius:11px;border:1px solid rgba(255,255,255,.13);background:#050a13;color:#fff;margin:7px 0 13px;font:inherit}textarea{min-height:110px}button,.btn{display:inline-flex;padding:11px 14px;border:0;border-radius:11px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f;font-weight:900;text-decoration:none;cursor:pointer}.msg,.err{padding:12px;border-radius:11px;margin:12px 0}.msg{background:#064e3b}.err{background:#4c1722}
</style></head><body><main class="wrap"><p><a class="btn" href="owner_console.php">← Owner Console</a></p><section class="card"><span class="badge"><?=htmlspecialchars(strtoupper($mode))?></span><h1>SDK Server Control</h1><p class="muted">This controls the existing SDK server mode used by connect.php. The endpoint URL, request fields, encryption and response contract are unchanged.</p><?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?><?php if($err):?><div class="err"><?=htmlspecialchars($err)?></div><?php endif;?><form method="post"><label>Server mode</label><select name="server_mode"><option value="online" <?=$mode==='online'?'selected':''?>>Online</option><option value="maintenance" <?=$mode==='maintenance'?'selected':''?>>Maintenance</option><option value="offline" <?=$mode==='offline'?'selected':''?>>Offline</option></select><label>Maintenance / offline message</label><textarea name="maintenance_message" maxlength="500" required><?=htmlspecialchars($maintenance)?></textarea><label>Confirm Owner password</label><input type="password" name="current_password" required autocomplete="current-password"><button type="submit">Apply server mode</button></form></section></main></body></html>
