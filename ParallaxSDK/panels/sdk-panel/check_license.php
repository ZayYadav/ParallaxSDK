<?php
// Include database and helper functions (matching your dashboard logic)
include("conn.php");
include("panel_helper.php");

if (!isset($_SESSION['username'])) {
    header("Location: login.php");
    exit();
}

// ==========================================
// POST REQUEST HANDLER (API for Validation)
// ==========================================
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    header('Content-Type: application/json');

    $license_key = strtoupper(trim((string) ($_POST['user_key'] ?? '')));

    if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $license_key) !== 1) {
        echo json_encode(['status' => 'fail', 'reason' => 'License key missing']);
        exit;
    }

    $stmt = $conn->prepare('SELECT expiry_date, status FROM licenses WHERE license_key = ? LIMIT 1');
    $stmt->bind_param('s', $license_key);
    $stmt->execute();
    $license = $stmt->get_result()->fetch_assoc();
    $stmt->close();

    if (!$license) {
        echo json_encode(['status' => 'fail', 'reason' => 'License key invalid']);
        exit;
    }

    if ((int)$license['status'] !== 1) {
        echo json_encode(['status' => 'fail', 'reason' => 'License inactive']);
        exit;
    }

    $current_time = new DateTimeImmutable('now', new DateTimeZone('UTC'));
    $expiry_time = new DateTimeImmutable((string) $license['expiry_date'], new DateTimeZone('UTC'));

    if ($current_time > $expiry_time) {
        echo json_encode(['status' => 'fail', 'reason' => 'License expired']);
        exit;
    }

    echo json_encode([
        'status' => 'success',
        'expiry' => $license['expiry_date']
    ]);
    exit;
}

// ==========================================
// PAGE UI RENDERING
// ==========================================
$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$uid = intval($_SESSION['user_id'] ?? 0);
$unread_ann_count = $uid ? count_unread_announcements($conn, $uid) : 0;

// Role check
$urow_dash = mysqli_fetch_assoc(mysqli_query($conn,"SELECT role FROM users WHERE id=$uid"));
$role_dash = $urow_dash['role'] ?? 'user';
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>License Validation - <?= htmlspecialchars($P['dashboard_title'] ?? 'VIP Panel') ?></title>
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
.orb1 { width:600px; height:600px; top:-200px; left:-150px; background: radial-gradient(circle, rgba(201,168,76,0.12) 0%, transparent 70%); --dur:18s; --delay:0s; }
.orb2 { width:500px; height:500px; bottom:-150px; right:-100px; background: radial-gradient(circle, rgba(79,142,247,0.1) 0%, transparent 70%); --dur:22s; --delay:4s; }
.orb3 { width:400px; height:400px; top:40%; left:40%; background: radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 70%); --dur:26s; --delay:8s; }
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
    font-size:1.4rem; cursor:pointer; width:40px; height:40px; border-radius:10px;
    display:flex; align-items:center; justify-content:center;
    transition: all .25s cubic-bezier(.34,1.5,.64,1);
}
.menu-btn:hover { background:rgba(255,255,255,0.08); transform:scale(1.1) rotate(90deg); }
.header-title { font-size:1rem; font-weight:700; letter-spacing:.08em; color:var(--p-primary); text-transform:uppercase; }
.user-badge { display:flex; align-items:center; gap:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); border-radius:50px; padding:7px 16px; font-size:.82rem; font-weight:600; cursor:default; transition: all .25s; }
.user-badge:hover { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.3); }
.user-badge i { color:var(--p-primary); }

/* ── SIDEBAR ── */
#sidebar {
    position:fixed; top:62px; left:-268px; bottom:0; width:248px;
    background: rgba(6,10,20,0.97); backdrop-filter: blur(24px);
    border-right:1px solid rgba(255,255,255,0.07);
    z-index:999; overflow-y:auto; overflow-x:hidden;
    transition: left .32s cubic-bezier(.4,0,.2,1);
    padding:16px 12px 80px;
}
#sidebar.active { left:0; }
#overlay { position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:998; display:none; backdrop-filter:blur(4px); transition:opacity .3s; }
#overlay.active { display:block; }
.sidebar-logo-section { text-align:center; padding:12px 8px 20px; border-bottom:1px solid rgba(255,255,255,0.07); margin-bottom:14px; }
.sidebar-logo-ring { position:relative; width:80px; height:80px; margin:0 auto 12px; border-radius:50%; background: conic-gradient(from 0deg, var(--p-primary), var(--p-accent), var(--p-primary)); animation: spinRing 5s linear infinite; padding:3px; }
@keyframes spinRing { to { transform:rotate(360deg); } }
.sidebar-logo-inner { width:100%; height:100%; border-radius:50%; background: var(--p-bg1); overflow:hidden; display:flex; align-items:center; justify-content:center; }
.sidebar-logo-inner img { width:100%; height:100%; object-fit:cover; border-radius:50%; display:block; }
.sidebar-panel-name { font-size:.85rem; font-weight:700; color:var(--p-primary); margin-bottom:4px; }
.sidebar-user-chip { display:inline-flex; align-items:center; gap:5px; font-size:.68rem; font-weight:600; letter-spacing:.08em; color:rgba(255,255,255,0.5); background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.08); border-radius:30px; padding:3px 10px; }

.sidebar-nav-label { font-size:.62rem; font-weight:700; letter-spacing:.15em; color:rgba(255,255,255,0.25); text-transform:uppercase; padding:10px 12px 6px; margin-top:6px; }
.nav-btn {
    width:100%; padding:11px 14px; margin-bottom:4px;
    background:rgba(255,255,255,0.04); border:1px solid transparent;
    border-radius:13px; color:rgba(255,255,255,0.75);
    text-align:left; cursor:pointer;
    font-family:'Montserrat',sans-serif; font-size:.83rem; font-weight:600;
    display:flex; align-items:center; gap:10px;
    transition: all .22s cubic-bezier(.34,1.2,.64,1);
    position:relative; overflow:hidden; text-decoration:none;
}
.nav-btn::before { content:''; position:absolute; left:0; top:0; bottom:0; width:3px; background:var(--p-primary); border-radius:0 3px 3px 0; transform:scaleY(0); transition:transform .2s; }
.nav-btn:hover { background:rgba(255,255,255,0.09); border-color:rgba(255,255,255,0.1); color:#fff; transform:translateX(4px); }
.nav-btn:hover::before { transform:scaleY(1); }
.nav-btn.active-page { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.25); color:var(--p-primary); }
.nav-btn.active-page::before { transform:scaleY(1); }
.nav-btn i { width:18px; text-align:center; color:var(--p-primary); font-size:.9rem; opacity:.85; }
.nav-badge { margin-left:auto; background:#ef4444; color:#fff; border-radius:50%; width:18px; height:18px; font-size:.6rem; font-weight:700; display:flex; align-items:center; justify-content:center; }

/* ── MAIN CONTENT ── */
.main { position:relative; z-index:1; padding: 100px 16px 40px; max-width:800px; margin:0 auto; transition: padding-left .32s; }
.page-title { font-size:1.6rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; background: linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%); background-size:200% auto; -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; animation: shimmer 4s linear infinite; margin-bottom:24px; text-align:center; }
@keyframes shimmer { to { background-position:200% center; } }

/* ── FORM ELEMENTS ── */
.form-container { padding: 30px; }
.form-label { font-size: 0.85rem; font-weight: 600; color: rgba(255,255,255,0.7); margin-bottom: 8px; display: block; letter-spacing: 0.05em; }
.glass-input {
    width: 100%; padding: 14px 18px;
    background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
    border-radius: 12px; color: #fff; font-family: 'Montserrat', sans-serif;
    font-size: 0.95rem; outline: none; transition: all 0.3s;
    margin-bottom: 20px;
}
.glass-input:focus { background: rgba(255,255,255,0.08); border-color: var(--p-primary); box-shadow: 0 0 0 3px rgba(201,168,76,0.15); }
.btn-submit {
    width: 100%; padding: 14px;
    background: linear-gradient(135deg, var(--p-primary), var(--p-accent));
    color: #000; font-weight: 700; font-size: 1rem;
    border: none; border-radius: 12px; cursor: pointer;
    transition: transform 0.2s, box-shadow 0.2s;
    text-transform: uppercase; letter-spacing: 0.1em;
}
.btn-submit:hover { transform: translateY(-3px); box-shadow: 0 10px 25px rgba(201,168,76,0.4); }

/* ── RESULT BOX ── */
#result-box { display: none; margin-top: 25px; padding: 18px; border-radius: 12px; font-weight: 600; text-align: center; font-size: 0.95rem; animation: cardIn 0.4s ease forwards; }
.result-success { background: rgba(74, 222, 128, 0.15); border: 1px solid rgba(74, 222, 128, 0.3); color: #4ade80; }
.result-error { background: rgba(248, 113, 113, 0.15); border: 1px solid rgba(248, 113, 113, 0.3); color: #f87171; }
@keyframes cardIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }

@media(min-width:769px) { .main { padding-left:24px; } }

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
        <span class="header-title"><i class="fas fa-shield-check me-2" style="font-size:.85rem;"></i><?= htmlspecialchars($P['panel_name'] ?? 'Panel') ?></span>
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
                <img src="<?= htmlspecialchars($P['sidebar_logo_url'] ?? '') ?>" alt="Logo" onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='flex';">
                <div class="sidebar-logo-fallback" style="display:none;"><i class="fas fa-box-open"></i></div>
            </div>
        </div>
        <div class="sidebar-panel-name"><?= htmlspecialchars($P['panel_name'] ?? 'Panel') ?></div>
        <div class="sidebar-user-chip"><i class="fas fa-shield-alt" style="color:var(--p-primary)"></i> <?= htmlspecialchars($current_user) ?></div>
    </div>

    <div class="sidebar-nav-label">Main</div>
    <a class="nav-btn" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn active-page" href="license_check_ui.php"><i class="fas fa-check-double"></i> Check Licenses</a>
    <a class="nav-btn" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>

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
    <div class="page-title">Validate License</div>

    <div class="glass form-container">
        <p class="text-center mb-4" style="color: rgba(255,255,255,0.6); font-size: 0.9rem;">
            Submit a license key below to instantly check its validity, active status, and expiration date.
        </p>

        <form id="validateForm">
            <label for="user_key" class="form-label">ENTER LICENSE KEY</label>
            <input type="text" id="user_key" name="user_key" class="glass-input" placeholder="e.g. VIP-XXXX-XXXX-XXXX" required autocomplete="off">

            <button type="submit" class="btn-submit" id="submitBtn">
                <i class="fas fa-search me-2"></i> Validate Now
            </button>
        </form>

        <div id="result-box"></div>
    </div>
</div>

<script>
// Sidebar Toggle Logic
const sidebar = document.getElementById('sidebar');
const overlay = document.getElementById('overlay');

function toggleSidebar() {
    sidebar.classList.toggle('active');
    overlay.classList.toggle('active');
}
function closeSidebar() {
    sidebar.classList.remove('active');
    overlay.classList.remove('active');
}

// AJAX Form Submission
document.getElementById('validateForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const key = document.getElementById('user_key').value.trim();
    const resultBox = document.getElementById('result-box');
    const submitBtn = document.getElementById('submitBtn');
    const originalBtnHTML = submitBtn.innerHTML;

    if(!key) return;

    // Loading State
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i> Validating...';
    submitBtn.disabled = true;
    resultBox.style.display = 'none';

    fetch('', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: 'user_key=' + encodeURIComponent(key) + '&_csrf=' + encodeURIComponent(<?= json_encode(panel_csrf_token()) ?>)
    })
    .then(response => response.json())
    .then(data => {
        resultBox.style.display = 'block';
        if(data.status === 'success') {
            resultBox.className = 'result-success';
            resultBox.innerHTML = '<i class="fas fa-check-circle me-2"></i> <strong>Valid License!</strong><br><span style="font-size:0.85rem; color:rgba(255,255,255,0.7); margin-top:5px; display:inline-block;">Expires on: ' + data.expiry + '</span>';
        } else {
            resultBox.className = 'result-error';
            resultBox.innerHTML = '<i class="fas fa-times-circle me-2"></i> <strong>Validation Failed</strong><br><span style="font-size:0.85rem; color:rgba(255,255,255,0.7); margin-top:5px; display:inline-block;">Reason: ' + data.reason + '</span>';
        }
    })
    .catch(error => {
        console.error('Error:', error);
        resultBox.style.display = 'block';
        resultBox.className = 'result-error';
        resultBox.innerHTML = '<i class="fas fa-exclamation-triangle me-2"></i> <strong>Network Error</strong><br>Could not connect to the server.';
    })
    .finally(() => {
        // Reset Button
        submitBtn.innerHTML = originalBtnHTML;
        submitBtn.disabled = false;
    });
});
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
