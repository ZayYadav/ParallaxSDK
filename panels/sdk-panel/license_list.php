<?php
session_start();
include("conn.php");
include("panel_helper.php");

// ============================================
// CHECK LOGIN
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

/* ================= DELETE ================= */
if ($_SERVER['REQUEST_METHOD']==='POST' && isset($_POST['delete_id'])) {
    panel_require_roles($conn, ['owner', 'admin']);
    $id = (int)$_POST['delete_id'];
    $conn->begin_transaction();
    $keyStmt = $conn->prepare('SELECT license_key FROM licenses WHERE id = ? FOR UPDATE');
    $keyStmt->bind_param('i', $id); $keyStmt->execute();
    $keyRow = $keyStmt->get_result()->fetch_assoc(); $keyStmt->close();
    if ($keyRow) {
        $deviceDelete = $conn->prepare('DELETE FROM devices WHERE license_key = ?');
        $deviceDelete->bind_param('s', $keyRow['license_key']); $deviceDelete->execute(); $deviceDelete->close();
        $stmt = $conn->prepare('DELETE FROM licenses WHERE id = ?');
        $stmt->bind_param('i', $id); $stmt->execute(); $stmt->close();
    }
    $conn->commit();
    header("Location: license_list.php");
    exit;
}

/* ================= EDIT ================= */
if ($_SERVER['REQUEST_METHOD']==='POST' && isset($_POST['edit_id'])) {
    panel_require_roles($conn, ['owner', 'admin']);
    $pkg_lock  = isset($_POST['package_lock']) ? 1 : 0;
    $pkg_name  = $pkg_lock ? trim($_POST['package_name'] ?? '') : null;
    $max_devices = max(1, min(100, (int) ($_POST['max_devices'] ?? 1)));
    $edit_id = (int) ($_POST['edit_id'] ?? 0);
    $license_key = strtoupper(trim((string) ($_POST['license_key'] ?? '')));
    $expiry_date = trim((string) ($_POST['expiry_date'] ?? ''));
    $status = (int) ($_POST['status'] ?? 0);
    if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $license_key) !== 1
        || ($pkg_lock && preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', (string) $pkg_name) !== 1)
        || !in_array($status, [0, 1, 2], true)) {
        http_response_code(400);
        exit('INVALID_LICENSE_UPDATE');
    }
    $stmt = $conn->prepare(
        'UPDATE licenses
         SET license_key=?, package_name=?, expiry_date=?, status=?, package_lock=?, max_devices=?
         WHERE id=?'
    );
    $stmt->bind_param(
        'sssiiii',
        $license_key,
        $pkg_name,
        $expiry_date,
        $status,
        $pkg_lock,
        $max_devices,
        $edit_id
    );
    $stmt->execute();
    header('Location: license_list.php');
    exit;
}

/* ================= FETCH LICENSES ================= */
$result = $conn->query("SELECT * FROM licenses ORDER BY id DESC");
$license_ids = []; // collect IDs for JS localStorage loop

// Function to get connected devices count for a package
function getConnectedDevices($conn, $license_key) {
    $tableCheck = $conn->query("SHOW TABLES LIKE 'devices'");
    if($tableCheck->num_rows == 0) {
        return 0;
    }

    $stmt = $conn->prepare("SELECT COUNT(*) as count FROM devices WHERE license_key = ? AND status = 'connected'");
    $stmt->bind_param("s", $license_key);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res->fetch_assoc();
    return $row['count'] ?? 0;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>License Keys - <?= htmlspecialchars($P['dashboard_title'] ?? 'Panel') ?></title>

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

/* ── MAIN ── */
.main {
    position:relative; z-index:1;
    padding: 90px 24px 24px;
    max-width: 1400px;
    margin: 0 auto;
    transition: padding-left .32s;
}
@media(min-width:769px) { .main { padding-left:24px; } }

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

/* ── GRID & CARDS ── */
.grid {
    display:grid;
    grid-template-columns:repeat(auto-fit,minmax(350px,1fr));
    gap:20px;
    margin-top:20px;
}

.card {
    padding: 20px;
    position: relative;
    transition: all 0.3s cubic-bezier(.34,1.5,.64,1);
    border-radius: 22px;
    overflow: hidden;
}
.card:hover {
    transform: translateY(-5px) scale(1.01);
    box-shadow: 0 15px 45px rgba(0,0,0,.6);
    border-color: rgba(201, 168, 76, 0.4);
}

/* ── APP NAME SECTION ── */
.app-name-section {
    display:flex; align-items:center; justify-content:space-between;
    margin-bottom:15px;
    background:rgba(255,255,255,0.03);
    padding:12px 15px;
    border-radius:16px;
    border:1px solid rgba(255,255,255,0.08);
}
.app-name-left { display:flex; align-items:center; gap:12px; }
.app-name-icon {
    width:40px; height:40px;
    background:rgba(201,168,76,0.15); border-radius:12px;
    display:flex; align-items:center; justify-content:center;
    color:var(--p-primary); font-size:1.3rem;
}
.app-name-text { display:flex; flex-direction:column; }
.app-name-label {
    font-size:0.7rem; color:var(--p-primary);
    text-transform:uppercase; letter-spacing:1px; opacity:0.8;
}
.app-name-value { font-size:1.2rem; font-weight:700; color:white; line-height:1.2; }

.app-name-edit {
    background:rgba(201,168,76,0.1); border:1px solid rgba(201,168,76,0.3);
    color:var(--p-primary); border-radius:10px; padding:6px 12px;
    font-size:11px; cursor:pointer; transition:all 0.3s ease;
    display:flex; align-items:center; gap:5px; font-weight:600;
}
.app-name-edit:hover { background:rgba(201,168,76,0.3); transform: scale(1.05); color:#fff; }

.name-edit-input {
    display:none; margin-bottom:15px;
    background:rgba(0,0,0,0.3); padding:15px;
    border-radius:16px; border: 1px solid rgba(255,255,255,0.1);
}
.name-edit-input.active { display:block; animation: slideDown 0.3s ease; }
@keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
.name-edit-field {
    background:rgba(0,0,0,0.5); border:1px solid var(--p-primary);
    color:white; border-radius:12px; padding:10px 12px;
    width:100%; margin-bottom:10px; font-family:'Montserrat',sans-serif;
}
.name-edit-field:focus { outline:none; box-shadow:0 0 15px rgba(201,168,76,0.3); }

/* ── CARD DETAILS & RGB ANIMATION ── */
@keyframes rgb-animation {
    0% {color:#ff3333;text-shadow:0 0 10px #ff0000;}
    25% {color:#ff9933;text-shadow:0 0 10px #ff8800;}
    50% {color:#33ff33;text-shadow:0 0 10px #00ff00;}
    75% {color:#33ccff;text-shadow:0 0 10px #0099ff;}
    100% {color:#ff3333;text-shadow:0 0 10px #ff0000;}
}

.package-rgb, .expiry-rgb {
    font-size:0.95rem; margin-bottom:12px;
    display:flex; align-items:center; gap:8px;
    padding:5px 0 5px 8px; color: rgba(255,255,255,0.9);
}
.package-rgb i, .expiry-rgb i { color:var(--p-primary); font-size:0.9rem; }
.package-rgb span, .expiry-rgb span { animation:rgb-animation 6s linear infinite; font-weight:600; }
.package-rgb { border-left:3px solid var(--p-primary); }

.license-key-rgb {
    background:rgba(0,0,0,.3); border:1px dashed rgba(201,168,76,0.4);
    padding:12px; border-radius:14px; font-family:'Courier New',monospace;
    margin:15px 0; display:flex; align-items:center; gap:10px; word-break:break-all;
}
.license-key-rgb i.fa-key { color:var(--p-primary); }
.license-key-text {
    flex:1; font-weight:700; font-size:1rem; transition: filter 0.2s ease;
    animation:rgb-animation 6s linear infinite;
}
.license-key-text.blurred { filter: blur(6px); user-select: none; }
.toggle-visibility {
    background: none; border: none; color: var(--p-primary);
    cursor: pointer; padding: 0 5px; font-size: 1.1rem;
    transition: color 0.2s, transform 0.2s; display: flex;
    align-items: center; justify-content: center;
}
.toggle-visibility:hover { color: #fff; transform: scale(1.1); }

/* ── ACTION BAR ── */
.action-bar {
    display:flex; align-items:center; justify-content:space-between;
    flex-wrap:wrap; gap:10px; margin-top:15px; padding-top:15px;
    border-top:1px solid rgba(255,255,255,0.1);
}
.glass-buttons { display:flex; gap:8px; flex-wrap:wrap; }

/* ── STATUS BADGE ── */
.glass-status {
    padding:8px 18px; border-radius:50px; font-size:12px;
    font-weight:700; letter-spacing:0.5em; display:inline-flex;
    align-items:center; gap:8px; backdrop-filter:blur(10px);
    -webkit-backdrop-filter:blur(10px); box-shadow:0 4px 12px rgba(0,0,0,0.2);
    background: rgba(255,255,255,0.05); text-transform: uppercase; letter-spacing: 0.05em;
}
.status-active-glass { color:#4ade80; border:1px solid rgba(52,211,153,0.3); }
.status-block-glass { color:#f87171; border:1px solid rgba(248,113,113,0.3); }
.status-pause-glass { color:#fde047; border:1px solid rgba(250,204,21,0.3); }

/* Package Lock badge */
.pkg-lock-badge {
    display:inline-flex; align-items:center; gap:5px;
    font-size:.68rem; font-weight:700; letter-spacing:.06em;
    padding:4px 10px; border-radius:20px;
}
.pkg-locked   { color:var(--p-primary); background:rgba(201,168,76,.12); border:1px solid rgba(201,168,76,.3); }
.pkg-universal{ color:#4ade80;          background:rgba(74,222,128,.10); border:1px solid rgba(74,222,128,.3); }

/* pkg lock toggle in edit modal */
.edit-pkg-lock-bar {
    display:flex; align-items:center; gap:12px;
    background:rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.09);
    border-radius:12px; padding:12px 14px; margin-bottom:12px;
    transition: border-color .25s, background .25s;
}
.edit-pkg-lock-bar.locked   { background:rgba(201,168,76,.07); border-color:rgba(201,168,76,.3); }
.edit-pkg-lock-bar.unlocked { background:rgba(79,142,247,.07); border-color:rgba(79,142,247,.3); }
.edit-toggle-switch { position:relative; width:42px; height:22px; flex-shrink:0; cursor:pointer; }
.edit-toggle-switch input { opacity:0; width:0; height:0; position:absolute; }
.edit-toggle-track {
    position:absolute; inset:0; border-radius:30px;
    background:rgba(79,142,247,.35); transition:background .3s;
}
.edit-toggle-track::after {
    content:''; position:absolute; left:3px; top:3px;
    width:16px; height:16px; border-radius:50%; background:#fff;
    transition:transform .3s cubic-bezier(.34,1.5,.64,1);
    box-shadow:0 2px 4px rgba(0,0,0,.3);
}
.edit-toggle-switch input:checked ~ .edit-toggle-track {
    background:linear-gradient(135deg, var(--p-primary), var(--p-accent));
}
.edit-toggle-switch input:checked ~ .edit-toggle-track::after { transform:translateX(20px); }

/* ── MODALS (OVERLAYS) ── */
.overlay-modal {
    position:fixed; inset:0; background:rgba(0,0,0,.7);
    backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px);
    display:none; align-items:center; justify-content:center; z-index:4000;
}
.overlay-modal.active { display:flex; }
.modal-box {
    width:90%; padding:25px; text-align:center;
    animation:pop 0.3s cubic-bezier(.34,1.5,.64,1);
}
@keyframes pop { from{transform:scale(0.9);opacity:0} to{transform:scale(1);opacity:1} }

/* USAGE MODAL SPECIFIC */
.usage-box { max-width:500px; }
.usage-stats {
    display:flex; flex-direction:column; gap:12px; margin:20px 0;
    background:rgba(255,255,255,0.03); border-radius:16px; padding:15px;
}
.usage-stat-item {
    display:flex; align-items:center; justify-content:space-between;
    padding:12px 15px; background:rgba(255,255,255,0.03);
    border-radius:12px; border:1px solid rgba(255,255,255,0.08);
}
.usage-stat-label { font-size:0.9rem; color:rgba(255,255,255,0.6); display:flex; align-items:center; gap:8px; }
.usage-stat-label i { color:var(--p-primary); width:20px; }
.usage-stat-value { font-size:1.1rem; font-weight:700; color:var(--p-primary); }

.usage-device-list {
    max-height:300px; overflow-y:auto; margin-top:20px;
    text-align:left; padding:5px; border-top:1px solid rgba(255,255,255,0.1); padding-top:15px;
}
.usage-device-item {
    padding:15px; border-bottom:1px solid rgba(255,255,255,0.05);
    font-size:0.95rem; display:flex; align-items:flex-start; gap:12px;
    transition:all 0.3s ease; background:rgba(255,255,255,0.02);
    border-radius:10px; margin-bottom:8px;
}
.usage-device-item:hover { background:rgba(255,255,255,0.05); transform:translateX(5px); }
.usage-device-item i.fa-circle { color:#4ade80; font-size:0.7rem; margin-top:6px; animation:pulse 2s infinite; }
.device-info { flex:1; }
.device-id { color:var(--p-primary); font-weight:600; font-size:1rem; margin-bottom:5px; }
.device-meta { display:flex; gap:20px; font-size:0.85rem; color:rgba(255,255,255,0.6); flex-wrap:wrap;}
.device-meta span { display:flex; align-items:center; gap:5px; }
.device-meta i { color:var(--p-primary); width:16px; }

/* DELETE MODAL SPECIFIC */
.delete-box { max-width:340px; }

/* ── BOOTSTRAP OVERRIDES (EDIT MODAL) ── */
.modal-content {
    background:rgba(10,16,34,.97) !important;
    border:1px solid rgba(255,255,255,0.1) !important;
    border-radius:20px !important; backdrop-filter:blur(24px) !important;
}
.modal-header { border-bottom:1px solid rgba(255,255,255,.08) !important; }
.modal-footer { border-top:1px solid rgba(255,255,255,.08) !important; gap:10px; }
.modal-title { font-weight:700; color:var(--p-primary); }

.form-control, .form-select {
    background:rgba(0,0,0,.28) !important; border:1px solid rgba(255,255,255,.1) !important;
    color:#fff !important; border-radius:12px !important; padding:11px 14px !important;
    font-family:'Montserrat',sans-serif; transition:border-color .22s ease,box-shadow .22s ease;
}
.form-control:focus, .form-select:focus {
    background:rgba(0,0,0,.45) !important; border-color:var(--p-primary) !important;
    box-shadow:0 0 0 3px rgba(201,168,76,.2),0 0 16px rgba(201,168,76,.15) !important;
    color:#fff !important; outline:none !important;
}
.form-select option { background:#1e2a45; color:#fff; }

/* ── BUTTONS v2 ── */
.btn-ios, .btn-save, .ob-btn {
    position:relative; overflow:hidden; display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:13px 28px; border:none; border-radius:14px; font-family:'Montserrat',sans-serif; font-size:.9rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer;
    background: linear-gradient(135deg, var(--p-primary) 0%, var(--p-accent) 50%, var(--p-primary) 100%);
    background-size:200% 200%; box-shadow:0 4px 20px rgba(201,168,76,0.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s ease; text-decoration:none; width:auto;
}
.btn-ios::before, .btn-save::before {
    content:''; position:absolute; inset:0; background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);
    transform:translateX(-100%); transition:transform .55s ease;
}
.btn-ios:hover::before, .btn-save:hover::before { transform:translateX(100%); }
.btn-ios:hover, .btn-save:hover { transform:translateY(-3px) scale(1.02); box-shadow:0 12px 32px rgba(201,168,76,.55); color:#fff; }
.btn-ios:active, .btn-save:active { transform:translateY(0) scale(.98); }

.glass-btn {
    position:relative; padding:7px 15px; border-radius:50px; font-size:.78rem; font-weight:700; letter-spacing:.03em;
    border:1px solid rgba(255,255,255,.18); display:inline-flex; align-items:center; gap:6px;
    transition:transform .22s cubic-bezier(.34,1.5,.64,1),box-shadow .22s ease,background .22s ease;
    cursor:pointer; backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px);
    box-shadow:0 2px 10px rgba(0,0,0,.22); background:rgba(255,255,255,.05); text-decoration:none; font-family:'Montserrat',sans-serif;
}
.glass-btn:hover { transform:translateY(-2px) scale(1.06); color:#fff; }
.glass-btn:active { transform:scale(.97); }
.btn-copy-glass { color:#a5b4fc; border-color:rgba(139,92,246,.35); background:rgba(99,102,241,.1); }
.btn-copy-glass:hover { background:rgba(99,102,241,.28); border-color:rgba(139,92,246,.6); box-shadow:0 0 18px rgba(99,102,241,.45); color:#e0e7ff; }
.btn-usage-glass { color:#7dd3fc; border-color:rgba(6,182,212,.35); background:rgba(6,182,212,.1); }
.btn-usage-glass:hover { background:rgba(6,182,212,.28); border-color:rgba(6,182,212,.6); box-shadow:0 0 18px rgba(6,182,212,.45); color:#e0f2fe; }
.btn-edit-glass { color:#fde68a; border-color:rgba(234,179,8,.35); background:rgba(234,179,8,.1); }
.btn-edit-glass:hover { background:rgba(234,179,8,.28); border-color:rgba(234,179,8,.6); box-shadow:0 0 18px rgba(234,179,8,.45); color:#fef3c7; }
.btn-delete-glass { color:#fca5a5; border-color:rgba(239,68,68,.35); background:rgba(239,68,68,.1); }
.btn-delete-glass:hover { background:rgba(239,68,68,.28); border-color:rgba(239,68,68,.6); box-shadow:0 0 18px rgba(239,68,68,.45); color:#fee2e2; }

.btn-back, .btn-secondary {
    position:relative; display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:11px 26px; background:rgba(255,255,255,.07); border:1px solid rgba(255,255,255,.16); border-radius:50px;
    color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600; font-family:'Montserrat',sans-serif; cursor:pointer;
    transition:all .25s cubic-bezier(.34,1.5,.64,1); backdrop-filter:blur(10px);
}
.btn-back:hover, .btn-secondary:hover {
    background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3); transform:translateY(-3px);
    box-shadow:0 10px 24px rgba(0,0,0,.35); color:#fff;
}
.btn-danger {
    background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); border:none; padding:11px 26px; border-radius:50px;
    color:#fff; font-weight:600; font-size:.85rem; font-family:'Montserrat',sans-serif; transition:all .25s; box-shadow:0 4px 15px rgba(239,68,68,0.3);
}
.btn-danger:hover { transform:translateY(-3px); box-shadow:0 10px 25px rgba(239,68,68,0.5); }

/* WATERMARK */
.watermark {
    position:fixed; bottom:12px; left:0; right:0; text-align:center;
    font-size:.6rem; letter-spacing:.15em; text-transform:uppercase;
    color:rgba(255,255,255,0.07); z-index:1; pointer-events:none;
}

@media (max-width: 768px) {
    .action-bar { flex-direction: column; align-items: stretch; }
    .glass-buttons { justify-content: center; }
    .glass-status { justify-content: center; }
    .device-meta { flex-direction:column; gap:5px; }
}
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
        <span class="header-title"><i class="fas fa-key me-2" style="font-size:.85rem;"></i>List Licenses</span>
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
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn active-page" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>
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
    <div class="page-title">
        <i class="fas fa-list me-3" style="color:var(--p-primary);"></i>License Management
    </div>

    <div class="grid">
    <?php
    if($result->num_rows > 0) {
        while($r=$result->fetch_assoc()):
            $statusText = "ACTIVE";
            $statusClass = "status-active-glass";
            if($r['status'] == 0) {
                $statusText = "BLOCKED";
                $statusClass = "status-block-glass";
            } elseif($r['status'] == 2) {
                $statusText = "PAUSED";
                $statusClass = "status-pause-glass";
            }

            // Get connected devices count
            $devices_count = getConnectedDevices($conn, $r['license_key']);
    ?>
    <?php $license_ids[] = $r['id']; ?>
    <div class="card glass">
        <div class="app-name-section">
            <div class="app-name-left">
                <div class="app-name-icon">
                    <i class="fas fa-crown"></i>
                </div>
                <div class="app-name-text">
                    <span class="app-name-label">APP NAME</span>
                    <div class="app-name-value" id="appNameDisplay<?=$r['id']?>">
                        <span class="custom-name"><?=htmlspecialchars($r['package_name'])?></span>
                    </div>
                </div>
            </div>
            <button class="app-name-edit" onclick="showNameInput(<?=$r['id']?>)">
                <i class="fas fa-pencil-alt"></i> Change
            </button>
        </div>

        <div class="name-edit-input" id="nameInput<?=$r['id']?>">
            <input type="text" class="name-edit-field" id="newName<?=$r['id']?>"
                   placeholder="Enter custom app name..."
                   value="<?=htmlspecialchars($r['package_name'])?>">
            <div class="d-flex gap-2">
                <button class="btn btn-sm btn-success" style="border-radius:10px; font-weight:600;" onclick="saveName(<?=$r['id']?>)">
                    <i class="fas fa-check"></i> Save
                </button>
                <button class="btn btn-sm btn-secondary" style="border-radius:10px; font-weight:600;" onclick="hideNameInput(<?=$r['id']?>)">
                    <i class="fas fa-times"></i> Cancel
                </button>
            </div>
        </div>

        <div class="package-rgb" style="justify-content:space-between;align-items:center;">
            <div style="display:flex;align-items:center;gap:8px;">
                <i class="fas fa-box"></i>
                <span><?= $r['package_lock'] ? htmlspecialchars($r['package_name'] ?: '—') : '<em style="color:rgba(255,255,255,.45);font-style:normal;">Any Package</em>' ?></span>
            </div>
            <?php if($r['package_lock']): ?>
                <span class="pkg-lock-badge pkg-locked"><i class="fas fa-lock"></i> LOCKED</span>
            <?php else: ?>
                <span class="pkg-lock-badge pkg-universal"><i class="fas fa-globe"></i> UNIVERSAL</span>
            <?php endif; ?>
        </div>

        <div class="expiry-rgb">
            <i class="far fa-calendar-alt"></i>
            <span><?=htmlspecialchars($r['expiry_date'])?></span>
        </div>

        <div class="expiry-rgb">
            <i class="fas fa-mobile-alt"></i>
            <span>Devices <?=$devices_count?> / <?=max(1, (int)($r['max_devices'] ?? 1))?></span>
        </div>

        <div class="license-key-rgb" id="k<?=$r['id']?>">
            <i class="fas fa-key"></i>
            <span class="license-key-text blurred" id="keyText<?=$r['id']?>"><?=htmlspecialchars($r['license_key'])?></span>
            <button class="toggle-visibility" onclick="toggleKeyVisibility(<?=$r['id']?>)" title="Toggle visibility">
                <i class="fas fa-eye" id="eyeIcon<?=$r['id']?>"></i>
            </button>
        </div>

        <div class="action-bar">
            <div class="glass-buttons">
                <button class="glass-btn btn-copy-glass" onclick="copyKey('<?=$r['id']?>', this)">
                    <i class="far fa-copy"></i> Copy
                </button>

                <button class="glass-btn btn-usage-glass" onclick='showUsage(<?= json_encode((int) $r["id"]) ?>, <?= json_encode((string) ($r["package_name"] ?? "Any package")) ?>, this)'>
                    <i class="fas fa-chart-line"></i> Usage (<?=$devices_count?>)
                </button>

                <button class="glass-btn btn-edit-glass" data-bs-toggle="modal" data-bs-target="#edit<?=$r['id']?>">
                    <i class="fas fa-edit"></i> Edit
                </button>

                <button class="glass-btn btn-delete-glass" onclick="openDeleteModal(<?=$r['id']?>)">
                    <i class="fas fa-trash-alt"></i> Delete
                </button>
            </div>

            <span class="glass-status <?=$statusClass?>">
                <i class="fas fa-<?=($statusText=='ACTIVE'?'check-circle':($statusText=='BLOCKED'?'ban':'pause-circle'))?>"></i>
                <?=$statusText?>
            </span>
        </div>
    </div>

    <div class="modal fade" id="edit<?=$r['id']?>" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content text-white">
                <form method="post">
                    <div class="modal-header border-0">
                        <h5 class="modal-title"><i class="fas fa-edit me-2"></i>Edit License</h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <input type="hidden" name="edit_id" value="<?=$r['id']?>">

                        <label class="mb-1"><i class="fas fa-key me-2" style="color:var(--p-primary);"></i>License Key</label>
                        <input class="form-control mb-3" name="license_key" value="<?=htmlspecialchars($r['license_key'])?>" required>

                        <!-- Package Lock Toggle -->
                        <div class="edit-pkg-lock-bar <?=$r['package_lock'] ? 'locked' : 'unlocked'?>" id="editLockBar<?=$r['id']?>">
                            <label class="edit-toggle-switch">
                                <input type="checkbox" name="package_lock" id="editLockCb<?=$r['id']?>"
                                    value="1" <?=$r['package_lock'] ? 'checked' : ''?>
                                    onchange="editOnLockToggle(<?=$r['id']?>, this)">
                                <span class="edit-toggle-track"></span>
                            </label>
                            <div style="flex:1;">
                                <div style="font-size:.83rem;font-weight:700;" id="editLockLabel<?=$r['id']?>">
                                    <?=$r['package_lock']
                                        ? '<i class="fas fa-lock me-1"></i> Package Lock <span style="font-size:.65rem;padding:2px 8px;border-radius:20px;background:rgba(201,168,76,.18);color:var(--p-primary);margin-left:5px;">ENABLED</span>'
                                        : '<i class="fas fa-globe me-1"></i> Package Lock <span style="font-size:.65rem;padding:2px 8px;border-radius:20px;background:rgba(79,142,247,.18);color:#4F8EF7;margin-left:5px;">DISABLED</span>'?>
                                </div>
                                <div style="font-size:.7rem;color:rgba(255,255,255,.4);" id="editLockDesc<?=$r['id']?>">
                                    <?=$r['package_lock'] ? 'Key is restricted to a specific package' : 'Universal key — any package accepted'?>
                                </div>
                            </div>
                        </div>

                        <!-- Package Name (shown only when locked) -->
                        <div id="editPkgWrap<?=$r['id']?>" style="<?=$r['package_lock'] ? '' : 'display:none;'?>">
                            <label class="mb-1"><i class="fas fa-box me-2" style="color:var(--p-primary);"></i>Package Name</label>
                            <input class="form-control mb-3" name="package_name" id="editPkgInput<?=$r['id']?>"
                                   value="<?=htmlspecialchars($r['package_name'] ?? '')?>"
                                   placeholder="e.g., com.example.app">
                        </div>

                        <label class="mb-1"><i class="far fa-calendar-alt me-2" style="color:var(--p-primary);"></i>Expiry Date</label>
                        <input type="date" class="form-control mb-3" name="expiry_date" value="<?=$r['expiry_date']?>" required>

                        <label class="mb-1"><i class="fas fa-mobile-alt me-2" style="color:var(--p-primary);"></i>Maximum Devices</label>
                        <input type="number" min="1" max="100" class="form-control mb-3" name="max_devices" value="<?=max(1, (int)($r['max_devices'] ?? 1))?>" required>

                        <label class="mb-1"><i class="fas fa-circle me-2" style="color:var(--p-primary);"></i>Status</label>
                        <select class="form-select mb-2" name="status" required>
                            <option value="1" <?=$r['status']==1?'selected':''?>>✅ ACTIVE</option>
                            <option value="2" <?=$r['status']==2?'selected':''?>>⏸️ PAUSED</option>
                            <option value="0" <?=$r['status']==0?'selected':''?>>❌ BLOCKED</option>
                        </select>
                    </div>
                    <div class="modal-footer border-0">
                        <button type="button" class="btn-back" style="padding: 10px 20px;" data-bs-dismiss="modal">
                            <i class="fas fa-times"></i> Cancel
                        </button>
                        <button type="submit" class="btn-ios" style="padding: 11px 22px;">
                            <i class="fas fa-save"></i> Save Changes
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <?php
        endwhile;
    } else {
        echo '<div class="col-12 text-center p-5 glass" style="max-width: 600px; margin: 40px auto; border-radius: 20px;">
                <i class="fas fa-key fa-4x mb-4" style="color:var(--p-primary); opacity:0.8;"></i>
                <h4 style="font-weight:700;">No Licenses Found</h4>
                <p class="opacity-75 mb-4">Click Generate License to create new licenses</p>
                <a href="generate_ui.php" class="btn-ios">
                    <i class="fas fa-plus-circle me-2"></i>Generate License
                </a>
              </div>';
    }
    ?>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text'] ?? 'ONEBOX PANEL') ?></div>

<div id="usageModal" class="overlay-modal">
    <div class="glass modal-box usage-box">
        <i class="fas fa-chart-line fa-3x mb-3" style="color:var(--p-accent);"></i>
        <h4 class="mb-3" style="font-weight:700;">Connected Devices</h4>

        <div class="usage-stats">
            <div class="usage-stat-item">
                <span class="usage-stat-label"><i class="fas fa-box"></i> Package</span>
                <span class="usage-stat-value" id="usagePackageName" style="font-size:0.95rem;">-</span>
            </div>
            <div class="usage-stat-item">
                <span class="usage-stat-label"><i class="fas fa-plug"></i> Connected</span>
                <span class="usage-stat-value" id="usageDeviceCount">0</span>
            </div>
        </div>

        <div class="usage-device-list" id="usageDeviceList">
            <div class="text-center py-4">
                <i class="fas fa-info-circle fa-2x mb-2" style="color:rgba(255,255,255,0.4);"></i>
                <p>Select a package to view connected devices</p>
            </div>
        </div>

        <div class="d-flex justify-content-center mt-4">
            <button class="btn-back" style="width: 100%; justify-content: center;" onclick="closeUsageModal()">
                <i class="fas fa-times me-2"></i>Close
            </button>
        </div>
    </div>
</div>

<div id="deleteModal" class="overlay-modal">
    <div class="glass modal-box delete-box">
        <i class="fas fa-exclamation-triangle fa-3x mb-3 text-warning"></i>
        <h4 class="mb-2" style="font-weight:700;">Delete License?</h4>
        <p class="opacity-75 mb-4">This action cannot be undone!</p>

        <div class="d-flex gap-3">
            <button class="btn-back w-50" style="justify-content:center;" onclick="closeDeleteModal()">
                <i class="fas fa-times"></i> Cancel
            </button>
            <button class="btn-danger w-50" style="display:flex; justify-content:center; align-items:center; gap:8px;" onclick="confirmDelete()">
                <i class="fas fa-trash-alt"></i> Delete
            </button>
        </div>
    </div>
</div>

<form id="deleteForm" method="post" style="display:none">
    <input type="hidden" name="delete_id" id="deleteId">
</form>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>

<script>
// Load saved names from localStorage
document.addEventListener('DOMContentLoaded', function() {
    const licenseIds = <?= json_encode($license_ids) ?>;
    licenseIds.forEach(function(id) {
        const savedName = localStorage.getItem('app_name_' + id);
        if (savedName) {
            const el = document.getElementById('appNameDisplay' + id);
            if (el) el.textContent = savedName;
        }
    });
});

// Edit modal package lock toggle
function editOnLockToggle(id, cb) {
    const bar   = document.getElementById('editLockBar'   + id);
    const label = document.getElementById('editLockLabel' + id);
    const desc  = document.getElementById('editLockDesc'  + id);
    const wrap  = document.getElementById('editPkgWrap'   + id);
    if (cb.checked) {
        bar.className  = 'edit-pkg-lock-bar locked';
        label.innerHTML = '<i class="fas fa-lock me-1"></i> Package Lock <span style="font-size:.65rem;padding:2px 8px;border-radius:20px;background:rgba(201,168,76,.18);color:var(--p-primary);margin-left:5px;">ENABLED</span>';
        desc.textContent = 'Key is restricted to a specific package';
        wrap.style.display = 'block';
    } else {
        bar.className  = 'edit-pkg-lock-bar unlocked';
        label.innerHTML = '<i class="fas fa-globe me-1"></i> Package Lock <span style="font-size:.65rem;padding:2px 8px;border-radius:20px;background:rgba(79,142,247,.18);color:#4F8EF7;margin-left:5px;">DISABLED</span>';
        desc.textContent = 'Universal key — any package accepted';
        wrap.style.display = 'none';
        const pkgInput = document.getElementById('editPkgInput' + id);
        if (pkgInput) pkgInput.value = '';
    }
}

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

// Toggle license key visibility
function toggleKeyVisibility(id) {
    const keyText = document.getElementById('keyText' + id);
    const eyeIcon = document.getElementById('eyeIcon' + id);
    keyText.classList.toggle('blurred');
    if (keyText.classList.contains('blurred')) {
        eyeIcon.className = 'fas fa-eye';
    } else {
        eyeIcon.className = 'fas fa-eye-slash';
    }
}

// Copy function
function copyKey(id, btn) {
    const element = document.getElementById('keyText' + id);
    const text = element.innerText.trim();

    navigator.clipboard.writeText(text).then(() => {
        const originalHTML = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check"></i> Copied!';

        setTimeout(() => {
            btn.innerHTML = originalHTML;
        }, 2000);
    });
}

// Show/Hide name input
function showNameInput(id) {
    document.getElementById('nameInput' + id).classList.add('active');
}
function hideNameInput(id) {
    document.getElementById('nameInput' + id).classList.remove('active');
}

// Save custom name
function saveName(id) {
    const newName = document.getElementById('newName' + id).value;
    if (newName.trim() !== '') {
        localStorage.setItem('app_name_' + id, newName);
        document.getElementById('appNameDisplay' + id).textContent = newName;
        hideNameInput(id);
        showToast('App name saved successfully!', 'success');
    } else {
        showToast('Please enter a name', 'error');
    }
}

// Usage modal functionality
function escapeHtml(value) {
    const element = document.createElement('div');
    element.textContent = String(value ?? '');
    return element.innerHTML;
}

function showUsage(licenseId, packageName, btn) {
    document.getElementById('usagePackageName').textContent = packageName;
    const deviceList = document.getElementById('usageDeviceList');
    deviceList.innerHTML = '<div class="text-center py-4"><i class="fas fa-spinner fa-spin fa-2x mb-2" style="color:var(--p-primary);"></i><p>Loading devices...</p></div>';
    document.getElementById('usageDeviceCount').textContent = '...';

    document.getElementById('usageModal').classList.add('active');

    fetch(`get_devices.php?license_id=${encodeURIComponent(licenseId)}`)
        .then(response => {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.json();
        })
        .then(data => {
            if (data.success && data.devices && data.devices.length > 0) {
                let html = '';
                data.devices.forEach(device => {
                    const lastSeen = device.last_seen ? new Date(device.last_seen).toLocaleString() : 'Just now';
                    const ipDisplay = device.ip_address || 'N/A';
                    html += `
                        <div class="usage-device-item">
                            <i class="fas fa-circle"></i>
                            <div class="device-info">
                                <div class="device-id">${escapeHtml(device.device_id)}</div>
                                <div class="device-meta">
                                    <span><i class="fas fa-network-wired"></i> ${escapeHtml(ipDisplay)}</span>
                                    <span><i class="fas fa-clock"></i> ${escapeHtml(lastSeen)}</span>
                                </div>
                            </div>
                        </div>
                    `;
                });
                deviceList.innerHTML = html;
                document.getElementById('usageDeviceCount').textContent = data.devices.length;
            } else {
                deviceList.innerHTML = `
                    <div class="text-center py-5">
                        <i class="fas fa-info-circle fa-3x mb-3" style="color:rgba(255,255,255,0.2);"></i>
                        <h6 class="mb-2">No Devices Connected</h6>
                        <p class="small opacity-50">Package: ${escapeHtml(packageName)}</p>
                    </div>
                `;
                document.getElementById('usageDeviceCount').textContent = '0';
            }
        })
        .catch(error => {
            deviceList.innerHTML = `
                <div class="text-center py-4">
                    <i class="fas fa-exclamation-triangle fa-2x mb-2" style="color:#f87171;"></i>
                    <p class="text-danger">Error loading devices</p>
                    <p class="small opacity-50">${error.message}</p>
                </div>
            `;
            document.getElementById('usageDeviceCount').textContent = '0';
        });
}

function closeUsageModal() {
    document.getElementById('usageModal').classList.remove('active');
}

// Toast message functionality
function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.style.cssText = `
        position: fixed; bottom: 40px; left: 50%; transform: translateX(-50%) translateY(20px);
        background: ${type === 'success' ? 'linear-gradient(135deg,#10b981,#059669)' : 'linear-gradient(135deg,#ef4444,#b91c1c)'};
        color: white; padding: 12px 24px; border-radius: 50px; font-weight:600;
        z-index: 9999; animation: slideUp 0.3s forwards; box-shadow: 0 10px 25px rgba(0,0,0,0.3);
        display:flex; align-items:center; gap:8px;
    `;
    toast.innerHTML = `<i class="fas ${type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle'}"></i> ${message}`;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideDownOut 0.3s forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// Delete modal logic
let deleteTargetId = null;

function openDeleteModal(id) {
    deleteTargetId = id;
    document.getElementById("deleteModal").classList.add("active");
}

function closeDeleteModal() {
    document.getElementById("deleteModal").classList.remove("active");
    deleteTargetId = null;
}

function confirmDelete() {
    if (deleteTargetId) {
        document.getElementById("deleteId").value = deleteTargetId;
        document.getElementById("deleteForm").submit();
    }
}

// Close overlays on outside click
document.getElementById('deleteModal').addEventListener('click', function(e) {
    if (e.target === this) closeDeleteModal();
});
document.getElementById('usageModal').addEventListener('click', function(e) {
    if (e.target === this) closeUsageModal();
});

// Animations for Toast
const style = document.createElement('style');
style.textContent = `
    @keyframes slideUp { from { transform: translateX(-50%) translateY(20px); opacity: 0; } to { transform: translateX(-50%) translateY(0); opacity: 1; } }
    @keyframes slideDownOut { from { transform: translateX(-50%) translateY(0); opacity: 1; } to { transform: translateX(-50%) translateY(20px); opacity: 0; } }
`;
document.head.appendChild(style);
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
