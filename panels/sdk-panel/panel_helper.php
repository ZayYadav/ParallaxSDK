<?php
/**
 * ONEBOX Panel Helper
 * Include this file AFTER conn.php in every page
 * Loads panel_settings and active announcements globally
 */

// ── Load all panel settings into $PANEL array ──────────────────────────────
function get_panel_settings($conn) {
    $settings = [];
    $res = mysqli_query($conn, "SELECT setting_key, setting_value FROM panel_settings");
    if ($res) {
        while ($row = mysqli_fetch_assoc($res)) {
            $settings[$row['setting_key']] = $row['setting_value'];
        }
    }
    // Defaults fallback
    $defaults = [
        'panel_name'       => 'NrCore  Panel',
        'panel_tagline'    => 'NrCore Premium Access Portal',
        'login_title'      => 'NrCore Login',
        'login_subtitle'   => 'Secure Sign In',
        'login_badge_text' => 'NrCore ',
        'dashboard_title'  => 'NrCore DASHBOARD',
        'watermark_text'   => 'NrCore  SECURED',
        'active_theme'     => 'dark_blue',
        'theme_primary'    => '#C9A84C',
        'theme_accent'     => '#4F8EF7',
        'theme_bg1'        => '#050810',
        'theme_bg2'        => '#0f172a',
        'theme_bg3'        => '#1e3a8a',
        'theme_bg4'        => '#312e81',
        'sidebar_logo_url' => 'logo.png',
        'footer_text'      => 'NrCore ',
    ];
    return array_merge($defaults, $settings);
}

// ── Save a setting ─────────────────────────────────────────────────────────
function save_panel_setting($conn, $key, $value, $user_id = null) {
    $key = trim((string) $key);
    $value = trim((string) $value);
    $uid = (int) $user_id;
    if (preg_match('/^[a-z0-9_]{2,64}$/D', $key) !== 1 || strlen($value) > 1000) {
        return false;
    }
    if ($key === 'sidebar_logo_url'
        && preg_match('#^(?:https://[A-Za-z0-9.-]+(?::\d+)?/[^\s"\']*|[A-Za-z0-9_./-]+)$#D', $value) !== 1) {
        return false;
    }
    if (preg_match('/[\x00-\x08\x0B\x0C\x0E-\x1F]/', $value) === 1) {
        return false;
    }
    $stmt = $conn->prepare(
        'INSERT INTO panel_settings (setting_key, setting_value, updated_by)
         VALUES (?, ?, ?)
         ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value),
             updated_by = VALUES(updated_by), updated_at = NOW()'
    );
    $stmt->bind_param('ssi', $key, $value, $uid);
    $ok = $stmt->execute();
    $stmt->close();
    return $ok;
}

// ── Get active announcements for a user ───────────────────────────────────
function get_active_announcements($conn, $user_id) {
    $uid = intval($user_id);
    return mysqli_query($conn,
        "SELECT a.* FROM announcements a
         LEFT JOIN announcement_reads ar ON ar.announcement_id = a.id AND ar.user_id = $uid
         WHERE a.is_active = 1
           AND ar.id IS NULL
           AND (a.expires_at IS NULL OR a.expires_at > NOW())
         ORDER BY a.created_at DESC"
    );
}

// ── Mark announcement as read ──────────────────────────────────────────────
function mark_announcement_read($conn, $ann_id, $user_id) {
    $a = intval($ann_id);
    $u = intval($user_id);
    mysqli_query($conn,
        "INSERT IGNORE INTO announcement_reads (announcement_id, user_id) VALUES ($a,$u)"
    );
}

// ── Count unread announcements ─────────────────────────────────────────────
function count_unread_announcements($conn, $user_id) {
    $uid = intval($user_id);
    $res = mysqli_query($conn,
        "SELECT COUNT(*) c FROM announcements a
         LEFT JOIN announcement_reads ar ON ar.announcement_id = a.id AND ar.user_id = $uid
         WHERE a.is_active = 1 AND ar.id IS NULL
           AND (a.expires_at IS NULL OR a.expires_at > NOW())"
    );
    $row = mysqli_fetch_assoc($res);
    return (int)($row['c'] ?? 0);
}

// ── CSS variables from settings ───────────────────────────────────────────
function panel_css_vars($P) {
    $safeColor = static function ($value, string $fallback): string {
        $value = trim((string) $value);
        return preg_match('/^#[0-9A-Fa-f]{6}$/D', $value) === 1 ? $value : $fallback;
    };
    $primary = $safeColor($P['theme_primary'] ?? '', '#C9A84C');
    $accent = $safeColor($P['theme_accent'] ?? '', '#4F8EF7');
    $bg1 = $safeColor($P['theme_bg1'] ?? '', '#050810');
    $bg2 = $safeColor($P['theme_bg2'] ?? '', '#0F172A');
    $bg3 = $safeColor($P['theme_bg3'] ?? '', '#1E3A8A');
    $bg4 = $safeColor($P['theme_bg4'] ?? '', '#312E81');

    return "
    :root {
        --p-primary:  {$primary};
        --p-accent:   {$accent};
        --p-bg1:      {$bg1};
        --p-bg2:      {$bg2};
        --p-bg3:      {$bg3};
        --p-bg4:      {$bg4};
        --p-surface: rgba(10,16,32,.88);
        --p-surface-soft: rgba(15,23,42,.74);
        --p-line: rgba(255,255,255,.10);
        --p-shadow: 0 18px 48px rgba(0,0,0,.32);
        --p-radius: 18px;
    }

    /* Smooth UI layer. Legacy pages define styles after this block, so the
       critical performance overrides intentionally use !important. */
    html { scroll-behavior: smooth; }
    body {
        background:
            radial-gradient(circle at 12% -10%, color-mix(in srgb, var(--p-primary) 13%, transparent), transparent 34rem),
            radial-gradient(circle at 92% 8%, color-mix(in srgb, var(--p-accent) 12%, transparent), transparent 32rem),
            linear-gradient(145deg, var(--p-bg1), var(--p-bg2) 52%, var(--p-bg1)) !important;
        background-size: 100% 100% !important;
        animation: none !important;
        overscroll-behavior-y: none;
        text-rendering: optimizeLegibility;
        -webkit-tap-highlight-color: transparent;
    }
    .bg-orbs { opacity: .62 !important; contain: strict; }
    .orb, .bg-orb {
        filter: blur(72px) !important;
        animation-duration: 36s !important;
        animation-timing-function: ease-in-out !important;
        will-change: auto !important;
    }
    .glass, .card, .cardx, .stat-card, .settings-card, .server-card,
    .announcement-card, .user-card, .package-card, .form-card, .modal-content {
        background: linear-gradient(160deg, rgba(17,25,46,.88), rgba(8,13,27,.92)) !important;
        border: 1px solid var(--p-line) !important;
        box-shadow: var(--p-shadow) !important;
        backdrop-filter: blur(12px) saturate(125%) !important;
        -webkit-backdrop-filter: blur(12px) saturate(125%) !important;
    }
    header, #sidebar {
        backdrop-filter: blur(12px) saturate(120%) !important;
        -webkit-backdrop-filter: blur(12px) saturate(120%) !important;
    }
    #overlay {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
    }
    button, .btn, .glass-btn, .btn-glass, .btn-back, .btn-ios,
    .menu-btn, .nav-btn, a {
        touch-action: manipulation;
    }
    button, .btn, .glass-btn, .btn-glass, .btn-back, .btn-ios, .menu-btn, .nav-btn {
        transition: transform .16s ease, border-color .16s ease, background-color .16s ease, box-shadow .16s ease, opacity .16s ease !important;
    }
    button:active, .btn:active, .glass-btn:active, .btn-glass:active, .btn-back:active, .btn-ios:active {
        transform: translateY(1px) scale(.985) !important;
    }
    input, select, textarea {
        transition: border-color .16s ease, box-shadow .16s ease, background-color .16s ease !important;
    }
    input:focus, select:focus, textarea:focus, button:focus-visible, a:focus-visible {
        outline: none !important;
        box-shadow: 0 0 0 3px color-mix(in srgb, var(--p-primary) 24%, transparent) !important;
    }
    .grid > .card, .license-card, .user-card, .announcement-card, .package-card {
        content-visibility: auto;
        contain-intrinsic-size: 1px 320px;
    }
    .table-responsive, #sidebar, .main, .page {
        -webkit-overflow-scrolling: touch;
    }
    @media (max-width: 900px), (pointer: coarse) {
        .orb, .bg-orb {
            animation: none !important;
            filter: blur(54px) !important;
            opacity: .45 !important;
        }
        .glass, .card, .cardx, .stat-card, .settings-card, .server-card,
        .announcement-card, .user-card, .package-card, .form-card, .modal-content,
        header, #sidebar {
            backdrop-filter: blur(8px) saturate(110%) !important;
            -webkit-backdrop-filter: blur(8px) saturate(110%) !important;
        }
    }
    @media (prefers-reduced-motion: reduce) {
        *, *::before, *::after {
            animation-duration: .01ms !important;
            animation-iteration-count: 1 !important;
            transition-duration: .01ms !important;
            scroll-behavior: auto !important;
        }
    }";
}

// ── Shared layout CSS (body, bg-orbs, glass, header, sidebar, buttons, etc.) ──
function panel_layout_css() {
    return file_exists(__DIR__.'/styles.css') ? file_get_contents(__DIR__.'/styles.css') : '';
}
