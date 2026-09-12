<?php
declare(strict_types=1);

function sdk_feature_handle_registration(mysqli $conn): never
{
    $username = trim((string)($_POST['username'] ?? ''));
    $password = (string)($_POST['password'] ?? '');
    $code = strtoupper(trim((string)($_POST['referral_code'] ?? '')));

    if (function_exists('panel_rate_limit') && !panel_rate_limit($conn, 'register|' . sdk_feature_ip(), 5)) {
        sdk_feature_registration_fail('Too many attempts. Wait one minute and try again.');
    }
    if (preg_match('/^[A-Za-z0-9_.-]{3,32}$/D', $username) !== 1 || strlen($password) < 12 || strlen($password) > 128) {
        sdk_feature_registration_fail('Invalid registration details.');
    }
    if (preg_match('/^[A-Z0-9-]{4,64}$/D', $code) !== 1) {
        sdk_feature_registration_fail('Invalid or unavailable referral code.');
    }

    $conn->begin_transaction();
    try {
        $stmt = $conn->prepare(
            'SELECT id,assigned_to,grant_balance,max_uses,use_count,status,created_by,expires_at,revoked_at '
            . 'FROM referral_codes WHERE code=? FOR UPDATE'
        );
        $stmt->bind_param('s', $code);
        $stmt->execute();
        $ref = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$ref || (int)$ref['status'] !== 1 || $ref['revoked_at'] !== null
            || ((int)$ref['max_uses'] > 0 && (int)$ref['use_count'] >= (int)$ref['max_uses'])
            || ($ref['expires_at'] !== null && strtotime((string)$ref['expires_at'] . ' UTC') <= time())) {
            throw new RuntimeException('Invalid or unavailable referral code.');
        }

        $role = (string)$ref['assigned_to'];
        if (!in_array($role, ['admin', 'reseller', 'user'], true)) {
            $role = 'user';
        }
        $balance = max(0, (int)$ref['grant_balance']);
        $referredBy = (int)($ref['created_by'] ?? 0);
        $referredByValue = $referredBy > 0 ? $referredBy : null;
        $hash = password_hash($password, PASSWORD_DEFAULT);
        $insert = $conn->prepare('INSERT INTO users(username,password,role,balance,referred_by,login_not_before) VALUES(?,?,?,?,?,UTC_TIMESTAMP()+INTERVAL 15 SECOND)');
        $insert->bind_param('sssii', $username, $hash, $role, $balance, $referredByValue);
        if (!$insert->execute()) {
            if ($conn->errno === 1062) {
                throw new RuntimeException('Username already exists.');
            }
            throw new RuntimeException('Registration could not be completed.');
        }
        $userId = (int)$conn->insert_id;
        $insert->close();

        $refId = (int)$ref['id'];
        $newCount = (int)$ref['use_count'] + 1;
        $exhausted = (int)$ref['max_uses'] > 0 && $newCount >= (int)$ref['max_uses'];
        $usedBy = $exhausted ? $userId : null;
        $status = $exhausted ? 0 : 1;
        $update = $conn->prepare(
            'UPDATE referral_codes SET use_count=?,used_by=COALESCE(?,used_by),used_at=CASE WHEN ?=1 THEN UTC_TIMESTAMP() ELSE used_at END,status=? WHERE id=?'
        );
        $exhaustInt = $exhausted ? 1 : 0;
        $update->bind_param('iiiii', $newCount, $usedBy, $exhaustInt, $status, $refId);
        $update->execute();
        $update->close();

        $use = $conn->prepare('INSERT INTO referral_code_uses(referral_id,user_id) VALUES(?,?)');
        $use->bind_param('ii', $refId, $userId);
        $use->execute();
        $use->close();

        if ($balance > 0) {
            $reason = 'Referral signup grant';
            $hist = $conn->prepare('INSERT INTO balance_history(user_id,actor_user_id,delta,balance_after,reason) VALUES(?,?,?,?,?)');
            $delta = $balance;
            $actor = $referredByValue;
            $hist->bind_param('iiiis', $userId, $actor, $delta, $balance, $reason);
            $hist->execute();
            $hist->close();
        }

        $conn->commit();
        sdk_feature_audit($conn, 'registration', 'success', null, $userId, ['role' => $role, 'referral_id' => $refId]);
        $_SESSION['sdk_registration_review'] = [
            'user_id' => $userId, 'username' => $username, 'role' => $role,
            'balance' => $balance, 'referral' => $code, 'created_at' => gmdate('Y-m-d H:i:s'),
            'ready_at' => time() + 15,
        ];
        header('Location: registration_review.php');
        exit;
    } catch (Throwable $e) {
        $conn->rollback();
        sdk_feature_audit($conn, 'registration', 'failed', null, null, ['reason' => substr($e->getMessage(), 0, 120)]);
        sdk_feature_registration_fail($e->getMessage());
    }
}

function sdk_feature_registration_fail(string $message): never
{
    http_response_code(400);
    $safe = htmlspecialchars($message, ENT_QUOTES, 'UTF-8');
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Registration</title>'
        . '<style>body{margin:0;min-height:100svh;display:grid;place-items:center;background:#050810;color:#fff;font-family:Inter,system-ui;padding:24px}.c{max-width:520px;padding:30px;border:1px solid rgba(255,255,255,.12);border-radius:22px;background:#0f172a}.e{color:#fda4af}a{color:#93c5fd}</style></head><body>'
        . '<div class="c"><h2>Registration could not continue</h2><p class="e">' . $safe . '</p><p><a href="register.php">Back to registration</a></p></div></body></html>';
    exit;
}

function sdk_feature_adjust_balance(mysqli $conn, int $userId, int $delta, ?int $actorId, string $reason): int
{
    $conn->begin_transaction();
    try {
        $stmt = $conn->prepare('SELECT balance FROM users WHERE id=? FOR UPDATE');
        $stmt->bind_param('i', $userId);
        $stmt->execute();
        $row = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$row) {
            throw new RuntimeException('User not found.');
        }
        $current = (int)$row['balance'];
        $next = $current + $delta;
        if ($next < 0) {
            throw new RuntimeException('Insufficient balance.');
        }
        $update = $conn->prepare('UPDATE users SET balance=? WHERE id=?');
        $update->bind_param('ii', $next, $userId);
        $update->execute();
        $update->close();
        $reason = substr(trim($reason), 0, 160);
        $hist = $conn->prepare('INSERT INTO balance_history(user_id,actor_user_id,delta,balance_after,reason) VALUES(?,?,?,?,?)');
        $hist->bind_param('iiiis', $userId, $actorId, $delta, $next, $reason);
        $hist->execute();
        $hist->close();
        $conn->commit();
        sdk_feature_audit($conn, 'balance_change', 'success', $actorId, $userId, ['delta' => $delta, 'balance_after' => $next, 'reason' => $reason]);
        return $next;
    } catch (Throwable $e) {
        $conn->rollback();
        throw $e;
    }
}

function sdk_feature_generate_license(mysqli $conn, array $actor, array $input, string $source = 'panel'): array
{
    $settings = sdk_feature_settings($conn);
    if ((string)$actor['role'] !== 'owner' && (!sdk_feature_bool($settings['panel_online']) || !sdk_feature_bool($settings['generation_open']))) {
        throw new RuntimeException('License generation is paused by the owner.');
    }

    $days = max(1, min(3650, (int)($input['days'] ?? 1)));
    $maxDevices = (int)($input['max_devices'] ?? 1);
    if (!in_array($maxDevices, [1, 10, 20, 30, 50, 100, 500, 1000], true)) {
        throw new RuntimeException('Invalid device limit.');
    }
    $clientName = trim((string)($input['client_name'] ?? 'Parallax Access'));
    if ($clientName === '' || mb_strlen($clientName) > 120) {
        throw new RuntimeException('Client name is required.');
    }
    $package = trim((string)($input['package_name'] ?? ''));
    $packageMode = $package === '' ? 'ANY' : 'SPECIFIC';
    if ($package !== '' && preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $package) !== 1) {
        throw new RuntimeException('Invalid Android package name.');
    }
    $custom = strtoupper(trim((string)($input['custom_key'] ?? '')));
    if ($custom !== '' && preg_match('/^[A-Z0-9_-]{16,96}$/D', $custom) !== 1) {
        throw new RuntimeException('Custom key must be 16-96 characters using A-Z, 0-9, underscore or hyphen.');
    }
    $prefix = strtoupper(preg_replace('/[^A-Z0-9]/', '', (string)($input['key_prefix'] ?? 'SDK')) ?: 'SDK');
    $license = $custom !== '' ? $custom : $prefix . '-' . strtoupper(bin2hex(random_bytes(12)));
    $costPerDay = max(0, min(1000000000, (int)($settings['key_cost_per_day'] ?? 1)));
    $cost = $days * $costPerDay;
    $actorId = (int)$actor['id'];
    $owner = (string)$actor['role'] === 'owner';
    $expiry = gmdate('Y-m-d H:i:s', time() + ($days * 86400));
    $generatedBy = (string)$actor['username'];
    $source = substr(preg_replace('/[^A-Za-z0-9_.:-]/', '_', $source) ?: 'panel', 0, 32);

    $conn->begin_transaction();
    try {
        $balanceAfter = (int)($actor['balance'] ?? 0);
        if (!$owner && $cost > 0) {
            $lock = $conn->prepare('SELECT balance FROM users WHERE id=? FOR UPDATE');
            $lock->bind_param('i', $actorId);
            $lock->execute();
            $row = $lock->get_result()->fetch_assoc();
            $lock->close();
            if (!$row || (int)$row['balance'] < $cost) {
                throw new RuntimeException('Insufficient balance for this license.');
            }
            $balanceAfter = (int)$row['balance'] - $cost;
            $upd = $conn->prepare('UPDATE users SET balance=? WHERE id=?');
            $upd->bind_param('ii', $balanceAfter, $actorId);
            $upd->execute();
            $upd->close();
        }

        $packageValue = $package === '' ? null : $package;
        $packageLock = $package === '' ? 0 : 1;
        $status = 1;
        $signingLock = 0;
        $signingMode = 'ANY';
        $deviceMode = $maxDevices === 1 ? 'SINGLE' : 'LIMITED';
        $javaNativeAuth = 1;
        $featurePolicy = '{}';
        $minSdk = 3;
        $latestSdk = 3;
        $forceUpdate = 0;
        $blockedVersions = '[]';
        $sessionLifetime = 600;
        $killSwitch = 0;
        $stmt = $conn->prepare(
            'INSERT INTO licenses(license_key,client_name,expiry_date,status,package_name,package_lock,package_mode,signing_lock,signing_mode,signing_cert_sha256,device_mode,max_devices,java_native_auth,feature_policy,minimum_sdk_version,latest_sdk_version,force_update,blocked_versions,session_lifetime_seconds,kill_switch,generated_by,owner_user_id) '
            . 'VALUES(?,?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,?,?,?,?,?,?,?)'
        );
        $stmt->bind_param(
            'sssisisissiisiiisiisi',
            $license, $clientName, $expiry, $status, $packageValue, $packageLock,
            $packageMode, $signingLock, $signingMode, $deviceMode, $maxDevices,
            $javaNativeAuth, $featurePolicy, $minSdk, $latestSdk, $forceUpdate,
            $blockedVersions, $sessionLifetime, $killSwitch, $generatedBy, $actorId
        );
        if (!$stmt->execute()) {
            if ($conn->errno === 1062) {
                throw new RuntimeException('That license key already exists.');
            }
            throw new RuntimeException('License could not be created.');
        }
        $licenseId = (int)$conn->insert_id;
        $stmt->close();

        if (!$owner && $cost > 0) {
            $delta = -$cost;
            $reason = 'License generation: ' . $days . ' day(s)';
            $hist = $conn->prepare('INSERT INTO balance_history(user_id,actor_user_id,delta,balance_after,reason) VALUES(?,?,?,?,?)');
            $hist->bind_param('iiiis', $actorId, $actorId, $delta, $balanceAfter, $reason);
            $hist->execute();
            $hist->close();
        }
        $conn->commit();
        sdk_feature_audit($conn, 'license_generate', 'success', $actorId, $actorId, [
            'license_id' => $licenseId,
            'days' => $days,
            'max_devices' => $maxDevices,
            'cost' => $owner ? 0 : $cost,
            'source' => $source,
        ]);
        return [
            'license_key' => $license,
            'expiry_date' => $expiry,
            'days' => $days,
            'max_devices' => $maxDevices,
            'cost' => $owner ? 0 : $cost,
            'balance_after' => $balanceAfter,
        ];
    } catch (Throwable $e) {
        $conn->rollback();
        sdk_feature_audit($conn, 'license_generate', 'failed', $actorId, $actorId, ['reason' => substr($e->getMessage(), 0, 120), 'source' => $source]);
        throw $e;
    }
}

function sdk_feature_create_referral(mysqli $conn, array $actor, array $input): string
{
    $role = strtolower(trim((string)($input['role'] ?? 'user')));
    $actorRole = (string)$actor['role'];
    $allowed = $actorRole === 'owner' ? ['admin', 'reseller', 'user'] : ($actorRole === 'admin' ? ['user'] : []);
    if (!in_array($role, $allowed, true)) {
        throw new RuntimeException('You cannot create that referral role.');
    }
    $grant = max(0, min(PHP_INT_MAX, (int)($input['grant_balance'] ?? 0)));
    $maxUses = max(1, min(1000, (int)($input['max_uses'] ?? 1)));
    $expiryDays = max(1, min(365, (int)($input['expiry_days'] ?? 7)));
    $custom = strtoupper(trim((string)($input['code'] ?? '')));
    if ($custom !== '' && preg_match('/^[A-Z0-9-]{8,40}$/D', $custom) !== 1) {
        throw new RuntimeException('Referral code must be 8-40 characters using A-Z, 0-9 or hyphen.');
    }
    $code = $custom !== '' ? $custom : 'SDK-' . strtoupper(bin2hex(random_bytes(8)));
    $creator = (int)$actor['id'];
    $expires = gmdate('Y-m-d H:i:s', time() + ($expiryDays * 86400));
    $status = 1;
    $stmt = $conn->prepare(
        'INSERT INTO referral_codes(code,assigned_to,grant_balance,max_uses,use_count,expires_at,status,created_by) VALUES(?,?,?,?,0,?,?,?)'
    );
    $stmt->bind_param('ssiisii', $code, $role, $grant, $maxUses, $expires, $status, $creator);
    if (!$stmt->execute()) {
        if ($conn->errno === 1062) {
            throw new RuntimeException('Referral code already exists.');
        }
        throw new RuntimeException('Referral could not be created.');
    }
    $refId = (int)$conn->insert_id;
    $stmt->close();
    sdk_feature_audit($conn, 'referral_create', 'success', $creator, null, [
        'referral_id' => $refId, 'role' => $role, 'grant_balance' => $grant, 'max_uses' => $maxUses,
    ]);
    return $code;
}
