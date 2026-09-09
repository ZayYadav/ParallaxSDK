<?php

declare(strict_types=1);

namespace ParallaxPanel;

use PDO;

final class Security
{
    private const SESSION_IDLE_SECONDS = 1800;
    private const SESSION_ABSOLUTE_SECONDS = 43200;
    private const SESSION_ROTATE_SECONDS = 900;

    public static function csrfToken(): string
    {
        if (!isset($_SESSION['csrf']) || !is_string($_SESSION['csrf'])) {
            $_SESSION['csrf'] = bin2hex(random_bytes(32));
        }
        return $_SESSION['csrf'];
    }

    public static function csrfField(): string
    {
        return '<input type="hidden" name="_csrf" value="' . h(self::csrfToken()) . '">';
    }

    public static function verifyCsrf(): void
    {
        $token = (string) ($_POST['_csrf'] ?? '');
        if (!self::sameOriginRequest() || $token === '' || !hash_equals(self::csrfToken(), $token)) {
            $_SESSION['flash'] = [
                'type' => 'danger',
                'message' => 'The form expired. Please complete the fresh form below.',
            ];
            $requestPath = (string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
            $requestPath = str_starts_with($requestPath, '/') ? $requestPath : '/';
            header('Location: ' . $requestPath, true, 303);
            exit;
        }
    }

    /** @return array<string,mixed>|null */
    public static function user(): ?array
    {
        if (!self::validAuthenticatedSession()) {
            return null;
        }
        $id = (int) ($_SESSION['user_id'] ?? 0);
        if ($id < 1 || !Database::installed()) {
            return null;
        }
        $statement = Database::connection()->prepare(
            "SELECT id, username, telegram_user_id, role, balance_credits, status FROM panel_users WHERE id = ? LIMIT 1"
        );
        $statement->execute([$id]);
        $user = $statement->fetch();
        if (!$user || $user['status'] !== 'active') {
            self::logout();
            return null;
        }
        return $user;
    }

    /** @return array<string,mixed> */
    public static function requireUser(bool $ownerOnly = false): array
    {
        $user = self::user();
        if (!$user) {
            redirect('login');
        }
        if ($ownerOnly && $user['role'] !== 'owner') {
            http_response_code(403);
            exit('Owner access required.');
        }
        return $user;
    }

    public static function login(string $username, string $password, string $telegramUserId): bool
    {
        $statement = Database::connection()->prepare(
            'SELECT id, password_hash, telegram_user_id, status FROM panel_users WHERE username = ? LIMIT 1'
        );
        $statement->execute([$username]);
        $user = $statement->fetch();
        $telegramMatches = !$user || $user['telegram_user_id'] === null
            || hash_equals((string) $user['telegram_user_id'], trim($telegramUserId));
        if (!$user || $user['status'] !== 'active' || !$telegramMatches
            || !password_verify($password, $user['password_hash'])) {
            password_verify($password, '$2y$12$usesomesillystringfore7hnbRJHxXVLeakoG8K30oukPsA.ztMG');
            return false;
        }
        session_regenerate_id(true);
        $_SESSION['user_id'] = (int) $user['id'];
        $_SESSION['auth_started_at'] = time();
        $_SESSION['last_seen_at'] = time();
        $_SESSION['last_regenerated_at'] = time();
        $_SESSION['user_agent_hash'] = self::userAgentHash();
        unset($_SESSION['csrf']);
        if (password_needs_rehash((string) $user['password_hash'], self::passwordAlgorithm())) {
            Database::connection()->prepare('UPDATE panel_users SET password_hash=? WHERE id=?')
                ->execute([self::hashPassword($password), (int) $user['id']]);
        }
        Database::connection()->prepare('UPDATE panel_users SET last_login_at = UTC_TIMESTAMP() WHERE id = ?')
            ->execute([(int) $user['id']]);
        return true;
    }

    public static function logout(): void
    {
        $_SESSION = [];
        if (session_status() === PHP_SESSION_ACTIVE) {
            $params = session_get_cookie_params();
            setcookie(session_name(), '', [
                'expires' => time() - 42000,
                'path' => $params['path'],
                'domain' => $params['domain'],
                'secure' => $params['secure'],
                'httponly' => $params['httponly'],
                'samesite' => $params['samesite'] ?? 'Strict',
            ]);
            session_destroy();
        }
    }

    public static function hashPassword(string $password): string
    {
        $hash = password_hash($password, self::passwordAlgorithm());
        if (!is_string($hash)) {
            throw new \RuntimeException('Password hashing failed.');
        }
        return $hash;
    }

    public static function audit(PDO $db, int $actorId, string $action, string $target = ''): void
    {
        $statement = $db->prepare(
            'INSERT INTO audit_log (actor_user_id, action_name, target_value, ip_address) VALUES (?, ?, ?, ?)'
        );
        $statement->execute([$actorId, substr($action, 0, 64), substr($target, 0, 191), self::clientIp()]);
    }

    public static function clientIp(): string
    {
        return substr((string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown'), 0, 45);
    }

    public static function captchaQuestion(): string
    {
        if (!isset($_SESSION['captcha_answer'], $_SESSION['captcha_expires'])
            || (int) $_SESSION['captcha_expires'] < time()) {
            $left = random_int(2, 12);
            $right = random_int(1, 9);
            $_SESSION['captcha_answer'] = (string) ($left + $right);
            $_SESSION['captcha_question'] = "$left + $right";
            $_SESSION['captcha_expires'] = time() + 300;
        }
        return (string) $_SESSION['captcha_question'];
    }

    public static function verifyCaptcha(string $answer): bool
    {
        $expected = (string) ($_SESSION['captcha_answer'] ?? '');
        $expires = (int) ($_SESSION['captcha_expires'] ?? 0);
        unset($_SESSION['captcha_answer'], $_SESSION['captcha_question'], $_SESSION['captcha_expires']);
        return $expected !== '' && $expires >= time() && hash_equals($expected, trim($answer));
    }

    private static function validAuthenticatedSession(): bool
    {
        if ((int) ($_SESSION['user_id'] ?? 0) < 1) {
            return false;
        }
        $now = time();
        $started = (int) ($_SESSION['auth_started_at'] ?? 0);
        $lastSeen = (int) ($_SESSION['last_seen_at'] ?? 0);
        $lastRegenerated = (int) ($_SESSION['last_regenerated_at'] ?? 0);
        $agentHash = (string) ($_SESSION['user_agent_hash'] ?? '');
        if ($started < 1 || $lastSeen < 1 || $lastRegenerated < 1
            || $now - $lastSeen > self::SESSION_IDLE_SECONDS
            || $now - $started > self::SESSION_ABSOLUTE_SECONDS
            || $agentHash === '' || !hash_equals($agentHash, self::userAgentHash())) {
            self::logout();
            return false;
        }
        if ($now - $lastRegenerated >= self::SESSION_ROTATE_SECONDS) {
            session_regenerate_id(true);
            $_SESSION['last_regenerated_at'] = $now;
        }
        $_SESSION['last_seen_at'] = $now;
        return true;
    }

    public static function sameOriginRequest(): bool
    {
        $fetchSite = strtolower((string) ($_SERVER['HTTP_SEC_FETCH_SITE'] ?? ''));
        $source = (string) ($_SERVER['HTTP_ORIGIN'] ?? '');
        if ($source === '') {
            $source = (string) ($_SERVER['HTTP_REFERER'] ?? '');
        }
        $expected = self::normalizedOrigin(Env::get('APP_URL'));
        if ($source !== '' && strtolower(trim($source)) !== 'null') {
            $actual = self::normalizedOrigin($source);
            return $expected !== '' && $actual !== '' && hash_equals($expected, $actual);
        }

        // Some privacy browsers omit Origin/Referer or report a top-level
        // same-origin form as Sec-Fetch-Site: none. Prefer an exact source
        // origin when supplied, otherwise accept only an explicit same-origin
        // browser signal. Headerless clients still require the random CSRF
        // token and are retained for compatibility with older WebViews.
        return $fetchSite === '' || $fetchSite === 'same-origin';
    }

    private static function normalizedOrigin(string $url): string
    {
        $parts = parse_url($url);
        if (!is_array($parts) || !isset($parts['scheme'], $parts['host'])) {
            return '';
        }
        $scheme = strtolower((string) $parts['scheme']);
        $host = strtolower((string) $parts['host']);
        $port = isset($parts['port']) ? (int) $parts['port'] : ($scheme === 'https' ? 443 : 80);
        return $scheme . '://' . $host . ':' . $port;
    }

    private static function userAgentHash(): string
    {
        return hash('sha256', substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 512));
    }

    private static function passwordAlgorithm(): string|int|null
    {
        return defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_DEFAULT;
    }
}
