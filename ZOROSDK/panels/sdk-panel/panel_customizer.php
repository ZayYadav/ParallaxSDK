<?php
include 'conn.php';
if (!isset($_SESSION['user_id'])) { header("Location: login.php"); exit(); }
include 'panel_helper.php';

$uid = intval($_SESSION['user_id']);
$urow = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM users WHERE id=$uid"));
$role = $urow['role'] ?? 'user';

if (!in_array($role, ['owner','admin'])) {
    header("Location: dashboard.php"); exit();
}

$P = get_panel_settings($conn);
$msg = ''; $err = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['save_settings'])) {
    $fields = [
        'panel_name','panel_tagline','login_title','login_subtitle',
        'login_badge_text','dashboard_title','watermark_text',
        'active_theme','theme_primary','theme_accent',
        'theme_bg1','theme_bg2','theme_bg3','theme_bg4',
        'sidebar_logo_url','footer_text'
    ];
    foreach ($fields as $f) {
        if (isset($_POST[$f])) save_panel_setting($conn, $f, trim($_POST[$f]), $uid);
    }
    $P = get_panel_settings($conn);
    $msg = "Settings saved! Changes are live across all pages.";
}

$themes = [
    'dark_blue'  => ['name'=>'Dark Blue',    'bg1'=>'#050810','bg2'=>'#0f172a','bg3'=>'#1e3a8a','bg4'=>'#312e81','primary'=>'#C9A84C','accent'=>'#4F8EF7'],
    'dark_green' => ['name'=>'Emerald',       'bg1'=>'#020c08','bg2'=>'#0a1f0f','bg3'=>'#064e3b','bg4'=>'#065f46','primary'=>'#34d399','accent'=>'#6ee7b7'],
    'dark_red'   => ['name'=>'Crimson',       'bg1'=>'#0f0508','bg2'=>'#1f0a0a','bg3'=>'#7f1d1d','bg4'=>'#991b1b','primary'=>'#f87171','accent'=>'#fb7185'],
    'dark_purple'=> ['name'=>'Royal Purple',  'bg1'=>'#07030f','bg2'=>'#130826','bg3'=>'#3b0764','bg4'=>'#4c0178','primary'=>'#a78bfa','accent'=>'#818cf8'],
    'midnight'   => ['name'=>'Midnight',      'bg1'=>'#000000','bg2'=>'#0a0a0a','bg3'=>'#111111','bg4'=>'#1a1a1a','primary'=>'#e5e7eb','accent'=>'#9ca3af'],
    'ocean'      => ['name'=>'Ocean Teal',    'bg1'=>'#030d12','bg2'=>'#0c1e28','bg3'=>'#0e3a50','bg4'=>'#0f5068','primary'=>'#22d3ee','accent'=>'#38bdf8'],
];
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Panel Customizer • <?= htmlspecialchars($P['panel_name']) ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>

*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

body {
    font-family:'Montserrat',sans-serif;
    background: linear-gradient(135deg, var(--p-bg1) 0%, var(--p-bg2) 35%, var(--p-bg3) 70%, var(--p-bg4) 100%);
    background-size:400% 400%;
    animation:bgShift 20s ease infinite;
    min-height:100vh; color:#fff; overflow-x:hidden;
}
@keyframes bgShift { 0%{background-position:0% 50%} 50%{background-position:100% 50%} 100%{background-position:0% 50%} }

/* Background orbs */
.bg-orbs { position:fixed; inset:0; z-index:0; pointer-events:none; overflow:hidden; }
.orb { position:absolute; border-radius:50%; filter:blur(100px); will-change:transform;
    animation:orbFloat var(--dur,16s) ease-in-out infinite; animation-delay:var(--delay,0s); }
.orb1 { width:500px;height:500px;top:-180px;left:-100px;background:radial-gradient(circle,rgba(201,168,76,0.1) 0%,transparent 70%);--dur:18s;--delay:0s; }
.orb2 { width:400px;height:400px;bottom:-120px;right:-80px;background:radial-gradient(circle,rgba(79,142,247,0.08) 0%,transparent 70%);--dur:22s;--delay:4s; }
@keyframes orbFloat { 0%,100%{transform:translate(0,0)} 50%{transform:translate(20px,-30px)} }

/* Glass */
.glass {
    background:rgba(12,18,36,0.72);
    backdrop-filter:blur(24px) saturate(180%);
    border:1px solid rgba(255,255,255,0.1);
    border-radius:20px;
    box-shadow:0 8px 32px rgba(0,0,0,0.35);
}

/* Header */
header {
    position:fixed; top:0; left:0; right:0; height:62px; z-index:1000;
    display:flex; align-items:center; justify-content:space-between; padding:0 20px;
    background:rgba(6,10,20,0.9); backdrop-filter:blur(24px);
    border-bottom:1px solid rgba(255,255,255,0.07);
    box-shadow:0 2px 20px rgba(0,0,0,0.4);
}
.menu-btn {
    background:none; border:none; color:var(--p-primary); font-size:1.4rem;
    width:40px; height:40px; border-radius:10px; cursor:pointer;
    display:flex; align-items:center; justify-content:center;
    transition:all .25s cubic-bezier(.34,1.5,.64,1);
}
.menu-btn:hover { background:rgba(255,255,255,0.08); transform:scale(1.1) rotate(90deg); }
.header-title { font-size:.95rem; font-weight:700; letter-spacing:.08em; color:var(--p-primary); text-transform:uppercase; }
.user-badge {
    display:flex; align-items:center; gap:8px;
    background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1);
    border-radius:50px; padding:7px 16px; font-size:.82rem; font-weight:600;
}
.user-badge i { color:var(--p-primary); }

/* Sidebar */
#sidebar {
    position:fixed; top:62px; left:-268px; bottom:0; width:248px;
    background:rgba(6,10,20,0.97); backdrop-filter:blur(24px);
    border-right:1px solid rgba(255,255,255,0.07);
    z-index:999; overflow-y:auto; padding:16px 12px 80px;
    transition:left .32s cubic-bezier(.4,0,.2,1);
    scrollbar-width:thin; scrollbar-color:rgba(201,168,76,0.3) transparent;
}
#sidebar.active { left:0; }
#overlay { position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:998; display:none; backdrop-filter:blur(4px); }
#overlay.active { display:block; }

.sidebar-logo-section { text-align:center; padding:12px 8px 18px; border-bottom:1px solid rgba(255,255,255,0.07); margin-bottom:14px; }
.sidebar-logo-ring { position:relative; width:72px; height:72px; margin:0 auto 10px; border-radius:50%;
    background:conic-gradient(from 0deg, var(--p-primary), var(--p-accent), var(--p-primary));
    animation:spinRing 5s linear infinite; padding:3px; }
@keyframes spinRing { to { transform:rotate(360deg); } }
.sidebar-logo-inner { width:100%; height:100%; border-radius:50%; background:var(--p-bg1); overflow:hidden; display:flex; align-items:center; justify-content:center; }
.sidebar-logo-inner img { width:100%; height:100%; object-fit:cover; border-radius:50%; }
.sidebar-logo-fallback { font-size:1.6rem; color:var(--p-primary); display:none; }
.sidebar-panel-name { font-size:.82rem; font-weight:700; color:var(--p-primary); }
.nav-label { font-size:.6rem; font-weight:700; letter-spacing:.15em; color:rgba(255,255,255,.22); text-transform:uppercase; padding:10px 12px 5px; margin-top:4px; }
.nav-btn {
    width:100%; padding:11px 14px; margin-bottom:4px;
    background:rgba(255,255,255,0.04); border:1px solid transparent; border-radius:13px;
    color:rgba(255,255,255,0.75); text-align:left; cursor:pointer; text-decoration:none;
    font-family:'Montserrat',sans-serif; font-size:.83rem; font-weight:600;
    display:flex; align-items:center; gap:10px;
    transition:all .22s cubic-bezier(.34,1.2,.64,1);
}
.nav-btn:hover { background:rgba(255,255,255,0.09); border-color:rgba(255,255,255,0.1); color:#fff; transform:translateX(4px); }
.nav-btn.active-page { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.25); color:var(--p-primary); }
.nav-btn i { width:18px; text-align:center; color:var(--p-primary); font-size:.9rem; }

/* Main */
.main { position:relative; z-index:1; padding:80px 16px 60px; max-width:920px; margin:0 auto; }

/* Alert */
.alert-ok  { background:rgba(52,211,153,0.1); border:1px solid rgba(52,211,153,0.3); color:#34d399; border-radius:14px; padding:14px 20px; font-size:.88rem; font-weight:600; margin-bottom:20px; display:flex; align-items:center; gap:10px; }
.alert-err { background:rgba(248,113,113,0.1); border:1px solid rgba(248,113,113,0.3); color:#f87171; border-radius:14px; padding:14px 20px; font-size:.88rem; font-weight:600; margin-bottom:20px; }

/* Preview Bar */
.preview-bar {
    display:flex; align-items:center; gap:16px;
    padding:18px 22px; margin-bottom:22px; position:relative;
    background:linear-gradient(135deg, rgba(var(--p-bg2-raw,15,23,42),0.8), transparent);
    border:1px solid rgba(255,255,255,0.1); border-radius:18px;
    backdrop-filter:blur(16px);
    overflow:hidden;
}
.preview-bar::before { content:'LIVE PREVIEW'; position:absolute; right:18px; top:50%; transform:translateY(-50%); font-size:.58rem; letter-spacing:.2em; color:rgba(255,255,255,.2); font-weight:700; }
.prev-logo { width:52px; height:52px; border-radius:50%; object-fit:cover; border:2px solid var(--p-primary); box-shadow:0 0 16px rgba(201,168,76,0.2); }
.prev-title { font-size:1.15rem; font-weight:800; color:var(--p-primary); letter-spacing:.06em; text-transform:uppercase; }
.prev-sub { font-size:.65rem; color:rgba(255,255,255,.4); letter-spacing:.15em; text-transform:uppercase; margin-top:2px; }

/* Section */
.sec-head {
    display:flex; align-items:center; gap:10px;
    padding-bottom:12px; margin:0 0 18px;
    border-bottom:1px solid rgba(255,255,255,0.08);
}
.sec-head i { color:var(--p-primary); }
.sec-head h5 { margin:0; font-size:.95rem; font-weight:700; letter-spacing:.05em; }
.sec-head .sec-badge { margin-left:auto; font-size:.65rem; padding:3px 10px; border-radius:20px; background:rgba(201,168,76,0.12); color:var(--p-primary); border:1px solid rgba(201,168,76,0.25); font-weight:700; letter-spacing:.08em; }

/* Form */
.form-label { color:rgba(255,255,255,0.65); font-size:.78rem; font-weight:600; letter-spacing:.05em; margin-bottom:6px; display:block; }
.form-control, .form-select {
    background:rgba(255,255,255,0.06) !important; border:1px solid rgba(255,255,255,0.1) !important;
    color:#fff !important; border-radius:12px !important; padding:10px 14px;
    font-family:'Montserrat',sans-serif; font-size:.87rem; transition:border-color .2s, box-shadow .2s;
}
.form-control:focus, .form-select:focus {
    border-color:var(--p-primary) !important;
    box-shadow:0 0 0 3px rgba(201,168,76,0.1) !important; outline:none;
    background:rgba(255,255,255,0.09) !important;
}
.form-control::placeholder { color:rgba(255,255,255,0.25); }
.form-select option { background:#111827; color:#fff; }
input[type="color"] { width:100%; height:44px; padding:4px 8px; border-radius:12px !important; cursor:pointer; }

/* Theme Grid */
.theme-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(120px,1fr)); gap:10px; margin-bottom:6px; }
.theme-card {
    border-radius:14px; padding:12px; cursor:pointer; text-align:center;
    border:2px solid transparent; transition:all .25s cubic-bezier(.34,1.5,.64,1);
    position:relative; overflow:hidden;
}
.theme-card:hover { transform:translateY(-3px) scale(1.03); box-shadow:0 10px 28px rgba(0,0,0,0.5); }
.theme-card.selected { border-color:var(--p-primary); box-shadow:0 0 0 3px rgba(201,168,76,0.2); }
.theme-card.selected::after { content:'✓'; position:absolute; top:5px; right:8px; font-size:.7rem; color:var(--p-primary); font-weight:800; }
.theme-swatches { display:flex; gap:3px; justify-content:center; margin-bottom:7px; }
.swatch { width:18px; height:18px; border-radius:50%; border:1px solid rgba(255,255,255,0.15); }
.theme-name { font-size:.7rem; font-weight:700; color:#fff; letter-spacing:.03em; }

/* Save button */

.btn-save::before { content:''; position:absolute; inset:0; background:linear-gradient(180deg,rgba(255,255,255,0.15) 0%,transparent 50%); border-radius:inherit; }
.btn-save:hover { background-position:right center; transform:translateY(-2px); box-shadow:0 14px 35px rgba(201,168,76,0.35); }

/* Logo Upload */
.logo-preview-wrap { display:flex; align-items:center; gap:14px; margin-top:12px; }
.logo-preview-img { width:64px; height:64px; border-radius:50%; object-fit:cover; border:2px solid var(--p-primary); box-shadow:0 0 18px rgba(201,168,76,0.2); flex-shrink:0; }
.logo-preview-fallback { width:64px; height:64px; border-radius:50%; background:rgba(201,168,76,0.1); border:2px solid rgba(201,168,76,0.3); display:none; align-items:center; justify-content:center; font-size:1.5rem; color:var(--p-primary); flex-shrink:0; }

::-webkit-scrollbar { width:6px; }
::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:4px; }

@media(max-width:640px) { .theme-grid { grid-template-columns:repeat(3,1fr); } .main { padding:78px 12px 50px; } }

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

<div class="bg-orbs"><div class="orb orb1"></div><div class="orb orb2"></div></div>
<div id="overlay" onclick="toggleSidebar()"></div>

<header>
    <button class="menu-btn" onclick="toggleSidebar()"><i class="fas fa-bars"></i></button>
    <div class="header-title"><i class="fas fa-paint-brush me-2" style="font-size:.85rem;"></i>Panel Customizer</div>
    <div class="user-badge"><i class="fas fa-user-circle"></i><span><?= htmlspecialchars($urow['username']) ?></span></div>
</header>

<!-- SIDEBAR -->
<aside id="sidebar">
    <div class="sidebar-logo-section">
        <div class="sidebar-logo-ring">
            <div class="sidebar-logo-inner">
                <img id="sbLogo" src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>" alt="Logo"
                     onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='flex';">
                <div class="sidebar-logo-fallback"><i class="fas fa-box-open"></i></div>
            </div>
        </div>
        <div class="sidebar-panel-name"><?= htmlspecialchars($P['panel_name']) ?></div>
    </div>
    <div class="nav-label">Main</div>
    <a class="nav-btn" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>
    <div class="nav-label">Management</div>
    <a class="nav-btn" href="manage_users.php"><i class="fas fa-circle-user"></i> Manage Users</a>
    <a class="nav-btn" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn active-page" href="panel_customizer.php"><i class="fas fa-paint-brush"></i> Customizer</a>
    <a class="nav-btn" href="announcements.php"><i class="fas fa-bullhorn"></i> Announcements</a>
    <div class="nav-label">Config</div>
    <a class="nav-btn" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>

<div class="main">

    <?php if ($msg): ?>
    <div class="alert-ok"><i class="fas fa-check-circle"></i><?= htmlspecialchars($msg) ?></div>
    <?php endif; ?>
    <?php if ($err): ?>
    <div class="alert-err"><i class="fas fa-exclamation-circle me-2"></i><?= htmlspecialchars($err) ?></div>
    <?php endif; ?>

    <!-- LIVE PREVIEW -->
    <div class="preview-bar" id="previewBar">
        <img src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>" class="prev-logo" id="prevLogo"
             onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='flex';">
        <div class="logo-preview-fallback" id="prevLogoFallback"><i class="fas fa-box-open"></i></div>
        <div>
            <div class="prev-title" id="prevTitle"><?= htmlspecialchars($P['panel_name']) ?></div>
            <div class="prev-sub" id="prevSub"><?= htmlspecialchars($P['panel_tagline']) ?></div>
        </div>
    </div>

    <form method="POST">

        <!-- THEME PRESETS -->
        <div class="glass p-4 mb-4">
            <div class="sec-head">
                <i class="fas fa-palette"></i><h5>Theme Presets</h5>
                <span class="sec-badge"><?= strtoupper($role) ?></span>
            </div>
            <div class="theme-grid" id="themeGrid">
                <?php foreach ($themes as $key => $t): ?>
                <div class="theme-card <?= ($P['active_theme']==$key)?'selected':'' ?>"
                     data-theme="<?= $key ?>"
                     style="background:linear-gradient(135deg,<?=$t['bg1']?>,<?=$t['bg3']?>);"
                     onclick="applyTheme('<?=$key?>','<?=$t['bg1']?>','<?=$t['bg2']?>','<?=$t['bg3']?>','<?=$t['bg4']?>','<?=$t['primary']?>','<?=$t['accent']?>')">
                    <div class="theme-swatches">
                        <div class="swatch" style="background:<?=$t['bg1']?>"></div>
                        <div class="swatch" style="background:<?=$t['bg3']?>"></div>
                        <div class="swatch" style="background:<?=$t['primary']?>"></div>
                        <div class="swatch" style="background:<?=$t['accent']?>"></div>
                    </div>
                    <div class="theme-name"><?= $t['name'] ?></div>
                </div>
                <?php endforeach; ?>
            </div>
            <input type="hidden" name="active_theme" id="inp_active_theme" value="<?= htmlspecialchars($P['active_theme']) ?>">
        </div>

        <!-- CUSTOM COLORS -->
        <div class="glass p-4 mb-4">
            <div class="sec-head"><i class="fas fa-droplet"></i><h5>Custom Colors</h5></div>
            <div class="row g-3">
                <?php
                $colorFields = [
                    ['theme_primary','Primary Color','--p-primary','inp_primary'],
                    ['theme_accent','Accent Color','--p-accent','inp_accent'],
                    ['theme_bg1','BG Tone 1','--p-bg1','inp_bg1'],
                    ['theme_bg2','BG Tone 2','--p-bg2','inp_bg2'],
                    ['theme_bg3','BG Tone 3','--p-bg3','inp_bg3'],
                    ['theme_bg4','BG Tone 4','--p-bg4','inp_bg4'],
                ];
                foreach ($colorFields as [$name,$label,$var,$id]):
                ?>
                <div class="col-6 col-md-4">
                    <label class="form-label"><?= $label ?></label>
                    <input type="color" name="<?= $name ?>" id="<?= $id ?>" value="<?= htmlspecialchars($P[$name]) ?>"
                           oninput="updateVar('<?= $var ?>',this.value)">
                </div>
                <?php endforeach; ?>
            </div>
        </div>

        <!-- BRANDING TEXT -->
        <div class="glass p-4 mb-4">
            <div class="sec-head"><i class="fas fa-font"></i><h5>Branding & Text</h5></div>
            <div class="row g-3">
                <div class="col-md-6">
                    <label class="form-label">Panel Name</label>
                    <input type="text" name="panel_name" class="form-control" value="<?= htmlspecialchars($P['panel_name']) ?>"
                           placeholder="OneBox VIP Panel" oninput="document.getElementById('prevTitle').textContent=this.value">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Panel Tagline</label>
                    <input type="text" name="panel_tagline" class="form-control" value="<?= htmlspecialchars($P['panel_tagline']) ?>"
                           placeholder="Premium Access Portal" oninput="document.getElementById('prevSub').textContent=this.value">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Login Page Title</label>
                    <input type="text" name="login_title" class="form-control" value="<?= htmlspecialchars($P['login_title']) ?>" placeholder="OneBox Login">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Login Subtitle</label>
                    <input type="text" name="login_subtitle" class="form-control" value="<?= htmlspecialchars($P['login_subtitle']) ?>" placeholder="Secure Sign In">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Login Badge Text</label>
                    <input type="text" name="login_badge_text" class="form-control" value="<?= htmlspecialchars($P['login_badge_text']) ?>" placeholder="RSDK VIP">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Dashboard Title</label>
                    <input type="text" name="dashboard_title" class="form-control" value="<?= htmlspecialchars($P['dashboard_title']) ?>" placeholder="ONEBOX DASHBOARD">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Watermark Text</label>
                    <input type="text" name="watermark_text" class="form-control" value="<?= htmlspecialchars($P['watermark_text']) ?>" placeholder="ONEBOX · SECURED">
                </div>
                <div class="col-md-6">
                    <label class="form-label">Footer Text</label>
                    <input type="text" name="footer_text" class="form-control" value="<?= htmlspecialchars($P['footer_text']) ?>" placeholder="RSDK VIP © 2025">
                </div>
            </div>
        </div>

        <!-- LOGO -->
        <div class="glass p-4 mb-4">
            <div class="sec-head"><i class="fas fa-image"></i><h5>Logo</h5></div>
            <p style="font-size:.78rem; color:rgba(255,255,255,.4); margin-bottom:14px;">
                <i class="fas fa-info-circle me-1" style="color:var(--p-primary);"></i>
                Place your <strong style="color:var(--p-primary);">logo.png</strong> file in the same folder. Or paste a URL below.
            </p>
            <div class="row g-3 align-items-end">
                <div class="col-md-9">
                    <label class="form-label">Logo Path / URL</label>
                    <input type="text" name="sidebar_logo_url" class="form-control"
                           value="<?= htmlspecialchars($P['sidebar_logo_url']) ?>"
                           placeholder="logo.png"
                           oninput="updateLogoPreview(this.value)">
                </div>
                <div class="col-md-3">
                    <div class="logo-preview-wrap">
                        <img id="logoPreviewImg" class="logo-preview-img"
                             src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>"
                             onerror="this.onerror=null; this.style.display='none'; document.getElementById('logoFallbackDiv').style.display='flex';">
                        <div id="logoFallbackDiv" class="logo-preview-fallback"><i class="fas fa-box-open"></i></div>
                    </div>
                </div>
            </div>
        </div>

        <!-- SAVE -->
        <div class="text-center mt-4">
            <button type="submit" name="save_settings" class="btn-save">
                <i class="fas fa-save me-2"></i>Save All Changes
            </button>
            <p style="margin-top:10px;font-size:.72rem;color:rgba(255,255,255,.25);">
                Changes apply globally to all users immediately
            </p>
        </div>

    </form>
</div>

<script>
function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('active');
    document.getElementById('overlay').classList.toggle('active');
}

function updateVar(v, val) {
    document.documentElement.style.setProperty(v, val);
}

function updateLogoPreview(src) {
    const img = document.getElementById('logoPreviewImg');
    const fb  = document.getElementById('logoFallbackDiv');
    const pLogo = document.getElementById('prevLogo');
    const pFb   = document.getElementById('prevLogoFallback');
    const sbLogo= document.getElementById('sbLogo');

    img.style.display = ''; fb.style.display = 'none';
    img.onerror = function() { this.style.display='none'; fb.style.display='flex'; };
    img.src = src;

    pLogo.style.display = ''; pFb.style.display = 'none';
    pLogo.onerror = function() { this.style.display='none'; pFb.style.display='flex'; };
    pLogo.src = src;

    if (sbLogo) { sbLogo.src = src; }
}

function applyTheme(key, bg1, bg2, bg3, bg4, primary, accent) {
    document.getElementById('inp_active_theme').value = key;
    document.getElementById('inp_primary').value = primary;
    document.getElementById('inp_accent').value  = accent;
    document.getElementById('inp_bg1').value     = bg1;
    document.getElementById('inp_bg2').value     = bg2;
    document.getElementById('inp_bg3').value     = bg3;
    document.getElementById('inp_bg4').value     = bg4;
    updateVar('--p-primary', primary);
    updateVar('--p-accent',  accent);
    updateVar('--p-bg1', bg1); updateVar('--p-bg2', bg2);
    updateVar('--p-bg3', bg3); updateVar('--p-bg4', bg4);
    document.querySelectorAll('.theme-card').forEach(c => c.classList.remove('selected'));
    document.querySelector(`[data-theme="${key}"]`).classList.add('selected');
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
