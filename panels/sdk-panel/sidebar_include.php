<?php
/**
 * sidebar_include.php — Shared sidebar + header for ONEBOX panel
 * Usage: include('sidebar_include.php'); after $P and $current_user are set
 * $active_page = filename like 'dashboard.php' to highlight current nav
 */

// Count unread announcements if not already done
if (!isset($unread_ann_count)) {
    $uid_si = intval($_SESSION['user_id'] ?? 0);
    $unread_ann_count = $uid_si ? count_unread_announcements($conn, $uid_si) : 0;
}
// Role check if not done
if (!isset($role_dash)) {
    $uid_rd = intval($_SESSION['user_id'] ?? 0);
    $urow_rd = $uid_rd ? mysqli_fetch_assoc(mysqli_query($conn,"SELECT role FROM users WHERE id=$uid_rd")) : [];
    $role_dash = $urow_rd['role'] ?? 'user';
}
$current_user_si = $_SESSION['username'] ?? 'User';
if (!isset($active_page)) $active_page = basename($_SERVER['PHP_SELF']);
?>
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
        <span><?= htmlspecialchars($current_user_si) ?></span>
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
        <div class="sidebar-user-chip"><i class="fas fa-shield-alt" style="color:var(--p-primary)"></i> <?= htmlspecialchars($current_user_si) ?></div>
    </div>

    <div class="sidebar-nav-label">Main</div>
    <a class="nav-btn <?= $active_page==='dashboard.php'?'active-page':'' ?>" href="dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a>
    <a class="nav-btn <?= $active_page==='generate_ui.php'?'active-page':'' ?>" href="generate_ui.php"><i class="fas fa-key"></i> Generate License</a>
    <a class="nav-btn <?= $active_page==='license_list.php'?'active-page':'' ?>" href="license_list.php"><i class="fas fa-code-branch"></i> List Licenses</a>
    <?php if (in_array($role_dash, ['owner','admin'], true)): ?>
    <a class="nav-btn <?= $active_page==='security_dashboard.php'?'active-page':'' ?>" href="security_dashboard.php"><i class="fas fa-shield-alt"></i> API Security</a>
    <?php endif; ?>

    <div class="sidebar-nav-label">Management</div>
    <a class="nav-btn <?= $active_page==='manage_users.php'?'active-page':'' ?>" href="manage_users.php"><i class="fas fa-circle-user"></i> Manage Users</a>
    <a class="nav-btn <?= $active_page==='manage_referrals.php'?'active-page':'' ?>" href="manage_referrals.php"><i class="fas fa-file-pen"></i> Manage Referrals</a>
    <a class="nav-btn <?= $active_page==='online_server.php'?'active-page':'' ?>" href="online_server.php"><i class="fas fa-circle-check"></i> Sdk Online Server</a>
    <a class="nav-btn <?= $active_page==='announcements.php'?'active-page':'' ?>" href="announcements.php" style="position:relative;">
        <i class="fas fa-bullhorn"></i> Announcements
        <?php if ($unread_ann_count > 0): ?>
        <span class="nav-badge"><?= min($unread_ann_count,9) ?></span>
        <?php endif; ?>
    </a>

    <div class="sidebar-nav-label">Config</div>
    <?php if (in_array($role_dash, ['owner','admin'])): ?>
    <a class="nav-btn <?= $active_page==='panel_customizer.php'?'active-page':'' ?>" href="panel_customizer.php"><i class="fas fa-paint-brush"></i> Customizer</a>
    <?php endif; ?>
    <a class="nav-btn <?= $active_page==='settings.php'?'active-page':'' ?>" href="settings.php"><i class="fas fa-cog"></i> Settings</a>
    <a class="nav-btn" href="logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a>
</aside>
