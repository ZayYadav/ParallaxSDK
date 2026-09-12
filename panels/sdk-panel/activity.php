<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';
if(!sdk_feature_installed($conn)){http_response_code(503);exit('FEATURE_MIGRATION_REQUIRED');}
$actor=sdk_feature_require_roles($conn,['owner','admin']);$P=get_panel_settings($conn);
$q=trim((string)($_GET['q']??''));$action=trim((string)($_GET['action']??''));$ip=trim((string)($_GET['ip']??''));$from=trim((string)($_GET['from']??''));$to=trim((string)($_GET['to']??''));$page=max(1,(int)($_GET['page']??1));$limit=50;$offset=($page-1)*$limit;
$where=['1=1'];
if($q!==''){$e=$conn->real_escape_string($q);$where[]="(au.username LIKE '%$e%' OR tu.username LIKE '%$e%' OR CAST(l.actor_user_id AS CHAR)='$e' OR CAST(l.target_user_id AS CHAR)='$e')";}
if($action!==''&&preg_match('/^[A-Za-z0-9_.:-]{1,80}$/D',$action)){$e=$conn->real_escape_string($action);$where[]="l.action='$e'";}
if($ip!==''&&filter_var($ip,FILTER_VALIDATE_IP)){$e=$conn->real_escape_string($ip);$where[]="l.ip_address='$e'";}

$displayTz=new DateTimeZone(getenv('DISPLAY_TIMEZONE')?:'Asia/Kolkata');$utcTz=new DateTimeZone('UTC');
if(preg_match('/^\d{4}-\d{2}-\d{2}$/D',$from)){
    $d=DateTimeImmutable::createFromFormat('!Y-m-d',$from,$displayTz);
    if($d&&$d->format('Y-m-d')===$from){$fromUtc=$d->setTimezone($utcTz)->format('Y-m-d H:i:s');$where[]="l.created_at>='".$conn->real_escape_string($fromUtc)."'";}
}
if(preg_match('/^\d{4}-\d{2}-\d{2}$/D',$to)){
    $d=DateTimeImmutable::createFromFormat('!Y-m-d',$to,$displayTz);
    if($d&&$d->format('Y-m-d')===$to){$toUtc=$d->setTime(23,59,59)->setTimezone($utcTz)->format('Y-m-d H:i:s');$where[]="l.created_at<='".$conn->real_escape_string($toUtc)."'";}
}
$sql=' FROM panel_activity_logs l LEFT JOIN users au ON au.id=l.actor_user_id LEFT JOIN users tu ON tu.id=l.target_user_id WHERE '.implode(' AND ',$where);
$count=$conn->query('SELECT COUNT(*) c'.$sql);$total=(int)($count->fetch_assoc()['c']??0);$pages=max(1,(int)ceil($total/$limit));
$logs=$conn->query('SELECT l.*,au.username actor_name,tu.username target_name'.$sql.' ORDER BY l.id DESC LIMIT '.$limit.' OFFSET '.$offset);
$actions=$conn->query('SELECT action,COUNT(*) c FROM panel_activity_logs GROUP BY action ORDER BY c DESC,action ASC LIMIT 100');
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Security Activity</title><style>
<?=panel_css_vars($P)?>*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:1420px;margin:auto;padding:30px 18px 90px}.head{display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap}.card{margin-top:16px;padding:20px;border-radius:20px;background:#0b1222;border:1px solid rgba(255,255,255,.1)}form.filters{display:grid;grid-template-columns:2fr 1fr 1fr 1fr 1fr auto;gap:8px}input,select,button{padding:11px;border-radius:11px;border:1px solid rgba(255,255,255,.12);background:#050a13;color:#fff;font:inherit}button{background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f;font-weight:900}.scroll{overflow:auto}table{width:100%;border-collapse:collapse;font-size:.84rem}th,td{padding:11px 9px;border-bottom:1px solid rgba(255,255,255,.07);text-align:left;vertical-align:top}.muted{color:#94a3b8}.meta{max-width:440px;white-space:pre-wrap;word-break:break-word;color:#a7b0c2}.pages{display:flex;gap:8px;margin-top:14px}.pages a{padding:8px 11px;border-radius:9px;background:#172033;color:#dbeafe;text-decoration:none}@media(max-width:900px){form.filters{grid-template-columns:1fr 1fr}}@media(max-width:560px){form.filters{grid-template-columns:1fr}}</style></head><body><main class="wrap"><div class="head"><div><h1>Security & management activity</h1><div class="muted"><?=$total?> retained events · timestamps and date filters use India time</div></div><div><a href="dashboard.php" style="color:#bfdbfe">Dashboard</a> · <a href="owner_console.php" style="color:#bfdbfe">Owner Console</a></div></div>
<section class="card"><form class="filters" method="get"><input name="q" value="<?=htmlspecialchars($q)?>" placeholder="Username / user ID"><select name="action"><option value="">All actions</option><?php while($a=$actions->fetch_assoc()):?><option value="<?=htmlspecialchars($a['action'])?>" <?=$action===$a['action']?'selected':''?>><?=htmlspecialchars($a['action'])?> (<?=(int)$a['c']?>)</option><?php endwhile;?></select><input name="ip" value="<?=htmlspecialchars($ip)?>" placeholder="IP"><input type="date" name="from" value="<?=htmlspecialchars($from)?>"><input type="date" name="to" value="<?=htmlspecialchars($to)?>"><button>Filter</button></form></section>
<section class="card scroll"><table><thead><tr><th>Date</th><th>Actor</th><th>Target</th><th>Action</th><th>Result / IP</th><th>Metadata</th></tr></thead><tbody><?php while($l=$logs->fetch_assoc()):?><tr><td><?=htmlspecialchars((string)$l['created_at'])?></td><td><?=htmlspecialchars((string)($l['actor_name']??'system'))?><br><span class="muted">#<?=htmlspecialchars((string)($l['actor_user_id']??'—'))?></span></td><td><?=htmlspecialchars((string)($l['target_name']??'—'))?><br><span class="muted">#<?=htmlspecialchars((string)($l['target_user_id']??'—'))?></span></td><td><b><?=htmlspecialchars($l['action'])?></b></td><td><?=htmlspecialchars($l['result'])?><br><span class="muted"><?=htmlspecialchars($l['ip_address'])?></span></td><td class="meta"><?=htmlspecialchars((string)($l['metadata']??''))?></td></tr><?php endwhile;?></tbody></table></section>
<?php if($pages>1):?><nav class="pages"><?php if($page>1):?><a href="?<?=http_build_query(array_merge($_GET,['page'=>$page-1]))?>">← Prev</a><?php endif;?><span class="muted" style="padding:8px">Page <?=$page?> / <?=$pages?></span><?php if($page<$pages):?><a href="?<?=http_build_query(array_merge($_GET,['page'=>$page+1]))?>">Next →</a><?php endif;?></nav><?php endif;?></main></body></html>