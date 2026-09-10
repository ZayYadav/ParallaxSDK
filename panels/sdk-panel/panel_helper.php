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
    $uiV2 = <<<'PARALLAX_UI_V2'
/* PARALLAX SDK PANEL — UI V2
   Visual-only stylesheet. No panel/backend behavior is changed. */

:root{
  --ui-bg:#050814;
  --ui-bg-2:#09101f;
  --ui-panel:rgba(12,20,38,.92);
  --ui-panel-2:rgba(17,28,50,.82);
  --ui-panel-soft:rgba(255,255,255,.045);
  --ui-border:rgba(255,255,255,.095);
  --ui-border-strong:rgba(255,255,255,.16);
  --ui-text:#f7f9ff;
  --ui-muted:#91a0bb;
  --ui-primary:var(--p-primary,#c9a84c);
  --ui-accent:var(--p-accent,#4f8ef7);
  --ui-success:#34d399;
  --ui-danger:#fb7185;
  --ui-warning:#fbbf24;
  --ui-info:#60a5fa;
  --ui-radius:18px;
  --ui-radius-sm:12px;
  --ui-shadow:0 18px 55px rgba(0,0,0,.36);
  --ui-shadow-soft:0 8px 26px rgba(0,0,0,.24);
}

*{box-sizing:border-box}
html{background:var(--ui-bg);scroll-behavior:smooth}
body{
  color:var(--ui-text)!important;
  background:
    radial-gradient(circle at 8% -5%, color-mix(in srgb,var(--ui-primary) 13%,transparent), transparent 32rem),
    radial-gradient(circle at 96% 6%, color-mix(in srgb,var(--ui-accent) 14%,transparent), transparent 34rem),
    linear-gradient(145deg,var(--ui-bg) 0%,var(--ui-bg-2) 52%,#070b18 100%)!important;
  background-size:100% 100%!important;
  animation:none!important;
  min-height:100vh;
  overflow-x:hidden;
  -webkit-font-smoothing:antialiased;
  text-rendering:optimizeLegibility;
  -webkit-tap-highlight-color:transparent;
}

/* background decoration: calmer and cheaper */
.bg-orbs,.bg-wrap{pointer-events:none!important}
.orb,.bg-orb{
  opacity:.5!important;
  filter:blur(70px)!important;
  animation-duration:34s!important;
  will-change:auto!important;
}
.bg-grid{opacity:.45}

/* Header */
header,.topbar,.navbar{
  background:rgba(5,10,22,.88)!important;
  border-bottom:1px solid var(--ui-border)!important;
  box-shadow:0 8px 30px rgba(0,0,0,.24)!important;
  backdrop-filter:blur(14px) saturate(125%)!important;
  -webkit-backdrop-filter:blur(14px) saturate(125%)!important;
}
.header-title,.page-title,.section-title,h1,h2,h3,h4,h5{
  letter-spacing:-.02em;
}
.header-title{
  color:var(--ui-text)!important;
  font-weight:800!important;
  text-transform:none!important;
}
.user-badge{
  background:rgba(255,255,255,.055)!important;
  border:1px solid var(--ui-border)!important;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.045);
}

/* Sidebar */
#sidebar,.sidebar{
  background:linear-gradient(180deg,rgba(7,12,25,.98),rgba(6,10,20,.98))!important;
  border-right:1px solid var(--ui-border)!important;
  box-shadow:18px 0 44px rgba(0,0,0,.26)!important;
  backdrop-filter:blur(14px)!important;
  -webkit-backdrop-filter:blur(14px)!important;
}
#overlay{
  background:rgba(1,4,11,.64)!important;
  backdrop-filter:none!important;
  -webkit-backdrop-filter:none!important;
}
.sidebar-logo-ring{
  background:linear-gradient(135deg,var(--ui-primary),var(--ui-accent))!important;
  padding:2px!important;
  box-shadow:0 10px 28px color-mix(in srgb,var(--ui-primary) 18%,transparent)!important;
}
.sidebar-logo-inner{
  background:#090f1e!important;
}
.sidebar-panel-name{
  color:var(--ui-text)!important;
  font-weight:800!important;
}
.nav-label{
  color:#66748e!important;
  font-size:.62rem!important;
  letter-spacing:.16em!important;
}
.nav-btn{
  min-height:43px!important;
  border-radius:12px!important;
  border:1px solid transparent!important;
  background:transparent!important;
  color:#aab5c9!important;
  font-weight:650!important;
  transition:background .16s ease,border-color .16s ease,color .16s ease,transform .16s ease!important;
}
.nav-btn:hover{
  color:#fff!important;
  background:rgba(255,255,255,.055)!important;
  border-color:rgba(255,255,255,.06)!important;
  transform:translateX(2px)!important;
}
.nav-btn.active,.nav-btn[aria-current="page"]{
  color:#fff!important;
  background:linear-gradient(90deg,color-mix(in srgb,var(--ui-primary) 18%,transparent),rgba(255,255,255,.035))!important;
  border-color:color-mix(in srgb,var(--ui-primary) 32%,transparent)!important;
  box-shadow:inset 3px 0 0 var(--ui-primary)!important;
}

/* Main layout */
.main,.page,.wrap,main{
  position:relative;
  z-index:1;
}
.main{
  width:min(1440px,100%)!important;
  margin-inline:auto!important;
}
.section-header{
  border-bottom:1px solid rgba(255,255,255,.07);
  padding-bottom:12px!important;
  margin-bottom:16px!important;
}
.section-header i{color:var(--ui-primary)!important}
.section-header small,.muted,.text-muted{color:var(--ui-muted)!important}

/* Surface system */
.glass,
.card,
.cardx,
.kpi-card,
.lic-card,
.license-card,
.settings-card,
.setting-card,
.server-card,
.server-control,
.status-card,
.user-card,
.package-card,
.referral-card,
.announcement-card,
.form-card,
.security-card,
.customizer-card,
.panel-card,
.stat-card,
.login-card,
.register-card,
.access-denied-card{
  background:linear-gradient(155deg,rgba(18,29,52,.88),rgba(8,14,29,.93))!important;
  border:1px solid var(--ui-border)!important;
  border-radius:var(--ui-radius)!important;
  box-shadow:var(--ui-shadow-soft)!important;
  backdrop-filter:blur(12px) saturate(120%)!important;
  -webkit-backdrop-filter:blur(12px) saturate(120%)!important;
}
.kpi-card:hover,
.lic-card:hover,
.license-card:hover,
.settings-card:hover,
.user-card:hover,
.package-card:hover,
.referral-card:hover,
.announcement-card:hover{
  border-color:color-mix(in srgb,var(--ui-primary) 30%,var(--ui-border))!important;
  box-shadow:var(--ui-shadow)!important;
  transform:translateY(-2px)!important;
}
.kpi-card,.lic-card,.license-card,.user-card,.package-card,.announcement-card{
  transition:transform .18s ease,border-color .18s ease,box-shadow .18s ease!important;
}

/* KPI */
.kpi-icon,.stat-icon,.app-name-icon{
  background:rgba(255,255,255,.055)!important;
  border:1px solid var(--ui-border)!important;
  box-shadow:none!important;
}
.kpi-val,.metric strong,.stat-value{
  color:#fff!important;
  font-weight:850!important;
  letter-spacing:-.04em!important;
}
.kpi-label,.stat-label,.app-name-label{
  color:var(--ui-muted)!important;
  letter-spacing:.08em!important;
}

/* Cards/details */
.lic-key,.key-text,.app-name-value{
  color:#fff!important;
  font-weight:750!important;
}
.lic-detail,.lic-time,.device-meta,.card-subtitle,.small-note{
  color:var(--ui-muted)!important;
}
.lic-divider,.divider{
  border-color:rgba(255,255,255,.07)!important;
}

/* Forms */
.form-control,
.form-select,
input[type="text"],
input[type="password"],
input[type="email"],
input[type="number"],
input[type="date"],
input[type="datetime-local"],
input[type="url"],
select,
textarea{
  min-height:46px;
  color:#f8fafc!important;
  background:rgba(3,8,20,.62)!important;
  border:1px solid rgba(148,163,184,.18)!important;
  border-radius:12px!important;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.025)!important;
  transition:border-color .16s ease,box-shadow .16s ease,background .16s ease!important;
}
textarea{min-height:110px}
.form-control::placeholder,input::placeholder,textarea::placeholder{color:#61708a!important}
.form-control:focus,
.form-select:focus,
input:focus,
select:focus,
textarea:focus{
  background:rgba(5,11,25,.82)!important;
  border-color:color-mix(in srgb,var(--ui-primary) 58%,#64748b)!important;
  box-shadow:0 0 0 3px color-mix(in srgb,var(--ui-primary) 14%,transparent)!important;
  outline:none!important;
}
.form-label,label{
  color:#c8d1e1!important;
  font-weight:650!important;
}
.form-text,.help-text,.form-hint{color:var(--ui-muted)!important}
.input-group-text{
  color:#aab6cb!important;
  background:rgba(255,255,255,.045)!important;
  border-color:rgba(148,163,184,.18)!important;
}
.form-check-input{
  border-color:rgba(148,163,184,.34)!important;
  background-color:rgba(2,7,18,.75)!important;
}
.form-check-input:checked{
  background-color:var(--ui-primary)!important;
  border-color:var(--ui-primary)!important;
}

/* Toggle controls */
.toggle-option,.status-option,.alert-type,.edit-pkg-lock-bar,.pkg-lock-bar{
  border:1px solid var(--ui-border)!important;
  background:rgba(255,255,255,.035)!important;
  border-radius:13px!important;
  transition:background .16s ease,border-color .16s ease,transform .16s ease!important;
}
.toggle-option:hover,.status-option:hover,.alert-type:hover{
  border-color:rgba(255,255,255,.18)!important;
  background:rgba(255,255,255,.06)!important;
}
.toggle-option.active,.status-option.selected,.alert-type.selected{
  border-color:color-mix(in srgb,var(--ui-primary) 55%,transparent)!important;
  background:color-mix(in srgb,var(--ui-primary) 12%,rgba(255,255,255,.03))!important;
}

/* Buttons */
button,.btn,.glass-btn,.btn-glass,.btn-back,.btn-ios,.btn-save,.btn-login,.btn-submit,
.btn-edit,.btn-delete,.btn-primary,.btn-secondary,.btn-success,.btn-danger,
.btn-danger-glass,.btn-danger-modal,.menu-btn,.eye-btn,.app-name-edit{
  touch-action:manipulation;
  font-weight:750!important;
  letter-spacing:.01em!important;
  border-radius:11px!important;
  transition:transform .15s ease,box-shadow .15s ease,border-color .15s ease,background .15s ease,opacity .15s ease!important;
}
.menu-btn,.eye-btn{
  background:rgba(255,255,255,.045)!important;
  border:1px solid var(--ui-border)!important;
  color:#dce5f5!important;
}
.menu-btn:hover,.eye-btn:hover{
  background:rgba(255,255,255,.08)!important;
  transform:none!important;
}
.btn-primary,.btn-save,.btn-login,.btn-submit,.btn-ios,
.glass-btn:not(.btn-delete-glass):not(.btn-edit-glass):not(.btn-danger-glass),
.btn-glass:not(.btn-delete):not(.btn-danger){
  color:#07101f!important;
  background:linear-gradient(135deg,var(--ui-primary),color-mix(in srgb,var(--ui-primary) 78%,#fff))!important;
  border:1px solid color-mix(in srgb,var(--ui-primary) 70%,#fff)!important;
  box-shadow:0 8px 22px color-mix(in srgb,var(--ui-primary) 20%,transparent)!important;
}
.btn-primary:hover,.btn-save:hover,.btn-login:hover,.btn-submit:hover,.btn-ios:hover,
.glass-btn:not(.btn-delete-glass):not(.btn-edit-glass):not(.btn-danger-glass):hover,
.btn-glass:not(.btn-delete):not(.btn-danger):hover{
  transform:translateY(-1px)!important;
  box-shadow:0 12px 28px color-mix(in srgb,var(--ui-primary) 25%,transparent)!important;
}
.btn-secondary,.btn-back,.btn-edit,.btn-edit-glass,.app-name-edit{
  color:#e6edf8!important;
  background:rgba(255,255,255,.055)!important;
  border:1px solid var(--ui-border-strong)!important;
  box-shadow:none!important;
}
.btn-secondary:hover,.btn-back:hover,.btn-edit:hover,.btn-edit-glass:hover,.app-name-edit:hover{
  background:rgba(255,255,255,.09)!important;
  border-color:rgba(255,255,255,.22)!important;
  transform:translateY(-1px)!important;
}
.btn-success{
  color:#042419!important;
  background:linear-gradient(135deg,#34d399,#6ee7b7)!important;
  border-color:#6ee7b7!important;
}
.btn-danger,.btn-delete,.btn-delete-glass,.btn-danger-glass,.btn-danger-modal{
  color:#fff!important;
  background:linear-gradient(135deg,#e11d48,#fb7185)!important;
  border:1px solid rgba(251,113,133,.72)!important;
  box-shadow:0 8px 22px rgba(225,29,72,.18)!important;
}
.btn-danger:hover,.btn-delete:hover,.btn-delete-glass:hover,.btn-danger-glass:hover,.btn-danger-modal:hover{
  transform:translateY(-1px)!important;
  box-shadow:0 12px 28px rgba(225,29,72,.25)!important;
}
button:active,.btn:active,.glass-btn:active,.btn-glass:active,.btn-back:active,.btn-ios:active{
  transform:translateY(1px) scale(.985)!important;
}
button:disabled,.btn:disabled,.glass-btn:disabled,.btn-glass:disabled{
  opacity:.45!important;
  cursor:not-allowed!important;
  box-shadow:none!important;
}

/* Status badges */
.badge,.badge2,.status-badge,.device-pill,.status-pill{
  border-radius:999px!important;
  font-weight:800!important;
  letter-spacing:.04em!important;
  border:1px solid rgba(255,255,255,.08)!important;
}
.status-active-glass,.badge-success,.status-online,.ok{
  color:#a7f3d0!important;
  background:rgba(16,185,129,.12)!important;
  border-color:rgba(52,211,153,.22)!important;
}
.status-block-glass,.badge-danger,.status-offline,.bad{
  color:#fecdd3!important;
  background:rgba(244,63,94,.12)!important;
  border-color:rgba(251,113,133,.22)!important;
}
.status-pause-glass,.badge-warning,.status-maintenance{
  color:#fde68a!important;
  background:rgba(245,158,11,.12)!important;
  border-color:rgba(251,191,36,.22)!important;
}

/* Tables */
.table-responsive{
  border-radius:14px!important;
  border:1px solid var(--ui-border)!important;
  background:rgba(3,8,20,.35)!important;
  overflow:auto!important;
}
table,.table{
  color:#dce4f2!important;
  --bs-table-bg:transparent!important;
  --bs-table-color:#dce4f2!important;
  margin-bottom:0!important;
}
.table thead th,table thead th{
  color:#8492aa!important;
  background:rgba(255,255,255,.035)!important;
  border-bottom:1px solid var(--ui-border)!important;
  font-size:.7rem!important;
  text-transform:uppercase!important;
  letter-spacing:.1em!important;
  white-space:nowrap;
}
.table td,table td{
  border-color:rgba(255,255,255,.065)!important;
  vertical-align:middle!important;
}
.table tbody tr:hover td,table tbody tr:hover td{
  background:rgba(255,255,255,.028)!important;
}

/* Modals */
.modal,.modal-overlay,.popup-overlay,.usage-modal,.delete-modal{
  background:rgba(1,4,11,.72)!important;
}
.modal-content,.popup-card,.success-card,.usage-modal-content,.delete-modal-content{
  color:var(--ui-text)!important;
  background:linear-gradient(160deg,#121c31,#080e1d)!important;
  border:1px solid var(--ui-border-strong)!important;
  border-radius:20px!important;
  box-shadow:0 28px 90px rgba(0,0,0,.58)!important;
  backdrop-filter:blur(14px)!important;
  -webkit-backdrop-filter:blur(14px)!important;
}
.modal-header,.modal-footer{
  border-color:rgba(255,255,255,.075)!important;
}
.btn-close{filter:invert(1) grayscale(1);opacity:.7}

/* Alerts / toasts */
.alert{
  border-radius:13px!important;
  border-width:1px!important;
  background:rgba(255,255,255,.045)!important;
}
.alert-success{color:#a7f3d0!important;border-color:rgba(52,211,153,.25)!important}
.alert-danger{color:#fecdd3!important;border-color:rgba(251,113,133,.25)!important}
.alert-warning{color:#fde68a!important;border-color:rgba(251,191,36,.25)!important}
.alert-info{color:#bfdbfe!important;border-color:rgba(96,165,250,.25)!important}

/* Auth pages */
.card.login-card,.card.register-card,
.login-card,.register-card{
  max-width:440px!important;
  border-radius:24px!important;
  padding:34px!important;
}
.logo-ring::before,.sidebar-logo-ring{animation-duration:12s!important}
.page-heading,.brand-title{
  color:#fff!important;
  -webkit-text-fill-color:initial!important;
  background:none!important;
  animation:none!important;
}

/* Footer/watermark */
.watermark{
  color:rgba(204,214,232,.18)!important;
  letter-spacing:.16em!important;
}

/* Scrollbars */
*{
  scrollbar-width:thin;
  scrollbar-color:rgba(148,163,184,.25) transparent;
}
*::-webkit-scrollbar{width:8px;height:8px}
*::-webkit-scrollbar-track{background:transparent}
*::-webkit-scrollbar-thumb{background:rgba(148,163,184,.22);border-radius:999px}
*::-webkit-scrollbar-thumb:hover{background:rgba(148,163,184,.34)}

/* Responsive */
@media(max-width:900px){
  .orb,.bg-orb{animation:none!important;filter:blur(48px)!important;opacity:.38!important}
  .glass,.card,.cardx,.kpi-card,.lic-card,.license-card,.settings-card,.user-card,.package-card,
  .announcement-card,.modal-content{
    backdrop-filter:blur(8px)!important;
    -webkit-backdrop-filter:blur(8px)!important;
  }
  .main{padding-left:12px!important;padding-right:12px!important}
}
@media(max-width:640px){
  header{padding-inline:12px!important}
  .header-title{font-size:.86rem!important}
  .user-badge{padding:7px 10px!important}
  .card,.glass,.lic-card,.kpi-card,.settings-card,.user-card,.package-card{border-radius:15px!important}
  .btn,.glass-btn,.btn-glass,.btn-back,.btn-ios,.btn-save,.btn-login,.btn-submit{min-height:44px}
  .card.login-card,.card.register-card,.login-card,.register-card{padding:26px 20px!important;border-radius:20px!important}
}
@media(prefers-reduced-motion:reduce){
  *,*::before,*::after{
    animation-duration:.01ms!important;
    animation-iteration-count:1!important;
    transition-duration:.01ms!important;
    scroll-behavior:auto!important;
  }
}

PARALLAX_UI_V2;

    $uiV3 = <<<'PARALLAX_UI_V3'

/* PARALLAX SDK PANEL — UI V3 FINAL
   Shared override layer for the folder-routed panel shell. */

:root{
  --ux-bg:#05070f;
  --ux-ink:#f8fbff;
  --ux-muted:#9aa8bf;
  --ux-soft:#151e31;
  --ux-card:rgba(12,18,31,.9);
  --ux-card-2:rgba(18,27,45,.82);
  --ux-line:rgba(255,255,255,.105);
  --ux-line-strong:rgba(255,255,255,.18);
  --ux-primary:var(--p-primary,#c9a84c);
  --ux-accent:var(--p-accent,#4f8ef7);
  --ux-danger:#fb7185;
  --ux-success:#34d399;
  --ux-warning:#fbbf24;
  --ux-radius:14px;
  --ux-radius-sm:10px;
  --ux-shadow:0 18px 48px rgba(0,0,0,.34);
  --ux-focus:0 0 0 4px color-mix(in srgb,var(--ux-accent) 25%,transparent);
}

html{color-scheme:dark}
body{
  font-family:Inter,Montserrat,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif!important;
  background:
    linear-gradient(180deg,rgba(255,255,255,.035),transparent 160px),
    radial-gradient(circle at 14% 0%,color-mix(in srgb,var(--ux-primary) 12%,transparent),transparent 440px),
    radial-gradient(circle at 88% 4%,color-mix(in srgb,var(--ux-accent) 13%,transparent),transparent 480px),
    #05070f!important;
  color:var(--ux-ink)!important;
  letter-spacing:0!important;
}
body::before{
  content:"";
  position:fixed;
  inset:0;
  pointer-events:none;
  z-index:0;
  opacity:.22;
  background-image:
    linear-gradient(rgba(255,255,255,.045) 1px,transparent 1px),
    linear-gradient(90deg,rgba(255,255,255,.045) 1px,transparent 1px);
  background-size:42px 42px;
  mask-image:linear-gradient(to bottom,#000,transparent 72%);
}

.bg-orbs,.bg-wrap,.particles-js-canvas-el{display:none!important}

header,.topbar,.navbar{
  height:64px!important;
  background:rgba(5,8,16,.86)!important;
  border-bottom:1px solid var(--ux-line)!important;
  box-shadow:0 10px 28px rgba(0,0,0,.22)!important;
  backdrop-filter:blur(16px)!important;
  -webkit-backdrop-filter:blur(16px)!important;
}
.header-left{gap:12px!important}
.header-title{
  display:inline-flex!important;
  align-items:center!important;
  gap:9px!important;
  color:var(--ux-ink)!important;
  font-size:.98rem!important;
  letter-spacing:0!important;
}
.header-title i,.menu-btn i{color:var(--ux-primary)!important}
.menu-btn{
  background:rgba(255,255,255,.055)!important;
  border:1px solid var(--ux-line)!important;
  border-radius:12px!important;
  transition:transform .18s ease,background .18s ease,border-color .18s ease!important;
}
.menu-btn:hover{transform:translateY(-1px)!important;background:rgba(255,255,255,.09)!important}
.user-badge{
  background:linear-gradient(135deg,rgba(255,255,255,.075),rgba(255,255,255,.035))!important;
  border:1px solid var(--ux-line)!important;
  color:var(--ux-ink)!important;
}

#sidebar,.sidebar{
  top:64px!important;
  width:270px!important;
  left:-292px;
  padding:14px 12px 88px!important;
  background:linear-gradient(180deg,rgba(8,13,24,.98),rgba(6,9,17,.98))!important;
  border-right:1px solid var(--ux-line)!important;
  box-shadow:18px 0 50px rgba(0,0,0,.28)!important;
}
#sidebar.active{left:0!important}
.sidebar-logo-section{
  text-align:left!important;
  padding:12px!important;
  border:1px solid var(--ux-line)!important;
  border-radius:16px!important;
  background:linear-gradient(135deg,rgba(255,255,255,.07),rgba(255,255,255,.025))!important;
}
.sidebar-logo-ring{
  width:52px!important;
  height:52px!important;
  margin:0 0 12px!important;
  animation:none!important;
  border-radius:14px!important;
  background:linear-gradient(135deg,var(--ux-primary),var(--ux-accent))!important;
}
.sidebar-logo-inner,.sidebar-logo-inner img{border-radius:12px!important}
.sidebar-panel-name{font-size:.92rem!important;color:#fff!important}
.sidebar-user-chip{border-radius:999px!important;color:var(--ux-muted)!important}
.sidebar-nav-label{
  padding:16px 10px 7px!important;
  color:#63708a!important;
  letter-spacing:.14em!important;
}
.nav-btn{
  min-height:46px!important;
  padding:11px 12px!important;
  border-radius:12px!important;
  background:transparent!important;
  border:1px solid transparent!important;
  color:#aebbd0!important;
  gap:11px!important;
  text-decoration:none!important;
}
.nav-btn i{
  width:30px!important;
  height:30px!important;
  display:inline-grid!important;
  place-items:center!important;
  border-radius:10px!important;
  background:rgba(255,255,255,.055)!important;
  color:var(--ux-primary)!important;
}
.nav-btn:hover{
  transform:translateX(3px)!important;
  color:#fff!important;
  background:rgba(255,255,255,.06)!important;
  border-color:rgba(255,255,255,.075)!important;
}
.nav-btn.active-page,.nav-btn.active,.nav-btn[aria-current="page"]{
  color:#fff!important;
  background:linear-gradient(90deg,color-mix(in srgb,var(--ux-primary) 20%,transparent),rgba(255,255,255,.04))!important;
  border-color:color-mix(in srgb,var(--ux-primary) 32%,transparent)!important;
}
.nav-btn.active-page i,.nav-btn.active i,.nav-btn[aria-current="page"] i{
  color:#05070f!important;
  background:linear-gradient(135deg,var(--ux-primary),#ffe08a)!important;
}
.nav-badge{
  background:var(--ux-danger)!important;
  box-shadow:0 0 0 3px rgba(251,113,133,.14)!important;
}

.main,main,.page,.wrap{
  position:relative!important;
  z-index:1!important;
}
.main{
  width:min(1440px,100%)!important;
  max-width:1440px!important;
  padding-top:92px!important;
}
.page-title{
  margin:0 0 20px!important;
  color:#fff!important;
  text-align:left!important;
  font-size:clamp(1.35rem,2vw,2.05rem)!important;
  letter-spacing:-.03em!important;
  text-transform:none!important;
  background:none!important;
  -webkit-text-fill-color:currentColor!important;
  animation:none!important;
}
.page-title::after{
  content:"";
  display:block;
  width:72px;
  height:3px;
  margin-top:10px;
  border-radius:999px;
  background:linear-gradient(90deg,var(--ux-primary),var(--ux-accent));
}

.glass,.card,.cardx,.panel-card,.settings-card,.kpi-card,.lic-card,.license-card,.user-card,.package-card,.announcement-card,.form-container,.modal-content{
  background:linear-gradient(145deg,var(--ux-card),rgba(9,14,25,.82))!important;
  border:1px solid var(--ux-line)!important;
  border-radius:var(--ux-radius)!important;
  box-shadow:var(--ux-shadow)!important;
  backdrop-filter:blur(12px)!important;
  -webkit-backdrop-filter:blur(12px)!important;
}
.glass:hover,.card:hover,.kpi-card:hover,.lic-card:hover,.license-card:hover,.user-card:hover,.announcement-card:hover{
  border-color:var(--ux-line-strong)!important;
}
.kpi-grid,.stats-grid,.cards-grid,.license-grid,.grid{
  gap:14px!important;
}
.quick-actions{
  display:grid!important;
  grid-template-columns:repeat(4,minmax(0,1fr))!important;
  gap:10px!important;
  padding:12px!important;
  margin-bottom:16px!important;
}
.quick-action{
  min-height:58px!important;
  border-radius:12px!important;
  border:1px solid var(--ux-line)!important;
  background:rgba(255,255,255,.055)!important;
  color:#eef4ff!important;
  display:flex!important;
  align-items:center!important;
  justify-content:center!important;
  gap:9px!important;
  text-decoration:none!important;
  font-weight:800!important;
  box-shadow:none!important;
  transition:transform .16s ease,background .16s ease,border-color .16s ease!important;
}
.quick-action i{
  color:var(--ux-primary)!important;
}
.quick-action:hover,.quick-action.copied{
  transform:translateY(-2px)!important;
  background:linear-gradient(135deg,color-mix(in srgb,var(--ux-primary) 16%,transparent),rgba(255,255,255,.065))!important;
  border-color:color-mix(in srgb,var(--ux-primary) 35%,transparent)!important;
  color:#fff!important;
}
.kpi-card,.stat-card{
  min-height:112px!important;
  overflow:hidden!important;
}
.kpi-card::before,.stat-card::before,.glass::before{
  opacity:.32!important;
}

table,.table{
  color:var(--ux-ink)!important;
  border-collapse:separate!important;
  border-spacing:0 8px!important;
}
.table-wrap,.table-responsive{
  overflow:auto!important;
  border-radius:var(--ux-radius)!important;
  border:1px solid var(--ux-line)!important;
  background:rgba(255,255,255,.025)!important;
}
thead th,.table thead th{
  color:#d9e4f5!important;
  background:rgba(255,255,255,.065)!important;
  border:0!important;
  font-size:.72rem!important;
  letter-spacing:.08em!important;
  text-transform:uppercase!important;
}
tbody tr{
  background:rgba(255,255,255,.04)!important;
  transition:background .16s ease,transform .16s ease!important;
}
tbody tr:hover{background:rgba(255,255,255,.07)!important}
td,.table td{
  border-color:rgba(255,255,255,.06)!important;
  vertical-align:middle!important;
}

label,.form-label{
  color:#dbe6f7!important;
  font-weight:700!important;
  letter-spacing:.01em!important;
}
input:not([type="checkbox"]):not([type="radio"]),select,textarea,.form-control,.form-select,.glass-input{
  width:100%;
  color:#fff!important;
  background:rgba(255,255,255,.065)!important;
  border:1px solid var(--ux-line)!important;
  border-radius:12px!important;
  outline:none!important;
  min-height:46px!important;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.035)!important;
  transition:border-color .16s ease,box-shadow .16s ease,background .16s ease!important;
}
select,.form-select{
  color-scheme:dark;
  cursor:pointer;
}
option{background:#0c1322;color:#fff}
textarea{min-height:110px!important;resize:vertical}
input:focus,select:focus,textarea:focus,.form-control:focus,.form-select:focus,.glass-input:focus{
  border-color:color-mix(in srgb,var(--ux-accent) 75%,#fff)!important;
  box-shadow:var(--ux-focus)!important;
  background:rgba(255,255,255,.09)!important;
}
input[type="checkbox"],input[type="radio"]{
  accent-color:var(--ux-primary);
}

.btn,.btn-ios,.btn-save,.btn-submit,.btn-login,.btn-back,.glass-btn,.btn-glass,.ob-btn,button[type="submit"],input[type="submit"]{
  min-height:44px!important;
  border-radius:12px!important;
  border:1px solid rgba(255,255,255,.1)!important;
  display:inline-flex!important;
  align-items:center!important;
  justify-content:center!important;
  gap:8px!important;
  color:#07101f!important;
  font-weight:800!important;
  letter-spacing:.01em!important;
  background:linear-gradient(135deg,var(--ux-primary),#ffe08a)!important;
  box-shadow:0 10px 28px color-mix(in srgb,var(--ux-primary) 22%,transparent)!important;
  text-decoration:none!important;
  transition:transform .16s ease,box-shadow .16s ease,filter .16s ease,background .16s ease!important;
}
.btn:hover,.btn-ios:hover,.btn-save:hover,.btn-submit:hover,.btn-login:hover,.btn-back:hover,.glass-btn:hover,.btn-glass:hover,.ob-btn:hover,button[type="submit"]:hover,input[type="submit"]:hover{
  transform:translateY(-2px)!important;
  filter:saturate(1.05) brightness(1.04)!important;
  box-shadow:0 16px 36px color-mix(in srgb,var(--ux-primary) 30%,transparent)!important;
}
.btn:active,.btn-ios:active,.btn-save:active,.btn-submit:active,.btn-login:active,.btn-back:active,.glass-btn:active,.btn-glass:active,.ob-btn:active,button:active{
  transform:translateY(0)!important;
}
.btn-secondary,.btn-outline,.btn-dark,.btn-light,.btnx,.copy-btn,.action-btn,.small-btn{
  color:#f8fbff!important;
  background:rgba(255,255,255,.07)!important;
  border:1px solid var(--ux-line)!important;
  box-shadow:none!important;
}
.btn-danger,.delete-btn,.btn-delete{
  color:#fff!important;
  background:linear-gradient(135deg,#ef4444,#fb7185)!important;
}
.btn-success,.enable-btn{
  color:#04140f!important;
  background:linear-gradient(135deg,#34d399,#86efac)!important;
}
.btn-warning{
  color:#1d1302!important;
  background:linear-gradient(135deg,#fbbf24,#fde68a)!important;
}
.is-loading,.is-loading *{cursor:progress!important}
.is-loading button[type="submit"],.is-loading input[type="submit"]{
  opacity:.78!important;
  pointer-events:none!important;
}

.badge,.status-badge,.chip,.pill{
  border-radius:999px!important;
  border:1px solid rgba(255,255,255,.12)!important;
  background:rgba(255,255,255,.075)!important;
  color:#eef4ff!important;
}
.alert,.notice,.toast,.result-success,.result-error{
  border-radius:12px!important;
  border:1px solid var(--ux-line)!important;
  box-shadow:0 12px 30px rgba(0,0,0,.22)!important;
}
.result-success{background:rgba(52,211,153,.12)!important;color:#86efac!important}
.result-error{background:rgba(251,113,133,.12)!important;color:#fecdd3!important}

.modal,.dialog-backdrop{
  backdrop-filter:blur(8px)!important;
  -webkit-backdrop-filter:blur(8px)!important;
}
.modal-content{
  color:var(--ux-ink)!important;
}

@media (min-width:1180px){
  #sidebar{left:0!important}
  #overlay{display:none!important}
  .main{padding-left:294px!important;padding-right:24px!important}
}
@media (max-width:760px){
  header{padding-inline:12px!important}
  .header-title{max-width:54vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .user-badge span{display:none}
  .main{padding:82px 12px 28px!important}
  .page-title{text-align:left!important}
  .kpi-grid,.stats-grid,.cards-grid,.license-grid,.grid{
    grid-template-columns:1fr!important;
  }
  .quick-actions{
    grid-template-columns:1fr 1fr!important;
  }
  table,.table{font-size:.84rem!important}
  .btn,.btn-ios,.btn-save,.btn-submit,.btn-login,.btn-back,.glass-btn,.btn-glass,.ob-btn{
    width:100%;
  }
}

PARALLAX_UI_V3;

    return "
    :root {
        --p-primary:  {$primary};
        --p-accent:   {$accent};
        --p-bg1:      {$bg1};
        --p-bg2:      {$bg2};
        --p-bg3:      {$bg3};
        --p-bg4:      {$bg4};
    }
    " . $uiV2 . $uiV3;
}

function panel_route(string $path = ''): string {
    $path = trim($path, '/');
    $base = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? ''))), '/');
    if ($base === '/' || $base === '.') {
        $base = '';
    }
    return $base . '/' . $path;
}

// ── Shared layout CSS (body, bg-orbs, glass, header, sidebar, buttons, etc.) ──
function panel_layout_css() {
    return file_exists(__DIR__.'/styles.css') ? file_get_contents(__DIR__.'/styles.css') : '';
}
