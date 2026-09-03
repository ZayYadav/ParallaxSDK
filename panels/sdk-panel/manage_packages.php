<?php
session_start();
if (!isset($_SESSION['username'])) {
    header('Location: login.php');
    exit();
}
include('conn.php');
include('panel_helper.php');
panel_require_roles($conn, ['owner', 'admin']);

$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$license_id = intval($_GET['license_id'] ?? 0);

// Toggle package active/deactive
if (isset($_POST['toggle'])) {
    $pkg_id = intval($_POST['id']);
    $status = intval($_POST['status']);
    if (!in_array($status, [0, 1], true)) { http_response_code(400); exit('INVALID_STATUS'); }
    $toggleStmt = $conn->prepare('UPDATE activated_packages SET is_allowed = ? WHERE id = ?');
    $toggleStmt->bind_param('ii', $status, $pkg_id);
    $toggleStmt->execute();
    $toggleStmt->close();
    header("Location: manage_packages.php?license_id=$license_id");
    exit;
}

// Fetch packages for this license
$pkgStmt = $conn->prepare('SELECT * FROM activated_packages WHERE license_id = ? ORDER BY id DESC');
$pkgStmt->bind_param('i', $license_id); $pkgStmt->execute(); $pkgs = $pkgStmt->get_result();
$pkg_count = $pkgs ? mysqli_num_rows($pkgs) : 0;

// Get license info
$lic_row = null;
if ($license_id) {
    $licStmt = $conn->prepare('SELECT * FROM licenses WHERE id = ? LIMIT 1');
    $licStmt->bind_param('i', $license_id); $licStmt->execute(); $lic_row = $licStmt->get_result()->fetch_assoc(); $licStmt->close();
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Manage Packages • <?= htmlspecialchars($P['panel_name']) ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>
<?= file_get_contents(__DIR__.'/styles.css') ?>

*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }
body {
    font-family:'Montserrat',sans-serif;
    background:linear-gradient(135deg, var(--p-bg1) 0%, var(--p-bg2) 35%, var(--p-bg3) 70%, var(--p-bg4) 100%);
    background-size:400% 400%; animation:bgShift 20s ease infinite;
    min-height:100vh; color:#fff; overflow-x:hidden;
}
@keyframes bgShift { 0%{background-position:0% 50%} 50%{background-position:100% 50%} 100%{background-position:0% 50%} }

.bg-orbs { position:fixed; inset:0; z-index:0; pointer-events:none; overflow:hidden; }
.orb { position:absolute; border-radius:50%; filter:blur(100px); animation:orbFloat var(--dur,16s) ease-in-out infinite; animation-delay:var(--delay,0s); will-change:transform; }
.orb1 { width:600px; height:600px; top:-200px; left:-150px; background:radial-gradient(circle, rgba(201,168,76,0.12) 0%, transparent 70%); --dur:18s; --delay:0s; }
.orb2 { width:500px; height:500px; bottom:-150px; right:-100px; background:radial-gradient(circle, rgba(79,142,247,0.1) 0%, transparent 70%); --dur:22s; --delay:4s; }
.orb3 { width:400px; height:400px; top:40%; left:40%; background:radial-gradient(circle, rgba(124,58,237,0.07) 0%, transparent 70%); --dur:26s; --delay:8s; }
@keyframes orbFloat { 0%,100%{transform:translate(0,0) scale(1)} 33%{transform:translate(30px,-40px) scale(1.05)} 66%{transform:translate(-20px,25px) scale(0.97)} }

.glass { background:rgba(15,23,42,0.72); backdrop-filter:blur(24px) saturate(180%); -webkit-backdrop-filter:blur(24px) saturate(180%); border:1px solid rgba(255,255,255,0.1); border-radius:20px; box-shadow:0 8px 32px rgba(0,0,0,0.35); }

header { position:fixed; top:0; left:0; right:0; height:62px; z-index:1000; display:flex; align-items:center; justify-content:space-between; padding:0 20px; background:rgba(8,12,24,0.88); backdrop-filter:blur(24px) saturate(200%); border-bottom:1px solid rgba(255,255,255,0.07); box-shadow:0 2px 20px rgba(0,0,0,0.4); }
.header-left { display:flex; align-items:center; gap:14px; }
.menu-btn { background:none; border:none; color:var(--p-primary); font-size:1.4rem; cursor:pointer; width:40px; height:40px; border-radius:10px; display:flex; align-items:center; justify-content:center; transition:all .25s cubic-bezier(.34,1.5,.64,1); }
.menu-btn:hover { background:rgba(255,255,255,0.08); transform:scale(1.1) rotate(90deg); }
.header-title { font-size:1rem; font-weight:700; letter-spacing:.08em; color:var(--p-primary); text-transform:uppercase; }
.user-badge { display:flex; align-items:center; gap:8px; background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.1); border-radius:50px; padding:7px 16px; font-size:.82rem; font-weight:600; transition:all .25s; cursor:default; }
.user-badge:hover { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.3); }
.user-badge i { color:var(--p-primary); }

#sidebar { position:fixed; top:62px; left:-268px; bottom:0; width:248px; background:rgba(6,10,20,0.97); backdrop-filter:blur(24px); border-right:1px solid rgba(255,255,255,0.07); z-index:999; overflow-y:auto; overflow-x:hidden; transition:left .32s cubic-bezier(.4,0,.2,1); padding:16px 12px 80px; scrollbar-width:thin; scrollbar-color:rgba(201,168,76,0.3) transparent; }
#sidebar.active { left:0; }
#sidebar::-webkit-scrollbar { width:4px; }
#sidebar::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:4px; }
#overlay { position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:998; display:none; backdrop-filter:blur(4px); }
#overlay.active { display:block; }

.sidebar-logo-section { text-align:center; padding:12px 8px 20px; border-bottom:1px solid rgba(255,255,255,0.07); margin-bottom:14px; }
.sidebar-logo-ring { position:relative; width:80px; height:80px; margin:0 auto 12px; border-radius:50%; background:conic-gradient(from 0deg, var(--p-primary), var(--p-accent), var(--p-primary)); animation:spinRing 5s linear infinite; padding:3px; }
@keyframes spinRing { to { transform:rotate(360deg); } }
.sidebar-logo-inner { width:100%; height:100%; border-radius:50%; background:var(--p-bg1); overflow:hidden; display:flex; align-items:center; justify-content:center; }
.sidebar-logo-inner img { width:100%; height:100%; object-fit:cover; border-radius:50%; display:block; }
.sidebar-logo-fallback { font-size:1.8rem; color:var(--p-primary); display:none; }
.sidebar-panel-name { font-size:.85rem; font-weight:700; color:var(--p-primary); margin-bottom:4px; }
.sidebar-user-chip { display:inline-flex; align-items:center; gap:5px; font-size:.68rem; font-weight:600; letter-spacing:.08em; color:rgba(255,255,255,0.5); background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.08); border-radius:30px; padding:3px 10px; }
.sidebar-nav-label { font-size:.62rem; font-weight:700; letter-spacing:.15em; color:rgba(255,255,255,0.25); text-transform:uppercase; padding:10px 12px 6px; margin-top:6px; }
.nav-btn { width:100%; padding:11px 14px; margin-bottom:4px; background:rgba(255,255,255,0.04); border:1px solid transparent; border-radius:13px; color:rgba(255,255,255,0.75); text-align:left; cursor:pointer; font-family:'Montserrat',sans-serif; font-size:.83rem; font-weight:600; display:flex; align-items:center; gap:10px; transition:all .22s cubic-bezier(.34,1.2,.64,1); position:relative; overflow:hidden; text-decoration:none; }
.nav-btn::before { content:''; position:absolute; left:0; top:0; bottom:0; width:3px; background:var(--p-primary); border-radius:0 3px 3px 0; transform:scaleY(0); transition:transform .2s; }
.nav-btn:hover,.nav-btn:focus { background:rgba(255,255,255,0.09); border-color:rgba(255,255,255,0.1); color:#fff; transform:translateX(4px); }
.nav-btn:hover::before { transform:scaleY(1); }
.nav-btn.active-page { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.25); color:var(--p-primary); }
.nav-btn.active-page::before { transform:scaleY(1); }
.nav-btn i { width:18px; text-align:center; color:var(--p-primary); font-size:.9rem; opacity:.85; }

.main { position:relative; z-index:1; padding:80px 16px 40px; max-width:860px; margin:0 auto; }

.page-title { font-size:1.6rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase; background:linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%); background-size:200% auto; -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; animation:shimmer 4s linear infinite; margin-bottom:24px; text-align:center; }
@keyframes shimmer { to { background-position:200% center; } }

.section-header { display:flex; align-items:center; gap:10px; margin:0 0 18px; padding-bottom:12px; border-bottom:1px solid rgba(255,255,255,0.08); }
.section-header i { color:var(--p-primary); }
.section-header h4 { margin:0; font-size:.95rem; font-weight:700; letter-spacing:.06em; text-transform:uppercase; }
.section-header small { margin-left:auto; font-size:.7rem; color:rgba(255,255,255,.3); font-weight:600; }

.pkg-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(280px,1fr)); gap:14px; }

.pkg-card { padding:20px; animation:cardIn .5s cubic-bezier(.34,1.2,.64,1) both; animation-delay:calc(var(--i,0) * 60ms); transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s; position:relative; }
.pkg-card:hover { transform:translateY(-3px); box-shadow:0 16px 40px rgba(0,0,0,.5); }
@keyframes cardIn { from{opacity:0;transform:translateY(20px) scale(.97)} to{opacity:1;transform:none} }

.pkg-name { font-size:.9rem; font-weight:700; color:var(--p-primary); margin-bottom:10px; display:flex; align-items:center; gap:8px; }
.pkg-detail { display:flex; align-items:center; gap:8px; font-size:.8rem; color:rgba(255,255,255,.75); margin:6px 0; }
.pkg-detail i { width:16px; text-align:center; color:var(--p-primary); opacity:.7; font-size:.8rem; }
.pkg-divider { height:1px; background:rgba(255,255,255,.07); margin:12px 0; }
.pkg-footer { display:flex; align-items:center; gap:10px; margin-top:4px; }

.lic-info-bar { padding:16px 20px; margin-bottom:20px; display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
.lic-info-bar i { color:var(--p-primary); }
.lic-key-text { font-family:monospace; font-size:.9rem; font-weight:700; color:var(--p-primary); }

.empty-state { text-align:center; padding:60px 20px; }
.empty-state i { font-size:3rem; color:rgba(255,255,255,.15); margin-bottom:16px; display:block; }
.empty-state p { color:rgba(255,255,255,.4); font-size:.9rem; }

.watermark { position:fixed; bottom:12px; left:0; right:0; text-align:center; font-size:.6rem; letter-spacing:.15em; text-transform:uppercase; color:rgba(255,255,255,0.07); z-index:1; pointer-events:none; }
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

<!-- HEADER -->
<header>
    <div class="header-left">
        <button class="menu-btn" onclick="toggleSidebar()" aria-label="Menu">
            <i class="fas fa-bars" id="menuIcon"></i>
        </button>
        <span class="header-title"><i class="fas fa-cubes me-2" style="font-size:.85rem;"></i>Manage Packages</span>
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
    <a class="nav-btn" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn active-page" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>
    <div class="sidebar-nav-label">Management</div>
    <a class="nav-btn" href="manage_users.php"><i class="fas fa-circle-user"></i> Manage Users</a>
    <a class="nav-btn" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn" href="announcements.php"><i class="fas fa-bullhorn"></i> Announcements</a>
    <div class="sidebar-nav-label">Config</div>
    <a class="nav-btn" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>

<!-- MAIN -->
<div class="main">
    <div class="page-title">Activated Packages</div>

    <?php if ($lic_row): ?>
    <div class="lic-info-bar glass" style="margin-bottom:20px;">
        <i class="fas fa-id-card"></i>
        <div>
            <div style="font-size:.7rem;color:rgba(255,255,255,.4);font-weight:700;letter-spacing:.08em;text-transform:uppercase;margin-bottom:2px;">License</div>
            <div class="lic-key-text"><?= htmlspecialchars($lic_row['license_key']) ?></div>
        </div>
        <span style="margin-left:auto;font-size:.75rem;color:rgba(255,255,255,.4);">
            <i class="fas fa-cubes me-1"></i> <?= $pkg_count ?> package<?= $pkg_count != 1 ? 's' : '' ?>
        </span>
    </div>
    <?php endif; ?>

    <div class="section-header">
        <i class="fas fa-cubes"></i>
        <h4>Activated Packages</h4>
        <small><?= $pkg_count ?> total</small>
    </div>

    <?php if ($pkg_count === 0): ?>
    <div class="empty-state glass">
        <i class="fas fa-box-open"></i>
        <p>No activated packages found for this license.</p>
    </div>
    <?php else: ?>
    <div class="pkg-grid">
    <?php
    $i = 0;
    // Re-fetch since pointer may be at end
    $pkgs2Stmt = $conn->prepare('SELECT * FROM activated_packages WHERE license_id = ? ORDER BY id DESC');
    $pkgs2Stmt->bind_param('i', $license_id); $pkgs2Stmt->execute(); $pkgs2 = $pkgs2Stmt->get_result();
    while ($pkg = mysqli_fetch_assoc($pkgs2)):
        $i++;
        $isAllowed = (int)($pkg['is_allowed'] ?? 1);
    ?>
        <div class="pkg-card glass" style="--i:<?= $i ?>">
            <div class="pkg-name">
                <i class="fas fa-box"></i>
                <?= htmlspecialchars($pkg['package_name']) ?>
            </div>
            <div class="pkg-detail"><i class="fas fa-chart-bar"></i> Usage Count: <strong style="color:#fff;margin-left:4px;"><?= intval($pkg['usage_count'] ?? 0) ?></strong></div>
            <div class="pkg-detail"><i class="fas fa-fingerprint"></i> Device: <code style="color:var(--p-accent);margin-left:4px;font-size:.75rem;"><?= htmlspecialchars($pkg['device_id'] ?? '—') ?></code></div>
            <?php if (!empty($pkg['last_used'])): ?>
            <div class="pkg-detail"><i class="fas fa-clock"></i><?= date('d M Y · H:i', strtotime($pkg['last_used'])) ?></div>
            <?php endif; ?>
            <?php if (!empty($pkg['ip_address'])): ?>
            <div class="pkg-detail"><i class="fas fa-network-wired"></i><?= htmlspecialchars($pkg['ip_address']) ?></div>
            <?php endif; ?>
            <div class="pkg-divider"></div>
            <div class="pkg-footer">
                <form method="POST" style="margin:0">
                    <input type="hidden" name="id" value="<?= $pkg['id'] ?>">
                    <input type="hidden" name="status" value="<?= $isAllowed ? 0 : 1 ?>">
                    <button type="submit" name="toggle"
                        class="glass-btn <?= $isAllowed ? 'btn-delete-glass' : 'btn-approve-glass' ?>">
                        <i class="fas <?= $isAllowed ? 'fa-ban' : 'fa-check' ?>"></i>
                        <?= $isAllowed ? 'Block' : 'Allow' ?>
                    </button>
                </form>
                <span class="device-pill <?= $isAllowed ? 'active' : 'none' ?>" style="margin-left:auto;">
                    <i class="fas fa-circle" style="font-size:.4rem;"></i>
                    <?= $isAllowed ? 'Allowed' : 'Blocked' ?>
                </span>
            </div>
        </div>
    <?php endwhile; ?>
    </div>
    <?php endif; ?>

    <div class="text-center mt-4">
        <a href="license_list.php" class="btn-back">
            <i class="fas fa-arrow-left"></i> Back to Licenses
        </a>
    </div>
</div>

<style>
.device-pill { display:inline-flex; align-items:center; gap:5px; font-size:.72rem; font-weight:700; letter-spacing:.05em; border-radius:30px; padding:4px 12px; }
.device-pill.active { background:rgba(52,211,153,.12); color:#4ade80; border:1px solid rgba(52,211,153,.25); }
.device-pill.none { background:rgba(239,68,68,.1); color:#fca5a5; border:1px solid rgba(239,68,68,.25); }
.btn-approve-glass { color:#6ee7b7; border-color:rgba(16,185,129,.35); background:rgba(16,185,129,.1); }
.btn-approve-glass:hover { background:rgba(16,185,129,.28); border-color:rgba(16,185,129,.6); box-shadow:0 0 18px rgba(16,185,129,.45); color:#d1fae5; }
</style>

<div class="watermark"><?= htmlspecialchars($P['watermark_text']) ?></div>

<script>
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
</script>
<script>
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
