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

    $origin = trim((string) ($_SERVER['HTTP_ORIGIN'] ?? ''));
    if ($origin === '') {
        // Older clients may omit Origin. The mandatory per-session CSRF token
        // remains the authorization boundary for those requests.
        return true;
    }

    if (strtolower($origin) === 'null') {
        return false;
    }

    $originParts = parse_url($origin);
    if (!is_array($originParts)
        || !isset($originParts['scheme'], $originParts['host'])
        || !in_array(strtolower((string) $originParts['scheme']), ['http', 'https'], true)
        || isset($originParts['user'])
        || isset($originParts['pass'])
        || isset($originParts['query'])
        || isset($originParts['fragment'])
        || (isset($originParts['path']) && !in_array($originParts['path'], ['', '/'], true))) {
        return false;
    }

    // Compare the browser-facing authority rather than rebuilding the full
    // origin from the backend transport. With TLS termination the browser can
    // correctly send https:// while PHP sees the proxy hop as plain HTTP.
    $requestAuthority = strtolower(trim((string) ($_SERVER['HTTP_HOST'] ?? '')));
    if ($requestAuthority === ''
        || preg_match('/[\x00-\x20\x7f\/@\\\\]/', $requestAuthority) === 1) {
        return false;
    }
    $requestParts = parse_url('http://' . $requestAuthority);
    if (!is_array($requestParts) || empty($requestParts['host'])) {
        return false;
    }

    $originHost = strtolower(rtrim((string) $originParts['host'], '.'));
    $requestHost = strtolower(rtrim((string) $requestParts['host'], '.'));
    if ($originHost === '' || $requestHost === '' || !hash_equals($requestHost, $originHost)) {
        return false;
    }

    $originScheme = strtolower((string) $originParts['scheme']);
    $originPort = isset($originParts['port'])
        ? (int) $originParts['port']
        : ($originScheme === 'https' ? 443 : 80);
    $requestPort = isset($requestParts['port']) ? (int) $requestParts['port'] : null;

    if ($requestPort !== null) {
        return $requestPort === $originPort;
    }

    // When Host has no explicit port, only standard browser origins qualify.
    return in_array($originPort, [80, 443], true);
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
html,body{min-height:100%;overflow-x:hidden!important}
body:not(.modal-open){overflow-y:auto!important}
body.modal-open{overflow-y:hidden!important}
.page,.login-wrap,.auth-wrap{min-height:100svh!important}
.login-card,.register-card,.auth-card{max-width:min(100%,460px)}
@media(max-width:1179px){body.sidebar-open{overflow:hidden!important}}
@media(max-width:680px){
  html,body{height:auto!important}
  .page,.login-wrap,.auth-wrap{min-height:100svh!important;overflow-y:visible!important;justify-content:flex-start!important}
  .login-card,.register-card,.auth-card{width:min(100%,420px)!important;margin:16px auto!important}
  .watermark{position:static!important;margin:14px 0!important}
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

  const sidebarBreakpoint = window.matchMedia('(min-width: 1180px)');

  const sidebarIsOpen = () => {
    if (sidebarBreakpoint.matches) {
      return !document.body.classList.contains('sidebar-collapsed');
    }
    return document.getElementById('sidebar')?.classList.contains('active') ?? false;
  };

  const setSidebar = (open, remember = true) => {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('overlay');
    const icon = document.getElementById('menuIcon');
    if (!sidebar) return;

    const desktop = sidebarBreakpoint.matches;
    sidebar.classList.toggle('active', open);
    document.body.classList.toggle('sidebar-open', open);
    document.body.classList.toggle('sidebar-collapsed', desktop && !open);
    overlay?.classList.toggle('active', !desktop && open);
    document.querySelectorAll('.menu-btn').forEach((button) => {
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    if (icon) {
      icon.classList.toggle('fa-bars', !open);
      icon.classList.toggle('fa-times', open);
    }
    if (desktop && remember) {
      try { localStorage.setItem('sdk_sidebar_collapsed', open ? '0' : '1'); } catch (_) {}
    }
  };

  /* Always replace legacy page functions: several pages used a desktop CSS
     rule that forced the sidebar open, making their three-line button inert. */
  window.toggleSidebar = function () {
    setSidebar(!sidebarIsOpen());
  };

  window.closeSidebar = function () {
    setSidebar(false);
  };

  const initialiseSidebar = () => {
    if (sidebarBreakpoint.matches) {
      let collapsed = false;
      try { collapsed = localStorage.getItem('sdk_sidebar_collapsed') === '1'; } catch (_) {}
      setSidebar(!collapsed, false);
    } else {
      setSidebar(false, false);
    }
  };

  initialiseSidebar();
  if (typeof sidebarBreakpoint.addEventListener === 'function') {
    sidebarBreakpoint.addEventListener('change', initialiseSidebar);
  } else if (typeof sidebarBreakpoint.addListener === 'function') {
    sidebarBreakpoint.addListener(initialiseSidebar);
  }

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    setSidebar(false);
  });

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
