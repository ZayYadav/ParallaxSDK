<?php
declare(strict_types=1);

function sdk_feature_login_intercept(mysqli $conn): void
{
    if (isset($_POST['verify_telegram_2fa'])) {
        sdk_feature_verify_telegram_login($conn);
    }
    if (!isset($_POST['login'])) {
        return;
    }
    $username = trim((string)($_POST['username'] ?? ''));
    $password = (string)($_POST['password'] ?? '');
    if (preg_match('/^[A-Za-z0-9_.-]{3,64}$/D', $username) !== 1 || $password === '') {
        return;
    }
    $stmt = $conn->prepare('SELECT id,username,password,status,mfa_enabled,telegram_chat_id,telegram_2fa_enabled,auth_version,login_not_before FROM users WHERE username=? LIMIT 1');
    if (!$stmt) return;
    $stmt->bind_param('s', $username); $stmt->execute(); $user = $stmt->get_result()->fetch_assoc(); $stmt->close();
    if (!$user || (int)$user['status'] !== 1 || !password_verify($password, (string)$user['password'])) {
        return;
    }
    if (!empty($user['login_not_before']) && strtotime((string)$user['login_not_before'] . ' UTC') > time()) {
        sdk_feature_render_login_challenge('Your new account is still completing its 15-second registration review. Try again when the countdown finishes.', false);
    }
    // Preserve the SDK panel's existing TOTP/recovery-code contract. If TOTP
    // is enabled, the original login.php flow remains authoritative and
    // Telegram 2FA never replaces or bypasses it.
    if ((int)($user['mfa_enabled'] ?? 0) === 1) {
        return;
    }
    if ((int)$user['telegram_2fa_enabled'] !== 1) {
        return;
    }
    $chatId = trim((string)($user['telegram_chat_id'] ?? ''));
    if ($chatId === '') {
        return;
    }
    if (function_exists('panel_rate_limit') && !panel_rate_limit($conn, 'tg2fa-login|' . sdk_feature_ip() . '|' . (int)$user['id'], 5)) {
        sdk_feature_render_login_challenge('Too many login challenges. Wait one minute and try again.', false);
    }
    $uid = (int)$user['id'];
    $conn->query('DELETE FROM telegram_login_challenges WHERE expires_at<=UTC_TIMESTAMP() OR consumed_at IS NOT NULL');
    $invalidate = $conn->prepare('UPDATE telegram_login_challenges SET consumed_at=UTC_TIMESTAMP() WHERE user_id=? AND consumed_at IS NULL');
    $invalidate->bind_param('i', $uid); $invalidate->execute(); $invalidate->close();
    $code = (string)random_int(10000000, 99999999);
    $key = hash('sha256', (string)(function_exists('panel_config') ? panel_config('PANEL_DATA_KEY', 'sdk') : 'sdk'), true);
    $hash = hash_hmac('sha256', $code, $key);
    $ins = $conn->prepare('INSERT INTO telegram_login_challenges(user_id,code_hash,expires_at) VALUES(?,?,UTC_TIMESTAMP()+INTERVAL 5 MINUTE)');
    $ins->bind_param('is', $uid, $hash); $ins->execute(); $challengeId = (int)$conn->insert_id; $ins->close();
    $_SESSION['sdk_pending_telegram_2fa'] = ['user_id' => $uid, 'challenge_id' => $challengeId, 'expires' => time() + 300, 'auth_version' => (int)$user['auth_version']];
    sdk_feature_telegram_send($chatId, '<b>Parallax SDK login code</b>\n<code>' . $code . '</code>\nExpires in 5 minutes. Do not share this code.');
    sdk_feature_audit($conn, 'telegram_2fa_challenge', 'created', $uid, $uid);
    sdk_feature_render_login_challenge('An 8-digit code was sent to your linked Telegram account.', true);
}

function sdk_feature_render_login_challenge(string $message, bool $showForm): never
{
    $safe = htmlspecialchars($message, ENT_QUOTES, 'UTF-8');
    $csrf = function_exists('panel_csrf_input') ? panel_csrf_input() : '';
    echo '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Telegram verification</title><style>'
        . 'body{margin:0;min-height:100svh;display:grid;place-items:center;background:#050810;color:#fff;font-family:Inter,system-ui;padding:20px}.c{width:min(92vw,430px);padding:30px;border-radius:24px;background:#0f172a;border:1px solid rgba(255,255,255,.12);box-shadow:0 30px 80px rgba(0,0,0,.45)}input,button{width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid rgba(255,255,255,.14);font:inherit}input{background:#070c18;color:#fff;font-size:1.2rem;letter-spacing:.25em;text-align:center}button{margin-top:12px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);font-weight:900;color:#07111f;cursor:pointer}p{color:#aab4c7;line-height:1.55}a{color:#93c5fd}</style></head><body><div class="c"><h2>Telegram verification</h2><p>' . $safe . '</p>';
    if ($showForm) {
        echo '<form method="post">' . $csrf . '<input name="telegram_code" inputmode="numeric" pattern="[0-9]{8}" maxlength="8" placeholder="00000000" required><button name="verify_telegram_2fa" value="1">Verify & sign in</button></form>';
    }
    echo '<p><a href="login.php">Cancel and return to login</a></p></div></body></html>';
    exit;
}

function sdk_feature_verify_telegram_login(mysqli $conn): never
{
    $pending = $_SESSION['sdk_pending_telegram_2fa'] ?? null;
    $code = trim((string)($_POST['telegram_code'] ?? ''));
    if (!is_array($pending) || (int)($pending['expires'] ?? 0) < time() || preg_match('/^\d{8}$/D', $code) !== 1) {
        unset($_SESSION['sdk_pending_telegram_2fa']);
        sdk_feature_render_login_challenge('The challenge is invalid or expired. Sign in again.', false);
    }
    $uid = (int)$pending['user_id']; $challengeId = (int)$pending['challenge_id'];
    $stmt = $conn->prepare('SELECT id,username,status,auth_version,telegram_2fa_enabled FROM users WHERE id=? LIMIT 1');
    $stmt->bind_param('i', $uid); $stmt->execute(); $user = $stmt->get_result()->fetch_assoc(); $stmt->close();
    if (!$user || (int)$user['status'] !== 1 || (int)$user['telegram_2fa_enabled'] !== 1 || (int)$user['auth_version'] !== (int)$pending['auth_version']) {
        unset($_SESSION['sdk_pending_telegram_2fa']);
        sdk_feature_render_login_challenge('Account security changed. Sign in again.', false);
    }
    $q = $conn->prepare('SELECT code_hash,attempts,expires_at,consumed_at FROM telegram_login_challenges WHERE id=? AND user_id=? LIMIT 1');
    $q->bind_param('ii', $challengeId, $uid); $q->execute(); $row = $q->get_result()->fetch_assoc(); $q->close();
    if (!$row || $row['consumed_at'] !== null || strtotime((string)$row['expires_at'] . ' UTC') < time() || (int)$row['attempts'] >= 5) {
        unset($_SESSION['sdk_pending_telegram_2fa']);
        sdk_feature_render_login_challenge('The challenge is no longer valid. Sign in again.', false);
    }
    $key = hash('sha256', (string)(function_exists('panel_config') ? panel_config('PANEL_DATA_KEY', 'sdk') : 'sdk'), true);
    $expected = hash_hmac('sha256', $code, $key);
    if (!hash_equals((string)$row['code_hash'], $expected)) {
        $inc = $conn->prepare('UPDATE telegram_login_challenges SET attempts=attempts+1 WHERE id=?');
        $inc->bind_param('i', $challengeId); $inc->execute(); $inc->close();
        sdk_feature_audit($conn, 'telegram_2fa_verify', 'failed', $uid, $uid);
        sdk_feature_render_login_challenge('Invalid code. Return to login and request a new challenge.', false);
    }
    $consume = $conn->prepare('UPDATE telegram_login_challenges SET consumed_at=UTC_TIMESTAMP() WHERE id=? AND consumed_at IS NULL');
    $consume->bind_param('i', $challengeId); $consume->execute(); $ok = $consume->affected_rows === 1; $consume->close();
    if (!$ok) {
        sdk_feature_render_login_challenge('The code was already used. Sign in again.', false);
    }
    $ip = sdk_feature_ip();
    $online = $conn->prepare('UPDATE users SET is_online=1,last_login_at=UTC_TIMESTAMP(),last_login_ip=? WHERE id=?');
    $online->bind_param('si', $ip, $uid); $online->execute(); $online->close();
    session_regenerate_id(true);
    unset($_SESSION['sdk_pending_telegram_2fa']);
    $_SESSION['user_id'] = $uid;
    $_SESSION['username'] = (string)$user['username'];
    $_SESSION['logged_in'] = true;
    $_SESSION['auth_v3'] = true;
    $_SESSION['sdk_auth_version'] = (int)$user['auth_version'];
    $_SESSION['mfa_verified_at'] = time();
    $_SESSION['last_activity'] = time();
    $_SESSION['session_started_at'] = time();
    $_SESSION['last_regenerated_at'] = time();
    $_SESSION['agent_hash'] = hash('sha256', (string)($_SERVER['HTTP_USER_AGENT'] ?? 'unknown'));
    if (function_exists('panel_audit')) {
        panel_audit($conn, 'panel_login', 'success', '', ['user_id' => $uid, 'mfa' => 'telegram']);
    }
    sdk_feature_audit($conn, 'telegram_2fa_verify', 'success', $uid, $uid);
    header('Location: dashboard.php');
    exit;
}

function sdk_feature_process_broadcast_batch(mysqli $conn, int $broadcastId, int $limit = 25): array
{
    $limit = max(1, min(100, $limit));
    $sent = 0; $failed = 0;
    $b = $conn->prepare('SELECT message FROM announcement_broadcasts WHERE id=? LIMIT 1');
    $b->bind_param('i', $broadcastId); $b->execute(); $broadcast = $b->get_result()->fetch_assoc(); $b->close();
    if (!$broadcast) return ['sent' => 0, 'failed' => 0, 'remaining' => 0];
    $conn->query("UPDATE announcement_broadcasts SET status='sending' WHERE id=" . (int)$broadcastId . " AND status='queued'");
    $res = $conn->query(
        'SELECT id,chat_id FROM announcement_recipients WHERE broadcast_id=' . (int)$broadcastId
        . " AND status IN ('queued','failed') AND attempts<3 ORDER BY id ASC LIMIT " . $limit
    );
    if ($res) while ($r = $res->fetch_assoc()) {
        $rid = (int)$r['id']; $chat = (string)$r['chat_id'];
        $mark = $conn->prepare("UPDATE announcement_recipients SET status='sending',attempts=attempts+1 WHERE id=?");
        $mark->bind_param('i', $rid); $mark->execute(); $mark->close();
        $ok = sdk_feature_telegram_send($chat, '<b>Official announcement</b>\n' . htmlspecialchars((string)$broadcast['message'], ENT_QUOTES, 'UTF-8'));
        if ($ok) {
            $done = $conn->prepare("UPDATE announcement_recipients SET status='sent',sent_at=UTC_TIMESTAMP(),last_error='' WHERE id=?");
            $done->bind_param('i', $rid); $done->execute(); $done->close(); $sent++;
        } else {
            $err = 'Telegram delivery failed';
            $done = $conn->prepare("UPDATE announcement_recipients SET status='failed',last_error=? WHERE id=?");
            $done->bind_param('si', $err, $rid); $done->execute(); $done->close(); $failed++;
        }
    }
    $counts = $conn->query(
        'SELECT SUM(status=\'sent\') sent,SUM(status=\'failed\') failed,SUM(status IN (\'queued\',\'sending\')) remaining FROM announcement_recipients WHERE broadcast_id=' . (int)$broadcastId
    )->fetch_assoc();
    $totalSent = (int)($counts['sent'] ?? 0); $totalFailed = (int)($counts['failed'] ?? 0); $remaining = (int)($counts['remaining'] ?? 0);
    $status = $remaining === 0 ? 'complete' : 'sending';
    $stmt = $conn->prepare("UPDATE announcement_broadcasts SET status=?,sent_count=?,failed_count=?,completed_at=CASE WHEN ?='complete' THEN UTC_TIMESTAMP() ELSE completed_at END WHERE id=?");
    $stmt->bind_param('siisi', $status, $totalSent, $totalFailed, $status, $broadcastId);
    $stmt->execute(); $stmt->close();
    return ['sent' => $sent, 'failed' => $failed, 'remaining' => $remaining];
}

function sdk_feature_queue_broadcast(mysqli $conn, int $actorId, string $message, bool $panel, bool $linked, bool $guest): int
{
    $message = trim($message);
    if ($message === '' || mb_strlen($message) > 1000) throw new RuntimeException('Announcement must be 1-1000 characters.');
    if (!$panel && !$linked && !$guest) throw new RuntimeException('Choose at least one audience.');
    $conn->begin_transaction();
    try {
        $announcementId = null;
        if ($panel) {
            $title = 'Official Announcement'; $type = 'info'; $active = 1;
            $a = $conn->prepare('INSERT INTO announcements(title,message,type,is_active,created_by) VALUES(?,?,?,?,?)');
            $a->bind_param('sssii', $title, $message, $type, $active, $actorId); $a->execute(); $announcementId = (int)$conn->insert_id; $a->close();
            sdk_feature_save_setting($conn, 'site_announcement', $message, $actorId);
        }
        $pi = $panel ? 1 : 0; $li = $linked ? 1 : 0; $gi = $guest ? 1 : 0;
        $b = $conn->prepare('INSERT INTO announcement_broadcasts(announcement_id,message,target_panel_users,target_linked_telegram,target_guest_telegram,created_by) VALUES(?,?,?,?,?,?)');
        $b->bind_param('isiiii', $announcementId, $message, $pi, $li, $gi, $actorId); $b->execute(); $broadcastId = (int)$conn->insert_id; $b->close();
        if ($linked || $guest) {
            $where = [];
            if ($linked) $where[] = 'linked_user_id IS NOT NULL';
            if ($guest) $where[] = 'linked_user_id IS NULL';
            $sql = 'SELECT id,chat_id FROM telegram_users WHERE ' . implode(' OR ', $where);
            $res = $conn->query($sql);
            $r = $conn->prepare('INSERT IGNORE INTO announcement_recipients(broadcast_id,telegram_user_id,chat_id) VALUES(?,?,?)');
            while ($res && ($row = $res->fetch_assoc())) {
                $tgId = (int)$row['id']; $chat = (string)$row['chat_id'];
                $r->bind_param('iis', $broadcastId, $tgId, $chat); $r->execute();
            }
            $r->close();
        }
        $conn->commit();
        sdk_feature_audit($conn, 'announcement_publish', 'success', $actorId, null, ['broadcast_id' => $broadcastId, 'panel' => $panel, 'linked' => $linked, 'guest' => $guest]);
        return $broadcastId;
    } catch (Throwable $e) {
        $conn->rollback(); throw $e;
    }
}
