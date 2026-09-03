<?php
session_start();
include("conn.php");
include 'desktop_only.php';
include("panel_helper.php");

if (!isset($_SESSION['username'])) {
    header("Location: login.php");
    exit();
}

$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$uid = intval($_SESSION['user_id'] ?? 0);
$unread_ann_count = $uid ? count_unread_announcements($conn, $uid) : 0;

/* COUNTS */
$total   = mysqli_fetch_assoc(mysqli_query($conn,"SELECT COUNT(*) c FROM licenses"))['c'] ?? 0;
$used    = mysqli_fetch_assoc(mysqli_query($conn,"SELECT COUNT(DISTINCT d.license_key) c FROM devices d INNER JOIN licenses l ON l.license_key = d.license_key"))['c'] ?? 0;
$unused  = $total - $used;
$users   = mysqli_fetch_assoc(mysqli_query($conn,"SELECT COUNT(*) c FROM users"))['c'] ?? 0;
$blocked = mysqli_fetch_assoc(mysqli_query($conn,"SELECT COUNT(*) c FROM licenses WHERE status = 0"))['c'] ?? 0;
$active  = mysqli_fetch_assoc(mysqli_query($conn,"SELECT COUNT(*) c FROM licenses WHERE status = 1 AND expiry_date >= UTC_DATE()"))['c'] ?? 0;

$licenses = mysqli_query($conn,"
SELECT
    l.license_key, l.expiry_date, l.status, l.package_name, l.max_devices,
    COUNT(DISTINCT d.device_id) as devices_used,
    COUNT(DISTINCT d.device_id) as unique_devices,
    MAX(d.last_seen) as last_activity,
    (SELECT d2.ip_address FROM devices d2
     WHERE d2.license_key = l.license_key ORDER BY d2.last_seen DESC LIMIT 1) as last_ip
FROM licenses l
LEFT JOIN devices d ON d.license_key = l.license_key
GROUP BY l.id ORDER BY l.id DESC");

// Role check
$roleStmt = $conn->prepare('SELECT role FROM users WHERE id = ? LIMIT 1');
$roleStmt->bind_param('i', $uid);
$roleStmt->execute();
$urow_dash = $roleStmt->get_result()->fetch_assoc();
$roleStmt->close();
$role_dash = $urow_dash['role'] ?? 'user';
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><?= htmlspecialchars($P['dashboard_title']) ?></title>
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

/* ── BACKGROUND ORBS (replaces heavy particles.js) ── */
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
    padding: 80px 16px 40px;
    max-width:1100px; margin:0 auto;
    transition: padding-left .32s;
}

/* ── PAGE TITLE ── */
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

/* ── KPI GRID ── */
.kpi-grid {
    display:grid;
    grid-template-columns:repeat(3, 1fr);
    gap:14px; margin-bottom:28px;
}
@media(max-width:600px) { .kpi-grid { grid-template-columns:repeat(2,1fr); } }
@media(max-width:360px) { .kpi-grid { grid-template-columns:1fr 1fr; } }

.kpi-card {
    padding:20px 16px;
    text-align:center; cursor:default;
    transition: transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s;
    animation: cardIn .5s cubic-bezier(.34,1.2,.64,1) both;
    animation-delay: calc(var(--i,.0) * 80ms);
    position:relative; overflow:hidden;
}
@keyframes cardIn {
    from { opacity:0; transform:translateY(20px) scale(.97); }
    to   { opacity:1; transform:none; }
}
.kpi-card::before {
    content:''; position:absolute; inset:0;
    background:linear-gradient(135deg, rgba(255,255,255,.04) 0%, transparent 60%);
    pointer-events:none;
}
.kpi-card:hover { transform:translateY(-4px) scale(1.02); box-shadow:0 16px 40px rgba(0,0,0,.45); }
.kpi-icon {
    width:44px; height:44px; border-radius:12px; margin:0 auto 10px;
    display:flex; align-items:center; justify-content:center;
    font-size:1.15rem;
    background:rgba(255,255,255,0.07);
    border:1px solid rgba(255,255,255,0.1);
}
.kpi-val {
    font-size:2rem; font-weight:800; line-height:1;
    background:linear-gradient(135deg, #fff 0%, rgba(255,255,255,.7) 100%);
    -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text;
    margin-bottom:4px;
}
.kpi-val.gold { background:linear-gradient(135deg, var(--p-primary), #f0d080); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }
.kpi-val.green { background:linear-gradient(135deg,#4ade80,#34d399); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }
.kpi-val.red { background:linear-gradient(135deg,#f87171,#fb7185); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }
.kpi-val.blue { background:linear-gradient(135deg,var(--p-accent),#93c5fd); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }
.kpi-val.yellow { background:linear-gradient(135deg,#fbbf24,#fde68a); -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; }
.kpi-label {
    font-size:.68rem; font-weight:700; letter-spacing:.1em; text-transform:uppercase;
    color:rgba(255,255,255,.4);
}

/* ── SECTION HEADER ── */
.section-header {
    display:flex; align-items:center; gap:10px;
    margin:28px 0 14px; padding-bottom:12px;
    border-bottom:1px solid rgba(255,255,255,0.08);
}
.section-header i { color:var(--p-primary); }
.section-header h4 { margin:0; font-size:.95rem; font-weight:700; letter-spacing:.06em; text-transform:uppercase; }
.section-header small { margin-left:auto; font-size:.7rem; color:rgba(255,255,255,.3); font-weight:600; }

/* ── LICENSE GRID ── */
.license-grid {
    display:grid;
    grid-template-columns:repeat(auto-fill, minmax(290px, 1fr));
    gap:14px;
}
.lic-card {
    padding:18px;
    animation: cardIn .5s cubic-bezier(.34,1.2,.64,1) both;
    animation-delay: calc(var(--i,.0) * 60ms);
    transition: transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s;
    position:relative; overflow:hidden;
}
.lic-card:hover { transform:translateY(-3px); box-shadow:0 16px 40px rgba(0,0,0,.5); }
.lic-card.blocked { border-color:rgba(248,113,113,0.3) !important; }
.lic-card.blocked::after {
    content:'BLOCKED';
    position:absolute; top:12px; right:12px;
    font-size:.6rem; font-weight:800; letter-spacing:.12em;
    background:rgba(239,68,68,0.15); color:#f87171;
    border:1px solid rgba(239,68,68,0.3);
    border-radius:30px; padding:3px 10px;
}
.lic-key {
    font-family:monospace; font-size:.95rem; font-weight:700;
    color:var(--p-primary); margin-bottom:12px;
    word-break:break-all; padding-right:60px; line-height:1.4;
}
.lic-detail {
    display:flex; align-items:center; gap:8px;
    font-size:.8rem; color:rgba(255,255,255,.75);
    margin:7px 0;
}
.lic-detail i { width:16px; text-align:center; color:var(--p-primary); opacity:.7; font-size:.8rem; }
.lic-divider { height:1px; background:rgba(255,255,255,.07); margin:10px 0; }
.lic-footer { display:flex; align-items:center; justify-content:space-between; margin-top:8px; }
.device-pill {
    display:inline-flex; align-items:center; gap:5px;
    font-size:.72rem; font-weight:700; letter-spacing:.05em;
    border-radius:30px; padding:4px 12px;
}
.device-pill.active { background:rgba(52,211,153,.12); color:#4ade80; border:1px solid rgba(52,211,153,.25); }
.device-pill.none { background:rgba(255,255,255,.06); color:rgba(255,255,255,.4); border:1px solid rgba(255,255,255,.08); }
.lic-time { font-size:.68rem; color:rgba(255,255,255,.3); margin-top:6px; }

.status-dot {
    position:absolute; top:14px; right:14px;
    width:8px; height:8px; border-radius:50%;
    box-shadow:0 0 8px currentColor;
}
.status-dot.active { background:#4ade80; color:#4ade80; }
.status-dot.blocked { background:#f87171; color:#f87171; }

/* ── SCROLLBAR ── */
::-webkit-scrollbar { width:6px; }
::-webkit-scrollbar-track { background:rgba(255,255,255,0.03); border-radius:10px; }
::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:10px; }
::-webkit-scrollbar-thumb:hover { background:rgba(201,168,76,0.55); }

/* ── WATERMARK ── */
.watermark {
    position:fixed; bottom:12px; left:0; right:0; text-align:center;
    font-size:.6rem; letter-spacing:.15em; text-transform:uppercase;
    color:rgba(255,255,255,0.07); z-index:1; pointer-events:none;
}

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

<!-- Background Orbs (lightweight, no JS) -->
<div class="bg-orbs">
    <div class="orb orb1"></div>
    <div class="orb orb2"></div>
    <div class="orb orb3"></div>
</div>

<div id="overlay" onclick="closeSidebar()"></div>

<!-- HEADER -->
<header>
    <div class="header-left">
        <button class="menu-btn" onclick="toggleSidebar()" aria-label="Menu">
            <i class="fas fa-bars" id="menuIcon"></i>
        </button>
        <span class="header-title"><i class="fas fa-tachometer-alt me-2" style="font-size:.85rem;"></i><?= htmlspecialchars($P['panel_name']) ?></span>
    </div>
    <div class="user-badge">
        <i class="fas fa-user-circle"></i>
        <span><?= htmlspecialchars($current_user) ?></span>
    </div>
</header>

<!-- SIDEBAR -->
<aside id="sidebar">
    <div class="sidebar-logo-section">
        <div class="sidebar-logo-ring">
            <div class="sidebar-logo-inner">
                <img src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>" alt="Logo"
                     onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='flex';">
                <div class="sidebar-logo-fallback"><i class="fas fa-box-open"></i></div>
            </div>
        </div>
        <div class="sidebar-panel-name"><?= htmlspecialchars($P['panel_name']) ?></div>
        <div class="sidebar-user-chip"><i class="fas fa-shield-alt" style="color:var(--p-primary)"></i> <?= htmlspecialchars($current_user) ?></div>
    </div>

    <div class="sidebar-nav-label">Main</div>
    <a class="nav-btn active-page" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
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

<!-- MAIN -->
<div class="main">

    <!-- Title -->
    <div class="page-title"><?= htmlspecialchars($P['dashboard_title']) ?></div>

    <!-- KPI Cards -->
    <div class="kpi-grid">
        <div class="kpi-card glass" style="--i:0">
            <div class="kpi-icon"><i class="fas fa-id-card" style="color:var(--p-primary)"></i></div>
            <div class="kpi-val gold" data-count="<?= $total ?>"><?= $total ?></div>
            <div class="kpi-label">Total Licenses</div>
        </div>
        <div class="kpi-card glass" style="--i:1">
            <div class="kpi-icon"><i class="fas fa-check-circle" style="color:#4ade80"></i></div>
            <div class="kpi-val green" data-count="<?= $active ?>"><?= $active ?></div>
            <div class="kpi-label">Active</div>
        </div>
        <div class="kpi-card glass" style="--i:2">
            <div class="kpi-icon"><i class="fas fa-ban" style="color:#f87171"></i></div>
            <div class="kpi-val red" data-count="<?= $blocked ?>"><?= $blocked ?></div>
            <div class="kpi-label">Blocked</div>
        </div>
        <div class="kpi-card glass" style="--i:3">
            <div class="kpi-icon"><i class="fas fa-plug" style="color:var(--p-accent)"></i></div>
            <div class="kpi-val blue" data-count="<?= $used ?>"><?= $used ?></div>
            <div class="kpi-label">Used</div>
        </div>
        <div class="kpi-card glass" style="--i:4">
            <div class="kpi-icon"><i class="fas fa-layer-group" style="color:#fbbf24"></i></div>
            <div class="kpi-val yellow" data-count="<?= $unused ?>"><?= $unused ?></div>
            <div class="kpi-label">Unused</div>
        </div>
        <div class="kpi-card glass" style="--i:5">
            <div class="kpi-icon"><i class="fas fa-users" style="color:var(--p-primary)"></i></div>
            <div class="kpi-val gold" data-count="<?= $users ?>"><?= $users ?></div>
            <div class="kpi-label">Total Users</div>
        </div>
    </div>

    <!-- Recent Licenses -->
    <div class="section-header">
        <i class="fas fa-key"></i>
        <h4>Recent Licenses</h4>
        <small><?= $total ?> total</small>
    </div>

    <div class="license-grid">
    <?php $i=0; while($r=mysqli_fetch_assoc($licenses)): $i++;
        $isBlocked = ($r['status'] == 0);
        $uDev = (int)($r['unique_devices'] ?? 0);
        $dDev = (int)($r['devices_used'] ?? 0);
        $disp = $uDev > 0 ? $uDev : $dDev;
    ?>
        <div class="lic-card glass <?= $isBlocked ? 'blocked' : '' ?>" style="--i:<?= $i ?>">
            <div class="status-dot <?= $isBlocked ? 'blocked' : 'active' ?>"></div>
            <div class="lic-key"><i class="fas fa-key me-2" style="opacity:.5;font-size:.8rem;"></i><?= htmlspecialchars($r['license_key']) ?></div>
            <?php if (!empty($r['package_name'])): ?>
            <div class="lic-detail"><i class="fas fa-box"></i><?= htmlspecialchars($r['package_name']) ?></div>
            <?php endif; ?>
            <div class="lic-detail"><i class="far fa-calendar-alt"></i>Expires: <strong><?= htmlspecialchars($r['expiry_date']) ?></strong></div>
            <div class="lic-divider"></div>
            <div class="lic-footer">
                <div class="lic-detail"><i class="fas fa-mobile-alt"></i>Devices:
                    <span style="font-weight:700; color:var(--p-primary); margin-left:4px;"><?= $disp ?>/<?= max(1, (int)($r['max_devices'] ?? 1)) ?></span>
                </div>
                <span class="device-pill <?= $disp > 0 ? 'active' : 'none' ?>">
                    <i class="fas fa-circle" style="font-size:.45rem;"></i>
                    <?= $disp > 0 ? 'Active' : 'Free' ?>
                </span>
            </div>
            <?php if (!empty($r['last_ip'])): ?>
            <div class="lic-detail" style="margin-top:4px;"><i class="fas fa-network-wired"></i><?= htmlspecialchars($r['last_ip']) ?></div>
            <?php endif; ?>
            <?php if (!empty($r['last_activity'])): ?>
            <div class="lic-time"><i class="fas fa-clock me-1"></i><?= date('d M Y · H:i', strtotime($r['last_activity'])) ?></div>
            <?php endif; ?>
        </div>
    <?php endwhile; ?>
    </div>

</div><!-- /main -->

<div class="watermark"><?= htmlspecialchars($P['watermark_text']) ?></div>

<script>
// Sidebar toggle
const sidebar  = document.getElementById('sidebar');
const overlay  = document.getElementById('overlay');
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

// Smooth count-up animation for KPI values
function countUp(el) {
    const target = parseInt(el.dataset.count, 10);
    if (!target || target < 1) return;
    let start = 0;
    const dur = Math.min(1200, target * 60);
    const step = target / (dur / 16);
    const timer = setInterval(() => {
        start = Math.min(start + step, target);
        el.textContent = Math.floor(start);
        if (start >= target) { el.textContent = target; clearInterval(timer); }
    }, 16);
}
document.querySelectorAll('.kpi-val[data-count]').forEach(countUp);

// ── Announcement Popups ──
const unreadAnns = <?php
    $arr2=[];
    if ($uid) {
        $res3 = get_active_announcements($conn, $uid);
        while($r=mysqli_fetch_assoc($res3)) {
            $arr2[]=['id'=>(int)$r['id'],'title'=>htmlspecialchars($r['title'],ENT_QUOTES),'message'=>htmlspecialchars(substr($r['message'],0,120),ENT_QUOTES),'type'=>$r['type']];
        }
    }
    echo json_encode($arr2);
?>;

if (unreadAnns.length > 0) {
    const style = document.createElement('style');
    style.textContent = `
        .ann-wrap{position:fixed;bottom:24px;right:20px;z-index:9999;max-width:320px;width:90%;}
        .ann-pop{border-radius:16px;padding:14px 15px;margin-bottom:10px;display:flex;gap:10px;align-items:flex-start;
            animation:annIn .4s cubic-bezier(.34,1.2,.64,1);box-shadow:0 8px 30px rgba(0,0,0,.55);backdrop-filter:blur(20px);}
        @keyframes annIn{from{opacity:0;transform:translateY(16px) scale(.96)}to{opacity:1;transform:none}}
        .ann-pop.type-info   {background:rgba(10,18,45,.94);border:1px solid rgba(79,142,247,.4);}
        .ann-pop.type-warning{background:rgba(38,28,5,.94);border:1px solid rgba(251,191,36,.4);}
        .ann-pop.type-success{background:rgba(4,28,18,.94);border:1px solid rgba(52,211,153,.4);}
        .ann-pop.type-danger {background:rgba(38,5,5,.94);border:1px solid rgba(248,113,113,.4);}
        .ann-ico{font-size:1rem;flex-shrink:0;margin-top:2px;}
        .ann-pop.type-info .ann-ico{color:#4F8EF7}.ann-pop.type-warning .ann-ico{color:#fbbf24}
        .ann-pop.type-success .ann-ico{color:#34d399}.ann-pop.type-danger .ann-ico{color:#f87171}
        .ann-ttl{font-size:.82rem;font-weight:700;color:#fff;font-family:Montserrat,sans-serif;margin-bottom:2px;}
        .ann-msg{font-size:.72rem;color:rgba(255,255,255,.6);line-height:1.4;font-family:Montserrat,sans-serif;}
        .ann-close{margin-left:auto;background:none;border:none;color:rgba(255,255,255,.3);cursor:pointer;font-size:.9rem;padding:2px;}
        .ann-close:hover{color:#fff;}
        .ann-read{font-size:.68rem;color:rgba(255,255,255,.3);background:none;border:none;cursor:pointer;padding:0;text-decoration:underline;font-family:Montserrat,sans-serif;margin-top:4px;}
        .ann-read:hover{color:#fff;}
    `;
    document.head.appendChild(style);
    const wrap = document.createElement('div');
    wrap.className = 'ann-wrap';
    document.body.appendChild(wrap);
    const icons = {info:'fa-circle-info',warning:'fa-triangle-exclamation',success:'fa-circle-check',danger:'fa-circle-exclamation'};
    unreadAnns.slice(0,3).forEach((a,i) => {
        setTimeout(()=>{
            const el = document.createElement('div');
            el.className = `ann-pop type-${a.type}`; el.id=`ap-${a.id}`;
            el.innerHTML=`
                <div class="ann-ico"><i class="fas ${icons[a.type]||'fa-bell'}"></i></div>
                <div style="flex:1">
                    <div class="ann-ttl">${a.title}</div>
                    <div class="ann-msg">${a.message}${a.message.length>=120?'…':''}</div>
                    <div><button class="ann-read" onclick="annRead(${a.id})">✓ Mark read</button>
                    &nbsp;<a href="announcements.php" style="font-size:.68rem;color:rgba(255,255,255,.25);">View all</a></div>
                </div>
                <button class="ann-close" onclick="annDismiss(${a.id})"><i class="fas fa-times"></i></button>
            `;
            wrap.appendChild(el);
            setTimeout(()=>{ if(el.parentNode) el.remove(); }, 10000);
        }, i * 600);
    });
    window.annDismiss = id => { const e=document.getElementById(`ap-${id}`); if(e)e.remove(); };
    window.annRead = id => {
        const body = new URLSearchParams({_csrf: <?= json_encode(panel_csrf_token()) ?>, mark_read: String(id)});
        fetch('announcements.php', {method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body});
        annDismiss(id);
    };
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
