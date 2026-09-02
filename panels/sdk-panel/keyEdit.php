<?php
include('conn.php');
if (!isset($_SESSION['username'])) {
    header('Location: login.php');
    exit();
}
include('panel_helper.php');
panel_require_roles($conn, ['owner', 'admin']);

$P = get_panel_settings($conn);
$current_user = $_SESSION['username'];
$active_page = 'license_list.php'; // highlight parent

// Update license
if(isset($_POST['update'])){
    $id = intval($_POST['id']);
    $license_key = strtoupper(trim((string) ($_POST['license_key'] ?? '')));
    $client_name = trim((string) ($_POST['client_name'] ?? ''));
    $expiry_date = trim((string) ($_POST['expiry_date'] ?? ''));
    $status = intval($_POST['status'] ?? 0);
    $package_mode = strtoupper(trim((string) ($_POST['package_mode'] ?? 'SPECIFIC')));
    $package_lock = $package_mode === 'SPECIFIC' ? 1 : 0;
    $package_name = $package_lock ? trim((string) ($_POST['package_name'] ?? '')) : '';
    $signing_mode = strtolower(trim((string) ($_POST['signing_mode'] ?? 'auto')));
    $signing_cert = strtoupper((string) preg_replace(
        '/[^A-Fa-f0-9]/',
        '',
        (string) ($_POST['signing_cert_sha256'] ?? '')
    ));
    $signing_lock = $signing_mode === 'any' ? 0 : 1;
    $signing_cert = $signing_mode === 'pinned' ? $signing_cert : null;
    $signing_mode_db = $signing_mode === 'pinned' ? 'SPECIFIC' : strtoupper($signing_mode);
    $device_mode = strtoupper(trim((string) ($_POST['device_mode'] ?? 'SINGLE')));
    $max_devices = max(1, min(100, (int) ($_POST['max_devices'] ?? 1)));
    $java_native_auth = isset($_POST['java_native_auth']) ? 1 : 0;
    $minimum_sdk_version = max(1, min(1000000, (int) ($_POST['minimum_sdk_version'] ?? 3)));
    $latest_sdk_version = max($minimum_sdk_version, min(1000000, (int) ($_POST['latest_sdk_version'] ?? 3)));
    $force_update = isset($_POST['force_update']) ? 1 : 0;
    $session_lifetime = max(600, min(1800, (int) ($_POST['session_lifetime_seconds'] ?? 600)));
    $kill_switch = isset($_POST['kill_switch']) ? 1 : 0;
    $featurePolicy = json_decode(trim((string) ($_POST['feature_policy'] ?? '{}')) ?: '{}', true);
    $blockedVersions = array_values(array_unique(array_filter(array_map(
        'intval', preg_split('/[\s,]+/', trim((string) ($_POST['blocked_versions'] ?? ''))) ?: []
    ), static fn(int $version): bool => $version > 0 && $version <= 1000000)));
    if (preg_match('/^[A-Z0-9_-]{4,96}$/D', $license_key) !== 1
        || $client_name === '' || mb_strlen($client_name) > 120
        || !in_array($package_mode, ['SPECIFIC', 'ANY'], true)
        || ($package_lock === 1 && preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $package_name) !== 1)
        || !in_array($signing_mode, ['pinned', 'auto', 'any'], true)
        || ($signing_mode === 'pinned' && preg_match('/^[A-F0-9]{64}$/D', (string) $signing_cert) !== 1)
        || !in_array($device_mode, ['DISABLED', 'SINGLE', 'LIMITED', 'UNLIMITED'], true)
        || !is_array($featurePolicy)
        || !in_array($status, [0, 1, 2], true)) {
        http_response_code(400);
        exit('INVALID_LICENSE_UPDATE');
    }
    foreach ($featurePolicy as $feature => $value) {
        if (!is_string($feature) || preg_match('/^[A-Za-z0-9_.-]{1,64}$/D', $feature) !== 1
            || !(is_bool($value) || is_int($value) || is_string($value))) {
            http_response_code(400);
            exit('INVALID_FEATURE_POLICY');
        }
    }
    $featurePolicyJson = $featurePolicy === []
        ? '{}'
        : json_encode($featurePolicy, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    $blockedVersionsJson = json_encode($blockedVersions, JSON_THROW_ON_ERROR);
    $updateStmt = $conn->prepare(
        'UPDATE licenses
         SET license_key = ?, client_name = ?, expiry_date = ?, status = ?, package_name = ?,
             package_lock = ?, package_mode = ?, signing_lock = ?, signing_mode = ?,
             signing_cert_sha256 = ?, device_mode = ?, max_devices = ?, java_native_auth = ?,
             feature_policy = ?, minimum_sdk_version = ?, latest_sdk_version = ?,
             force_update = ?, blocked_versions = ?, session_lifetime_seconds = ?, kill_switch = ?
         WHERE id = ?'
    );
    $updateStmt->bind_param(
        str_repeat('s', 21),
        $license_key,
        $client_name,
        $expiry_date,
        $status,
        $package_name,
        $package_lock,
        $package_mode,
        $signing_lock,
        $signing_mode_db,
        $signing_cert,
        $device_mode,
        $max_devices,
        $java_native_auth,
        $featurePolicyJson,
        $minimum_sdk_version,
        $latest_sdk_version,
        $force_update,
        $blockedVersionsJson,
        $session_lifetime,
        $kill_switch,
        $id
    );
    $updateStmt->execute();
    $updateStmt->close();

    header('Location: license_list.php');
    exit;
}

// Fetch license
$id = intval($_GET['id'] ?? 0);
$selectStmt = $conn->prepare('SELECT * FROM licenses WHERE id = ? LIMIT 1');
$selectStmt->bind_param('i', $id);
$selectStmt->execute();
$row = $selectStmt->get_result()->fetch_assoc();
$selectStmt->close();
if(!$row){
    header('Location: license_list.php');
    exit;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Edit License • <?= htmlspecialchars($P['panel_name']) ?></title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
<?= panel_css_vars($P) ?>
<?= file_get_contents(__DIR__.'/styles.css') ?>

/* ── page-level layout ── */
*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }

body {
    font-family:'Montserrat',sans-serif;
    background: linear-gradient(135deg, var(--p-bg1) 0%, var(--p-bg2) 35%, var(--p-bg3) 70%, var(--p-bg4) 100%);
    background-size:400% 400%;
    animation:bgShift 20s ease infinite;
    min-height:100vh; color:#fff; overflow-x:hidden;
}
@keyframes bgShift {
    0%   { background-position: 0% 50%; }
    50%  { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

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

.glass {
    background: rgba(15,23,42,0.72);
    backdrop-filter: blur(24px) saturate(180%);
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 20px;
    box-shadow: 0 8px 32px rgba(0,0,0,0.35);
}

header {
    position:fixed; top:0; left:0; right:0; height:62px; z-index:1000;
    display:flex; align-items:center; justify-content:space-between;
    padding:0 20px;
    background:rgba(8,12,24,0.88);
    backdrop-filter:blur(24px) saturate(200%);
    border-bottom:1px solid rgba(255,255,255,0.07);
    box-shadow:0 2px 20px rgba(0,0,0,0.4);
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
.header-title { font-size:1rem; font-weight:700; letter-spacing:.08em; color:var(--p-primary); text-transform:uppercase; }
.user-badge {
    display:flex; align-items:center; gap:8px;
    background:rgba(255,255,255,0.06);
    border:1px solid rgba(255,255,255,0.1);
    border-radius:50px; padding:7px 16px;
    font-size:.82rem; font-weight:600;
    transition: all .25s; cursor:default;
}
.user-badge:hover { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.3); }
.user-badge i { color:var(--p-primary); }

#sidebar {
    position:fixed; top:62px; left:-268px; bottom:0; width:248px;
    background:rgba(6,10,20,0.97);
    backdrop-filter:blur(24px);
    border-right:1px solid rgba(255,255,255,0.07);
    z-index:999; overflow-y:auto; overflow-x:hidden;
    transition:left .32s cubic-bezier(.4,0,.2,1);
    padding:16px 12px 80px;
    scrollbar-width:thin; scrollbar-color:rgba(201,168,76,0.3) transparent;
}
#sidebar.active { left:0; }
#sidebar::-webkit-scrollbar { width:4px; }
#sidebar::-webkit-scrollbar-thumb { background:rgba(201,168,76,0.3); border-radius:4px; }
#overlay { position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:998; display:none; backdrop-filter:blur(4px); transition:opacity .3s; }
#overlay.active { display:block; }

.sidebar-logo-section { text-align:center; padding:12px 8px 20px; border-bottom:1px solid rgba(255,255,255,0.07); margin-bottom:14px; }
.sidebar-logo-ring { position:relative; width:80px; height:80px; margin:0 auto 12px; border-radius:50%;
    background:conic-gradient(from 0deg, var(--p-primary), var(--p-accent), var(--p-primary));
    animation:spinRing 5s linear infinite; padding:3px; }
@keyframes spinRing { to { transform:rotate(360deg); } }
.sidebar-logo-inner { width:100%; height:100%; border-radius:50%; background:var(--p-bg1); overflow:hidden; display:flex; align-items:center; justify-content:center; }
.sidebar-logo-inner img { width:100%; height:100%; object-fit:cover; border-radius:50%; display:block; }
.sidebar-logo-fallback { font-size:1.8rem; color:var(--p-primary); display:none; }
.sidebar-panel-name { font-size:.85rem; font-weight:700; color:var(--p-primary); margin-bottom:4px; }
.sidebar-user-chip { display:inline-flex; align-items:center; gap:5px; font-size:.68rem; font-weight:600; letter-spacing:.08em; color:rgba(255,255,255,0.5); background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.08); border-radius:30px; padding:3px 10px; }
.sidebar-nav-label { font-size:.62rem; font-weight:700; letter-spacing:.15em; color:rgba(255,255,255,0.25); text-transform:uppercase; padding:10px 12px 6px; margin-top:6px; }
.nav-btn { width:100%; padding:11px 14px; margin-bottom:4px; background:rgba(255,255,255,0.04); border:1px solid transparent; border-radius:13px; color:rgba(255,255,255,0.75); text-align:left; cursor:pointer; font-family:'Montserrat',sans-serif; font-size:.83rem; font-weight:600; display:flex; align-items:center; gap:10px; transition:all .22s cubic-bezier(.34,1.2,.64,1); position:relative; overflow:hidden; text-decoration:none; }
.nav-btn::before { content:''; position:absolute; left:0; top:0; bottom:0; width:3px; background:var(--p-primary); border-radius:0 3px 3px 0; transform:scaleY(0); transition:transform .2s; }
.nav-btn:hover, .nav-btn:focus { background:rgba(255,255,255,0.09); border-color:rgba(255,255,255,0.1); color:#fff; transform:translateX(4px); }
.nav-btn:hover::before { transform:scaleY(1); }
.nav-btn.active-page { background:rgba(201,168,76,0.1); border-color:rgba(201,168,76,0.25); color:var(--p-primary); }
.nav-btn.active-page::before { transform:scaleY(1); }
.nav-btn i { width:18px; text-align:center; color:var(--p-primary); font-size:.9rem; opacity:.85; }
.nav-btn:hover i, .nav-btn.active-page i { opacity:1; }
.nav-badge { margin-left:auto; background:#ef4444; color:#fff; border-radius:50%; width:18px; height:18px; font-size:.6rem; font-weight:700; display:flex; align-items:center; justify-content:center; }

.main { position:relative; z-index:1; padding:80px 16px 40px; max-width:640px; margin:0 auto; }

.page-title {
    font-size:1.6rem; font-weight:800; letter-spacing:.08em; text-transform:uppercase;
    background:linear-gradient(135deg, var(--p-primary) 0%, #fff8e0 45%, var(--p-primary) 100%);
    background-size:200% auto;
    -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text;
    animation:shimmer 4s linear infinite;
    margin-bottom:24px; text-align:center;
}
@keyframes shimmer { to { background-position:200% center; } }

.edit-form-card { padding:28px; }

.field-label { font-size:.75rem; font-weight:700; letter-spacing:.08em; text-transform:uppercase; color:rgba(255,255,255,.45); margin-bottom:6px; display:block; }

.status-badges { display:flex; gap:10px; margin-top:8px; }
.status-badge-opt { flex:1; padding:10px 16px; border-radius:12px; border:1.5px solid rgba(255,255,255,.12); background:rgba(255,255,255,.04); color:rgba(255,255,255,.6); font-size:.82rem; font-weight:700; cursor:pointer; text-align:center; transition:all .22s; }
.status-badge-opt:hover { border-color:rgba(201,168,76,.35); }
.status-badge-opt.opt-active { background:rgba(74,222,128,.12); border-color:rgba(74,222,128,.4); color:#4ade80; }
.status-badge-opt.opt-blocked { background:rgba(248,113,113,.12); border-color:rgba(248,113,113,.4); color:#f87171; }

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
        <span class="header-title"><i class="fas fa-key me-2" style="font-size:.85rem;"></i>Edit License</span>
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
    <div class="page-title">Edit License</div>

    <div class="edit-form-card glass" style="animation:cardIn .5s cubic-bezier(.34,1.2,.64,1) both">
        <form method="POST">
            <input type="hidden" name="id" value="<?= $row['id'] ?>">

            <div class="mb-4">
                <label class="field-label"><i class="fas fa-building me-1"></i> Client / App Name</label>
                <input type="text" name="client_name" class="form-control" maxlength="120"
                       value="<?= htmlspecialchars((string) ($row['client_name'] ?? '')) ?>" required>
            </div>

            <div class="mb-4">
                <label class="field-label"><i class="fas fa-key me-1"></i> License Key</label>
                <input type="text" name="license_key" class="form-control"
                       value="<?= htmlspecialchars($row['license_key']) ?>" required>
            </div>

            <div class="mb-4">
                <label class="field-label"><i class="far fa-calendar-alt me-1"></i> Expiry Date</label>
                <input type="datetime-local" name="expiry_date" class="form-control"
                       value="<?= date('Y-m-d\TH:i', strtotime($row['expiry_date'])) ?>" required>
            </div>

            <div class="mb-4">
                <label class="field-label"><i class="fas fa-link me-1"></i> Package Verification</label>
                <select name="package_mode" id="packageMode" class="form-select" onchange="updatePackageMode()">
                    <option value="SPECIFIC" <?= ($row['package_mode'] ?? 'SPECIFIC') === 'SPECIFIC' ? 'selected' : '' ?>>Bind Specific Package</option>
                    <option value="ANY" <?= ($row['package_mode'] ?? 'SPECIFIC') === 'ANY' ? 'selected' : '' ?>>Allow Any Package</option>
                </select>
            </div>

            <div class="mb-4" id="packageNameWrap">
                <label class="field-label"><i class="fas fa-box me-1"></i> Package Name</label>
                <input type="text" name="package_name" class="form-control"
                       value="<?= htmlspecialchars((string) $row['package_name']) ?>">
            </div>

            <div class="mb-4">
                <?php
                $currentSigningMode = (int) ($row['signing_lock'] ?? 1) !== 1
                    ? 'any'
                    : (empty($row['signing_cert_sha256']) ? 'auto' : 'pinned');
                ?>
                <label class="field-label"><i class="fas fa-shield-halved me-1"></i> APK Signing Policy</label>
                <select name="signing_mode" id="signingMode" class="form-select" onchange="updateSigningMode()">
                    <option value="pinned" <?= $currentSigningMode === 'pinned' ? 'selected' : '' ?>>Exact signing SHA-256</option>
                    <option value="auto" <?= $currentSigningMode === 'auto' ? 'selected' : '' ?>>Auto-bind first v3 activation</option>
                    <option value="any" <?= $currentSigningMode === 'any' ? 'selected' : '' ?>>Any signing key</option>
                </select>
            </div>

            <div class="mb-4" id="signingCertWrap">
                <label class="field-label"><i class="fas fa-certificate me-1"></i> APK Signing Certificate SHA-256</label>
                <input type="text" name="signing_cert_sha256" class="form-control" maxlength="64"
                       placeholder="Auto-binds on first secure v3 activation"
                       value="<?= htmlspecialchars((string) ($row['signing_cert_sha256'] ?? '')) ?>">
                <div class="form-text text-light opacity-50">Clear this only after verifying an intentional app signing-key rotation.</div>
            </div>

            <div class="mb-4" id="anySigningWarning" style="display:none;color:#fde68a;background:rgba(245,158,11,.12);border:1px solid rgba(245,158,11,.3);padding:12px;border-radius:12px">
                <i class="fas fa-triangle-exclamation me-1"></i> Any signing key allows repackaged APKs for this license.
            </div>

            <div class="mb-4">
                <label class="field-label">Device Binding</label>
                <select name="device_mode" class="form-select">
                    <?php foreach (['DISABLED','SINGLE','LIMITED','UNLIMITED'] as $mode): ?>
                    <option value="<?= $mode ?>" <?= ($row['device_mode'] ?? 'SINGLE') === $mode ? 'selected' : '' ?>><?= $mode ?></option>
                    <?php endforeach; ?>
                </select>
            </div>
            <div class="mb-4">
                <label class="field-label">Maximum Devices</label>
                <input type="number" name="max_devices" class="form-control" min="1" max="100"
                       value="<?= max(1, (int) ($row['max_devices'] ?? 1)) ?>">
            </div>
            <div class="mb-4">
                <label class="field-label">Feature Policy (JSON)</label>
                <textarea name="feature_policy" class="form-control" rows="3"><?= htmlspecialchars((string) ($row['feature_policy'] ?? '{}')) ?></textarea>
            </div>
            <div class="row g-3 mb-4">
                <div class="col-md-6"><label class="field-label">Minimum SDK</label><input type="number" name="minimum_sdk_version" class="form-control" min="1" value="<?= (int) ($row['minimum_sdk_version'] ?? 3) ?>"></div>
                <div class="col-md-6"><label class="field-label">Latest SDK</label><input type="number" name="latest_sdk_version" class="form-control" min="1" value="<?= (int) ($row['latest_sdk_version'] ?? 3) ?>"></div>
            </div>
            <div class="mb-4"><label class="field-label">Blocked SDK Versions</label><input name="blocked_versions" class="form-control" value="<?= htmlspecialchars(implode(', ', json_decode((string) ($row['blocked_versions'] ?? '[]'), true) ?: [])) ?>"></div>
            <div class="mb-4"><label class="field-label">Session Lifetime</label><select name="session_lifetime_seconds" class="form-select"><?php foreach ([600=>'10 minutes',900=>'15 minutes',1800=>'30 minutes'] as $seconds=>$label): ?><option value="<?= $seconds ?>" <?= (int) ($row['session_lifetime_seconds'] ?? 600) === $seconds ? 'selected' : '' ?>><?= $label ?></option><?php endforeach; ?></select></div>
            <div class="form-check mb-2"><input class="form-check-input" type="checkbox" name="java_native_auth" id="javaNativeAuth" <?= (int) ($row['java_native_auth'] ?? 1) === 1 ? 'checked' : '' ?>><label class="form-check-label" for="javaNativeAuth">Require Java + Native auth</label></div>
            <div class="form-check mb-2"><input class="form-check-input" type="checkbox" name="force_update" id="forceUpdate" <?= (int) ($row['force_update'] ?? 0) === 1 ? 'checked' : '' ?>><label class="form-check-label" for="forceUpdate">Force update</label></div>
            <div class="form-check mb-4"><input class="form-check-input" type="checkbox" name="kill_switch" id="killSwitch" <?= (int) ($row['kill_switch'] ?? 0) === 1 ? 'checked' : '' ?>><label class="form-check-label" for="killSwitch">Kill switch</label></div>

            <div class="mb-4">
                <label class="field-label"><i class="fas fa-toggle-on me-1"></i> Status</label>
                <div class="status-badges">
                    <label class="status-badge-opt <?= $row['status'] ? 'opt-active' : '' ?>" onclick="setStatus(1)">
                        <i class="fas fa-check-circle me-1"></i> Active
                    </label>
                    <label class="status-badge-opt <?= !$row['status'] ? 'opt-blocked' : '' ?>" onclick="setStatus(0)">
                        <i class="fas fa-ban me-1"></i> Blocked
                    </label>
                </div>
                <input type="hidden" name="status" id="statusField" value="<?= intval($row['status']) ?>">
            </div>

            <div class="d-flex gap-3 mt-4">
                <button type="submit" name="update" class="btn-ios ob-btn-blue" style="flex:1">
                    <i class="fas fa-save"></i> Update License
                </button>
                <a href="license_list.php" class="btn-back">
                    <i class="fas fa-arrow-left"></i> Back
                </a>
            </div>
        </form>
    </div>
</div>

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

function updateSigningMode() {
    const mode = document.getElementById('signingMode').value;
    const certWrap = document.getElementById('signingCertWrap');
    const certInput = certWrap.querySelector('input');
    const warning = document.getElementById('anySigningWarning');
    certWrap.style.display = mode === 'pinned' ? 'block' : 'none';
    certInput.required = mode === 'pinned';
    warning.style.display = mode === 'any' ? 'block' : 'none';
    if (mode !== 'pinned') certInput.value = '';
}

function updatePackageMode() {
    const specific = document.getElementById('packageMode').value === 'SPECIFIC';
    const wrap = document.getElementById('packageNameWrap');
    const input = wrap.querySelector('input');
    wrap.style.display = specific ? 'block' : 'none';
    input.required = specific;
}

updateSigningMode();
updatePackageMode();

function setStatus(val) {
    document.getElementById('statusField').value = val;
    document.querySelectorAll('.status-badge-opt').forEach(el => {
        el.classList.remove('opt-active','opt-blocked');
    });
    if (val === 1) {
        document.querySelectorAll('.status-badge-opt')[0].classList.add('opt-active');
    } else {
        document.querySelectorAll('.status-badge-opt')[1].classList.add('opt-blocked');
    }
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
<style>
@keyframes cardIn { from { opacity:0; transform:translateY(20px) scale(.97); } to { opacity:1; transform:none; } }
@keyframes obRpl{to{transform:scale(4);opacity:0;}}
</style>
</body>
</html>
