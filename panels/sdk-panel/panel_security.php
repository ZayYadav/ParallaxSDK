<?php
declare(strict_types=1);

function panel_config(string $key, $default = null)
{
    global $SDK_PANEL_CONFIG;
    return $SDK_PANEL_CONFIG[$key] ?? $default;
}

function panel_security_bootstrap(array $config): void
{
    static $started = false;
    if ($started) {
        return;
    }
    $started = true;

    // CLI maintenance tools connect to the same database but have no HTTP/TLS
    // request, browser session, headers, or CSRF boundary to initialize.
    if (PHP_SAPI === 'cli') {
        return;
    }

    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: no-referrer');
    header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
    header("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'; upgrade-insecure-requests");
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');

    $https = panel_is_https($config);
    if (($config['REQUIRE_HTTPS'] ?? true) && !$https) {
        http_response_code(400);
        exit('HTTPS_REQUIRED');
    }
    if ($https) {
        header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
    }

    if (session_status() !== PHP_SESSION_ACTIVE) {
        session_name('SDKPANELSESSID');
        session_set_cookie_params([
            'lifetime' => 0,
            'path' => '/',
            'secure' => $https,
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
        session_start();
    } elseif (session_id() !== '' && !headers_sent()) {
        setcookie(session_name(), session_id(), [
            'expires' => 0,
            'path' => '/',
            'secure' => $https,
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
    }

    $idleLimit = max(300, (int) ($config['SESSION_IDLE_SECONDS'] ?? 1800));
    if (isset($_SESSION['last_activity']) && time() - (int) $_SESSION['last_activity'] > $idleLimit) {
        panel_destroy_session();
        session_start();
    }
    $_SESSION['last_activity'] = time();

    $absoluteLimit = max($idleLimit, (int) ($config['SESSION_ABSOLUTE_SECONDS'] ?? 28800));
    if (!isset($_SESSION['session_started_at'])) {
        $_SESSION['session_started_at'] = time();
    } elseif (time() - (int) $_SESSION['session_started_at'] > $absoluteLimit) {
        panel_destroy_session();
        session_start();
        $_SESSION['session_started_at'] = time();
    }
    if (!isset($_SESSION['last_regenerated_at'])
        || time() - (int) $_SESSION['last_regenerated_at'] > 900) {
        session_regenerate_id(true);
        $_SESSION['last_regenerated_at'] = time();
    }

    $agentHash = hash('sha256', (string) ($_SERVER['HTTP_USER_AGENT'] ?? 'unknown'));
    if (isset($_SESSION['agent_hash']) && !hash_equals((string) $_SESSION['agent_hash'], $agentHash)) {
        panel_destroy_session();
        session_start();
    }
    $_SESSION['agent_hash'] = $agentHash;

    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }

    $script = strtolower((string) ($_SERVER['SDK_PANEL_ROUTE_TARGET'] ?? basename((string) ($_SERVER['SCRIPT_NAME'] ?? ''))));
    $csrfExempt = in_array($script, ['connect.php', 'telegram_bot.php'], true);
    $authUpgradeExempt = in_array($script, ['login.php', 'logout.php', 'register.php', 'connect.php', 'telegram_bot.php'], true);
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
        $contentLength = (int) ($_SERVER['CONTENT_LENGTH'] ?? 0);
        $maxPostBytes = max(8192, min(1048576, (int) ($config['MAX_POST_BYTES'] ?? 65536)));
        if ($contentLength > $maxPostBytes) {
            http_response_code(413);
            exit('REQUEST_TOO_LARGE');
        }
    }
    if (!$authUpgradeExempt && !empty($_SESSION['user_id']) && empty($_SESSION['auth_v3'])) {
        panel_destroy_session();
        header('Location: login.php');
        exit;
    }
    if (!$csrfExempt && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
        if (!panel_browser_post_is_same_origin($config)) {
            http_response_code(403);
            exit('ORIGIN_VALIDATION_FAILED');
        }
        $provided = (string) ($_POST['_csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ''));
        if ($provided === '' || !hash_equals((string) $_SESSION['csrf_token'], $provided)) {
            http_response_code(419);
            exit('CSRF_VALIDATION_FAILED');
        }
    }

    if (!$csrfExempt) {
        ob_start('panel_enhance_html');
    }
}

/**
 * Forwarded headers are security-sensitive. Only honor them when the request
 * arrived from an explicitly configured reverse-proxy IP.
 */
function panel_remote_is_trusted_proxy(array $config = []): bool
{
    $trustProxy = array_key_exists('TRUST_PROXY', $config)
        ? ($config['TRUST_PROXY'] === true)
        : (panel_config('TRUST_PROXY', false) === true);
    if (!$trustProxy) {
        return false;
    }

    $trustedProxies = array_key_exists('TRUSTED_PROXIES', $config)
        ? $config['TRUSTED_PROXIES']
        : panel_config('TRUSTED_PROXIES', []);
    if (!is_array($trustedProxies) || $trustedProxies === []) {
        // Fail closed: TRUST_PROXY alone must never make client-controlled
        // X-Forwarded-* headers authoritative.
        return false;
    }

    $remote = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
    if (!filter_var($remote, FILTER_VALIDATE_IP)) {
        return false;
    }

    foreach ($trustedProxies as $proxy) {
        $proxy = trim((string) $proxy);
        if ($proxy !== ''
            && filter_var($proxy, FILTER_VALIDATE_IP)
            && hash_equals($proxy, $remote)) {
            return true;
        }
    }
    return false;
}

function panel_is_https(array $config = []): bool
{
    if (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    if (panel_remote_is_trusted_proxy($config)) {
        return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
    }
    return false;
}

function panel_browser_post_is_same_origin(array $config = []): bool
{
    $fetchSite = strtolower((string) ($_SERVER['HTTP_SEC_FETCH_SITE'] ?? ''));
    if ($fetchSite === 'cross-site') {
        return false;
    }

    $origin = (string) ($_SERVER['HTTP_ORIGIN'] ?? '');
    if ($origin === '') {
        return true;
    }

    $expected = panel_request_origin($config);
    return hash_equals($expected, rtrim($origin, '/'));
}

function panel_request_origin(array $config = []): string
{
    $scheme = panel_is_https($config) ? 'https' : 'http';
    $host = strtolower((string) ($_SERVER['HTTP_HOST'] ?? 'localhost'));
    $host = preg_replace('/[^a-z0-9.:\[\]-]/', '', $host) ?: 'localhost';
    return $scheme . '://' . $host;
}

function panel_destroy_session(): void
{
    $_SESSION = [];
    if (session_status() === PHP_SESSION_ACTIVE) {
        if (ini_get('session.use_cookies')) {
            $params = session_get_cookie_params();
            setcookie(session_name(), '', [
                'expires' => time() - 42000,
                'path' => $params['path'] ?: '/',
                'domain' => $params['domain'] ?: '',
                'secure' => (bool) $params['secure'],
                'httponly' => true,
                'samesite' => 'Strict',
            ]);
        }
        session_destroy();
        session_id('');
    }
}

function panel_csrf_token(): string
{
    return (string) ($_SESSION['csrf_token'] ?? '');
}

function panel_csrf_input(): string
{
    return '<input type="hidden" name="_csrf" value="' . htmlspecialchars(panel_csrf_token(), ENT_QUOTES, 'UTF-8') . '">';
}

function panel_inject_csrf_fields(string $html): string
{
    if (stripos($html, '<form') === false) {
        return $html;
    }
    $field = panel_csrf_input();
    return (string) preg_replace_callback(
        '/<form\b[^>]*\bmethod\s*=\s*(["\']?)post\1[^>]*>/i',
        static fn(array $match): string => $match[0] . $field,
        $html
    );
}

function panel_enhance_html(string $html): string
{
    $html = panel_inject_csrf_fields($html);
    if (stripos($html, '</body>') === false || stripos($html, '<html') === false) {
        return $html;
    }

    $token = htmlspecialchars(panel_csrf_token(), ENT_QUOTES, 'UTF-8');
    if (stripos($html, 'name="csrf-token"') === false && stripos($html, '</head>') !== false) {
        $runtimeCss = <<<'CSS'
<style id="sdk-panel-runtime-polish">
html,body{width:100%;min-height:100%;overflow-x:hidden!important;overflow-y:auto!important;overscroll-behavior-y:none}
body{min-width:0;touch-action:manipulation}
body.sdk-sidebar-open{overflow:hidden!important}
img,svg,canvas,video{max-width:100%;height:auto}
.page,.login-wrap,.auth-wrap{min-height:100svh!important}
.login-card,.register-card,.auth-card{max-width:min(100%,460px)}
.main,main,.page,.wrap{min-width:0}
#sidebar{max-width:min(88vw,292px)!important}
#sidebar.active{visibility:visible}
#overlay{touch-action:none}
.table-responsive,.table-wrap,.table-card{max-width:100%;overflow:auto!important;-webkit-overflow-scrolling:touch}
table,.table{max-width:100%}
code,pre,.lic-key,.key-text,.app-name-value,.device-id,.mono,[data-copy]{overflow-wrap:anywhere;word-break:break-word}
.modal-dialog{max-width:min(96vw,720px);margin:.75rem auto}
.modal-content,.modal-box,.popup-card,.success-card,.usage-modal-content,.delete-modal-content{max-height:calc(100svh - 24px);overflow:hidden}
.modal-body,.modal-box-body,.usage-list,.device-list{overflow:auto;-webkit-overflow-scrolling:touch}
.overlay-modal,.success-overlay,.usage-modal,.delete-modal{padding:12px!important;align-items:center!important;justify-content:center!important}
.overlay-modal.active,.success-overlay.active,.usage-modal.active,.delete-modal.active{display:flex!important}
.overlay-modal .modal-box,.success-overlay .success-card,.usage-modal .usage-modal-content,.delete-modal .delete-modal-content{width:min(100%,560px)!important;max-width:100%!important;overflow:auto}
.actions,.action-row,.button-row,.modal-footer{gap:10px;flex-wrap:wrap}
.form-control,.form-select,input,select,textarea,button{font-size:16px}
.spinner-border{flex:0 0 auto}
@media(max-width:680px){
  html,body{height:auto!important}
  body{padding-left:env(safe-area-inset-left);padding-right:env(safe-area-inset-right)}
  .page,.login-wrap,.auth-wrap{min-height:100svh!important;overflow-y:visible!important;justify-content:flex-start!important;padding:18px 12px max(22px,env(safe-area-inset-bottom))!important}
  .login-card,.register-card,.auth-card{width:min(100%,420px)!important;margin:12px auto!important}
  .brand-title,.page-heading,.page-title{font-size:clamp(1.35rem,7vw,1.85rem)!important;line-height:1.15!important}
  .header-title{max-width:58vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .user-badge span{display:none}
  table,.table{min-width:680px}
  .modal-dialog{width:calc(100vw - 20px);margin:10px auto}
  .modal-content,.modal-box,.popup-card,.success-card,.usage-modal-content,.delete-modal-content{max-height:calc(100svh - 20px)}
  .modal-footer>*{flex:1 1 auto}
  .actions .btn,.action-row .btn,.button-row .btn,.actions button,.action-row button,.button-row button{width:100%}
  .watermark{position:static!important;margin:14px 0!important}
}
@media(min-width:1180px){
  body.sdk-sidebar-open{overflow-y:auto!important}
}
@media(prefers-reduced-motion:reduce){
  *,*::before,*::after{animation-duration:.01ms!important;animation-iteration-count:1!important;scroll-behavior:auto!important;transition-duration:.01ms!important}
}
</style>
CSS;
        $html = str_ireplace(
            '</head>',
            '<meta name="csrf-token" content="' . $token . '">' . "\n" . $runtimeCss . "\n</head>",
            $html
        );
    }

    $script = <<<'HTML'
<script>
(() => {
  const root = document.documentElement;
  root.classList.add('sdk-ui-v3');
  const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content || '';

  if (csrfToken && window.fetch && !window.fetch.__sdkPanelWrapped) {
    const nativeFetch = window.fetch.bind(window);
    const sameOrigin = (url) => {
      try { return new URL(url, window.location.href).origin === window.location.origin; }
      catch (_) { return false; }
    };
    window.fetch = (input, init = {}) => {
      const url = typeof input === 'string' ? input : input?.url;
      const method = String(init.method || (input?.method ?? 'GET')).toUpperCase();
      if (url && sameOrigin(url) && method !== 'GET' && method !== 'HEAD') {
        const headers = new Headers(init.headers || input?.headers || {});
        if (!headers.has('X-CSRF-Token')) headers.set('X-CSRF-Token', csrfToken);
        init = {...init, headers};
      }
      return nativeFetch(input, init);
    };
    window.fetch.__sdkPanelWrapped = true;
  }

  const setSidebarOpen = (open) => {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('overlay');
    const icon = document.getElementById('menuIcon');
    const next = Boolean(open);
    sidebar?.classList.toggle('active', next);
    overlay?.classList.toggle('active', next);
    const overlayMode = !window.matchMedia || window.matchMedia('(max-width: 1179px)').matches;
    document.body.classList.toggle('sdk-sidebar-open', next && overlayMode);
    if (icon) {
      icon.classList.toggle('fa-bars', !next);
      icon.classList.toggle('fa-times', next);
      icon.style.transform = next ? 'rotate(90deg)' : '';
    }
  };

  window.toggleSidebar = function () {
    const sidebar = document.getElementById('sidebar');
    setSidebarOpen(!sidebar?.classList.contains('active'));
  };
  window.closeSidebar = function () {
    setSidebarOpen(false);
  };

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    setSidebarOpen(false);
  });

  document.addEventListener('click', (event) => {
    if (!(event.target instanceof Element)) return;
    if (event.target.id === 'overlay') setSidebarOpen(false);
    if (event.target.closest('#sidebar a')) setSidebarOpen(false);
  });

  window.addEventListener('resize', () => {
    const sidebar = document.getElementById('sidebar');
    const overlayMode = !window.matchMedia || window.matchMedia('(max-width: 1179px)').matches;
    document.body.classList.toggle(
      'sdk-sidebar-open',
      Boolean(sidebar?.classList.contains('active')) && overlayMode
    );
  });

  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');
  if ((sidebar || overlay) && 'MutationObserver' in window) {
    const syncSidebar = () => {
      const overlayMode = !window.matchMedia || window.matchMedia('(max-width: 1179px)').matches;
      document.body.classList.toggle(
        'sdk-sidebar-open',
        Boolean(sidebar?.classList.contains('active')) && overlayMode
      );
    };
    const observer = new MutationObserver(syncSidebar);
    if (sidebar) observer.observe(sidebar, {attributes: true, attributeFilter: ['class']});
    if (overlay) observer.observe(overlay, {attributes: true, attributeFilter: ['class']});
    syncSidebar();
  }

  document.addEventListener('submit', (event) => {
    if (event.defaultPrevented) return;
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || form.dataset.noLoading === 'true') return;
    if (typeof form.checkValidity === 'function' && !form.checkValidity()) return;
    form.classList.add('is-loading');
    const button = form.querySelector('button[type="submit"], input[type="submit"]');
    if (button && !button.dataset.originalText) {
      button.dataset.originalText = button.value || button.textContent || '';
      if ('value' in button && button.tagName === 'INPUT') {
        button.value = 'Working...';
      } else {
        button.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>Working...</span>';
      }
    }
  });

  document.addEventListener('click', async (event) => {
    if (!(event.target instanceof Element)) return;
    const copyButton = event.target.closest('[data-copy]');
    if (!copyButton || !navigator.clipboard) return;
    event.preventDefault();
    const value = copyButton.getAttribute('data-copy') || '';
    try {
      await navigator.clipboard.writeText(value);
      copyButton.classList.add('copied');
      setTimeout(() => copyButton.classList.remove('copied'), 1100);
    } catch (_) {}
  });
})();
</script>
HTML;

    return str_ireplace('</body>', $script . "\n</body>", $html);
}

function panel_client_ip(): string
{
    if (panel_remote_is_trusted_proxy()) {
        $forwarded = trim(explode(',', (string) ($_SERVER['HTTP_X_FORWARDED_FOR'] ?? ''))[0]);
        if (filter_var($forwarded, FILTER_VALIDATE_IP)) {
            return $forwarded;
        }
    }
    $remote = (string) ($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0');
    return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
}

function panel_require_auth(): void
{
    if (empty($_SESSION['user_id']) || empty($_SESSION['username']) || empty($_SESSION['auth_v3'])) {
        header('Location: login.php');
        exit;
    }
}

function panel_require_roles(mysqli $conn, array $roles): array
{
    panel_require_auth();
    $id = (int) $_SESSION['user_id'];
    $stmt = $conn->prepare('SELECT id, username, role, status FROM users WHERE id = ? LIMIT 1');
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $user = $stmt->get_result()->fetch_assoc();
    $stmt->close();
    if (!$user || (isset($user['status']) && (int) $user['status'] !== 1)
        || !in_array(strtolower((string) $user['role']), array_map('strtolower', $roles), true)) {
        http_response_code(403);
        exit('ACCESS_DENIED');
    }
    return $user;
}

function panel_rate_limit(mysqli $conn, string $bucket, int $limit): bool
{
    $limit = max(1, min(600, $limit));
    $bucketHash = hash('sha256', $bucket);
    $stmt = $conn->prepare(
        'INSERT INTO api_rate_limits (bucket_hash, window_start, request_count)
         VALUES (?, UTC_TIMESTAMP(), 1)
         ON DUPLICATE KEY UPDATE
           request_count = IF(window_start < UTC_TIMESTAMP() - INTERVAL 60 SECOND, 1, request_count + 1),
           window_start = IF(window_start < UTC_TIMESTAMP() - INTERVAL 60 SECOND, UTC_TIMESTAMP(), window_start)'
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('s', $bucketHash);
    $stmt->execute();
    $stmt->close();

    $stmt = $conn->prepare('SELECT request_count FROM api_rate_limits WHERE bucket_hash = ?');
    $stmt->bind_param('s', $bucketHash);
    $stmt->execute();
    $count = (int) ($stmt->get_result()->fetch_assoc()['request_count'] ?? ($limit + 1));
    $stmt->close();
    return $count <= $limit;
}

function panel_consume_nonce(mysqli $conn, string $nonce, string $deviceId): bool
{
    $nonceHash = hash('sha256', $nonce);
    $stmt = $conn->prepare(
        'INSERT IGNORE INTO api_nonces (nonce_hash, device_id, expires_at)
         VALUES (?, ?, UTC_TIMESTAMP() + INTERVAL 5 MINUTE)'
    );
    $stmt->bind_param('ss', $nonceHash, $deviceId);
    $stmt->execute();
    $inserted = $stmt->affected_rows === 1;
    $stmt->close();
    if (random_int(1, 100) === 1) {
        $conn->query('DELETE FROM api_nonces WHERE expires_at < UTC_TIMESTAMP()');
    }
    return $inserted;
}

function panel_consume_request_id(mysqli $conn, string $requestId, string $deviceId): bool
{
    $requestHash = hash('sha256', $requestId);
    $stmt = $conn->prepare(
        'INSERT IGNORE INTO api_request_ids (request_id_hash, device_id, expires_at)
         VALUES (?, ?, UTC_TIMESTAMP() + INTERVAL 5 MINUTE)'
    );
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ss', $requestHash, $deviceId);
    $stmt->execute();
    $inserted = $stmt->affected_rows === 1;
    $stmt->close();
    if (random_int(1, 100) === 1) {
        $conn->query('DELETE FROM api_request_ids WHERE expires_at < UTC_TIMESTAMP()');
    }
    return $inserted;
}

function panel_audit(mysqli $conn, string $event, string $result, string $deviceId = '', array $details = []): void
{
    $ip = panel_client_ip();
    $json = json_encode($details, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    $licenseId = isset($details['license_id']) ? (int) $details['license_id'] : null;
    $clientName = mb_substr((string) ($details['client_name'] ?? ''), 0, 120);
    $packageName = mb_substr((string) ($details['package'] ?? ''), 0, 191);
    $signing = strtoupper(substr((string) ($details['signing_sha256'] ?? ''), 0, 64));
    $sdkVersion = isset($details['sdk_version']) ? (int) $details['sdk_version'] : null;
    $requestId = mb_substr((string) ($details['request_id'] ?? ''), 0, 96);
    $stmt = $conn->prepare(
        'INSERT INTO api_audit_logs
            (event_type, result, device_id, ip_address, license_id, client_name,
             package_name, signing_sha256, sdk_version, request_id, details)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    );
    if ($stmt) {
        $stmt->bind_param(
            'ssssisssiss',
            $event,
            $result,
            $deviceId,
            $ip,
            $licenseId,
            $clientName,
            $packageName,
            $signing,
            $sdkVersion,
            $requestId,
            $json
        );
        $stmt->execute();
        $stmt->close();
    }
}
