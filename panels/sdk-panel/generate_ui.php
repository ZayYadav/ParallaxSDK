<?php
session_start();
include("conn.php");
include 'desktop_only.php';
include("panel_helper.php");

// ============================================
// CHECK IF USER IS LOGGED IN
// ============================================
if (!isset($_SESSION['username'])) {
    header("Location: login.php");
    exit();
}

$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$uid = intval($_SESSION['user_id'] ?? 0);
$unread_ann_count = $uid ? count_unread_announcements($conn, $uid) : 0;

// Role check
$urow_dash = mysqli_fetch_assoc(mysqli_query($conn,"SELECT role FROM users WHERE id=$uid"));
$role_dash = $urow_dash['role'] ?? 'user';

/* ================= HANDLE GENERATE ================= */
if ($_SERVER['REQUEST_METHOD'] === 'POST') {

    $package_lock = isset($_POST['package_lock']) ? 1 : 0;
    $package      = $package_lock ? trim($_POST['package_name'] ?? '') : '';
    $key_type     = $_POST['key_type'] ?? 'auto';
    $custom_key   = trim($_POST['custom_key'] ?? '');
    $max_devices  = max(1, min(100, (int) ($_POST['max_devices'] ?? 1)));

    // Validate: if package lock ON, package name is required
    if ($package_lock && preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $package) !== 1) {
        $_SESSION['gen_error'] = 'Enter a valid Android package name when Package Lock is enabled.';
        header('Location: generate_ui.php');
        exit();
    }

    // Handle expiry based on key type
    if ($key_type === 'custom' && !empty($_POST['custom_date'])) {
        $expiry = trim((string) $_POST['custom_date']);
        $days   = 'custom';
    } else {
        $days = max(1, min(3650, intval($_POST['expiry_duration'] ?? 30)));
        $expiry = gmdate('Y-m-d', time() + ($days * 86400));
    }

    $expiryDate = DateTimeImmutable::createFromFormat('!Y-m-d', $expiry, new DateTimeZone('UTC'));
    if ($expiryDate && $expiryDate->format('Y-m-d') === $expiry && $expiryDate >= new DateTimeImmutable('today', new DateTimeZone('UTC'))) {
        // Key prefix from settings
        $prefix = strtoupper(preg_replace('/[^A-Z0-9]/i', '', $P['key_prefix'] ?? 'ONEBOX'));
        if (empty($prefix)) $prefix = 'ONEBOX';

        // Generate or use custom key
        if ($key_type === 'custom' && !empty($custom_key)) {
            $license = strtoupper($custom_key);
            if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $license) !== 1) {
                $_SESSION['gen_error'] = 'Custom keys may contain only letters, numbers, underscore and hyphen.';
                header('Location: generate_ui.php');
                exit();
            }
        } else {
            $license = $prefix . '-' . strtoupper(bin2hex(random_bytes(12)));
        }

        // package_name = NULL when lock disabled
        $pkg_value = $package_lock ? $package : null;

        $stmt = mysqli_prepare(
            $conn,
            "INSERT INTO licenses
                (license_key, expiry_date, status, package_name, package_lock, max_devices, generated_by)
             VALUES (?, ?, 1, ?, ?, ?, ?)"
        );
        mysqli_stmt_bind_param($stmt, 'sssiis', $license, $expiry, $pkg_value, $package_lock, $max_devices, $current_user);
        if (!mysqli_stmt_execute($stmt)) {
            $_SESSION['gen_error'] = mysqli_errno($conn) === 1062
                ? 'That license key already exists.'
                : 'License could not be created.';
            header('Location: generate_ui.php');
            exit();
        }

        $_SESSION['success_license'] = [
            'package'      => $package_lock ? $package : '— Universal Key —',
            'package_lock' => $package_lock,
            'license'      => $license,
            'expiry'       => $expiry,
            'days'         => $days,
            'max_devices'  => $max_devices,
            'key_type'     => $key_type,
            'status'       => 'ACTIVE'
        ];

        header('Location: generate_ui.php');
        exit();
    } else {
        $_SESSION['gen_error'] = 'Choose a valid future expiry date.';
        header('Location: generate_ui.php');
        exit();
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Generate License - <?= htmlspecialchars($P['dashboard_title'] ?? 'Panel') ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>

*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

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

/* ── MAIN & FORM ── */
.main {
    position:relative; z-index:1;
    padding: 100px 16px 40px;
    max-width:1100px; margin:0 auto;
    transition: padding-left .32s;
}
@media(min-width:769px) { .main { padding-left:24px; } }

.form-card {
    width: 100%;
    max-width: 550px;
    margin: 0 auto;
    padding: 30px;
    animation: cardIn 0.5s cubic-bezier(.34,1.2,.64,1) both;
}
@keyframes cardIn {
    from { opacity:0; transform:translateY(20px) scale(.97); }
    to   { opacity:1; transform:none; }
}

.page-title {
    font-size:1.6rem; font-weight:800; letter-spacing:.08em;
    text-transform:uppercase;
    background: linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%);
    background-size:200% auto;
    -webkit-background-clip:text; -webkit-text-fill-color:transparent;
    background-clip:text;
    animation: shimmer 4s linear infinite;
    margin-bottom:24px; text-align:center;
}
@keyframes shimmer { to { background-position:200% center; } }

/* ── FORM ELEMENTS ── */
.form-control, .form-select, .form-date {
    background: rgba(0,0,0,.28) !important;
    border: 1px solid rgba(255,255,255,.1) !important;
    color: #fff !important;
    border-radius: 12px !important;
    padding: 14px !important;
    font-family: 'Montserrat', sans-serif;
    transition: border-color .22s ease, box-shadow .22s ease;
    width: 100%;
}
.form-control:focus, .form-select:focus, .form-date:focus {
    background: rgba(0,0,0,.45) !important;
    border-color: var(--p-primary) !important;
    box-shadow: 0 0 0 3px rgba(201,168,76,.2), 0 0 16px rgba(201,168,76,.15) !important;
    outline: none !important;
}
.form-control::placeholder { color:rgba(255,255,255,.35) !important; }
.form-select option { background:#1e2a45; color:#fff; }
.form-date::-webkit-calendar-picker-indicator { filter:invert(1); cursor:pointer; }
label { margin-bottom: 8px; font-weight: 600; font-size: 0.9rem; }

/* ── CUSTOM UI FOR GENERATE ── */
.toggle-container {
    display: flex; align-items: center; justify-content: space-between;
    background: rgba(255,255,255,.05); border-radius: 50px;
    padding: 5px; margin: 20px 0; border: 1px solid rgba(255,255,255,.1);
}
.toggle-option {
    flex: 1; text-align: center; padding: 12px; border-radius: 50px;
    cursor: pointer; transition: all 0.3s ease;
    font-size: 0.9rem; font-weight: 600; color: rgba(255,255,255,0.6);
}
.toggle-option.active {
    background: linear-gradient(135deg, var(--p-primary), var(--p-accent));
    color: #fff; box-shadow: 0 4px 15px rgba(201,168,76,0.3);
}

.custom-key-container, .custom-date-container { display: none; }

/* ── PACKAGE LOCK TOGGLE ── */
.pkg-lock-bar {
    display:flex; align-items:center; gap:14px;
    background:rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.09);
    border-radius:14px; padding:14px 16px; margin-bottom:18px;
    transition: border-color .25s, background .25s;
}
.pkg-lock-bar.locked {
    background:rgba(201,168,76,.07); border-color:rgba(201,168,76,.35);
}
.pkg-lock-bar.unlocked {
    background:rgba(79,142,247,.07); border-color:rgba(79,142,247,.35);
}
.toggle-switch {
    position:relative; width:48px; height:26px; flex-shrink:0; cursor:pointer;
}
.toggle-switch input { opacity:0; width:0; height:0; position:absolute; }
.toggle-track {
    position:absolute; inset:0; border-radius:30px;
    background:rgba(79,142,247,.35);
    transition: background .3s;
}
.toggle-track::after {
    content:''; position:absolute; left:3px; top:3px;
    width:20px; height:20px; border-radius:50%; background:#fff;
    transition: transform .3s cubic-bezier(.34,1.5,.64,1);
    box-shadow:0 2px 6px rgba(0,0,0,.3);
}
.toggle-switch input:checked ~ .toggle-track {
    background: linear-gradient(135deg, var(--p-primary), var(--p-accent));
}
.toggle-switch input:checked ~ .toggle-track::after { transform:translateX(22px); }
.pkg-lock-info { flex:1; }
.pkg-lock-label { font-size:.88rem; font-weight:700; margin-bottom:2px; }
.pkg-lock-desc  { font-size:.72rem; color:rgba(255,255,255,.45); }
.pkg-lock-icon  { font-size:1.4rem; transition:color .3s; }
.locked   .pkg-lock-icon { color:var(--p-primary); }
.unlocked .pkg-lock-icon { color:#4F8EF7; }
.custom-key-container.active, .custom-date-container.active {
    display: block; animation: slideDown 0.3s ease;
}
.duration-container.hidden { display: none; }

@keyframes slideDown {
    from { opacity:0; transform:translateY(-10px); }
    to { opacity:1; transform:translateY(0); }
}

.auto-key-preview, .preview-date {
    background: rgba(255,255,255,.03); border: 1px dashed var(--p-primary);
    border-radius: 14px; padding: 20px; margin: 15px 0; text-align: center;
}
.masked-key {
    color: var(--p-primary); font-weight: 700; font-size: 1.3rem;
    letter-spacing: 3px; font-family: monospace; margin: 8px 0;
}
.format-text { color:rgba(255,255,255,.5); font-size:0.8rem; }
.preview-date strong { color: var(--p-primary); font-size: 1.2rem; font-weight: 700; }

/* ── BUTTONS v2 ── */
.btn-ios {
    position:relative; overflow:hidden;
    display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:14px 28px; border:none; border-radius:14px;
    font-family:'Montserrat',sans-serif; font-size:.95rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer;
    background: linear-gradient(135deg, var(--p-primary) 0%, var(--p-accent) 50%, var(--p-primary) 100%);
    background-size:200% 200%;
    box-shadow:0 4px 20px rgba(201,168,76,0.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s ease;
    text-decoration:none; width:auto;
}
.btn-ios::before {
    content:''; position:absolute; inset:0;
    background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);
    transform:translateX(-100%); transition:transform .55s ease;
}
.btn-ios:hover::before { transform:translateX(100%); }
.btn-ios:hover {
    transform:translateY(-3px) scale(1.02);
    box-shadow:0 12px 32px rgba(201,168,76,.55); color:#fff;
}
.btn-ios:active { transform:translateY(0) scale(.98); }

.btn-back {
    position:relative; display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:11px 26px; background:rgba(255,255,255,.07); border:1px solid rgba(255,255,255,.16); border-radius:50px;
    color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600; font-family:'Montserrat',sans-serif; cursor:pointer;
    transition:all .25s cubic-bezier(.34,1.5,.64,1); backdrop-filter:blur(10px);
}
.btn-back:hover {
    background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3); transform:translateY(-3px);
    box-shadow:0 10px 24px rgba(0,0,0,.35); color:#fff;
}

/* ── SUCCESS OVERLAY & TOAST ── */
.success-overlay {
    position:fixed; inset:0; background:rgba(0,0,0,.65);
    backdrop-filter:blur(14px); -webkit-backdrop-filter:blur(14px);
    display:none; align-items:center; justify-content:center; z-index:2000;
}
.success-overlay.active { display:flex; }
.success-card {
    width:90%; max-width:380px; padding:30px; text-align:center;
    animation:popupIn .45s cubic-bezier(.2,1.4,.4,1);
}
@keyframes popupIn {
    from{transform:scale(.85);opacity:0} to{transform:scale(1);opacity:1}
}
.success-row {
    display:flex; justify-content:space-between; padding:12px 0;
    border-bottom:1px solid rgba(255,255,255,.1); font-size: 0.9rem;
}
.success-row span { color: rgba(255,255,255,0.6); }

#toast {
    position:fixed; bottom:40px; left:50%; transform:translateX(-50%) translateY(20px);
    background:linear-gradient(135deg,#10b981,#059669);
    backdrop-filter:blur(10px); color:#fff; font-weight:600;
    padding:12px 24px; border-radius:50px; opacity:0; transition:.35s; z-index:3000;
    box-shadow:0 10px 25px rgba(16,185,129,0.3); pointer-events: none;
}
#toast.show { opacity:1; transform:translateX(-50%) translateY(0); }

/* ── WATERMARK ── */
.watermark {
    position:fixed; bottom:12px; left:0; right:0; text-align:center;
    font-size:.6rem; letter-spacing:.15em; text-transform:uppercase;
    color:rgba(255,255,255,0.07); z-index:1; pointer-events:none;
}

::-webkit-scrollbar { width:6px; }
::-webkit-scrollbar-track { background:rgba(255,255,255,0.03); border-radius:10px; }
::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:10px; }
::-webkit-scrollbar-thumb:hover { background:rgba(201,168,76,0.55); }
</style>
</head>
<body>

<div class="bg-orbs">
    <div class="orb orb1"></div>
    <div class="orb orb2"></div>
    <div class="orb orb3"></div>
</div>

<div id="overlay" onclick="closeSidebar()"></div>

<header>
    <div class="header-left">
        <button class="menu-btn" onclick="toggleSidebar()" aria-label="Menu">
            <i class="fas fa-bars" id="menuIcon"></i>
        </button>
        <span class="header-title"><i class="fas fa-magic me-2" style="font-size:.85rem;"></i>Generate</span>
    </div>
    <div class="user-badge">
        <i class="fas fa-user-circle"></i>
        <span><?= htmlspecialchars($current_user) ?></span>
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
    <a class="nav-btn active-page" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>
    <?php if (in_array($role_dash, ['owner','admin'], true)): ?><a class="nav-btn" href="security_dashboard.php"><i class="fas fa-shield-alt"></i> API Security</a><?php endif; ?>

    <div class="sidebar-nav-label">Management</div>
    <a class="nav-btn" href="manage_users.php"><i class="fas fa-circle-user"></i> Manage Users</a>
    <a class="nav-btn" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn" href="announcements.php" style="position:relative;">
        <i class="fas fa-bullhorn"></i> Announcements
        <?php if ($unread_ann_count > 0): ?>
        <span class="nav-badge"><?= min($unread_ann_count,9) ?></span>
        <?php endif; ?>
    </a>

    <div class="sidebar-nav-label">Config</div>
    <?php if (in_array($role_dash, ['owner','admin'])): ?>
    <a class="nav-btn" href="panel_customizer.php"><i class="fas fa-paint-brush"></i> Customizer</a>
    <?php endif; ?>
    <a class="nav-btn" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>

<div class="main">
    <div class="page-title">Generate License</div>

    <div class="glass form-card">

        <?php if(!empty($_SESSION['gen_error'])): ?>
        <div style="background:rgba(239,68,68,.15);border:1px solid rgba(239,68,68,.35);border-radius:12px;padding:12px 16px;margin-bottom:18px;font-size:.85rem;color:#fca5a5;">
            <i class="fas fa-exclamation-circle me-2"></i><?=htmlspecialchars($_SESSION['gen_error'])?>
        </div>
        <?php unset($_SESSION['gen_error']); endif; ?>

        <form method="POST" id="licenseForm">

            <!-- ── PACKAGE LOCK TOGGLE ── -->
            <div class="pkg-lock-bar locked" id="pkgLockBar">
                <label class="toggle-switch">
                    <input type="checkbox" name="package_lock" id="pkgLockToggle" value="1" checked onchange="onLockToggle(this)">
                    <span class="toggle-track"></span>
                </label>
                <div class="pkg-lock-info">
                    <div class="pkg-lock-label" id="lockLabel">
                        <i class="fas fa-lock me-1"></i> Package Lock <span style="font-size:.68rem;padding:2px 8px;border-radius:20px;background:rgba(201,168,76,.18);color:var(--p-primary);margin-left:6px;">ENABLED</span>
                    </div>
                    <div class="pkg-lock-desc" id="lockDesc">Key works only for the specified package name</div>
                </div>
                <i class="fas fa-lock pkg-lock-icon" id="lockIcon"></i>
            </div>

            <!-- ── PACKAGE NAME FIELD (shown when locked) ── -->
            <div class="mb-3" id="packageNameWrap">
                <label><i class="fas fa-box me-2" style="color:var(--p-primary);"></i>Package Name</label>
                <input name="package_name" id="packageNameInput" class="form-control" placeholder="e.g., com.example.app">
                <small style="color:rgba(255,255,255,.4);font-size:.72rem;margin-top:4px;display:block;"><i class="fas fa-info-circle me-1"></i>This key will ONLY work for this package</small>
            </div>

            <div class="toggle-container">
                <div class="toggle-option active" id="autoToggle" onclick="setKeyType('auto')">
                    <i class="fas fa-random"></i> Auto
                </div>
                <div class="toggle-option" id="customToggle" onclick="setKeyType('custom')">
                    <i class="fas fa-pencil-alt"></i> Custom
                </div>
            </div>

            <input type="hidden" name="key_type" id="keyType" value="auto">

            <div class="auto-key-preview" id="autoPreview">
                <i class="fas fa-key" style="color:var(--p-primary); font-size:1.2rem;"></i>
                <div class="masked-key" id="maskedKey">ONEBOX-XXXX-XXXX</div>
                <div class="format-text">
                    <i class="fas fa-info-circle"></i> Format: ONEBOX-XXXX-XXX
                </div>
            </div>

            <div class="custom-key-container" id="customKeyContainer">
                <label><i class="fas fa-key me-2" style="color:var(--p-primary);"></i>Custom Key</label>
                <input type="text" name="custom_key" class="form-control" placeholder="Enter custom license key...">
            </div>

            <div class="mb-3">
                <label><i class="fas fa-mobile-alt me-2" style="color:var(--p-primary);"></i>Maximum Devices</label>
                <input type="number" name="max_devices" class="form-control" min="1" max="100" value="1" required>
                <small style="color:rgba(255,255,255,.4);font-size:.72rem;margin-top:4px;display:block;">The API rejects additional devices after this limit.</small>
            </div>

            <div class="duration-container" id="durationContainer">
                <label class="mt-3"><i class="fas fa-clock me-2" style="color:var(--p-primary);"></i>Expiry Duration</label>
                <select name="expiry_duration" id="expiryDuration" class="form-select mt-2" onchange="updateAutoPreview()">
                    <option value="1">1 Day</option>
                    <option value="3">3 Days</option>
                    <option value="7">7 Days</option>
                    <option value="30" selected>30 Days</option>
                    <option value="60">60 Days</option>
                    <option value="90">90 Days</option>
                    <option value="180">180 Days</option>
                    <option value="365">365 Days (1 Year)</option>
                </select>
                <div class="preview-date" id="autoPreviewDate">
                    <i class="fas fa-calendar-check" style="color:var(--p-primary);"></i>
                    Expires: <strong id="autoExpiryPreview"><?=date('Y-m-d', strtotime('+30 days'))?></strong>
                </div>
            </div>

            <div class="custom-date-container" id="customDateContainer">
                <label class="mt-3"><i class="fas fa-calendar-alt me-2" style="color:var(--p-primary);"></i>Expiry Date</label>
                <input type="date" name="custom_date" id="customDate" class="form-date mt-2" min="<?=date('Y-m-d')?>" value="<?=date('Y-m-d', strtotime('+30 days'))?>">
            </div>

            <div class="d-grid mt-4">
                <button class="btn-ios" name="generate">
                    <i class="fas fa-magic me-2"></i>Generate Now
                </button>
            </div>
        </form>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text'] ?? 'ONEBOX PANEL') ?></div>

<?php if(isset($_SESSION['success_license'])):
$s=$_SESSION['success_license']; ?>
<div class="success-overlay active" id="successModal">
    <div class="success-card glass">
        <i class="fas fa-check-circle fa-3x mb-3" style="color:#10b981;"></i>
        <h4 class="mb-4" style="font-weight:700;">License Generated</h4>

        <div class="success-row"><span>License</span><strong id="copyKey" style="color:var(--p-primary);"><?=htmlspecialchars($s['license'])?></strong></div>
        <div class="success-row"><span>Package Lock</span>
            <?php if($s['package_lock']): ?>
                <strong style="color:var(--p-primary);"><i class="fas fa-lock me-1"></i>Locked</strong>
            <?php else: ?>
                <strong style="color:#4ade80;"><i class="fas fa-globe me-1"></i>Universal</strong>
            <?php endif; ?>
        </div>
        <?php if($s['package_lock']): ?>
        <div class="success-row"><span>Package</span><strong><?=htmlspecialchars($s['package'])?></strong></div>
        <?php endif; ?>
        <div class="success-row"><span>Expiry</span><strong><?=htmlspecialchars($s['expiry'])?></strong></div>
        <div class="success-row"><span>Duration</span><strong><?=($s['days'] == 'custom' ? 'Custom' : $s['days'].' Days')?></strong></div>
        <div class="success-row"><span>Status</span><strong style="color:#10b981;"><?=$s['status']?></strong></div>

        <div class="d-grid gap-3 mt-4">
            <button class="btn-ios" onclick="copyKey()">
                <i class="far fa-copy me-2"></i>Copy License
            </button>
            <button class="btn-ios" onclick="window.location.href='license_list.php'" style="background:linear-gradient(135deg,#4F8EF7,#3b6fd4);">
                <i class="fas fa-list me-2"></i>View in List
            </button>
            <button class="btn-back" style="width:100%; justify-content:center;" onclick="closeSuccess()">
                <i class="fas fa-times me-2"></i>Close
            </button>
        </div>
    </div>
</div>
<?php unset($_SESSION['success_license']); endif; ?>

<div id="toast">✅ License Copied!</div>

<script>
// Sidebar Toggle
const sidebar = document.getElementById('sidebar');
const overlay = document.getElementById('overlay');
const menuIcon = document.getElementById('menuIcon');
let sidebarOpen = false;

function toggleSidebar() {
    sidebarOpen = !sidebarOpen;
    sidebar.classList.toggle('active', sidebarOpen);
    overlay.classList.toggle('active', sidebarOpen);
    menuIcon.style.transform = sidebarOpen ? 'rotate(90deg)' : '';
}
function closeSidebar() {
    sidebarOpen = false;
    sidebar.classList.remove('active');
    overlay.classList.remove('active');
    menuIcon.style.transform = '';
}

/* KEY TYPE TOGGLE */
function setKeyType(type) {
    document.getElementById('keyType').value = type;

    const autoToggle = document.getElementById('autoToggle');
    const customToggle = document.getElementById('customToggle');
    const autoPreview = document.getElementById('autoPreview');
    const customContainer = document.getElementById('customKeyContainer');
    const durationContainer = document.getElementById('durationContainer');
    const customDateContainer = document.getElementById('customDateContainer');

    if (type === 'auto') {
        autoToggle.classList.add('active');
        customToggle.classList.remove('active');
        autoPreview.style.display = 'block';
        customContainer.classList.remove('active');
        durationContainer.classList.remove('hidden');
        customDateContainer.classList.remove('active');
    } else {
        autoToggle.classList.remove('active');
        customToggle.classList.add('active');
        autoPreview.style.display = 'none';
        customContainer.classList.add('active');
        durationContainer.classList.add('hidden');
        customDateContainer.classList.add('active');
    }
}

/* UPDATE AUTO PREVIEW DATE */
function updateAutoPreview() {
    const days = document.getElementById('expiryDuration').value;
    const today = new Date();
    const expiryDate = new Date(today);
    expiryDate.setDate(today.getDate() + parseInt(days));

    const year = expiryDate.getFullYear();
    const month = String(expiryDate.getMonth() + 1).padStart(2, '0');
    const day = String(expiryDate.getDate()).padStart(2, '0');

    document.getElementById('autoExpiryPreview').textContent = `${year}-${month}-${day}`;
}

/* AUTO PREVIEW - MASKED */
function refreshAutoPreview() {
    const maskedElement = document.getElementById('maskedKey');
    if(!maskedElement) return;
    const patterns = ['ONEBOX-XXXX-DRE', 'ONEBOX-XXXX-543', 'ONEBOX-XXXX-98J'];
    const randomIndex = Math.floor(Math.random() * patterns.length);
    maskedElement.textContent = patterns[randomIndex];
}
setInterval(refreshAutoPreview, 3000);

/* SUCCESS MODAL */
function closeSuccess() {
    document.getElementById("successModal").classList.remove("active");
}

/* COPY FUNCTION */
function copyKey() {
    const text = document.getElementById("copyKey").innerText;
    navigator.clipboard.writeText(text).then(() => {
        const toast = document.getElementById("toast");
        toast.classList.add("show");
        setTimeout(() => toast.classList.remove("show"), 2000);
    });
}

/* ── PACKAGE LOCK TOGGLE LOGIC ── */
function onLockToggle(cb) {
    const bar    = document.getElementById('pkgLockBar');
    const wrap   = document.getElementById('packageNameWrap');
    const label  = document.getElementById('lockLabel');
    const desc   = document.getElementById('lockDesc');
    const icon   = document.getElementById('lockIcon');
    const input  = document.getElementById('packageNameInput');

    if (cb.checked) {
        bar.className   = 'pkg-lock-bar locked';
        label.innerHTML = '<i class="fas fa-lock me-1"></i> Package Lock <span style="font-size:.68rem;padding:2px 8px;border-radius:20px;background:rgba(201,168,76,.18);color:var(--p-primary);margin-left:6px;">ENABLED</span>';
        desc.textContent = 'Key works only for the specified package name';
        icon.className  = 'fas fa-lock pkg-lock-icon';
        wrap.style.display = 'block';
        input.placeholder  = 'e.g., com.example.app';
    } else {
        bar.className   = 'pkg-lock-bar unlocked';
        label.innerHTML = '<i class="fas fa-globe me-1"></i> Package Lock <span style="font-size:.68rem;padding:2px 8px;border-radius:20px;background:rgba(79,142,247,.18);color:#4F8EF7;margin-left:6px;">DISABLED</span>';
        desc.textContent = 'Universal key — works with any package name';
        icon.className  = 'fas fa-globe pkg-lock-icon';
        wrap.style.display = 'none';
        input.value = '';
    }
}

// Initialize
window.onload = function() {
    updateAutoPreview();
    refreshAutoPreview();
    setKeyType('auto');
    // init lock bar to correct state
    const cb = document.getElementById('pkgLockToggle');
    if (cb) onLockToggle(cb);
};
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
