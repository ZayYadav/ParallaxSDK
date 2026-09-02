<?php
include 'conn.php';
if (!isset($_SESSION['username'])) {
    header("Location: login.php");
    exit();
}
include 'panel_helper.php';

$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$current_user_id = (int)($_SESSION['user_id'] ?? 0);
$uid = $current_user_id;
$unread_ann_count = $uid ? count_unread_announcements($conn, $uid) : 0;

// Get current user's role
$user_query = mysqli_query($conn, "SELECT * FROM users WHERE id = '$current_user_id'");
$current_user_data = mysqli_fetch_assoc($user_query);
$user_role = $current_user_data['role'] ?? 'user';
$role_dash = $user_role;
$is_owner = ($user_role == 'owner');
$is_admin = ($user_role == 'admin' || $user_role == 'owner');

/* ========= GENERATE ========= */
if (isset($_POST['generate'])) {
    $newCode = strtoupper(bin2hex(random_bytes(4)));
    $assigned_to = $_POST['assigned_to'] ?? 'user';
    $created_by = $current_user_id;

    // Check if columns exist, if not use basic insert
    $columns_check = mysqli_query($conn, "SHOW COLUMNS FROM referral_codes LIKE 'assigned_to'");
    if (mysqli_num_rows($columns_check) > 0) {
        // New structure with all columns
        $stmt = $conn->prepare("INSERT INTO referral_codes (code, assigned_to, created_by) VALUES (?, ?, ?)");
        $stmt->bind_param("ssi", $newCode, $assigned_to, $created_by);
    } else {
        // Old structure - basic insert
        $stmt = $conn->prepare("INSERT INTO referral_codes (code) VALUES (?)");
        $stmt->bind_param("s", $newCode);
    }

    if ($stmt->execute()) {
        $_SESSION['success_msg'] = "Referral code generated successfully!";
    } else {
        $_SESSION['error_msg'] = "Error: " . $conn->error;
    }

    header("Location: manage_referrals.php");
    exit();
}

/* ========= EDIT ========= */
if (isset($_POST['edit_code'])) {
    $id = (int)$_POST['code_id'];
    $assigned_to = $_POST['assigned_to'];
    $status = (int)$_POST['status'];

    // Check if columns exist
    $columns_check = mysqli_query($conn, "SHOW COLUMNS FROM referral_codes LIKE 'assigned_to'");
    if (mysqli_num_rows($columns_check) > 0) {
        $stmt = $conn->prepare("UPDATE referral_codes SET assigned_to = ?, status = ? WHERE id = ?");
        $stmt->bind_param("sii", $assigned_to, $status, $id);
        $stmt->execute();
    }

    header("Location: manage_referrals.php");
    exit();
}

/* ========= DELETE ========= */
if (isset($_POST['delete_id'])) {
    $id = (int)$_POST['delete_id'];
    $stmt = $conn->prepare("DELETE FROM referral_codes WHERE id=?");
    $stmt->bind_param("i", $id);
    $stmt->execute();
    header("Location: manage_referrals.php");
    exit();
}

/* ========= FETCH ========= */
// Check if used_by column exists to join with users
$used_by_exists = mysqli_query($conn, "SHOW COLUMNS FROM referral_codes LIKE 'used_by'");
if (mysqli_num_rows($used_by_exists) > 0) {
    $referrals = mysqli_query($conn, "SELECT r.*, u.username as used_by_username
                                      FROM referral_codes r
                                      LEFT JOIN users u ON r.used_by = u.id
                                      ORDER BY r.id DESC");
} else {
    $referrals = mysqli_query($conn, "SELECT * FROM referral_codes ORDER BY id DESC");
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Manage Referrals - <?= htmlspecialchars($P['dashboard_title'] ?? 'Panel') ?></title>

<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap" rel="stylesheet">

<style>
<?= panel_css_vars($P) ?>

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
    font-family: 'Montserrat', sans-serif;
    background: linear-gradient(135deg, var(--p-bg1) 0%, var(--p-bg2) 35%, var(--p-bg3) 70%, var(--p-bg4) 100%);
    background-size: 400% 400%;
    animation: bgShift 20s ease infinite;
    min-height: 100vh;
    color: #fff;
    overflow-x: hidden;
}
@keyframes bgShift {
    0%   { background-position: 0% 50%; }
    50%  { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

/* ── BACKGROUND ORBS ── */
.bg-orbs { position:fixed; inset:0; z-index:0; pointer-events:none; overflow:hidden; }
.orb {
    position:absolute; border-radius:50%;
    filter: blur(100px);
    animation: orbFloat var(--dur,16s) ease-in-out infinite;
    animation-delay: var(--delay,0s);
    will-change: transform;
}
.orb1 { width:600px; height:600px; top:-200px; left:-150px;
    background: radial-gradient(circle, rgba(201,168,76,0.12) 0%, transparent 70%);
    --dur:18s; --delay:0s; }
.orb2 { width:500px; height:500px; bottom:-150px; right:-100px;
    background: radial-gradient(circle, rgba(79,142,247,0.1) 0%, transparent 70%);
    --dur:22s; --delay:4s; }
.orb3 { width:400px; height:400px; top:40%; left:40%;
    background: radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 70%);
    --dur:26s; --delay:8s; }
@keyframes orbFloat {
    0%,100% { transform: translate(0,0) scale(1); }
    33%  { transform: translate(30px,-40px) scale(1.05); }
    66%  { transform: translate(-20px,25px) scale(0.97); }
}

/* ── GLASS ── */
.glass {
    background: rgba(15,23,42,0.72);
    backdrop-filter: blur(24px) saturate(180%);
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 20px;
    box-shadow: 0 8px 32px rgba(0,0,0,0.35);
}

/* ── HEADER ── */
header {
    position: fixed; top:0; left:0; right:0; height:62px; z-index:1000;
    display: flex; align-items: center; justify-content: space-between;
    padding: 0 20px;
    background: rgba(8,12,24,0.88);
    backdrop-filter: blur(24px) saturate(200%);
    border-bottom: 1px solid rgba(255,255,255,0.07);
    box-shadow: 0 2px 20px rgba(0,0,0,0.4);
}
.header-left { display:flex; align-items:center; gap:14px; }
.menu-btn {
    background:none; border:none; color:var(--p-primary);
    font-size:1.4rem; cursor:pointer;
    width:40px; height:40px; border-radius:10px;
    display:flex; align-items:center; justify-content:center;
    transition: all .25s cubic-bezier(.34,1.5,.64,1);
}
.menu-btn:hover { background:rgba(255,255,255,0.08); transform:scale(1.1) rotate(90deg); }
.header-title {
    font-size:1rem; font-weight:700; letter-spacing:.08em;
    color:var(--p-primary); text-transform:uppercase;
}
.user-badge {
    display:flex; align-items:center; gap:8px;
    background:rgba(255,255,255,0.06);
    border:1px solid rgba(255,255,255,0.1);
    border-radius:50px; padding:7px 16px;
    font-size:.82rem; font-weight:600;
    transition: all .25s;
    cursor:default;
}
.user-badge:hover { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.3); }
.user-badge i { color:var(--p-primary); }

/* ── SIDEBAR ── */
#sidebar {
    position:fixed; top:62px; left:-268px; bottom:0; width:248px;
    background: rgba(6,10,20,0.97);
    backdrop-filter: blur(24px);
    border-right:1px solid rgba(255,255,255,0.07);
    z-index:999; overflow-y:auto; overflow-x:hidden;
    transition: left .32s cubic-bezier(.4,0,.2,1);
    padding:16px 12px 80px;
    scrollbar-width: thin;
    scrollbar-color: rgba(201,168,76,0.3) transparent;
}
#sidebar.active { left:0; }
#sidebar::-webkit-scrollbar { width:4px; }
#sidebar::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:4px; }

#overlay {
    position:fixed; inset:0; background:rgba(0,0,0,0.55);
    z-index:998; display:none; backdrop-filter:blur(4px);
    transition:opacity .3s;
}
#overlay.active { display:block; }

.sidebar-logo-section {
    text-align:center;
    padding:12px 8px 20px;
    border-bottom:1px solid rgba(255,255,255,0.07);
    margin-bottom:14px;
}
.sidebar-logo-ring {
    position:relative; width:80px; height:80px;
    margin:0 auto 12px; border-radius:50%;
    background: conic-gradient(from 0deg, var(--p-primary), var(--p-accent), var(--p-primary));
    animation: spinRing 5s linear infinite;
    padding:3px;
}
@keyframes spinRing { to { transform:rotate(360deg); } }
.sidebar-logo-inner {
    width:100%; height:100%; border-radius:50%;
    background: var(--p-bg1);
    overflow:hidden; display:flex; align-items:center; justify-content:center;
}
.sidebar-logo-inner img { width:100%; height:100%; object-fit:cover; border-radius:50%; display:block; }
.sidebar-logo-fallback { font-size:1.8rem; color:var(--p-primary); display:none; }
.sidebar-panel-name { font-size:.85rem; font-weight:700; color:var(--p-primary); margin-bottom:4px; }
.sidebar-user-chip {
    display:inline-flex; align-items:center; gap:5px;
    font-size:.68rem; font-weight:600; letter-spacing:.08em;
    color:rgba(255,255,255,0.5);
    background:rgba(255,255,255,0.06);
    border:1px solid rgba(255,255,255,0.08);
    border-radius:30px; padding:3px 10px;
}

.sidebar-nav-label {
    font-size:.62rem; font-weight:700; letter-spacing:.15em; color:rgba(255,255,255,0.25);
    text-transform:uppercase; padding:10px 12px 6px; margin-top:6px;
}
.nav-btn {
    width:100%; padding:11px 14px; margin-bottom:4px;
    background:rgba(255,255,255,0.04); border:1px solid transparent;
    border-radius:13px; color:rgba(255,255,255,0.75);
    text-align:left; cursor:pointer;
    font-family:'Montserrat',sans-serif; font-size:.83rem; font-weight:600;
    display:flex; align-items:center; gap:10px;
    transition: all .22s cubic-bezier(.34,1.2,.64,1);
    position:relative; overflow:hidden;
    text-decoration:none;
}
.nav-btn::before {
    content:''; position:absolute; left:0; top:0; bottom:0; width:3px;
    background:var(--p-primary); border-radius:0 3px 3px 0;
    transform:scaleY(0); transition:transform .2s;
}
.nav-btn:hover, .nav-btn:focus {
    background:rgba(255,255,255,0.09);
    border-color:rgba(255,255,255,0.1);
    color:#fff; transform:translateX(4px);
}
.nav-btn:hover::before { transform:scaleY(1); }
.nav-btn.active-page {
    background:rgba(201,168,76,0.1);
    border-color:rgba(201,168,76,0.25);
    color:var(--p-primary);
}
.nav-btn.active-page::before { transform:scaleY(1); }
.nav-btn i { width:18px; text-align:center; color:var(--p-primary); font-size:.9rem; opacity:.85; }
.nav-btn:hover i, .nav-btn.active-page i { opacity:1; }
.nav-badge {
    margin-left:auto; background:#ef4444; color:#fff;
    border-radius:50%; width:18px; height:18px; font-size:.6rem;
    font-weight:700; display:flex; align-items:center; justify-content:center;
}

/* ── MAIN CONTENT ── */
.main {
    position:relative; z-index:1;
    padding: 90px 24px 24px;
    max-width: 1200px; margin: 0 auto;
    transition: padding-left .32s;
    animation: fadeIn 0.5s ease;
}
@media(min-width:769px) { .main { padding-left:24px; } }

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}

/* ALERTS */
.alert {
    border-radius: 16px; padding: 15px 20px; margin-bottom: 20px; border: none;
    backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
}
.alert-success { background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3); }
.alert-danger { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }

/* PAGE TITLE */
.page-title {
    font-size:1.6rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase;
    background: linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%);
    background-size:200% auto; -webkit-background-clip:text; -webkit-text-fill-color:transparent;
    background-clip:text; animation: shimmer 4s linear infinite; margin-bottom:24px; text-align:center;
}
@keyframes shimmer { to { background-position:200% center; } }

/* GENERATE CARD */
.generate-card {
    background: rgba(201, 168, 76, 0.05); border: 1px solid rgba(201, 168, 76, 0.2);
    border-radius: 20px; padding: 25px; margin-bottom: 30px;
    backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
}
.form-select {
    background: rgba(0, 0, 0, 0.28) !important; border: 1px solid rgba(255, 255, 255, 0.1) !important;
    color: #fff !important; border-radius: 12px !important; padding: 12px 14px !important;
    font-family: 'Montserrat', sans-serif; transition: border-color .22s ease, box-shadow .22s ease;
}
.form-select:focus {
    background: rgba(0, 0, 0, 0.45) !important; border-color: var(--p-primary) !important;
    box-shadow: 0 0 0 3px rgba(201, 168, 76, .2), 0 0 16px rgba(201, 168, 76, .15) !important;
    outline: none !important;
}
.form-select option { background: #1e2a45; color: #fff; }

/* ── PREMIUM TABLE ── */
.table-container {
    background: rgba(15, 23, 42, 0.5); backdrop-filter: blur(22px) saturate(180%);
    -webkit-backdrop-filter: blur(22px) saturate(180%);
    border: 1px solid rgba(255, 255, 255, 0.18); border-radius: 30px; padding: 25px;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4); overflow-x: auto;
}
.premium-table { width: 100%; border-collapse: collapse; color: #fff; }
.premium-table thead tr { background: rgba(255, 255, 255, 0.05); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }
.premium-table th {
    background: rgba(201, 168, 76, 0.1); color: var(--p-primary); font-weight: 600;
    text-transform: uppercase; font-size: 0.85rem; letter-spacing: 1px;
    padding: 16px 12px; border: none; position: relative; overflow: hidden;
}
.premium-table th i { margin-right: 8px; color: var(--p-primary); }
.premium-table td { padding: 14px 12px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); vertical-align: middle; }
.premium-table tbody tr { transition: all 0.3s ease; background: rgba(255, 255, 255, 0.02); }
.premium-table tbody tr:hover { background: rgba(255, 255, 255, 0.06); transform: scale(1.01); box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3); }

/* BADGES */
.role-badge {
    padding: 5px 14px; border-radius: 50px; font-size: 0.75rem; font-weight: 600;
    display: inline-flex; align-items: center; gap: 6px; backdrop-filter: blur(5px);
}
.role-owner { background: linear-gradient(135deg, rgba(201,168,76,0.2), rgba(255,165,0,0.2)); color: var(--p-primary); border: 1px solid rgba(201,168,76,0.3); }
.role-admin { background: linear-gradient(135deg, rgba(59,130,246,0.2), rgba(37,99,235,0.2)); color: #93c5fd; border: 1px solid rgba(59,130,246,0.3); }
.role-user { background: linear-gradient(135deg, rgba(107,114,128,0.2), rgba(75,85,99,0.2)); color: #d1d5db; border: 1px solid rgba(255,255,255,0.2); }

.status-badge { padding: 5px 14px; border-radius: 50px; font-size: 0.75rem; font-weight: 600; display: inline-flex; align-items: center; gap: 5px; }
.status-available { background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3); }
.status-used { background: rgba(107, 114, 128, 0.15); color: #9ca3af; border: 1px solid rgba(255, 255, 255, 0.2); }

/* ── BUTTONS v2 ── */
.btn-ios, .btn-save, .ob-btn {
    position:relative; overflow:hidden; display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:13px 28px; border:none; border-radius:14px; font-family:'Montserrat',sans-serif; font-size:.9rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer;
    background: linear-gradient(135deg, var(--p-primary) 0%, var(--p-accent) 50%, var(--p-primary) 100%);
    background-size:200% 200%; box-shadow:0 4px 20px rgba(201,168,76,0.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s ease; text-decoration:none; width:auto;
}
.btn-ios::before { content:''; position:absolute; inset:0; background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent); transform:translateX(-100%); transition:transform .55s ease; }
.btn-ios:hover::before { transform:translateX(100%); }
.btn-ios:hover { transform:translateY(-3px) scale(1.02); box-shadow:0 12px 32px rgba(201,168,76,.55); color:#fff; }

.glass-btn {
    position:relative; padding:7px 15px; border-radius:50px; font-size:.78rem; font-weight:700; letter-spacing:.03em;
    border:1px solid rgba(255,255,255,.18); display:inline-flex; align-items:center; gap:6px;
    transition:transform .22s cubic-bezier(.34,1.5,.64,1),box-shadow .22s ease,background .22s ease;
    cursor:pointer; backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px); box-shadow:0 2px 10px rgba(0,0,0,.22); background:rgba(255,255,255,.05); color: #fff;
}
.glass-btn:hover { transform:translateY(-2px) scale(1.06); color:#fff; }
.btn-copy-glass { color:#4ade80; border-color:rgba(52,211,153,.35); background:rgba(34,197,94,.1); }
.btn-copy-glass:hover { background:rgba(34,197,94,.28); border-color:rgba(52,211,153,.6); box-shadow:0 0 18px rgba(34,197,94,.45); color:#bbf7d0; }
.btn-edit-glass { color:#fde68a; border-color:rgba(234,179,8,.35); background:rgba(234,179,8,.1); }
.btn-edit-glass:hover { background:rgba(234,179,8,.28); border-color:rgba(234,179,8,.6); box-shadow:0 0 18px rgba(234,179,8,.45); color:#fef3c7; }
.btn-delete-glass { color:#fca5a5; border-color:rgba(239,68,68,.35); background:rgba(239,68,68,.1); }
.btn-delete-glass:hover { background:rgba(239,68,68,.28); border-color:rgba(239,68,68,.6); box-shadow:0 0 18px rgba(239,68,68,.45); color:#fee2e2; }

.btn-back {
    position:relative; display:inline-flex; align-items:center; gap:9px; padding:11px 26px; background:rgba(255,255,255,.07); border:1px solid rgba(255,255,255,.16); border-radius:50px;
    color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600; text-decoration:none; cursor:pointer; transition:all .25s; backdrop-filter:blur(10px);
}
.btn-back:hover { background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3); transform:translateY(-3px); box-shadow:0 10px 24px rgba(0,0,0,.35); color:#fff; }

.btn-danger {
    background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); border:none; padding:11px 26px; border-radius:50px;
    color:#fff; font-weight:600; font-size:.85rem; transition:all .25s; box-shadow:0 4px 15px rgba(239,68,68,0.3);
}
.btn-danger:hover { transform:translateY(-3px); box-shadow:0 10px 25px rgba(239,68,68,0.5); }

/* ACTION BUTTONS Container */
.action-buttons { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }

/* ── OVERLAY MODALS ── */
.overlay-modal {
    position:fixed; inset:0; background:rgba(0,0,0,.7); backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px);
    display:none; align-items:center; justify-content:center; z-index:4000;
}
.overlay-modal.active { display:flex; }
.modal-box { width:90%; padding:25px; text-align:center; animation:pop 0.3s cubic-bezier(.34,1.5,.64,1); }
@keyframes pop { from{transform:scale(0.9);opacity:0} to{transform:scale(1);opacity:1} }

.delete-box { max-width:340px; border: 1px solid rgba(239,68,68,0.3); }
.delete-code {
    background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 12px;
    padding: 12px; margin-bottom: 20px; font-family: monospace; font-size: 1.1rem; color: #f87171; font-weight: 600;
}

/* BOOTSTRAP MODAL OVERRIDES */
.modal-content { background:rgba(10,16,34,.97) !important; border:1px solid rgba(255,255,255,.1) !important; border-radius:20px !important; backdrop-filter:blur(24px) !important; color:#fff; }
.modal-header { border-bottom:1px solid rgba(255,255,255,.08) !important; }
.modal-footer { border-top:1px solid rgba(255,255,255,.08) !important; gap:10px; }
.modal-title { font-weight:700; color:var(--p-primary); }

.watermark { position:fixed; bottom:12px; left:0; right:0; text-align:center; font-size:.6rem; letter-spacing:.15em; text-transform:uppercase; color:rgba(255,255,255,0.07); z-index:1; pointer-events:none; }
::-webkit-scrollbar { width:8px; height:8px; }
::-webkit-scrollbar-track { background:rgba(255,255,255,0.05); border-radius:10px; }
::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:10px; }
::-webkit-scrollbar-thumb:hover { background:rgba(201,168,76,0.6); }
</style>
</head>

<body>

<div class="bg-orbs">
    <div class="orb orb1"></div>
    <div class="orb orb2"></div>
    <div class="orb orb3"></div>
</div>

<div id="overlay" onclick="toggleSidebar()"></div>

<header>
    <div class="header-left">
        <button class="menu-btn" onclick="toggleSidebar()" aria-label="Menu">
            <i class="fas fa-bars" id="menuIcon"></i>
        </button>
        <span class="header-title"><i class="fas fa-gift me-2" style="font-size:.85rem;"></i>Referrals</span>
    </div>
    <div class="user-badge">
        <i class="fas fa-user-circle"></i>
        <span><?= htmlspecialchars($_SESSION['username']) ?></span>
    </div>
</header>

<aside id="sidebar">
    <div class="sidebar-logo-section">
        <div class="sidebar-logo-ring">
            <div class="sidebar-logo-inner">
                <img src="<?= htmlspecialchars($P['sidebar_logo_url'] ?? 'logo.png') ?>" alt="Logo"
                     onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='flex';">
                <div class="sidebar-logo-fallback"><i class="fas fa-box-open"></i></div>
            </div>
        </div>
        <div class="sidebar-panel-name"><?= htmlspecialchars($P['panel_name'] ?? 'ONEBOX PANEL') ?></div>
        <div class="sidebar-user-chip"><i class="fas fa-shield-alt" style="color:var(--p-primary)"></i> <?= htmlspecialchars($current_user) ?></div>
    </div>

    <div class="sidebar-nav-label">Main</div>
    <a class="nav-btn" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>

    <div class="sidebar-nav-label">Management</div>
    <a class="nav-btn" href="manage_users.php"><i class="fas fa-circle-user"></i> Manage Users</a>
    <a class="nav-btn active-page" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn" href="announcements.php" style="position:relative;">
        <i class="fas fa-bullhorn"></i> Announcements
        <?php if ($unread_ann_count > 0): ?>
        <span class="nav-badge"><?= min($unread_ann_count,9) ?></span>
        <?php endif; ?>
    </a>

    <div class="sidebar-nav-label">Config</div>
    <?php if ($is_owner || $role_dash === 'admin'): ?>
    <a class="nav-btn" href="panel_customizer.php"><i class="fas fa-paint-brush"></i> Customizer</a>
    <?php endif; ?>
    <a class="nav-btn" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>

<div class="main">

    <?php if(isset($_SESSION['success_msg'])): ?>
    <div class="alert alert-success alert-dismissible fade show">
        <i class="fas fa-check-circle me-2"></i> <?= $_SESSION['success_msg'] ?>
        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="alert"></button>
    </div>
    <?php unset($_SESSION['success_msg']); endif; ?>

    <?php if(isset($_SESSION['error_msg'])): ?>
    <div class="alert alert-danger alert-dismissible fade show">
        <i class="fas fa-exclamation-circle me-2"></i> <?= $_SESSION['error_msg'] ?>
        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="alert"></button>
    </div>
    <?php unset($_SESSION['error_msg']); endif; ?>

    <div class="page-title">Manage Referrals</div>

    <div class="generate-card glass">
        <form method="POST" class="row g-3 align-items-end">
            <div class="col-md-8">
                <label class="form-label" style="font-weight:600; color:var(--p-primary); font-size:0.9rem;"><i class="fas fa-user-tag me-2"></i>Assign To</label>
                <select name="assigned_to" class="form-select" required>
                    <option value="user">👤 User</option>
                    <?php if($is_admin): ?>
                    <option value="admin">🛡️ Admin</option>
                    <?php endif; ?>
                    <?php if($is_owner): ?>
                    <option value="owner">👑 Owner</option>
                    <?php endif; ?>
                </select>
            </div>
            <div class="col-md-4">
                <button type="submit" name="generate" class="btn-ios w-100" style="padding:13px 20px;">
                    <i class="fas fa-plus-circle me-2"></i>Generate Code
                </button>
            </div>
        </form>
    </div>

    <div class="table-container">
        <table class="premium-table">
            <thead>
                <tr>
                    <th><i class="fas fa-hashtag"></i> ID</th>
                    <th><i class="fas fa-code"></i> Code</th>
                    <th><i class="fas fa-tag"></i> Assigned To</th>
                    <th><i class="fas fa-user"></i> Used By</th>
                    <th><i class="fas fa-circle"></i> Status</th>
                    <th><i class="fas fa-cog"></i> Actions</th>
                </tr>
            </thead>
            <tbody>
                <?php while($ref = mysqli_fetch_assoc($referrals)):
                    $status = $ref['used_by'] ? 'used' : 'available';
                    $statusClass = $status == 'available' ? 'status-available' : 'status-used';
                    $statusText = $status == 'available' ? 'AVAILABLE' : 'USED';

                    $assigned_to = $ref['assigned_to'] ?? 'user';
                    $assignedClass = 'role-' . $assigned_to;
                    $assignedIcon = $assigned_to == 'owner' ? 'fa-crown' : ($assigned_to == 'admin' ? 'fa-shield-alt' : 'fa-user');
                ?>
                <tr>
                    <td><span style="color:var(--p-primary); font-weight:700;">#<?= $ref['id'] ?></span></td>
                    <td><code style="background:rgba(255,255,255,0.08); padding:5px 10px; border-radius:8px; color:var(--p-primary); font-family:monospace; font-weight:700; letter-spacing:1px;"><?= $ref['code'] ?></code></td>
                    <td>
                        <span class="role-badge <?= $assignedClass ?>">
                            <i class="fas <?= $assignedIcon ?>"></i>
                            <?= strtoupper($assigned_to) ?>
                        </span>
                    </td>
                    <td>
                        <?php if($ref['used_by']): ?>
                            <span style="color:#4ade80; font-weight:600; font-size:0.9rem;">
                                <i class="fas fa-user-check me-1"></i><?= htmlspecialchars($ref['used_by_username'] ?? 'User') ?>
                            </span>
                        <?php else: ?>
                            <span style="color:rgba(255,255,255,0.4); font-weight:500;">—</span>
                        <?php endif; ?>
                    </td>
                    <td>
                        <span class="status-badge <?= $statusClass ?>">
                            <i class="fas fa-<?= $status == 'available' ? 'check-circle' : 'ban' ?>"></i>
                            <?= $statusText ?>
                        </span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button class="glass-btn btn-copy-glass" onclick="copyCode('<?= $ref['code'] ?>', this)">
                                <i class="fas fa-copy"></i> Copy
                            </button>

                            <?php if($is_admin): ?>
                            <button class="glass-btn btn-edit-glass" onclick="openEditModal(<?= $ref['id'] ?>, '<?= $assigned_to ?>', <?= $ref['used_by'] ? 0 : 1 ?>)">
                                <i class="fas fa-edit"></i> Edit
                            </button>
                            <?php endif; ?>

                            <?php if($is_owner): ?>
                            <button class="glass-btn btn-delete-glass" onclick="openDeleteModal(<?= $ref['id'] ?>, '<?= $ref['code'] ?>')">
                                <i class="fas fa-trash-alt"></i> Delete
                            </button>
                            <?php endif; ?>
                        </div>
                    </td>
                </tr>
                <?php endwhile; ?>
            </tbody>
        </table>

        <div class="text-center mt-5">
            <a href="dashboard.php" class="btn-back">
                <i class="fas fa-arrow-left"></i> Back to Dashboard
            </a>
        </div>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text'] ?? 'ONEBOX PANEL') ?></div>

<div id="deleteModal" class="overlay-modal">
    <div class="glass modal-box delete-box">
        <i class="fas fa-exclamation-triangle fa-3x mb-3 text-danger" style="animation: pulse 1s infinite;"></i>
        <h4 class="mb-2" style="font-weight:700;">Delete Code?</h4>
        <p class="opacity-75 mb-3">This action cannot be undone.</p>

        <div class="delete-code" id="deleteCodeDisplay">CODE</div>

        <form method="post" id="deleteForm">
            <input type="hidden" name="delete_id" id="deleteId">
            <div class="d-flex gap-3">
                <button type="button" class="btn-back w-50" style="justify-content:center;" onclick="closeDeleteModal()">
                    <i class="fas fa-times"></i> Cancel
                </button>
                <button type="submit" class="btn-danger w-50" style="display:flex; justify-content:center; align-items:center; gap:8px;">
                    <i class="fas fa-trash-alt"></i> Delete
                </button>
            </div>
        </form>
    </div>
</div>

<?php if($is_admin): ?>
<div class="modal fade" id="editModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass">
            <div class="modal-header border-0">
                <h5 class="modal-title">
                    <i class="fas fa-edit me-2"></i>Edit Referral
                </h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
            </div>
            <form method="POST">
                <div class="modal-body">
                    <input type="hidden" name="code_id" id="edit_code_id">

                    <div class="mb-3">
                        <label class="form-label" style="font-weight:600; color:var(--p-primary); font-size:0.9rem;">Assigned To</label>
                        <select name="assigned_to" class="form-select" id="edit_assigned_to">
                            <option value="user">👤 User</option>
                            <?php if($is_admin): ?>
                            <option value="admin">🛡️ Admin</option>
                            <?php endif; ?>
                            <?php if($is_owner): ?>
                            <option value="owner">👑 Owner</option>
                            <?php endif; ?>
                        </select>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" style="font-weight:600; color:var(--p-primary); font-size:0.9rem;">Status</label>
                        <select name="status" class="form-select" id="edit_status">
                            <option value="1">✅ Available</option>
                            <option value="0">❌ Used</option>
                        </select>
                    </div>
                </div>
                <div class="modal-footer border-0">
                    <button type="button" class="btn-back" style="padding:10px 20px;" data-bs-dismiss="modal">
                        <i class="fas fa-times"></i> Cancel
                    </button>
                    <button type="submit" name="edit_code" class="btn-ios" style="padding:11px 22px;">
                        <i class="fas fa-save"></i> Save Changes
                    </button>
                </div>
            </form>
        </div>
    </div>
</div>
<?php endif; ?>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>

<script>
// Sidebar logic
const sidebar = document.getElementById("sidebar");
const overlay = document.getElementById("overlay");
const menuIcon = document.getElementById('menuIcon');
let sidebarOpen = false;

function toggleSidebar() {
    sidebarOpen = !sidebarOpen;
    sidebar.classList.toggle('active', sidebarOpen);
    overlay.classList.toggle('active', sidebarOpen);
    if(menuIcon) menuIcon.style.transform = sidebarOpen ? 'rotate(90deg)' : '';
}

function closeSidebar() {
    sidebarOpen = false;
    sidebar.classList.remove('active');
    overlay.classList.remove('active');
    if(menuIcon) menuIcon.style.transform = '';
}

// Copy function with feedback
function copyCode(text, btn) {
    navigator.clipboard.writeText(text).then(() => {
        const originalHTML = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check"></i> Copied!';

        setTimeout(() => {
            btn.innerHTML = originalHTML;
        }, 2000);
    });
}

// Edit modal function
function openEditModal(id, assignedTo, status) {
    document.getElementById('edit_code_id').value = id;
    document.getElementById('edit_assigned_to').value = assignedTo;
    document.getElementById('edit_status').value = status;

    new bootstrap.Modal(document.getElementById('editModal')).show();
}

// Custom Delete Modal functions
function openDeleteModal(id, code) {
    document.getElementById('deleteId').value = id;
    document.getElementById('deleteCodeDisplay').textContent = code;
    document.getElementById('deleteModal').classList.add('active');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('active');
}

// Close delete modal when clicking outside
document.getElementById('deleteModal').addEventListener('click', function(e) {
    if (e.target === this) {
        closeDeleteModal();
    }
});
</script>

<script>
/* ✦ ONEBOX Ripple v2 */
(function(){
  document.addEventListener('click',function(e){
    var btn=e.target.closest('.btn-ios,.btn-save,.ob-btn,.glass-btn,.btn-glass,.btn-back,.btn-danger');
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
