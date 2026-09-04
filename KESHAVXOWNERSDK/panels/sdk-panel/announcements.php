<?php
include 'conn.php';
if (!isset($_SESSION['user_id'])) { header("Location: login.php"); exit(); }
include 'panel_helper.php';

$uid  = intval($_SESSION['user_id']);
$userStmt = $conn->prepare('SELECT * FROM users WHERE id = ? LIMIT 1');
$userStmt->bind_param('i', $uid);
$userStmt->execute();
$urow = $userStmt->get_result()->fetch_assoc();
$userStmt->close();
$role = $urow['role'] ?? 'user';
$can_manage = in_array($role, ['owner','admin']);

$P   = get_panel_settings($conn);
$msg = ''; $err = '';

// ── Mark as read (AJAX) ─────────────────────────────────────────────────
if (isset($_POST['mark_read']) && is_numeric($_POST['mark_read'])) {
    mark_announcement_read($conn, intval($_POST['mark_read']), $uid);
    echo json_encode(['ok'=>true]); exit();
}

// ── Mark all read (AJAX) ───────────────────────────────────────────────
if (isset($_POST['mark_all_read'])) {
    $res = mysqli_query($conn,"SELECT id FROM announcements WHERE is_active=1");
    while($r=mysqli_fetch_assoc($res)) mark_announcement_read($conn,$r['id'],$uid);
    echo json_encode(['ok'=>true]); exit();
}

// ── Create Announcement ────────────────────────────────────────────────
if ($can_manage && isset($_POST['create_ann'])) {
    $title = trim((string) ($_POST['ann_title'] ?? ''));
    $message = trim((string) ($_POST['ann_message'] ?? ''));
    $type = in_array($_POST['ann_type'] ?? '', ['info','warning','success','danger'], true) ? $_POST['ann_type'] : 'info';
    $expires = !empty($_POST['ann_expires']) ? (string) $_POST['ann_expires'] : null;

    if (strlen($title) < 2 || strlen($message) < 2) {
        $err = "Title and message are required.";
    } else {
        $createStmt = $conn->prepare(
            'INSERT INTO announcements (title, message, type, is_active, created_by, expires_at)
             VALUES (?, ?, ?, 1, ?, ?)'
        );
        $createStmt->bind_param('sssis', $title, $message, $type, $uid, $expires);
        $createStmt->execute();
        $createStmt->close();
        $msg = "✅ Announcement published! All users will see it on their next page load.";
    }
}

// ── Delete Announcement ───────────────────────────────────────────────
if ($can_manage && isset($_POST['delete_ann']) && is_numeric($_POST['delete_ann'])) {
    $aid = intval($_POST['delete_ann']);
    $deleteReads = $conn->prepare('DELETE FROM announcement_reads WHERE announcement_id = ?');
    $deleteReads->bind_param('i', $aid); $deleteReads->execute(); $deleteReads->close();
    $deleteAnn = $conn->prepare('DELETE FROM announcements WHERE id = ?');
    $deleteAnn->bind_param('i', $aid); $deleteAnn->execute(); $deleteAnn->close();
    header("Location: announcements.php?deleted=1"); exit();
}

// ── Toggle Active ─────────────────────────────────────────────────────
if ($can_manage && isset($_POST['toggle_ann']) && is_numeric($_POST['toggle_ann'])) {
    $aid = intval($_POST['toggle_ann']);
    $toggleStmt = $conn->prepare('UPDATE announcements SET is_active = 1 - is_active WHERE id = ?');
    $toggleStmt->bind_param('i', $aid); $toggleStmt->execute(); $toggleStmt->close();
    header("Location: announcements.php"); exit();
}

// Load all announcements for management view
$all_ann = mysqli_query($conn,"SELECT a.*, u.username as author FROM announcements a
    LEFT JOIN users u ON u.id=a.created_by ORDER BY a.created_at DESC");

// Load unread for current user
$unread_res = get_active_announcements($conn, $uid);
$unread_count = count_unread_announcements($conn, $uid);
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Announcements • <?= htmlspecialchars($P['panel_name']) ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;700&display=swap" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>
*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }
body {
    font-family:'Montserrat',sans-serif;
    background: linear-gradient(135deg, var(--p-bg1), var(--p-bg2), var(--p-bg3), var(--p-bg4));
    background-size:400% 400%;
    animation:bgAnim 18s ease infinite;
    min-height:100vh; color:#fff; overflow-x:hidden;
}
@keyframes bgAnim { 0%{background-position:0% 50%} 50%{background-position:100% 50%} 100%{background-position:0% 50%} }
.glass {
    background:rgba(15,23,42,0.75); backdrop-filter:blur(22px);
    border:1px solid rgba(255,255,255,0.13); border-radius:22px;
    box-shadow:0 12px 35px rgba(0,0,0,0.4);
}
header {
    position:fixed; top:0; left:0; right:0; height:60px;
    padding:0 20px; display:flex; align-items:center; justify-content:space-between;
    z-index:1000; background:rgba(10,15,30,0.88); border-bottom:1px solid rgba(255,255,255,0.08);
    backdrop-filter:blur(20px);
}
.menu-btn { font-size:1.5rem; cursor:pointer; color:var(--p-primary); background:none; border:none; transition:all .3s; }
.menu-btn:hover { transform:scale(1.1) rotate(90deg); }
.header-title { font-size:1rem; font-weight:700; color:var(--p-primary); letter-spacing:.08em; }
.user-badge {
    background:rgba(255,255,255,0.08); padding:8px 16px;
    border-radius:50px; display:flex; align-items:center; gap:8px;
    border:1px solid rgba(255,255,255,0.13); font-size:.85rem; font-weight:600;
}
.user-badge i { color:var(--p-primary); }
#sidebar {
    position:fixed; top:60px; left:-260px; bottom:0; width:240px;
    background:rgba(10,15,30,0.96); border-right:1px solid rgba(255,255,255,0.07);
    z-index:999; transition:left .3s; overflow-y:auto; padding:16px 12px;
}
#sidebar.active { left:0; }
#overlay { position:fixed; inset:0; background:rgba(0,0,0,0.4); z-index:998; display:none; }
#overlay.active { display:block; }
.sidebar-btn {
    width:100%; padding:12px 16px; margin-bottom:8px;
    background:rgba(255,255,255,0.05); border:1px solid rgba(255,255,255,0.08);
    border-radius:14px; color:#fff; text-align:left; cursor:pointer;
    font-family:'Montserrat',sans-serif; font-size:.88rem; font-weight:600;
    display:flex; align-items:center; gap:10px; transition:all .25s;
}
.sidebar-btn:hover, .sidebar-btn.active-page { background:rgba(201,168,76,0.12); border-color:var(--p-primary); color:var(--p-primary); }
.sidebar-btn i { width:18px; text-align:center; color:var(--p-primary); }
.main { padding:80px 20px 40px; max-width:860px; margin:0 auto; }
.section-header { display:flex; align-items:center; gap:12px; margin:28px 0 16px; padding-bottom:12px; border-bottom:1px solid rgba(255,255,255,0.09); }
.section-header i { color:var(--p-primary); font-size:1.1rem; }
.section-header h5 { margin:0; font-size:1rem; font-weight:700; }
.form-label { color:rgba(255,255,255,0.65); font-size:.82rem; font-weight:600; letter-spacing:.04em; margin-bottom:6px; }
.form-control, .form-select {
    background:rgba(255,255,255,0.06) !important; border:1px solid rgba(255,255,255,0.12) !important;
    color:#fff !important; border-radius:12px !important; padding:10px 14px;
    font-family:'Montserrat',sans-serif; font-size:.88rem;
}
.form-control:focus, .form-select:focus { border-color:var(--p-primary) !important; box-shadow:0 0 0 3px rgba(201,168,76,0.1) !important; outline:none; }
.form-control::placeholder { color:rgba(255,255,255,0.25); }
.form-select option { background:#1a1f30; }
.btn-publish {
    background:linear-gradient(135deg,#8B6914,var(--p-primary),#F0D080,var(--p-primary),#8B6914);
    background-size:200% auto; border:none; border-radius:12px;
    color:#1a1000; font-weight:800; font-size:.88rem; letter-spacing:.08em;
    padding:12px 30px; text-transform:uppercase; cursor:pointer;
    box-shadow:0 6px 20px rgba(201,168,76,0.2); transition:background-position .4s,transform .2s;
}
.btn-publish:hover { background-position:right center; transform:translateY(-1px); }
.btn-sm-action {
    padding:5px 12px; border-radius:8px; font-size:.75rem; font-weight:600;
    border:none; cursor:pointer; transition:all .2s;
}

.btn-delete:hover  { background:rgba(248,113,113,0.3); }
.btn-toggle  { background:rgba(52,211,153,0.12); color:#34d399; border:1px solid rgba(52,211,153,0.25); }
.btn-toggle:hover  { background:rgba(52,211,153,0.25); }

/* ANN CARD */
.ann-card {
    border-radius:16px; padding:18px 20px; margin-bottom:14px;
    border:1px solid rgba(255,255,255,0.1);
    background:rgba(15,23,42,0.65); backdrop-filter:blur(14px);
    animation:fadeIn .4s ease;
}
@keyframes fadeIn { from{opacity:0;transform:translateY(8px)} to{opacity:1;transform:none} }
.ann-card.type-info    { border-left:4px solid #4F8EF7; }
.ann-card.type-warning { border-left:4px solid #fbbf24; }
.ann-card.type-success { border-left:4px solid #34d399; }
.ann-card.type-danger  { border-left:4px solid #f87171; }
.ann-title { font-size:.95rem; font-weight:700; margin-bottom:6px; }
.ann-msg   { font-size:.85rem; color:rgba(255,255,255,0.7); line-height:1.5; }
.ann-meta  { font-size:.72rem; color:rgba(255,255,255,0.35); margin-top:10px; display:flex; gap:16px; flex-wrap:wrap; align-items:center; }
.type-badge { padding:2px 10px; border-radius:20px; font-size:.68rem; font-weight:700; letter-spacing:.06em; text-transform:uppercase; }
.type-info    .type-badge { background:rgba(79,142,247,.15); color:#4F8EF7; }
.type-warning .type-badge { background:rgba(251,191,36,.12); color:#fbbf24; }
.type-success .type-badge { background:rgba(52,211,153,.12); color:#34d399; }
.type-danger  .type-badge { background:rgba(248,113,113,.12); color:#f87171; }
.inactive-tag { opacity:.45; }
.alert-custom { border-radius:14px; padding:14px 20px; font-size:.88rem; font-weight:600; margin-bottom:20px; }
.alert-success-custom { background:rgba(52,211,153,.1); border:1px solid rgba(52,211,153,.3); color:#34d399; }
.alert-danger-custom  { background:rgba(248,113,113,.1); border:1px solid rgba(248,113,113,.3); color:#f87171; }

/* NOTIFICATION POPUP */
.notif-popup {
    position:fixed; bottom:24px; right:24px; z-index:9999;
    max-width:360px; width:90%;
}
.notif-item {
    border-radius:16px; padding:16px 18px; margin-bottom:10px;
    display:flex; gap:12px; align-items:flex-start;
    animation:slideUp .4s cubic-bezier(.34,1.3,.64,1);
    box-shadow:0 8px 28px rgba(0,0,0,0.5);
    backdrop-filter:blur(16px);
}
@keyframes slideUp { from{opacity:0;transform:translateY(24px)} to{opacity:1;transform:none} }
.notif-item.type-info    { background:rgba(12,20,50,.92); border:1px solid rgba(79,142,247,.4); }
.notif-item.type-warning { background:rgba(40,28,5,.92); border:1px solid rgba(251,191,36,.4); }
.notif-item.type-success { background:rgba(5,30,20,.92); border:1px solid rgba(52,211,153,.4); }
.notif-item.type-danger  { background:rgba(40,5,5,.92); border:1px solid rgba(248,113,113,.4); }
.notif-icon { font-size:1.2rem; flex-shrink:0; margin-top:2px; }
.type-info    .notif-icon { color:#4F8EF7; }
.type-warning .notif-icon { color:#fbbf24; }
.type-success .notif-icon { color:#34d399; }
.type-danger  .notif-icon { color:#f87171; }
.notif-title { font-size:.88rem; font-weight:700; margin-bottom:3px; }
.notif-msg   { font-size:.78rem; color:rgba(255,255,255,.65); line-height:1.4; }
.notif-close { margin-left:auto; background:none; border:none; color:rgba(255,255,255,.4); cursor:pointer; font-size:1rem; line-height:1; }
.notif-close:hover { color:#fff; }
.notif-read-btn { font-size:.72rem; color:rgba(255,255,255,.4); background:none; border:none; cursor:pointer; padding:0; text-decoration:underline; }
.notif-read-btn:hover { color:#fff; }

/* ── Lightweight Background Orbs (replaces particles.js) ── */
.bg-orbs{position:fixed;inset:0;z-index:-1;pointer-events:none;overflow:hidden;}
.bg-orb{position:absolute;border-radius:50%;filter:blur(90px);will-change:transform;animation:obOrb var(--d,18s) ease-in-out infinite;animation-delay:var(--dl,0s);}
.ob1{width:500px;height:500px;top:-180px;left:-100px;background:radial-gradient(circle,rgba(201,168,76,0.1) 0%,transparent 70%);--d:18s;--dl:0s;}
.ob2{width:400px;height:400px;bottom:-120px;right:-100px;background:radial-gradient(circle,rgba(79,142,247,0.08) 0%,transparent 70%);--d:22s;--dl:5s;}
.ob3{width:300px;height:300px;top:50%;left:50%;background:radial-gradient(circle,rgba(124,58,237,0.06) 0%,transparent 70%);--d:28s;--dl:10s;}
 obOrb{0%,100%{transform:translate(0,0) scale(1)}50%{transform:translate(25px,-35px) scale(1.06);}}

/* ══════════════════════════════════════
   ✦ ONEBOX PANEL — UPGRADED BUTTONS v2
   ══════════════════════════════════════ */

/* ─ PRIMARY / ACTION BUTTON ─ */
.btn-ios,
.btn-save,
.ob-btn {
    position:relative; overflow:hidden;
    display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:13px 28px; border:none; border-radius:14px;
    font-family:'Montserrat',sans-serif; font-size:.9rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer;
    background: linear-gradient(135deg, var(--p-primary,#c9a84c) 0%, #e8c96a 50%, var(--p-primary,#c9a84c) 100%);
    background-size:200% 200%;
    box-shadow:0 4px 20px rgba(201,168,76,0.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s ease;
    text-decoration:none; width:auto;
}
.btn-ios::before, .btn-save::before {
    content:''; position:absolute; inset:0;
    background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);
    transform:translateX(-100%); transition:transform .55s ease;
}
.btn-ios:hover::before, .btn-save:hover::before { transform:translateX(100%); }
.btn-ios:hover, .btn-save:hover {
    transform:translateY(-3px) scale(1.02);
    box-shadow:0 12px 32px rgba(201,168,76,.55); color:#fff; text-decoration:none;
}
.btn-ios:active, .btn-save:active { transform:translateY(0) scale(.98); }

/* ─ BLUE PRIMARY (Generate License etc.) ─ */
.generate-btn .btn-ios,
.ob-btn-blue {
    background:linear-gradient(135deg,#2563eb 0%,#38bdf8 60%,#6366f1 100%) !important;
    box-shadow:0 4px 20px rgba(37,99,235,.4) !important;
}
.generate-btn .btn-ios:hover,
.ob-btn-blue:hover { box-shadow:0 12px 32px rgba(37,99,235,.55) !important; }

/* ─ GLASS ACTION BUTTONS ─ */
.glass-btn, .btn-glass {
    position:relative; padding:7px 15px; border-radius:50px;
    font-size:.78rem; font-weight:700; letter-spacing:.03em;
    border:1px solid rgba(255,255,255,.18);
    display:inline-flex; align-items:center; gap:6px;
    transition:transform .22s cubic-bezier(.34,1.5,.64,1),box-shadow .22s ease,background .22s ease;
    cursor:pointer; backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px);
    box-shadow:0 2px 10px rgba(0,0,0,.22); background:rgba(255,255,255,.05);
    text-decoration:none; font-family:'Montserrat',sans-serif;
}
.glass-btn:hover, .btn-glass:hover { transform:translateY(-2px) scale(1.06); color:#fff; text-decoration:none; }
.glass-btn:active, .btn-glass:active { transform:scale(.97); }
.glass-btn:disabled, .btn-glass:disabled { opacity:.38; cursor:not-allowed; pointer-events:none; }

.btn-copy-glass { color:#a5b4fc; border-color:rgba(139,92,246,.35); background:rgba(99,102,241,.1); }
.btn-copy-glass:hover { background:rgba(99,102,241,.28); border-color:rgba(139,92,246,.6); box-shadow:0 0 18px rgba(99,102,241,.45); color:#e0e7ff; }

.btn-usage-glass { color:#7dd3fc; border-color:rgba(6,182,212,.35); background:rgba(6,182,212,.1); }
.btn-usage-glass:hover { background:rgba(6,182,212,.28); border-color:rgba(6,182,212,.6); box-shadow:0 0 18px rgba(6,182,212,.45); color:#e0f2fe; }

.btn-edit-glass, .btn-edit {
    color:#fde68a; border-color:rgba(234,179,8,.35); background:rgba(234,179,8,.1);
}
.btn-edit-glass:hover, .btn-edit:hover:not(:disabled) {
    background:rgba(234,179,8,.28); border-color:rgba(234,179,8,.6);
    box-shadow:0 0 18px rgba(234,179,8,.45); color:#fef3c7;
    transform:translateY(-2px) scale(1.06);
}

.btn-delete-glass, .btn-delete {
    color:#fca5a5; border-color:rgba(239,68,68,.35); background:rgba(239,68,68,.1);
}
.btn-delete-glass:hover, .btn-delete:hover:not(:disabled) {
    background:rgba(239,68,68,.28); border-color:rgba(239,68,68,.6);
    box-shadow:0 0 18px rgba(239,68,68,.45); color:#fee2e2;
    transform:translateY(-2px) scale(1.06);
}

/* ─ BACK / SECONDARY ─ */
.btn-back, .ob-btn-secondary {
    position:relative; display:inline-flex; align-items:center; gap:9px;
    padding:11px 26px; background:rgba(255,255,255,.07);
    border:1px solid rgba(255,255,255,.16); border-radius:50px;
    color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600;
    font-family:'Montserrat',sans-serif; text-decoration:none; cursor:pointer;
    transition:all .25s cubic-bezier(.34,1.5,.64,1);
    backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px);
}
.btn-back:hover, .ob-btn-secondary:hover {
    background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3);
    transform:translateY(-3px); box-shadow:0 10px 24px rgba(0,0,0,.35);
    color:#fff; text-decoration:none;
}

/* ─ MODAL UPGRADE ─ */
.modal-content {
    background:rgba(10,16,34,.97) !important;
    border:1px solid rgba(201,168,76,.2) !important;
    border-radius:20px !important; backdrop-filter:blur(24px) !important;
}
.modal-header { border-bottom:1px solid rgba(255,255,255,.08) !important; }
.modal-footer { border-top:1px solid rgba(255,255,255,.08) !important; gap:10px; }
.modal-title { font-weight:700; color:var(--p-primary,#c9a84c); }

/* ─ FORM INPUT UPGRADE ─ */
.form-control,.form-select {
    background:rgba(0,0,0,.28) !important; border:1px solid rgba(255,255,255,.1) !important;
    color:#fff !important; border-radius:12px !important; padding:11px 14px !important;
    font-family:'Montserrat',sans-serif;
    transition:border-color .22s ease,box-shadow .22s ease;
}
.form-control:focus,.form-select:focus {
    background:rgba(0,0,0,.45) !important;
    border-color:var(--p-primary,#c9a84c) !important;
    box-shadow:0 0 0 3px rgba(201,168,76,.2),0 0 16px rgba(201,168,76,.15) !important;
    color:#fff !important; outline:none !important;
}
.form-control::placeholder { color:rgba(255,255,255,.35) !important; }
.form-select option { background:#1e2a45; color:#fff; }

</style>
</head>
<body>
<div class="bg-orbs"><div class="bg-orb ob1"></div><div class="bg-orb ob2"></div><div class="bg-orb ob3"></div></div>

<header>
    <button class="menu-btn" onclick="toggleSidebar()"><i class="fas fa-bars"></i></button>
    <div class="header-title"><i class="fas fa-bullhorn me-2"></i>Announcements</div>
    <div class="user-badge"><i class="fas fa-user-circle"></i><span><?= htmlspecialchars($urow['username']) ?></span></div>
</header>
<div id="overlay" onclick="toggleSidebar()"></div>

<aside id="sidebar">
    <div style="padding:12px 8px 16px; text-align:center; border-bottom:1px solid rgba(255,255,255,0.08); margin-bottom:12px;">
        <img src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>" style="width:60px;height:60px;border-radius:50%;object-fit:cover;border:2px solid var(--p-primary);"
             onerror="this.onerror=null;this.style.opacity='0.3';this.title='logo.png'">
        <div style="margin-top:8px;font-size:.8rem;font-weight:700;color:var(--p-primary);"><?= htmlspecialchars($P['panel_name']) ?></div>
    </div>
    <button class="sidebar-btn" onclick="location.href='dashboard.php'"><i class="fas fa-tachometer-alt"></i> Dashboard</button>
    <button class="sidebar-btn" onclick="location.href='generate_ui.php'"><i class="fas fa-key"></i> Generate License</button>
    <button class="sidebar-btn" onclick="location.href='license_list.php'"><i class="fas fa-code-branch"></i> List Licenses</button>
    <button class="sidebar-btn" onclick="location.href='manage_users.php'"><i class="fas fa-circle-user"></i> Manage Users</button>
    <button class="sidebar-btn" onclick="location.href='manage_referrals.php'"><i class="fas fa-file-pen"></i> Manage Referrals</button>
    <button class="sidebar-btn" onclick="location.href='online_server.php'"><i class="fas fa-circle-check"></i> Sdk Online Server</button>
    <?php if ($can_manage): ?>
    <button class="sidebar-btn" onclick="location.href='panel_customizer.php'"><i class="fas fa-paint-brush"></i> Customizer</button>
    <?php endif; ?>
    <button class="sidebar-btn active-page" onclick="location.href='announcements.php'"><i class="fas fa-bullhorn"></i> Announcements</button>
    <button class="sidebar-btn" onclick="location.href='settings.php'"><i class="fas fa-cog"></i> Settings</button>
    <button class="sidebar-btn" onclick="location.href='logout.php'"><i class="fas fa-sign-out-alt"></i> Logout</button>
</aside>

<div class="main">

    <?php if ($msg): ?><div class="alert-custom alert-success-custom"><i class="fas fa-check-circle me-2"></i><?= $msg ?></div><?php endif; ?>
    <?php if ($err): ?><div class="alert-custom alert-danger-custom"><i class="fas fa-exclamation-circle me-2"></i><?= $err ?></div><?php endif; ?>
    <?php if (isset($_GET['deleted'])): ?><div class="alert-custom alert-success-custom"><i class="fas fa-trash me-2"></i>Announcement deleted.</div><?php endif; ?>

    <!-- ── CREATE (owner/admin only) ── -->
    <?php if ($can_manage): ?>
    <div class="glass p-4 mb-4">
        <div class="section-header">
            <i class="fas fa-plus-circle"></i>
            <h5>New Announcement</h5>
            <span style="margin-left:auto;font-size:.7rem;background:rgba(201,168,76,.12);color:var(--p-primary);border:1px solid rgba(201,168,76,.3);padding:3px 10px;border-radius:20px;font-weight:700;">
                <?= strtoupper($role) ?>
            </span>
        </div>
        <form method="POST">
            <div class="row g-3">
                <div class="col-md-8">
                    <label class="form-label">Title</label>
                    <input type="text" name="ann_title" class="form-control" placeholder="Announcement heading..." required>
                </div>
                <div class="col-md-4">
                    <label class="form-label">Type</label>
                    <select name="ann_type" class="form-select">
                        <option value="info">ℹ️ Info</option>
                        <option value="success">✅ Success</option>
                        <option value="warning">⚠️ Warning</option>
                        <option value="danger">🚨 Urgent</option>
                    </select>
                </div>
                <div class="col-12">
                    <label class="form-label">Message</label>
                    <textarea name="ann_message" class="form-control" rows="3" placeholder="Write your announcement here..." required></textarea>
                </div>
                <div class="col-md-6">
                    <label class="form-label">Expires At (optional)</label>
                    <input type="datetime-local" name="ann_expires" class="form-control">
                </div>
                <div class="col-md-6 d-flex align-items-end">
                    <button type="submit" name="create_ann" class="btn-publish w-100">
                        <i class="fas fa-paper-plane me-2"></i>Publish to All Users
                    </button>
                </div>
            </div>
        </form>
    </div>

    <!-- ── ALL ANNOUNCEMENTS (manage) ── -->
    <div class="section-header">
        <i class="fas fa-list"></i>
        <h5>All Announcements</h5>
    </div>

    <?php
    $count = 0;
    if (mysqli_num_rows($all_ann) === 0): ?>
        <div class="ann-card type-info"><div class="ann-msg">No announcements yet. Create one above!</div></div>
    <?php else:
        while ($a = mysqli_fetch_assoc($all_ann)):
            $count++;
            $inactiveClass = $a['is_active'] ? '' : 'inactive-tag';
    ?>
    <div class="ann-card type-<?= $a['type'] ?> <?= $inactiveClass ?>">
        <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">
            <div style="flex:1;">
                <div class="ann-title" style="display:flex;align-items:center;gap:10px;">
                    <span class="type-badge"><?= strtoupper($a['type']) ?></span>
                    <?= htmlspecialchars($a['title']) ?>
                    <?php if (!$a['is_active']): ?>
                        <span style="font-size:.68rem;color:rgba(255,255,255,.3);font-weight:600;">[INACTIVE]</span>
                    <?php endif; ?>
                </div>
                <div class="ann-msg"><?= nl2br(htmlspecialchars($a['message'])) ?></div>
                <div class="ann-meta">
                    <span><i class="fas fa-user me-1"></i><?= htmlspecialchars($a['author'] ?? 'system') ?></span>
                    <span><i class="fas fa-clock me-1"></i><?= date('d M Y H:i', strtotime($a['created_at'])) ?></span>
                    <?php if ($a['expires_at']): ?><span><i class="fas fa-hourglass me-1"></i>Expires: <?= date('d M Y H:i', strtotime($a['expires_at'])) ?></span><?php endif; ?>
                </div>
            </div>
            <div style="display:flex;gap:8px;flex-shrink:0;flex-direction:column;">
                <form method="post"><button name="toggle_ann" value="<?= (int)$a['id'] ?>" class="btn-sm-action btn-toggle" type="submit">
                    <i class="fas <?= $a['is_active'] ? 'fa-eye-slash' : 'fa-eye' ?> me-1"></i><?= $a['is_active'] ? 'Disable' : 'Enable' ?>
                </button></form>
                <form method="post" onsubmit="return confirm('Delete this announcement?')"><button name="delete_ann" value="<?= (int)$a['id'] ?>" type="submit" class="btn-sm-action btn-delete">
                    <i class="fas fa-trash me-1"></i>Delete
                </button></form>
            </div>
        </div>
    </div>
    <?php endwhile; endif; ?>

    <?php else: ?>
    <!-- ── READ VIEW (regular users) ── -->
    <div class="section-header">
        <i class="fas fa-bell"></i>
        <h5>Announcements</h5>
        <?php if ($unread_count > 0): ?>
        <span style="background:rgba(248,113,113,.2);color:#f87171;border:1px solid rgba(248,113,113,.3);padding:3px 10px;border-radius:20px;font-size:.72rem;font-weight:700;">
            <?= $unread_count ?> Unread
        </span>
        <?php endif; ?>
        <a href="#" id="markAllBtn"
           style="margin-left:auto;font-size:.75rem;color:rgba(255,255,255,.4);text-decoration:none;">
            Mark all read
        </a>
    </div>

    <?php
    // Show all active announcements to user (read + unread, for reference)
    $all_visible = mysqli_query($conn,"SELECT * FROM announcements WHERE is_active=1 AND (expires_at IS NULL OR expires_at>NOW()) ORDER BY created_at DESC");
    if (mysqli_num_rows($all_visible) === 0): ?>
        <div class="ann-card type-info"><div class="ann-msg">No announcements at the moment. Check back later!</div></div>
    <?php else:
        while ($a = mysqli_fetch_assoc($all_visible)):
            // check if read
            $read_check = mysqli_fetch_assoc(mysqli_query($conn,"SELECT id FROM announcement_reads WHERE announcement_id={$a['id']} AND user_id=$uid"));
            $is_read = !empty($read_check);
    ?>
    <div class="ann-card type-<?= $a['type'] ?>" style="<?= $is_read ? 'opacity:.55' : '' ?>" id="ann-<?=$a['id']?>">
        <div style="display:flex;align-items:flex-start;gap:12px;">
            <div style="flex:1;">
                <div class="ann-title" style="display:flex;align-items:center;gap:10px;">
                    <span class="type-badge"><?= strtoupper($a['type']) ?></span>
                    <?= htmlspecialchars($a['title']) ?>
                    <?php if (!$is_read): ?><span style="width:8px;height:8px;border-radius:50%;background:#f87171;display:inline-block;"></span><?php endif; ?>
                </div>
                <div class="ann-msg"><?= nl2br(htmlspecialchars($a['message'])) ?></div>
                <div class="ann-meta">
                    <span><i class="fas fa-clock me-1"></i><?= date('d M Y H:i', strtotime($a['created_at'])) ?></span>
                    <?php if (!$is_read): ?>
                    <button class="notif-read-btn" onclick="markRead(<?=$a['id']?>)"><i class="fas fa-check me-1"></i>Mark as read</button>
                    <?php else: ?>
                    <span style="color:rgba(52,211,153,.5);"><i class="fas fa-check-double me-1"></i>Read</span>
                    <?php endif; ?>
                </div>
            </div>
        </div>
    </div>
    <?php endwhile; endif; ?>
    <?php endif; ?>

</div><!-- /main -->

<!-- NOTIFICATION POPUP for unread announcements -->
<div class="notif-popup" id="notifPopup"></div>

<script>
function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('active');
    document.getElementById('overlay').classList.toggle('active');
}

// Show popup notifications for unread announcements
const unreadAnns = <?php
    $arr = [];
    $res2 = get_active_announcements($conn, $uid);
    while($r=mysqli_fetch_assoc($res2)) {
        $arr[] = ['id'=>(int)$r['id'],'title'=>htmlspecialchars($r['title'],ENT_QUOTES),'message'=>htmlspecialchars(substr($r['message'],0,120),ENT_QUOTES),'type'=>$r['type']];
    }
    echo json_encode($arr);
?>;

const typeIcons = { info:'fa-circle-info', warning:'fa-triangle-exclamation', success:'fa-circle-check', danger:'fa-circle-exclamation' };

function showPopup(ann, delay=0) {
    setTimeout(() => {
        const popup = document.getElementById('notifPopup');
        const el = document.createElement('div');
        el.className = `notif-item type-${ann.type}`;
        el.id = `notif-${ann.id}`;
        el.innerHTML = `
            <div class="notif-icon"><i class="fas ${typeIcons[ann.type] || 'fa-bell'}"></i></div>
            <div style="flex:1">
                <div class="notif-title">${ann.title}</div>
                <div class="notif-msg">${ann.message}${ann.message.length>=120?'…':''}</div>
                <div style="margin-top:6px;">
                    <button class="notif-read-btn" onclick="markReadPopup(${ann.id})"><i class="fas fa-check me-1"></i>Mark read</button>
                </div>
            </div>
            <button class="notif-close" onclick="dismissPopup(${ann.id})"><i class="fas fa-times"></i></button>
        `;
        popup.appendChild(el);
    }, delay);
}

// Show up to 3 popups
unreadAnns.slice(0,3).forEach((a,i) => showPopup(a, i*600));

function dismissPopup(id) {
    const el = document.getElementById(`notif-${id}`);
    if (el) { el.style.animation='fadeOut .3s forwards'; setTimeout(()=>el.remove(),300); }
}

function markReadPopup(id) {
    announcementPost('mark_read', id);
    dismissPopup(id);
    const card = document.getElementById(`ann-${id}`);
    if (card) card.style.opacity='.5';
}

function markRead(id) {
    announcementPost('mark_read', id).then(()=> location.reload());
}

function announcementPost(action, value) {
    const body = new URLSearchParams({_csrf: <?= json_encode(panel_csrf_token()) ?>});
    body.set(action, String(value));
    return fetch('announcements.php', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body});
}

// Mark all read
const markAllBtn = document.getElementById('markAllBtn');
if (markAllBtn) {
    markAllBtn.addEventListener('click', async(e) => {
        e.preventDefault();
        await announcementPost('mark_all_read', 1);
        location.reload();
    });
}
</script>
<script>
/* ✦ ONEBOX Ripple v2 */
(function(){
  document.addEventListener('click',function(e){
    var btn=e.target.closest('.btn-ios,.btn-save,.ob-btn,.glass-btn,.btn-glass,.btn-back');
    if(!btn)return;
    var r=document.createElement('span');
    var d=Math.max(btn.offsetWidth,btn.offsetHeight);
    var rect=btn.getBoundingClientRect();
    r.style.cssText='position:absolute;border-radius:50%;background:rgba(255,255,255,.25);pointer-events:none;transform:scale(0);animation:obRpl .55s linear;width:'+d+'px;height:'+d+'px;left:'+(e.clientX-rect.left-d/2)+'px;top:'+(e.clientY-rect.top-d/2)+'px;';
    btn.style.position='relative';btn.style.overflow='hidden';
    btn.appendChild(r);
    setTimeout(function(){r.remove();},600);
  });
})();
</script>
<style>@keyframes obRpl{to{transform:scale(4);opacity:0;}}</style>
</body>
</html>
