<?php
include 'conn.php';
include 'panel_helper.php';

// ============================================
// OWNER ACCESS ONLY - Check if logged in user is MANISH
// ============================================

if (!isset($_SESSION['user_id'])) {
    header("Location: login.php");
    exit;
}

$P = get_panel_settings($conn);
$user_id = (int)$_SESSION['user_id'];
$user_query = $conn->query("SELECT username, role FROM users WHERE id = $user_id");
$user_data = $user_query->fetch_assoc();
$current_user = $user_data['username'];
$role_dash = $user_data['role'] ?? 'user';
$unread_ann_count = count_unread_announcements($conn, $user_id);

// Check if user is the designated owner
if ($current_user !== 'ParallaxOwner') {
    // Not owner - show access denied page
    ?>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Access Denied - <?= htmlspecialchars($P['dashboard_title'] ?? 'Panel') ?></title>
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
        <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
        <link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;700;800&display=swap" rel="stylesheet">
        <style>
            <?= panel_css_vars($P) ?>
            body {
                font-family: 'Montserrat', sans-serif;
                background: linear-gradient(135deg, var(--p-bg1) 0%, var(--p-bg2) 35%, var(--p-bg3) 70%, var(--p-bg4) 100%);
                background-size: 400% 400%; animation: bgShift 20s ease infinite;
                min-height: 100vh; display: flex; align-items: center; justify-content: center;
                color: #fff; margin: 0; padding: 20px; overflow: hidden;
            }
            @keyframes bgShift { 0% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } 100% { background-position: 0% 50%; } }

            .bg-orbs { position:fixed; inset:0; z-index:0; pointer-events:none; }
            .orb { position:absolute; border-radius:50%; filter: blur(100px); animation: orbFloat 20s ease-in-out infinite; }
            .orb1 { width:500px; height:500px; top:-100px; left:-100px; background: radial-gradient(circle, rgba(239,68,68,0.15) 0%, transparent 70%); }
            .orb2 { width:400px; height:400px; bottom:-100px; right:-100px; background: radial-gradient(circle, rgba(201,168,76,0.1) 0%, transparent 70%); }
            @keyframes orbFloat { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(30px,-30px) scale(1.05); } }

            .access-denied-card {
                background: rgba(15, 23, 42, 0.75); backdrop-filter: blur(24px) saturate(180%);
                -webkit-backdrop-filter: blur(24px) saturate(180%); border: 1px solid rgba(239, 68, 68, 0.3);
                border-radius: 30px; padding: 50px 40px; max-width: 500px; text-align: center;
                box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5); position: relative; z-index: 1;
                animation: popIn 0.4s cubic-bezier(.34,1.5,.64,1);
            }
            @keyframes popIn { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }

            .lock-icon { font-size: 5.5rem; color: #ef4444; margin-bottom: 20px; animation: pulse 2s infinite; }
            @keyframes pulse { 0% { transform: scale(1); text-shadow: 0 0 0 rgba(239,68,68,0.4); } 50% { transform: scale(1.1); text-shadow: 0 0 20px rgba(239,68,68,0.6); } 100% { transform: scale(1); text-shadow: 0 0 0 rgba(239,68,68,0.4); } }

            h2 { font-size: 2.2rem; font-weight: 800; margin-bottom: 15px; color: #fff; letter-spacing: 1px; }
            .user-name { background: rgba(255, 255, 255, 0.05); padding: 12px 24px; border-radius: 50px; display: inline-block; margin: 20px 0; border: 1px solid rgba(255, 255, 255, 0.1); font-weight: 600; }
            .user-name i { color: var(--p-primary); margin-right: 8px; }
            .owner-badge { background: linear-gradient(135deg, rgba(239,68,68,0.2), rgba(185,28,28,0.2)); color: #fca5a5; padding: 6px 18px; border-radius: 50px; font-weight: 700; display: inline-flex; align-items: center; gap: 8px; margin-bottom: 25px; border: 1px solid rgba(239,68,68,0.3); font-size: 0.85rem; letter-spacing: 1px; }

            .btn-back { position:relative; display:inline-flex; align-items:center; gap:9px; padding:13px 30px; background:rgba(255,255,255,.07); border:1px solid rgba(255,255,255,.16); border-radius:50px; color:#fff; font-size:.95rem; font-weight:700; text-decoration:none; cursor:pointer; transition:all .25s; margin-top: 20px; }
            .btn-back:hover { background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3); transform:translateY(-3px); box-shadow:0 10px 24px rgba(0,0,0,.35); color:#fff; }
        </style>
    </head>
    <body>
        <div class="bg-orbs"><div class="orb orb1"></div><div class="orb orb2"></div></div>
        <div class="access-denied-card">
            <div class="lock-icon"><i class="fas fa-lock"></i></div>
            <div class="owner-badge"><i class="fas fa-crown"></i> OWNER ACCESS REQUIRED</div>
            <h2>Access Denied</h2>
            <p style="opacity: 0.7; font-size: 0.95rem;">This restricted area is for the system owner only.</p>

            <div class="user-name">
                <i class="fas fa-user-shield"></i> Current User: <strong style="color:var(--p-primary);"><?=htmlspecialchars($current_user)?></strong>
            </div>

            <p style="background: rgba(239, 68, 68, 0.15); padding: 15px; border-radius: 14px; font-size: 0.85rem; border: 1px solid rgba(239,68,68,0.3); color: #fca5a5;">
                <i class="fas fa-exclamation-triangle me-2"></i> You lack the necessary permissions to control the Main Server.
            </p>

            <a href="dashboard.php" class="btn-back"><i class="fas fa-arrow-left"></i> Return to Dashboard</a>
        </div>
    </body>
    </html>
    <?php
    exit;
}

// ============================================
// OWNER VERIFIED - Continue with server controller
// ============================================

// Get current server status
$server_status = 'online'; // Default
$maintenance_message = '';
$status_result = $conn->query("SELECT * FROM server_settings WHERE setting_key = 'server_status'");
if ($status_result->num_rows > 0) {
    $row = $status_result->fetch_assoc();
    $server_status = $row['setting_value'];
}

$message_result = $conn->query("SELECT * FROM server_settings WHERE setting_key = 'maintenance_message'");
if ($message_result->num_rows > 0) {
    $row = $message_result->fetch_assoc();
    $maintenance_message = $row['setting_value'];
}

// Handle status update
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['update_status'])) {
        $new_status = $_POST['server_status'];
        $new_message = $_POST['maintenance_message'];
        $old_status = $server_status;

        // Update server status
        $stmt = $conn->prepare("UPDATE server_settings SET setting_value = ? WHERE setting_key = 'server_status'");
        $stmt->bind_param("s", $new_status);
        $stmt->execute();

        if ($stmt->affected_rows == 0) {
            $stmt = $conn->prepare("INSERT INTO server_settings (setting_key, setting_value) VALUES ('server_status', ?)");
            $stmt->bind_param("s", $new_status);
            $stmt->execute();
        }

        // Update maintenance message
        $stmt = $conn->prepare("UPDATE server_settings SET setting_value = ? WHERE setting_key = 'maintenance_message'");
        $stmt->bind_param("s", $new_message);
        $stmt->execute();

        if ($stmt->affected_rows == 0) {
            $stmt = $conn->prepare("INSERT INTO server_settings (setting_key, setting_value) VALUES ('maintenance_message', ?)");
            $stmt->bind_param("s", $new_message);
            $stmt->execute();
        }

        // If server is offline/maintenance, block all SDK keys
        if ($new_status == 'offline' || $new_status == 'maintenance') {
            $conn->query("UPDATE licenses SET status = 0, blocked_by_server = 1, last_blocked_at = NOW() WHERE status = 1");
            $conn->query("UPDATE devices SET status = 'disconnected' WHERE status = 'connected'");
            error_log("Server set to $new_status mode by $current_user. All licenses blocked and devices disconnected.");

        } elseif ($new_status == 'online' && ($old_status == 'offline' || $old_status == 'maintenance')) {
            $conn->query("UPDATE licenses SET status = 1, blocked_by_server = 0 WHERE blocked_by_server = 1");
            error_log("Server set to online mode by $current_user. All previously blocked licenses restored.");
        }

        $success = "Server status updated successfully!";
        $server_status = $new_status;
        $maintenance_message = $new_message;
    }

    if (isset($_POST['test_alert'])) {
        $alert_type = $_POST['alert_type'];
        $alert_message = $_POST['alert_message'];

        error_log("Alert Sent by $current_user: $alert_type - $alert_message");

        $stmt = $conn->prepare("INSERT INTO alert_history (alert_type, message, sent_by) VALUES (?, ?, ?)");
        $stmt->bind_param("sss", $alert_type, $alert_message, $current_user);
        $stmt->execute();

        $alert_sent = true;
    }
}

// Get connected devices count by package name
$devices_by_package = [];
$tableCheck = $conn->query("SHOW TABLES LIKE 'devices'");
if ($tableCheck && $tableCheck->num_rows > 0) {
    $result = $conn->query("SELECT package_name, COUNT(*) as count FROM devices WHERE status = 'connected' GROUP BY package_name");
    if ($result) {
        while ($row = $result->fetch_assoc()) {
            $devices_by_package[$row['package_name']] = $row['count'];
        }
    }
}

$total_devices = array_sum($devices_by_package);

$active_licenses = 0;
$result = $conn->query("SELECT COUNT(*) as count FROM licenses WHERE status = 1");
if ($result) {
    $row = $result->fetch_assoc();
    $active_licenses = $row['count'];
}

$total_licenses = 0;
$result = $conn->query("SELECT COUNT(*) as count FROM licenses");
if ($result) {
    $row = $result->fetch_assoc();
    $total_licenses = $row['count'];
}

$blocked_by_server = 0;
$result = $conn->query("SELECT COUNT(*) as count FROM licenses WHERE blocked_by_server = 1");
if ($result) {
    $row = $result->fetch_assoc();
    $blocked_by_server = $row['count'];
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Server Controller - <?= htmlspecialchars($P['dashboard_title'] ?? 'Panel') ?></title>

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
    max-width: 1400px; margin: 0 auto;
    transition: padding-left .32s;
    animation: fadeIn 0.5s ease;
}
@media(min-width:769px) { .main { padding-left:24px; } }

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}

.page-title {
    font-size:1.6rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase;
    background: linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%);
    background-size:200% auto; -webkit-background-clip:text; -webkit-text-fill-color:transparent;
    background-clip:text; animation: shimmer 4s linear infinite; margin-bottom:24px; text-align:center;
}
@keyframes shimmer { to { background-position:200% center; } }

/* ALERTS */
.alert {
    border-radius: 16px; padding: 15px 20px; margin-bottom: 24px; border: none;
    backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); color: #fff; font-weight: 500;
}
.alert-success { background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3); }
.alert-info { background: rgba(59, 130, 246, 0.15); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.3); }

/* WARNING BANNER */
.warning-banner {
    background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3);
    border-radius: 20px; padding: 20px 25px; margin-bottom: 30px;
    display: flex; align-items: center; gap: 20px; animation: blink 3s infinite;
    backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.8; } }

/* STATS GRID */
.status-card { padding: 30px; margin-bottom: 30px; border-radius: 24px; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; margin-bottom: 20px; }
.stat-card {
    padding: 25px; background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 20px; transition: all 0.3s ease; text-align: center;
}
.stat-card:hover { transform: translateY(-5px); border-color: var(--p-primary); box-shadow: 0 10px 30px rgba(0,0,0,0.3); }
.stat-icon {
    width: 50px; height: 50px; background: rgba(201, 168, 76, 0.1); border-radius: 14px;
    display: flex; align-items: center; justify-content: center; margin: 0 auto 15px; color: var(--p-primary); font-size: 1.5rem;
}
.stat-value { font-size: 2.2rem; font-weight: 800; margin-bottom: 5px; color: #fff; }
.stat-label { color: rgba(255, 255, 255, 0.6); font-size: 0.85rem; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; }

/* DEVICES BY PACKAGE */
.package-devices { margin-top: 30px; padding: 25px; background: rgba(0, 0, 0, 0.2); border-radius: 20px; border: 1px solid rgba(255,255,255,0.05); }
.package-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 15px; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.package-item:last-child { border-bottom: none; }
.package-name { font-weight: 600; color: var(--p-primary); }
.package-count { background: rgba(201, 168, 76, 0.15); padding: 4px 12px; border-radius: 20px; font-size: 0.85rem; color: #fef08a; font-weight: 600; }

/* CONTROL PANELS */
.control-panel, .alert-panel { padding: 30px; margin-bottom: 30px; border-radius: 24px; }
.control-title { font-size: 1.2rem; font-weight: 700; margin-bottom: 25px; display: flex; align-items: center; gap: 10px; color: #fff; }
.control-title i { color: var(--p-primary); }

.status-option, .alert-type {
    padding: 20px; border: 2px solid transparent; border-radius: 20px; cursor: pointer;
    transition: all 0.3s ease; text-align: center; background: rgba(255, 255, 255, 0.03); flex: 1; min-width: 120px;
}
.status-option:hover, .alert-type:hover { background: rgba(255, 255, 255, 0.08); }

.status-option.selected { border-color: var(--p-primary); background: rgba(201, 168, 76, 0.1); transform: scale(1.02); }
.status-option i { font-size: 2rem; margin-bottom: 12px; display: block; }
.status-option.online i { color: #4ade80; }
.status-option.offline i { color: #f87171; }
.status-option.maintenance i { color: #facc15; }
.status-option h6 { font-weight: 700; margin-bottom: 5px; }

.alert-types { display: flex; gap: 15px; margin-bottom: 20px; flex-wrap: wrap; }
.alert-type.info.selected { border-color: #3b82f6; background: rgba(59, 130, 246, 0.15); transform: scale(1.02); }
.alert-type.warning.selected { border-color: #eab308; background: rgba(234, 179, 8, 0.15); transform: scale(1.02); }
.alert-type.danger.selected { border-color: #ef4444; background: rgba(239, 68, 68, 0.15); transform: scale(1.02); }
.alert-type.success.selected { border-color: #22c55e; background: rgba(34, 197, 94, 0.15); transform: scale(1.02); }
.alert-type i { font-size: 1.8rem; margin-bottom: 8px; display: block; }
.alert-type.info i { color: #3b82f6; }
.alert-type.warning i { color: #eab308; }
.alert-type.danger i { color: #ef4444; }
.alert-type.success i { color: #22c55e; }
.alert-type span { font-weight: 600; font-size: 0.9rem; }

/* FORM ELEMENTS */
.form-control {
    background: rgba(0, 0, 0, 0.3) !important; border: 1px solid rgba(255, 255, 255, 0.1) !important;
    color: #fff !important; border-radius: 16px !important; padding: 14px 16px !important; font-family: 'Montserrat', sans-serif;
    transition: all 0.3s ease;
}
.form-control:focus {
    background: rgba(0, 0, 0, 0.5) !important; border-color: var(--p-primary) !important;
    box-shadow: 0 0 0 3px rgba(201, 168, 76, 0.2) !important; outline: none !important;
}

/* ── BUTTONS v2 ── */
.btn-ios, .btn-apply, .btn-alert {
    position:relative; overflow:hidden; display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:14px 28px; border:none; border-radius:14px; font-family:'Montserrat',sans-serif; font-size:.95rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer; text-decoration:none; transition:all .25s ease;
}
.btn-apply { background: linear-gradient(135deg, #10b981 0%, #059669 100%); box-shadow:0 4px 20px rgba(16, 185, 129, 0.3); }
.btn-apply:hover { transform:translateY(-3px) scale(1.02); box-shadow:0 12px 32px rgba(16, 185, 129, 0.5); }
.btn-alert { background: linear-gradient(135deg, #6366f1 0%, #4338ca 100%); box-shadow:0 4px 20px rgba(99, 102, 241, 0.3); }
.btn-alert:hover { transform:translateY(-3px) scale(1.02); box-shadow:0 12px 32px rgba(99, 102, 241, 0.5); }
.btn-danger-glass {
    position:relative; display:inline-flex; align-items:center; justify-content:center; gap:9px; padding:13px 28px;
    background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.4); border-radius: 14px;
    color: #fca5a5; font-weight: 700; transition: all 0.3s ease; cursor: pointer; text-decoration: none;
}
.btn-danger-glass:hover { background: rgba(239, 68, 68, 0.3); border-color: #ef4444; color: #fff; transform: translateY(-3px); box-shadow: 0 10px 25px rgba(239,68,68,0.3); }

/* MODAL OVERRIDES */
.modal-content { background:rgba(10,16,34,.97) !important; border:1px solid rgba(255,255,255,.1) !important; border-radius:20px !important; backdrop-filter:blur(24px) !important; color:#fff; }
.modal-header { border-bottom:1px solid rgba(255,255,255,.08) !important; }
.modal-footer { border-top:1px solid rgba(255,255,255,.08) !important; gap:10px; }
.modal-title { font-weight:700; color:var(--p-primary); }
.btn-back { position:relative; display:inline-flex; align-items:center; gap:9px; padding:11px 26px; background:rgba(255,255,255,.07); border:1px solid rgba(255,255,255,.16); border-radius:50px; color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600; text-decoration:none; cursor:pointer; transition:all .25s; backdrop-filter:blur(10px); }
.btn-back:hover { background:rgba(255,255,255,.15); color:#fff; transform:translateY(-2px); }
.btn-danger-modal { background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); border:none; padding:11px 26px; border-radius:50px; color:#fff; font-weight:600; font-size:.85rem; transition:all .25s; }
.btn-danger-modal:hover { transform:translateY(-2px); box-shadow:0 8px 20px rgba(239,68,68,0.4); }

.watermark { position:fixed; bottom:12px; left:0; right:0; text-align:center; font-size:.6rem; letter-spacing:.15em; text-transform:uppercase; color:rgba(255,255,255,0.07); z-index:1; pointer-events:none; }
::-webkit-scrollbar { width:8px; height:8px; }
::-webkit-scrollbar-track { background:rgba(255,255,255,0.05); border-radius:10px; }
::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:10px; }
::-webkit-scrollbar-thumb:hover { background:rgba(201,168,76,0.6); }

@media (max-width: 768px) {
    .alert-types { flex-direction: column; }
    .stats-grid { grid-template-columns: 1fr; }
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
        <span class="header-title"><i class="fas fa-server me-2" style="font-size:.85rem;"></i>Controller</span>
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
                <img src="<?= htmlspecialchars($P['sidebar_logo_url'] ?? 'logo.svg') ?>" alt="Logo"
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
    <a class="nav-btn" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn active-page" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn" href="announcements.php" style="position:relative;">
        <i class="fas fa-bullhorn"></i> Announcements
        <?php if ($unread_ann_count > 0): ?>
        <span class="nav-badge"><?= min($unread_ann_count,9) ?></span>
        <?php endif; ?>
    </a>

    <div class="sidebar-nav-label">Config</div>
    <a class="nav-btn" href="panel_customizer.php"><i class="fas fa-paint-brush"></i> Customizer</a>
    <a class="nav-btn" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>

<div class="main">

    <?php if (isset($success)): ?>
        <div class="alert alert-success alert-dismissible fade show" role="alert">
            <i class="fas fa-check-circle me-2"></i> <?=$success?>
            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="alert"></button>
        </div>
    <?php endif; ?>

    <?php if (isset($alert_sent)): ?>
        <div class="alert alert-info alert-dismissible fade show" role="alert">
            <i class="fas fa-bell me-2"></i> Alert broadcasted successfully!
            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="alert"></button>
        </div>
    <?php endif; ?>

    <?php if ($server_status != 'online'): ?>
        <div class="warning-banner">
            <i class="fas fa-exclamation-triangle fa-3x" style="color: #f87171;"></i>
            <div>
                <h5 style="font-weight: 800; margin-bottom: 5px; color: #fff;">SERVER IS IN <?=strtoupper($server_status)?> MODE</h5>
                <p class="mb-0 opacity-75" style="font-size: 0.95rem;"><?=htmlspecialchars($maintenance_message ?: 'All SDK keys are currently blocked.')?></p>
            </div>
        </div>
    <?php endif; ?>

    <div class="status-card glass">
        <h5 class="control-title"><i class="fas fa-server"></i> Server Overview</h5>
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-plug"></i></div>
                <div class="stat-value"><?=$total_devices?></div>
                <div class="stat-label">Connected Devices</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-key"></i></div>
                <div class="stat-value"><?=$active_licenses?></div>
                <div class="stat-label">Active Licenses</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-database"></i></div>
                <div class="stat-value"><?=$total_licenses?></div>
                <div class="stat-label">Total Licenses</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon" style="color:#f87171; background:rgba(239,68,68,0.1);"><i class="fas fa-ban"></i></div>
                <div class="stat-value" style="color:#fca5a5;"><?=$blocked_by_server?></div>
                <div class="stat-label">Blocked by Server</div>
            </div>
        </div>

        <?php if (!empty($devices_by_package)): ?>
        <div class="package-devices">
            <h6 class="control-title" style="font-size: 1.05rem; margin-bottom: 15px;"><i class="fas fa-boxes"></i> Devices by Package</h6>
            <?php foreach ($devices_by_package as $package => $count): ?>
            <div class="package-item">
                <span class="package-name"><i class="fas fa-box me-2 opacity-50"></i><?=htmlspecialchars($package)?></span>
                <span class="package-count"><?=$count?> device<?=$count>1?'s':''?></span>
            </div>
            <?php endforeach; ?>
        </div>
        <?php endif; ?>
    </div>

    <div class="control-panel glass">
        <h5 class="control-title"><i class="fas fa-sliders-h"></i> Status Controller</h5>

        <form method="post" id="serverForm">
            <div class="row g-3 mb-4">
                <div class="col-md-4">
                    <div class="status-option online <?=$server_status=='online'?'selected':''?>" onclick="selectStatus('online')">
                        <i class="fas fa-check-circle"></i>
                        <h6>Online Mode</h6>
                        <small class="opacity-50">All services running</small>
                    </div>
                </div>
                <div class="col-md-4">
                    <div class="status-option offline <?=$server_status=='offline'?'selected':''?>" onclick="selectStatus('offline')">
                        <i class="fas fa-times-circle"></i>
                        <h6>Offline Mode</h6>
                        <small class="opacity-50">All services stopped</small>
                    </div>
                </div>
                <div class="col-md-4">
                    <div class="status-option maintenance <?=$server_status=='maintenance'?'selected':''?>" onclick="selectStatus('maintenance')">
                        <i class="fas fa-tools"></i>
                        <h6>Maintenance</h6>
                        <small class="opacity-50">Under maintenance</small>
                    </div>
                </div>
            </div>

            <input type="hidden" name="server_status" id="server_status" value="<?=$server_status?>">
            <input type="hidden" name="update_status" value="1">

            <div class="mb-4">
                <label class="form-label" style="font-weight: 600; color: rgba(255,255,255,0.8);">Maintenance Message</label>
                <textarea class="form-control" name="maintenance_message" rows="3" placeholder="Enter message to display to users..."><?=htmlspecialchars($maintenance_message)?></textarea>
                <small class="opacity-50 mt-2 d-block"><i class="fas fa-info-circle me-1"></i>This message will be shown to users when the server is offline or in maintenance mode.</small>
            </div>

            <div class="d-flex gap-3 flex-wrap mt-4">
                <button type="submit" class="btn-apply">
                    <i class="fas fa-save"></i> Apply Changes
                </button>

                <?php if($server_status != 'offline'): ?>
                <button type="button" class="btn-danger-glass" onclick="emergencyShutdown()">
                    <i class="fas fa-exclamation-triangle"></i> Emergency Shutdown
                </button>
                <?php endif; ?>
            </div>
        </form>
    </div>

    <div class="alert-panel glass">
        <h5 class="control-title"><i class="fas fa-bullhorn"></i> Broadcast Alert</h5>

        <form method="post">
            <input type="hidden" name="test_alert" value="1">
            <input type="hidden" name="alert_type" id="alert_type" value="info">

            <div class="alert-types">
                <div class="alert-type info selected" onclick="selectAlertType('info')">
                    <i class="fas fa-info-circle"></i>
                    <span>Info</span>
                </div>
                <div class="alert-type warning" onclick="selectAlertType('warning')">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>Warning</span>
                </div>
                <div class="alert-type danger" onclick="selectAlertType('danger')">
                    <i class="fas fa-times-circle"></i>
                    <span>Danger</span>
                </div>
                <div class="alert-type success" onclick="selectAlertType('success')">
                    <i class="fas fa-check-circle"></i>
                    <span>Success</span>
                </div>
            </div>

            <div class="mb-4">
                <label class="form-label" style="font-weight: 600; color: rgba(255,255,255,0.8);">Alert Message</label>
                <input type="text" class="form-control" name="alert_message" placeholder="Enter broadcast message..." required>
            </div>

            <div class="d-flex mt-4">
                <button type="submit" class="btn-alert">
                    <i class="fas fa-paper-plane"></i> Send Alert
                </button>
            </div>
        </form>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text'] ?? 'ONEBOX PANEL') ?></div>

<div class="modal fade" id="emergencyModal" tabindex="-1">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content text-white">
            <div class="modal-header border-0">
                <h5 class="modal-title"><i class="fas fa-exclamation-triangle text-danger me-2"></i>Emergency Shutdown</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p class="fw-bold mb-3" style="font-size: 1.1rem;">⚠️ This will immediately:</p>
                <ul class="text-start mb-4" style="background: rgba(239, 68, 68, 0.1); padding: 15px 30px; border-radius: 12px; border: 1px solid rgba(239, 68, 68, 0.2);">
                    <li class="mb-2">Block all active licenses</li>
                    <li class="mb-2">Disconnect all active devices</li>
                    <li class="mb-2">Set the server to offline mode</li>
                    <li>Prevent all new connections</li>
                </ul>
                <p class="text-danger fw-bold text-center" style="font-size: 1.1rem;">This action cannot be undone automatically!</p>
            </div>
            <div class="modal-footer border-0">
                <button type="button" class="btn-back" data-bs-dismiss="modal" style="padding: 11px 22px;">Cancel</button>
                <button type="button" class="btn-danger-modal" onclick="confirmEmergency()">Confirm Shutdown</button>
            </div>
        </div>
    </div>
</div>

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

// Control selection logic
function selectStatus(status) {
    document.getElementById('server_status').value = status;
    document.querySelectorAll('.status-option').forEach(el => el.classList.remove('selected'));
    document.querySelector(`.status-option.${status}`).classList.add('selected');
}

function selectAlertType(type) {
    document.getElementById('alert_type').value = type;
    document.querySelectorAll('.alert-type').forEach(el => el.classList.remove('selected'));
    document.querySelector(`.alert-type.${type}`).classList.add('selected');
}

// Emergency Shutdown Logic
function emergencyShutdown() {
    new bootstrap.Modal(document.getElementById('emergencyModal')).show();
}

function confirmEmergency() {
    document.getElementById('server_status').value = 'offline';
    const messageField = document.querySelector('textarea[name="maintenance_message"]');
    messageField.value = '🚨 EMERGENCY SHUTDOWN - Server is down for immediate maintenance';
    document.getElementById('serverForm').submit();
}

// Close overlays on click outside
document.getElementById('overlay').onclick = function() {
    closeSidebar();
};
</script>

<script>
/* ✦ ONEBOX Ripple v2 */
(function(){
  document.addEventListener('click',function(e){
    var btn=e.target.closest('.btn-ios,.btn-apply,.btn-alert,.glass-btn,.btn-back,.btn-danger-modal,.btn-danger-glass');
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
