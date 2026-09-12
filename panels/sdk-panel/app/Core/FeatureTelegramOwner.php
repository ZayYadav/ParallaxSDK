<?php
declare(strict_types=1);

function sdk_feature_telegram_owner_stats(mysqli $conn, string $chatId): void
{
    $users = (int)(($conn->query('SELECT COUNT(*) c FROM users')->fetch_assoc()['c'] ?? 0));
    $keys = (int)(($conn->query('SELECT COUNT(*) c FROM licenses')->fetch_assoc()['c'] ?? 0));
    $active = (int)(($conn->query('SELECT COUNT(*) c FROM licenses WHERE status=1 AND expiry_date>UTC_TIMESTAMP()')->fetch_assoc()['c'] ?? 0));
    $tg = (int)(($conn->query('SELECT COUNT(*) c FROM telegram_users')->fetch_assoc()['c'] ?? 0));
    sdk_feature_telegram_send($chatId, "<b>Panel stats</b>\nUsers: $users\nLicenses: $keys\nActive licenses: $active\nTelegram users: $tg");
}

function sdk_feature_telegram_owner_users(mysqli $conn, string $chatId): void
{
    $res = $conn->query('SELECT username,role,balance,status FROM users ORDER BY id DESC LIMIT 12');
    $lines = ['<b>Recent users</b>'];
    if ($res) while ($r = $res->fetch_assoc()) {
        $balance = (string)$r['role'] === 'owner' ? '∞' : (string)(int)$r['balance'];
        $lines[] = htmlspecialchars((string)$r['username'], ENT_QUOTES, 'UTF-8') . ' · ' . $r['role'] . ' · balance ' . $balance . ((int)$r['status'] === 1 ? '' : ' · DISABLED');
    }
    sdk_feature_telegram_send($chatId, implode("\n", $lines));
}

function sdk_feature_telegram_owner_tgusers(mysqli $conn, string $chatId): void
{
    $res = $conn->query('SELECT chat_id,username,first_name,linked_user_id,last_seen_at FROM telegram_users ORDER BY last_seen_at DESC LIMIT 12');
    $lines = ['<b>Telegram users</b>'];
    if ($res) while ($r = $res->fetch_assoc()) {
        $label = $r['username'] !== '' ? '@' . $r['username'] : ($r['first_name'] !== '' ? $r['first_name'] : $r['chat_id']);
        $lines[] = htmlspecialchars((string)$label, ENT_QUOTES, 'UTF-8') . ' · ' . ($r['linked_user_id'] ? 'linked' : 'guest') . ' · ' . sdk_feature_display_time((string)$r['last_seen_at']);
    }
    sdk_feature_telegram_send($chatId, implode("\n", $lines));
}

function sdk_feature_telegram_owner_keys(mysqli $conn, string $chatId): void
{
    $res = $conn->query('SELECT license_key,client_name,expiry_date,status FROM licenses ORDER BY id DESC LIMIT 10');
    $lines = ['<b>Recent licenses</b>'];
    if ($res) while ($r = $res->fetch_assoc()) {
        $lines[] = '<code>' . htmlspecialchars((string)$r['license_key'], ENT_QUOTES, 'UTF-8') . '</code> · ' . htmlspecialchars((string)$r['client_name'], ENT_QUOTES, 'UTF-8') . ' · ' . sdk_feature_display_time((string)$r['expiry_date']);
    }
    sdk_feature_telegram_send($chatId, implode("\n", $lines));
}

function sdk_feature_telegram_owner_referrals(mysqli $conn, string $chatId): void
{
    $res = $conn->query('SELECT code,assigned_to,use_count,max_uses,expires_at FROM referral_codes ORDER BY id DESC LIMIT 10');
    $lines = ['<b>Recent referrals</b>'];
    if ($res) while ($r = $res->fetch_assoc()) {
        $lines[] = '<code>' . htmlspecialchars((string)$r['code'], ENT_QUOTES, 'UTF-8') . '</code> · ' . $r['assigned_to'] . ' · ' . (int)$r['use_count'] . '/' . (int)$r['max_uses'];
    }
    sdk_feature_telegram_send($chatId, implode("\n", $lines));
}

function sdk_feature_owner_user_by_name(mysqli $conn, string $username): ?array
{
    if (preg_match('/^[A-Za-z0-9_.-]{3,64}$/D', $username) !== 1) return null;
    $stmt = $conn->prepare('SELECT id,username,role,balance,status FROM users WHERE username=? LIMIT 1');
    $stmt->bind_param('s', $username);
    $stmt->execute();
    $row = $stmt->get_result()->fetch_assoc() ?: null;
    $stmt->close();
    return $row;
}

function sdk_feature_telegram_owner_balance(mysqli $conn, string $chatId, string $username, string $amount): void
{
    if (!preg_match('/^-?\d{1,9}$/D', $amount) || (int)$amount === 0) {
        sdk_feature_telegram_send($chatId, 'Usage: /balance username +/-amount (non-zero)'); return;
    }
    $user = sdk_feature_owner_user_by_name($conn, $username);
    if (!$user || (string)$user['role'] === 'owner') {
        sdk_feature_telegram_send($chatId, 'User not found or Owner account is protected.'); return;
    }
    $delta = (int)$amount;
    try {
        $next = sdk_feature_adjust_balance($conn, (int)$user['id'], $delta, null, 'Telegram owner adjustment');
        sdk_feature_telegram_send($chatId, '✅ ' . htmlspecialchars($username, ENT_QUOTES, 'UTF-8') . ' balance: ' . $next);
    } catch (Throwable $e) {
        sdk_feature_telegram_send($chatId, '❌ ' . htmlspecialchars($e->getMessage(), ENT_QUOTES, 'UTF-8'));
    }
}

function sdk_feature_telegram_owner_status(mysqli $conn, string $chatId, string $username, bool $enable): void
{
    $user = sdk_feature_owner_user_by_name($conn, $username);
    if (!$user || (string)$user['role'] === 'owner') { sdk_feature_telegram_send($chatId, 'User not found or protected.'); return; }
    $status = $enable ? 1 : 0;
    $uid = (int)$user['id'];
    $stmt = $conn->prepare('UPDATE users SET status=?,auth_version=auth_version+1 WHERE id=?');
    $stmt->bind_param('ii', $status, $uid);
    $stmt->execute(); $stmt->close();
    sdk_feature_audit($conn, 'telegram_owner_user_status', 'success', null, $uid, ['status' => $status]);
    sdk_feature_telegram_send($chatId, $enable ? '✅ User enabled.' : '⛔ User disabled.');
}

function sdk_feature_telegram_owner_announce(mysqli $conn, string $chatId, string $message): void
{
    $message = trim($message);
    if ($message === '' || mb_strlen($message) > 1000) { sdk_feature_telegram_send($chatId, 'Usage: /announce your message (max 1000 chars)'); return; }
    if (!sdk_feature_save_setting($conn, 'site_announcement', $message, null)) {
        sdk_feature_telegram_send($chatId, '❌ Could not update the panel announcement.');
        return;
    }
    sdk_feature_telegram_send($chatId, '✅ Panel announcement updated.');
}
