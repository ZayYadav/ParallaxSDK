<?php
declare(strict_types=1);

function sdk_feature_telegram_link(mysqli $conn, string $chatId, int $tgUserId, string $token): void
{
    $token = strtoupper(trim($token));
    if (preg_match('/^SDKLINK-[A-F0-9]{12}$/D', $token) !== 1) {
        sdk_feature_telegram_send($chatId, 'Invalid link code. Start linking from Panel → Account.');
        return;
    }
    $hash = hash('sha256', $token);
    $conn->begin_transaction();
    try {
        $stmt = $conn->prepare(
            'SELECT id,user_id,requested_chat_id FROM telegram_link_tokens '
            . 'WHERE token_hash=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP() FOR UPDATE'
        );
        $stmt->bind_param('s', $hash);
        if (!$stmt->execute()) throw new RuntimeException('Could not validate link code.');
        $row = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$row || (string)$row['requested_chat_id'] !== $chatId) {
            throw new RuntimeException('This code does not belong to this Telegram chat or has expired.');
        }
        $userId = (int)$row['user_id'];

        $tg = $conn->prepare('SELECT linked_user_id FROM telegram_users WHERE id=? FOR UPDATE');
        $tg->bind_param('i', $tgUserId);
        if (!$tg->execute()) throw new RuntimeException('Could not lock Telegram account.');
        $tgRow = $tg->get_result()->fetch_assoc();
        $tg->close();
        if (!$tgRow) throw new RuntimeException('Telegram account record is unavailable.');
        $existingLinked = (int)($tgRow['linked_user_id'] ?? 0);
        if ($existingLinked > 0 && $existingLinked !== $userId) {
            throw new RuntimeException('This Telegram account is already linked to another panel account. Unlink it there first.');
        }

        // A panel account may intentionally move to a new Telegram chat, but one
        // Telegram identity can never be shared by two panel accounts.
        $clear = $conn->prepare('UPDATE telegram_users SET linked_user_id=NULL WHERE linked_user_id=? AND id<>?');
        $clear->bind_param('ii', $userId, $tgUserId);
        if (!$clear->execute()) throw new RuntimeException('Could not clear the previous Telegram link.');
        $clear->close();

        $link = $conn->prepare('UPDATE telegram_users SET linked_user_id=? WHERE id=?');
        $link->bind_param('ii', $userId, $tgUserId);
        if (!$link->execute() || $link->affected_rows < 0) throw new RuntimeException('Could not link Telegram account.');
        $link->close();

        // auth_version change revokes older web sessions because Telegram is a
        // security identity and may later be used for 2FA/Owner authority.
        $u = $conn->prepare('UPDATE users SET telegram_chat_id=?,auth_version=auth_version+1 WHERE id=?');
        $u->bind_param('si', $chatId, $userId);
        if (!$u->execute() || $u->affected_rows !== 1) {
            $u->close();
            throw new RuntimeException('Telegram Chat ID is already linked or the account is unavailable.');
        }
        $u->close();

        // Guest licenses are tagged with the Telegram row id. When that guest
        // becomes a panel user, preserve their history by assigning those keys
        // to the newly linked panel account.
        $guestMarker = 'telegram_guest:' . $tgUserId;
        $move = $conn->prepare('UPDATE licenses SET owner_user_id=? WHERE owner_user_id IS NULL AND generated_by=?');
        $move->bind_param('is', $userId, $guestMarker);
        if (!$move->execute()) throw new RuntimeException('Could not transfer guest license history.');
        $movedKeys = $move->affected_rows;
        $move->close();

        $tokenId = (int)$row['id'];
        $consume = $conn->prepare('UPDATE telegram_link_tokens SET consumed_at=UTC_TIMESTAMP() WHERE id=? AND consumed_at IS NULL');
        $consume->bind_param('i', $tokenId);
        if (!$consume->execute() || $consume->affected_rows !== 1) {
            $consume->close();
            throw new RuntimeException('Link code was already consumed.');
        }
        $consume->close();

        $conn->commit();
        sdk_feature_audit($conn, 'telegram_link', 'success', $userId, $userId, [
            'telegram_user_id' => $tgUserId,
            'guest_keys_transferred' => max(0, (int)$movedKeys),
        ]);
        sdk_feature_telegram_send($chatId, '✅ Panel account linked successfully. Previous guest keys were attached to your account when available. You can now use /account and request /2fa setup.');
    } catch (Throwable $e) {
        $conn->rollback();
        sdk_feature_telegram_send($chatId, '❌ ' . htmlspecialchars($e->getMessage(), ENT_QUOTES, 'UTF-8'));
    }
}

function sdk_feature_telegram_linked_user(mysqli $conn, int $tgUserId): ?array
{
    $stmt = $conn->prepare(
        'SELECT u.id,u.username,u.role,u.balance,u.status,u.mfa_enabled,u.telegram_2fa_enabled,u.telegram_chat_id '
        . 'FROM telegram_users t JOIN users u ON u.id=t.linked_user_id WHERE t.id=? LIMIT 1'
    );
    $stmt->bind_param('i', $tgUserId);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc() ?: null;
    $stmt->close();
    return $row;
}

function sdk_feature_telegram_2fa_token(mysqli $conn, string $chatId, int $tgUserId): void
{
    $user = sdk_feature_telegram_linked_user($conn, $tgUserId);
    if (!$user || (int)$user['status'] !== 1) {
        sdk_feature_telegram_send($chatId, 'Link an active panel account first with /link CODE.');
        return;
    }
    if ((int)($user['mfa_enabled'] ?? 0) === 1) {
        sdk_feature_telegram_send($chatId, 'TOTP MFA is already enabled on this account. Telegram 2FA cannot replace or bypass TOTP.');
        return;
    }
    if ((int)($user['telegram_2fa_enabled'] ?? 0) === 1) {
        sdk_feature_telegram_send($chatId, 'Telegram 2FA is already enabled for this account.');
        return;
    }
    $token = 'SDK2FA-' . strtoupper(bin2hex(random_bytes(6)));
    $hash = hash('sha256', $token);
    $uid = (int)$user['id'];
    $conn->query('DELETE FROM telegram_2fa_activation_tokens WHERE expires_at<=UTC_TIMESTAMP() OR consumed_at IS NOT NULL');
    $clear = $conn->prepare('DELETE FROM telegram_2fa_activation_tokens WHERE user_id=? AND consumed_at IS NULL');
    $clear->bind_param('i', $uid);
    $clear->execute();
    $clear->close();
    $stmt = $conn->prepare('INSERT INTO telegram_2fa_activation_tokens(user_id,token_hash,expires_at) VALUES(?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)');
    $stmt->bind_param('is', $uid, $hash);
    if (!$stmt->execute()) {
        $stmt->close();
        sdk_feature_telegram_send($chatId, 'Could not create a Telegram 2FA activation key.');
        return;
    }
    $stmt->close();
    sdk_feature_telegram_send($chatId, 'Your one-time Telegram 2FA activation key is <code>' . $token . '</code>\nIt expires in 10 minutes. Enter it in Panel → Account.');
}

function sdk_feature_telegram_account(mysqli $conn, string $chatId, int $tgUserId): void
{
    $user = sdk_feature_telegram_linked_user($conn, $tgUserId);
    if (!$user) {
        sdk_feature_telegram_send($chatId, 'No panel account is linked. Use /link CODE first.');
        return;
    }
    $balance = (string)$user['role'] === 'owner' ? '∞' : (string)(int)$user['balance'];
    $text = '<b>Linked account</b>\nUsername: <code>' . htmlspecialchars((string)$user['username'], ENT_QUOTES, 'UTF-8') . '</code>'
        . '\nRole: ' . htmlspecialchars((string)$user['role'], ENT_QUOTES, 'UTF-8')
        . '\nBalance: ' . $balance
        . '\nTelegram 2FA: ' . ((int)$user['telegram_2fa_enabled'] === 1 ? 'ON' : 'OFF');
    sdk_feature_telegram_send($chatId, $text);
}

function sdk_feature_telegram_free_key(mysqli $conn, string $chatId, int $tgUserId): void
{
    $settings = sdk_feature_settings($conn);
    if (!sdk_feature_bool($settings['guest_key_enabled']) || !sdk_feature_bool($settings['panel_online']) || !sdk_feature_bool($settings['generation_open'])) {
        sdk_feature_telegram_send($chatId, 'Guest key generation is currently unavailable.');
        return;
    }
    if (sdk_feature_telegram_linked_user($conn, $tgUserId)) {
        sdk_feature_telegram_send($chatId, 'Linked accounts are not eligible for the guest key. Use /key when linked generation is enabled.');
        return;
    }

    $conn->begin_transaction();
    try {
        $stmt = $conn->prepare('SELECT guest_last_key_at,guest_key_count FROM telegram_users WHERE id=? FOR UPDATE');
        $stmt->bind_param('i', $tgUserId);
        if (!$stmt->execute()) throw new RuntimeException('Could not check guest eligibility.');
        $row = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$row) throw new RuntimeException('Telegram guest record is unavailable.');
        if ($row['guest_last_key_at'] !== null && strtotime((string)$row['guest_last_key_at'] . ' UTC') > time() - 7 * 86400) {
            throw new RuntimeException('Your next free guest key is available 7 days after the previous one.');
        }

        $license = 'SDK-GUEST-' . strtoupper(bin2hex(random_bytes(10)));
        $expiry = gmdate('Y-m-d H:i:s', time() + 7200);
        $client = 'Telegram Guest'; $status = 1; $packageLock = 0; $packageMode = 'ANY'; $signingLock = 0; $signingMode = 'ANY';
        $deviceMode = 'SINGLE'; $maxDevices = 1; $javaNative = 1; $policy = '{}'; $min = 3; $latest = 3; $force = 0; $blocked = '[]'; $session = 600; $kill = 0;
        $generatedBy = 'telegram_guest:' . $tgUserId;
        $ins = $conn->prepare(
            'INSERT INTO licenses(license_key,client_name,expiry_date,status,package_name,package_lock,package_mode,signing_lock,signing_mode,signing_cert_sha256,device_mode,max_devices,java_native_auth,feature_policy,minimum_sdk_version,latest_sdk_version,force_update,blocked_versions,session_lifetime_seconds,kill_switch,generated_by,owner_user_id) '
            . 'VALUES(?,?,?,?,NULL,?,?,?,?,NULL,?,?,?,?,?,?,?,?,?,?,?,NULL)'
        );
        $ins->bind_param('sssiisissiisiiisiis', $license,$client,$expiry,$status,$packageLock,$packageMode,$signingLock,$signingMode,$deviceMode,$maxDevices,$javaNative,$policy,$min,$latest,$force,$blocked,$session,$kill,$generatedBy);
        if (!$ins->execute()) {
            $ins->close();
            throw new RuntimeException('Could not create a guest key.');
        }
        $ins->close();
        $upd = $conn->prepare('UPDATE telegram_users SET guest_last_key_at=UTC_TIMESTAMP(),guest_key_count=guest_key_count+1 WHERE id=?');
        $upd->bind_param('i', $tgUserId);
        if (!$upd->execute() || $upd->affected_rows !== 1) {
            $upd->close();
            throw new RuntimeException('Could not update guest eligibility.');
        }
        $upd->close();
        $conn->commit();
        sdk_feature_audit($conn, 'telegram_guest_key', 'success', null, null, ['telegram_user_id' => $tgUserId]);
        sdk_feature_telegram_send($chatId, '✅ 2-hour guest key\n<code>' . $license . '</code>\nExpires: ' . sdk_feature_display_time($expiry));
    } catch (Throwable $e) {
        $conn->rollback();
        sdk_feature_telegram_send($chatId, '❌ ' . htmlspecialchars($e->getMessage(), ENT_QUOTES, 'UTF-8'));
    }
}

function sdk_feature_telegram_linked_key(mysqli $conn, string $chatId, int $tgUserId, string $daysArg): void
{
    $settings = sdk_feature_settings($conn);
    if (!sdk_feature_bool($settings['telegram_linked_generation_enabled'])) {
        sdk_feature_telegram_send($chatId, 'Linked-user Telegram generation is disabled by the owner.');
        return;
    }
    $days = (int)$daysArg;
    if (!in_array($days, [1, 7, 30], true)) {
        sdk_feature_telegram_send($chatId, 'Usage: /key 1, /key 7, or /key 30');
        return;
    }
    $user = sdk_feature_telegram_linked_user($conn, $tgUserId);
    if (!$user || (int)$user['status'] !== 1) {
        sdk_feature_telegram_send($chatId, 'Link an active panel account first.');
        return;
    }
    try {
        $result = sdk_feature_generate_license($conn, $user, ['days' => $days, 'max_devices' => 1, 'client_name' => 'Telegram Linked'], 'telegram_linked');
        $balance = (string)$user['role'] === 'owner' ? '∞' : (string)$result['balance_after'];
        sdk_feature_telegram_send($chatId, '✅ Key created\n<code>' . $result['license_key'] . '</code>\nExpires: ' . sdk_feature_display_time((string)$result['expiry_date']) . '\nCost: ' . $result['cost'] . '\nBalance: ' . $balance);
    } catch (Throwable $e) {
        sdk_feature_telegram_send($chatId, '❌ ' . htmlspecialchars($e->getMessage(), ENT_QUOTES, 'UTF-8'));
    }
}
