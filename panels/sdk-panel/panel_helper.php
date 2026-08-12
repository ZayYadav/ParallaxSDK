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
    }";
}

// ── Shared layout CSS (body, bg-orbs, glass, header, sidebar, buttons, etc.) ──
function panel_layout_css() {
    return file_exists(__DIR__.'/styles.css') ? file_get_contents(__DIR__.'/styles.css') : '';
}
