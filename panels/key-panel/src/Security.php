<?php

declare(strict_types=1);

namespace ParallaxPanel;

use PDO;

final class Security
{
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
        if ($token === '' || !hash_equals(self::csrfToken(), $token)) {
            http_response_code(419);
            exit('Invalid or expired form token. Reload the page and try again.');
        }
    }

    /** @return array<string,mixed>|null */
    public static function user(): ?array
    {
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
        unset($_SESSION['csrf']);
        Database::connection()->prepare('UPDATE panel_users SET last_login_at = UTC_TIMESTAMP() WHERE id = ?')
            ->execute([(int) $user['id']]);
        return true;
    }

    public static function logout(): void
    {
        unset($_SESSION['user_id'], $_SESSION['csrf']);
        session_regenerate_id(true);
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
}
