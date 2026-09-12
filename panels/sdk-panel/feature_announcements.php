<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!sdk_feature_installed($conn)) { http_response_code(503); exit('FEATURE_MIGRATION_REQUIRED'); }
$actor = sdk_feature_require_roles($conn, ['reseller','user']);
$P = get_panel_settings($conn);
$uid = (int)$actor['id'];
$msg = '';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $id = (int)($_POST['announcement_id'] ?? 0);
    if ($id > 0) {
        $st = $conn->prepare('SELECT id FROM announcements WHERE id=? AND is_active=1 AND (expires_at IS NULL OR expires_at>UTC_TIMESTAMP()) LIMIT 1');
        $st->bind_param('i', $id);
        $st->execute();
        $valid = (bool)$st->get_result()->fetch_assoc();
        $st->close();
        if ($valid) {
            mark_announcement_read($conn, $id, $uid);
            $msg = 'Announcement marked as read.';
        }
    }
}

$st = $conn->prepare(
    'SELECT a.id,a.title,a.message,a.type,a.created_at,a.expires_at,ar.read_at '
    . 'FROM announcements a LEFT JOIN announcement_reads ar ON ar.announcement_id=a.id AND ar.user_id=? '
    . 'WHERE a.is_active=1 AND (a.expires_at IS NULL OR a.expires_at>UTC_TIMESTAMP()) '
    . 'ORDER BY a.created_at DESC LIMIT 100'
);
$st->bind_param('i', $uid);
$st->execute();
$rows = $st->get_result();
?>
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Announcements</title><style>
<?=panel_css_vars($P)?>
*{box-sizing:border-box}body{margin:0;background:#050810;color:#f8fafc;font-family:Inter,system-ui}.wrap{max-width:900px;margin:auto;padding:28px 16px 90px}.top{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}.btn,button{display:inline-flex;padding:9px 12px;border-radius:10px;border:1px solid rgba(255,255,255,.1);background:#172033;color:#e2e8f0;text-decoration:none;font-weight:800;cursor:pointer}.card{margin-top:14px;padding:18px;border-radius:18px;background:#0b1222;border:1px solid rgba(255,255,255,.1)}.unread{border-color:rgba(201,168,76,.42)}.muted{color:#94a3b8;line-height:1.55}.msg{padding:11px;border-radius:11px;background:#064e3b;margin:12px 0}.meta{display:flex;gap:10px;flex-wrap:wrap;font-size:.78rem;color:#94a3b8}.body{white-space:pre-wrap;line-height:1.65}.pill{padding:4px 8px;border-radius:999px;background:rgba(255,255,255,.07)}
</style></head><body><main class="wrap"><div class="top"><div><h1>Announcements</h1><p class="muted">Only currently active announcements are shown. Management history and inactive announcements stay private to Owner/Admin.</p></div><a class="btn" href="feature_dashboard.php">Dashboard</a></div><?php if($msg):?><div class="msg"><?=htmlspecialchars($msg)?></div><?php endif;?>
<?php while($r=$rows->fetch_assoc()):$read=$r['read_at']!==null;?><section class="card <?=$read?'':'unread'?>"><div class="meta"><span class="pill"><?=htmlspecialchars(strtoupper((string)$r['type']))?></span><span><?=htmlspecialchars(sdk_feature_display_time((string)$r['created_at']))?></span><?php if($r['expires_at']!==null):?><span>Expires <?=htmlspecialchars(sdk_feature_display_time((string)$r['expires_at']))?></span><?php endif;?></div><h2><?=htmlspecialchars((string)$r['title'])?></h2><div class="body"><?=htmlspecialchars((string)$r['message'])?></div><?php if(!$read):?><form method="post" style="margin-top:14px"><input type="hidden" name="announcement_id" value="<?= (int)$r['id']?>"><button type="submit">Mark read</button></form><?php else:?><p class="muted">Read</p><?php endif;?></section><?php endwhile;$st->close();?></main></body></html>