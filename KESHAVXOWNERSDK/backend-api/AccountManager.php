<?php
declare(strict_types=1);

final class AccountException extends RuntimeException
{
}

/** Role, invitation, balance, and license-key authorization service. */
final class AccountManager
{
    public const ROLE_OWNER = 'owner';
    public const ROLE_ADMIN = 'admin';
    public const ROLE_USER = 'user';

    private const ROLES = [self::ROLE_OWNER, self::ROLE_ADMIN, self::ROLE_USER];

    public function __construct(private Database $database)
    {
    }

    public function hasUsers(): bool
    {
        $row = $this->database->fetchOne('SELECT COUNT(*) AS count FROM dashboard_users');
        return (int) ($row['count'] ?? 0) > 0;
    }

    public function bootstrapOwner(string $username, string $password): array
    {
        $username = self::normalizeUsername($username);
        self::validatePassword($password);

        return $this->database->transaction(function (Database $db) use ($username, $password): array {
            $row = $db->fetchOne(
                'SELECT id FROM dashboard_users ORDER BY id LIMIT 1 FOR UPDATE'
            );
            if ($row !== null) {
                throw new AccountException('Owner setup has already been completed.');
            }
            $db->execute(
                'INSERT INTO dashboard_users
                    (username, password_hash, role, referral_code)
                 VALUES (:username, :password_hash, :role, :referral_code)',
                [
                    'username' => $username,
                    'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                    'role' => self::ROLE_OWNER,
                    'referral_code' => self::newReferralCode(),
                ]
            );
            return $this->userById((int) $db->pdo()->lastInsertId(), $db);
        });
    }

    public function authenticate(string $username, string $password): ?array
    {
        try {
            $username = self::normalizeUsername($username);
        } catch (AccountException) {
            password_verify($password, '$2y$10$usesomesillystringfore7hnbRJHxXVLeakoG8K30oukPsA.ztMG');
            return null;
        }
        $user = $this->database->fetchOne(
            'SELECT * FROM dashboard_users WHERE username = :username LIMIT 1',
            ['username' => $username]
        );
        if ($user === null || !password_verify($password, (string) $user['password_hash'])) {
            return null;
        }
        if (($user['status'] ?? '') !== 'active') {
            throw new AccountException('This dashboard account is suspended.');
        }
        if (password_needs_rehash((string) $user['password_hash'], PASSWORD_DEFAULT)) {
            $this->database->execute(
                'UPDATE dashboard_users SET password_hash = :password_hash WHERE id = :id',
                [
                    'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                    'id' => $user['id'],
                ]
            );
        }
        $this->database->execute(
            'UPDATE dashboard_users SET last_login_at = UTC_TIMESTAMP(6) WHERE id = :id',
            ['id' => $user['id']]
        );
        unset($user['password_hash']);
        return $user;
    }

    public function userById(int $userId, ?Database $database = null): array
    {
        $database ??= $this->database;
        $user = $database->fetchOne(
            'SELECT id, username, role, balance_credits, referral_code, status,
                    referred_by_user_id, last_login_at, created_at
             FROM dashboard_users WHERE id = :id',
            ['id' => $userId]
        );
        if ($user === null) {
            throw new AccountException('Dashboard account no longer exists.');
        }
        return $user;
    }

    public function createInvite(
        array $actor,
        string $role,
        int $initialBalance,
        int $validHours,
        int $maxUses
    ): string {
        self::requireRole($actor, [self::ROLE_OWNER]);
        if (!in_array($role, self::ROLES, true)) {
            throw new AccountException('Invitation role is invalid.');
        }
        if ($initialBalance < 0 || $initialBalance > 1_000_000) {
            throw new AccountException('Initial balance must be 0-1000000 credits.');
        }
        if ($validHours < 1 || $validHours > 720) {
            throw new AccountException('Invitation validity must be 1-720 hours.');
        }
        if ($maxUses < 1 || $maxUses > 100) {
            throw new AccountException('Invitation uses must be 1-100.');
        }

        $token = '5RI-' . self::base64Url(random_bytes(24));
        $this->database->execute(
            'INSERT INTO registration_invites
                (token_hash, token_prefix, created_by_user_id, assigned_role,
                 initial_balance, max_uses, expires_at)
             VALUES
                (:token_hash, :token_prefix, :creator, :role,
                 :initial_balance, :max_uses,
                 DATE_ADD(UTC_TIMESTAMP(6), INTERVAL :valid_hours HOUR))',
            [
                'token_hash' => hash('sha256', $token),
                'token_prefix' => substr($token, 0, 16),
                'creator' => $actor['id'],
                'role' => $role,
                'initial_balance' => $initialBalance,
                'max_uses' => $maxUses,
                'valid_hours' => $validHours,
            ]
        );
        return $token;
    }

    public function register(string $inviteToken, string $username, string $password): array
    {
        $inviteToken = trim($inviteToken);
        if (preg_match('/^5RI-[A-Za-z0-9_-]{32}$/D', $inviteToken) !== 1) {
            throw new AccountException('Registration invitation is invalid.');
        }
        $username = self::normalizeUsername($username);
        self::validatePassword($password);

        return $this->database->transaction(function (Database $db) use (
            $inviteToken,
            $username,
            $password
        ): array {
            $invite = $db->fetchOne(
                'SELECT * FROM registration_invites
                 WHERE token_hash = :token_hash FOR UPDATE',
                ['token_hash' => hash('sha256', $inviteToken)]
            );
            if ($invite === null
                || $invite['status'] !== 'active'
                || strtotime((string) $invite['expires_at']) <= time()
                || (int) $invite['use_count'] >= (int) $invite['max_uses']) {
                throw new AccountException('Registration invitation is expired or unavailable.');
            }
            try {
                $db->execute(
                    'INSERT INTO dashboard_users
                        (username, password_hash, role, balance_credits,
                         referral_code, referred_by_user_id)
                     VALUES
                        (:username, :password_hash, :role, :balance,
                         :referral_code, :referrer)',
                    [
                        'username' => $username,
                        'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                        'role' => $invite['assigned_role'],
                        'balance' => $invite['initial_balance'],
                        'referral_code' => self::newReferralCode(),
                        'referrer' => $invite['created_by_user_id'],
                    ]
                );
            } catch (PDOException $exception) {
                if ((string) $exception->getCode() === '23000') {
                    throw new AccountException('Username is already registered.');
                }
                throw $exception;
            }
            $userId = (int) $db->pdo()->lastInsertId();
            $db->execute(
                'UPDATE registration_invites
                 SET use_count = use_count + 1,
                     status = IF(use_count + 1 >= max_uses, \'revoked\', status)
                 WHERE id = :id',
                ['id' => $invite['id']]
            );
            if ((int) $invite['initial_balance'] > 0) {
                $db->execute(
                    'INSERT INTO balance_transactions
                        (user_id, delta_credits, balance_after, reason, actor_user_id)
                     VALUES (:user_id, :delta, :balance_after, :reason, :actor)',
                    [
                        'user_id' => $userId,
                        'delta' => $invite['initial_balance'],
                        'balance_after' => $invite['initial_balance'],
                        'reason' => 'Registration referral balance',
                        'actor' => $invite['created_by_user_id'],
                    ]
                );
            }
            return $this->userById($userId, $db);
        });
    }

    public function adjustBalance(array $actor, int $targetUserId, int $delta, string $reason): int
    {
        self::requireRole($actor, [self::ROLE_OWNER]);
        $reason = trim($reason);
        if ($delta === 0 || abs($delta) > 1_000_000) {
            throw new AccountException('Balance adjustment must be between -1000000 and 1000000.');
        }
        if ($reason === '' || strlen($reason) > 200) {
            throw new AccountException('Balance reason must be 1-200 characters.');
        }

        return $this->database->transaction(function (Database $db) use (
            $actor,
            $targetUserId,
            $delta,
            $reason
        ): int {
            $target = $db->fetchOne(
                'SELECT balance_credits FROM dashboard_users WHERE id = :id FOR UPDATE',
                ['id' => $targetUserId]
            );
            if ($target === null) {
                throw new AccountException('Target account was not found.');
            }
            $balance = (int) $target['balance_credits'] + $delta;
            if ($balance < 0) {
                throw new AccountException('Balance cannot become negative.');
            }
            $db->execute(
                'UPDATE dashboard_users SET balance_credits = :balance WHERE id = :id',
                ['balance' => $balance, 'id' => $targetUserId]
            );
            $db->execute(
                'INSERT INTO balance_transactions
                    (user_id, delta_credits, balance_after, reason, actor_user_id)
                 VALUES (:user_id, :delta, :balance_after, :reason, :actor)',
                [
                    'user_id' => $targetUserId,
                    'delta' => $delta,
                    'balance_after' => $balance,
                    'reason' => $reason,
                    'actor' => $actor['id'],
                ]
            );
            return $balance;
        });
    }

    public function setUserStatus(array $actor, int $targetUserId, string $status): void
    {
        self::requireRole($actor, [self::ROLE_OWNER]);
        if (!in_array($status, ['active', 'suspended'], true)) {
            throw new AccountException('Account status is invalid.');
        }
        if ((int) $actor['id'] === $targetUserId) {
            throw new AccountException('Owner cannot suspend the active session account.');
        }
        $this->database->execute(
            'UPDATE dashboard_users SET status = :status WHERE id = :id',
            ['status' => $status, 'id' => $targetUserId]
        );
    }

    public function setUserRole(array $actor, int $targetUserId, string $role): void
    {
        self::requireRole($actor, [self::ROLE_OWNER]);
        if (!in_array($role, self::ROLES, true)) {
            throw new AccountException('Account role is invalid.');
        }
        if ((int) $actor['id'] === $targetUserId) {
            throw new AccountException('Owner cannot change the active session role.');
        }
        $this->database->execute(
            'UPDATE dashboard_users SET role = :role WHERE id = :id',
            ['role' => $role, 'id' => $targetUserId]
        );
    }

    public function revokeInvite(array $actor, int $inviteId): void
    {
        self::requireRole($actor, [self::ROLE_OWNER]);
        $this->database->execute(
            'UPDATE registration_invites SET status = \'revoked\' WHERE id = :id',
            ['id' => $inviteId]
        );
    }

    public function createLicense(
        array $actor,
        string $label,
        string $prefix,
        int $maxDevices,
        int $validDays
    ): array {
        self::requireRole($actor, self::ROLES);
        $label = trim($label);
        if ($label === '' || strlen($label) > 100) {
            throw new AccountException('Custom key name must be 1-100 characters.');
        }
        try {
            $prefix = SelfHostedVerifier::normalizeActivationPrefix($prefix);
        } catch (InvalidArgumentException $exception) {
            throw new AccountException($exception->getMessage());
        }
        if ($maxDevices < 1 || $maxDevices > 100) {
            throw new AccountException('Max devices must be 1-100.');
        }
        if ($validDays < 1 || $validDays > 3650) {
            throw new AccountException('Validity must be 1-3650 days.');
        }
        $plainKey = SelfHostedVerifier::generateActivationKey($prefix);
        $cost = max(0, min(1_000_000, (int) $this->database->getAppConfig('key_cost_credits', 1)));

        $balanceAfter = $this->database->transaction(function (Database $db) use (
            $actor,
            $plainKey,
            $label,
            $maxDevices,
            $validDays,
            $cost
        ): int {
            $balance = (int) $actor['balance_credits'];
            if ($actor['role'] !== self::ROLE_OWNER && $cost > 0) {
                $locked = $db->fetchOne(
                    'SELECT balance_credits, status FROM dashboard_users WHERE id = :id FOR UPDATE',
                    ['id' => $actor['id']]
                );
                if ($locked === null || $locked['status'] !== 'active') {
                    throw new AccountException('Dashboard account is unavailable.');
                }
                $balance = (int) $locked['balance_credits'];
                if ($balance < $cost) {
                    throw new AccountException('Insufficient balance to generate this key.');
                }
                $balance -= $cost;
                $db->execute(
                    'UPDATE dashboard_users SET balance_credits = :balance WHERE id = :id',
                    ['balance' => $balance, 'id' => $actor['id']]
                );
                $db->execute(
                    'INSERT INTO balance_transactions
                        (user_id, delta_credits, balance_after, reason, actor_user_id)
                     VALUES (:user_id, :delta, :balance_after, :reason, :actor)',
                    [
                        'user_id' => $actor['id'],
                        'delta' => -$cost,
                        'balance_after' => $balance,
                        'reason' => 'Activation key generation: ' . $label,
                        'actor' => $actor['id'],
                    ]
                );
            }
            $db->execute(
                'INSERT INTO license_keys
                    (created_by_user_id, key_hash, key_prefix, label,
                     max_devices, expires_at)
                 VALUES
                    (:creator, :key_hash, :key_prefix, :label,
                     :max_devices, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL :valid_days DAY))',
                [
                    'creator' => $actor['id'],
                    'key_hash' => SelfHostedVerifier::activationKeyHash($plainKey),
                    'key_prefix' => substr($plainKey, 0, 16),
                    'label' => $label,
                    'max_devices' => $maxDevices,
                    'valid_days' => $validDays,
                ]
            );
            return $balance;
        });

        return ['key' => $plainKey, 'cost' => $actor['role'] === self::ROLE_OWNER ? 0 : $cost,
            'balance_after' => $balanceAfter];
    }

    public function revokeLicense(array $actor, int $licenseId): void
    {
        $license = $this->database->fetchOne(
            'SELECT created_by_user_id FROM license_keys WHERE id = :id',
            ['id' => $licenseId]
        );
        if ($license === null) {
            throw new AccountException('Activation key was not found.');
        }
        $mayManageAll = in_array($actor['role'], [self::ROLE_OWNER, self::ROLE_ADMIN], true);
        if (!$mayManageAll && (int) $license['created_by_user_id'] !== (int) $actor['id']) {
            throw new AccountException('You can revoke only your own keys.');
        }
        $this->database->transaction(function (Database $db) use ($licenseId): void {
            $db->execute(
                'UPDATE license_keys SET status = \'revoked\' WHERE id = :id',
                ['id' => $licenseId]
            );
            $db->execute(
                'UPDATE devices d
                 INNER JOIN device_license_bindings b ON b.device_id = d.device_id
                 SET d.status = \'revoked\'
                 WHERE b.license_key_id = :id',
                ['id' => $licenseId]
            );
        });
    }

    public static function canOperate(array $user): bool
    {
        return in_array($user['role'] ?? '', [self::ROLE_OWNER, self::ROLE_ADMIN], true);
    }

    public static function isOwner(array $user): bool
    {
        return ($user['role'] ?? '') === self::ROLE_OWNER;
    }

    public static function normalizeUsername(string $username): string
    {
        $normalized = strtolower(trim($username));
        if (preg_match('/^[a-z0-9][a-z0-9._-]{2,31}$/D', $normalized) !== 1) {
            throw new AccountException('Username must be 3-32 letters, digits, dot, dash, or underscore.');
        }
        return $normalized;
    }

    public static function validatePassword(string $password): void
    {
        if (strlen($password) < 10 || strlen($password) > 128
            || preg_match('/[A-Za-z]/', $password) !== 1
            || preg_match('/[0-9]/', $password) !== 1) {
            throw new AccountException('Password must be 10-128 characters with letters and numbers.');
        }
    }

    private static function requireRole(array $user, array $roles): void
    {
        if (($user['status'] ?? '') !== 'active' || !in_array($user['role'] ?? '', $roles, true)) {
            throw new AccountException('This account is not allowed to perform that action.');
        }
    }

    private static function newReferralCode(): string
    {
        return strtoupper(bin2hex(random_bytes(8)));
    }

    private static function base64Url(string $bytes): string
    {
        return rtrim(strtr(base64_encode($bytes), '+/', '-_'), '=');
    }
}
