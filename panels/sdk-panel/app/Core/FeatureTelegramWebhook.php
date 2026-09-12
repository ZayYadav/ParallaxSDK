<?php
declare(strict_types=1);

function sdk_feature_telegram_request(string $method, array $payload): bool
{
    $token = trim((string)(function_exists('panel_config') ? panel_config('TELEGRAM_BOT_TOKEN', '') : ''));
    if ($token === '') {
        return false;
    }
    $url = 'https://api.telegram.org/bot' . rawurlencode($token) . '/' . $method;
    $body = http_build_query($payload, '', '&', PHP_QUERY_RFC3986);
    $ctx = stream_context_create([
        'http' => [
            'method' => 'POST',
            'header' => "Content-Type: application/x-www-form-urlencoded\r\nConnection: close\r\n",
            'content' => $body,
            'timeout' => 8,
            'ignore_errors' => true,
        ],
    ]);
    $result = @file_get_contents($url, false, $ctx);
    if ($result === false) {
        return false;
    }
    $decoded = json_decode($result, true);
    return is_array($decoded) && !empty($decoded['ok']);
}

function sdk_feature_telegram_send(string $chatId, string $text, ?array $keyboard = null): bool
{
    $payload = ['chat_id' => $chatId, 'text' => $text, 'parse_mode' => 'HTML', 'disable_web_page_preview' => 'true'];
    if ($keyboard !== null) {
        $payload['reply_markup'] = json_encode(['inline_keyboard' => $keyboard], JSON_UNESCAPED_SLASHES);
    }
    return sdk_feature_telegram_request('sendMessage', $payload);
}

function sdk_feature_admin_chat_ids(mysqli $conn): array
{
    $ids = [];
    $res = $conn->query("SELECT setting_value FROM server_settings WHERE setting_key='admin_chat_ids' LIMIT 1");
    if ($res && ($row = $res->fetch_assoc())) {
        foreach (explode(',', (string)$row['setting_value']) as $id) {
            $id = trim($id);
            if ($id !== '') {
                $ids[] = $id;
            }
        }
    }
    $fallback = trim((string)(function_exists('panel_config') ? panel_config('TELEGRAM_DEFAULT_ADMIN_CHAT_ID', '') : ''));
    if ($fallback !== '') {
        $ids[] = $fallback;
    }
    return array_values(array_unique($ids));
}

function sdk_feature_upsert_telegram_user(mysqli $conn, array $chat, array $from): int
{
    $chatId = (string)($chat['id'] ?? '');
    if (!preg_match('/^-?\d{1,20}$/D', $chatId)) {
        return 0;
    }
    $first = mb_substr(trim((string)($from['first_name'] ?? '')), 0, 100);
    $last = mb_substr(trim((string)($from['last_name'] ?? '')), 0, 100);
    $username = mb_substr(trim((string)($from['username'] ?? '')), 0, 64);
    $lang = mb_substr(trim((string)($from['language_code'] ?? '')), 0, 16);
    $stmt = $conn->prepare(
        'INSERT INTO telegram_users(chat_id,first_name,last_name,username,language_code,first_seen_at,last_seen_at) '
        . 'VALUES(?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP()) '
        . 'ON DUPLICATE KEY UPDATE first_name=VALUES(first_name),last_name=VALUES(last_name),username=VALUES(username),language_code=VALUES(language_code),last_seen_at=UTC_TIMESTAMP()'
    );
    $stmt->bind_param('sssss', $chatId, $first, $last, $username, $lang);
    $stmt->execute();
    $stmt->close();
    $q = $conn->prepare('SELECT id FROM telegram_users WHERE chat_id=? LIMIT 1');
    $q->bind_param('s', $chatId);
    $q->execute();
    $row = $q->get_result()->fetch_assoc();
    $q->close();
    return (int)($row['id'] ?? 0);
}

function sdk_feature_handle_telegram_webhook(mysqli $conn): bool
{
    $secret = trim((string)(function_exists('panel_config') ? panel_config('TELEGRAM_WEBHOOK_SECRET', '') : ''));
    $provided = (string)($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');
    if ($secret === '' || $provided === '' || !hash_equals($secret, $provided)) {
        return false; // Legacy bot will return the canonical 403/503 response.
    }
    $raw = file_get_contents('php://input');
    $update = json_decode((string)$raw, true);
    if (!is_array($update)) {
        return false;
    }
    $message = $update['message'] ?? null;
    $callback = $update['callback_query'] ?? null;
    if (!is_array($message) && !is_array($callback)) {
        return false;
    }

    $updateId = (string)($update['update_id'] ?? '');
    if ($updateId !== '' && preg_match('/^\d{1,20}$/D', $updateId)) {
        $check = $conn->prepare('SELECT update_id FROM telegram_update_replays WHERE update_id=? LIMIT 1');
        $check->bind_param('s', $updateId);
        $check->execute();
        $seen = (bool)$check->get_result()->fetch_assoc();
        $check->close();
        if ($seen) {
            http_response_code(200);
            return true;
        }
    }

    $chat = is_array($message) ? ($message['chat'] ?? []) : ($callback['message']['chat'] ?? []);
    $from = is_array($message) ? ($message['from'] ?? $chat) : ($callback['from'] ?? []);
    $chatId = (string)($chat['id'] ?? '');
    if ($chatId === '' || !preg_match('/^-?\d{1,20}$/D', $chatId)) {
        return false;
    }
    $tgUserId = sdk_feature_upsert_telegram_user($conn, is_array($chat) ? $chat : [], is_array($from) ? $from : []);
    $text = is_array($message) ? trim((string)($message['text'] ?? '')) : '';
    $data = is_array($callback) ? trim((string)($callback['data'] ?? '')) : '';
    $cmdParts = preg_split('/\s+/', $text, 3) ?: [];
    $cmd = strtolower((string)($cmdParts[0] ?? ''));
    $arg1 = (string)($cmdParts[1] ?? '');
    $arg2 = (string)($cmdParts[2] ?? '');
    $admin = in_array($chatId, sdk_feature_admin_chat_ids($conn), true);
    if (!$admin && function_exists('panel_rate_limit') && !panel_rate_limit($conn, 'telegram-feature|' . $chatId, 30)) {
        sdk_feature_telegram_send($chatId, 'Too many requests. Try again in a minute.');
        if ($updateId !== '') {
            $mark = $conn->prepare('INSERT IGNORE INTO telegram_update_replays(update_id) VALUES(?)');
            $mark->bind_param('s', $updateId); $mark->execute(); $mark->close();
        }
        http_response_code(200);
        return true;
    }
    $handled = false;

    if ($data !== '' && str_starts_with($data, 'sdk:') && $admin) {
        $handled = true;
        switch ($data) {
            case 'sdk:stats': sdk_feature_telegram_owner_stats($conn, $chatId); break;
            case 'sdk:users': sdk_feature_telegram_owner_users($conn, $chatId); break;
            case 'sdk:tgusers': sdk_feature_telegram_owner_tgusers($conn, $chatId); break;
            case 'sdk:keys': sdk_feature_telegram_owner_keys($conn, $chatId); break;
            case 'sdk:refs': sdk_feature_telegram_owner_referrals($conn, $chatId); break;
        }
        if (!empty($callback['id'])) {
            sdk_feature_telegram_request('answerCallbackQuery', ['callback_query_id' => (string)$callback['id']]);
        }
    }

    if (!$handled && in_array($cmd, ['/start', '/help'], true)) {
        $handled = true;
        if ($admin) {
            sdk_feature_telegram_send($chatId, '<b>Parallax SDK Owner Console</b>\nChoose a read-only view below. Existing license-management commands remain available.', [
                [['text' => '📊 Stats', 'callback_data' => 'sdk:stats'], ['text' => '👥 Users', 'callback_data' => 'sdk:users']],
                [['text' => '📱 TG Users', 'callback_data' => 'sdk:tgusers'], ['text' => '🔑 Keys', 'callback_data' => 'sdk:keys']],
                [['text' => '🎟 Referrals', 'callback_data' => 'sdk:refs']],
            ]);
        } else {
            sdk_feature_telegram_send($chatId, '<b>Parallax SDK Bot</b>\n/link CODE — link your panel account\n/2fa — create a Telegram 2FA activation key\n/account — show linked account\n/free — guest 2-hour key (eligible guests)\n/key 1|7|30 — linked-user key generation when enabled');
        }
    }

    if (!$handled && $cmd === '/link') {
        $handled = true;
        sdk_feature_telegram_link($conn, $chatId, $tgUserId, $arg1);
    }
    if (!$handled && $cmd === '/2fa') {
        $handled = true;
        sdk_feature_telegram_2fa_token($conn, $chatId, $tgUserId);
    }
    if (!$handled && $cmd === '/account') {
        $handled = true;
        sdk_feature_telegram_account($conn, $chatId, $tgUserId);
    }
    if (!$handled && $cmd === '/free') {
        $handled = true;
        sdk_feature_telegram_free_key($conn, $chatId, $tgUserId);
    }
    if (!$handled && $cmd === '/key') {
        $handled = true;
        sdk_feature_telegram_linked_key($conn, $chatId, $tgUserId, $arg1);
    }

    if (!$handled && $admin && $cmd === '/stats') {
        $handled = true; sdk_feature_telegram_owner_stats($conn, $chatId);
    }
    if (!$handled && $admin && $cmd === '/users') {
        $handled = true; sdk_feature_telegram_owner_users($conn, $chatId);
    }
    if (!$handled && $admin && $cmd === '/tgusers') {
        $handled = true; sdk_feature_telegram_owner_tgusers($conn, $chatId);
    }
    if (!$handled && $admin && $cmd === '/balance') {
        $handled = true; sdk_feature_telegram_owner_balance($conn, $chatId, $arg1, $arg2);
    }
    if (!$handled && $admin && in_array($cmd, ['/userdisable', '/userenable'], true)) {
        $handled = true; sdk_feature_telegram_owner_status($conn, $chatId, $arg1, $cmd === '/userenable');
    }
    if (!$handled && $admin && $cmd === '/announce') {
        $handled = true;
        $messageText = trim(substr($text, strlen('/announce')));
        sdk_feature_telegram_owner_announce($conn, $chatId, $messageText);
    }

    if ($updateId !== '') {
        // Record every valid update before falling through to the legacy admin
        // command switch. The first delivery can still be handled there, while
        // Telegram retries/replays are stopped here on subsequent requests.
        $mark = $conn->prepare('INSERT IGNORE INTO telegram_update_replays(update_id) VALUES(?)');
        $mark->bind_param('s', $updateId);
        $mark->execute();
        $mark->close();
    }
    if ($handled) {
        http_response_code(200);
        return true;
    }
    return false;
}
