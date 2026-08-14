<?php

declare(strict_types=1);

namespace ParallaxPanel;

use PDO;
use RuntimeException;
use Throwable;

final class TelegramBot
{
    public function __construct(private PDO $db)
    {
    }

    public function handleWebhook(): never
    {
        header('Content-Type: application/json; charset=UTF-8');
        if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
            http_response_code(405);
            exit('{"ok":false}');
        }
        $configuredSecret = Env::get('TELEGRAM_WEBHOOK_SECRET');
        $receivedSecret = (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');
        if (strlen($configuredSecret) < 32 || !hash_equals($configuredSecret, $receivedSecret)) {
            http_response_code(403);
            exit('{"ok":false}');
        }
        $raw = file_get_contents('php://input', false, null, 0, 1048577);
        if (!is_string($raw) || $raw === '' || strlen($raw) > 1048576) {
            http_response_code(400);
            exit('{"ok":false}');
        }
        $update = json_decode($raw, true, 32);
        if (!is_array($update) || !isset($update['update_id'])) {
            http_response_code(400);
            exit('{"ok":false}');
        }
        try {
            $statement = $this->db->prepare('INSERT INTO telegram_updates (update_id) VALUES (?)');
            $statement->execute([(int) $update['update_id']]);
        } catch (Throwable) {
            exit('{"ok":true}');
        }
        if (random_int(1, 100) === 1) {
            $this->db->exec("DELETE FROM telegram_updates WHERE processed_at < UTC_TIMESTAMP() - INTERVAL 7 DAY");
        }
        try {
            if (isset($update['callback_query']) && is_array($update['callback_query'])) {
                $this->handleCallback($update['callback_query']);
            } elseif (isset($update['message']) && is_array($update['message'])) {
                $this->handleMessage($update['message']);
            }
        } catch (Throwable $error) {
            error_log('Telegram bot: ' . $error->getMessage());
        }
        exit('{"ok":true}');
    }

    /** @param array<string,mixed> $message */
    private function handleMessage(array $message): void
    {
        $chat = is_array($message['chat'] ?? null) ? $message['chat'] : [];
        $from = is_array($message['from'] ?? null) ? $message['from'] : [];
        $telegramId = (string) ($from['id'] ?? '');
        $chatId = (string) ($chat['id'] ?? '');
        if (($chat['type'] ?? '') !== 'private' || $telegramId === '' || $chatId !== $telegramId) {
            return;
        }
        $user = $this->authorizedUser($telegramId);
        if (!$user) {
            $this->sendMessage($chatId, 'Access denied. Link this Telegram user ID to an active owner/admin account and add it to TELEGRAM_ALLOWED_USER_IDS.');
            return;
        }
        $this->sendMenu($chatId, $user);
    }

    /** @param array<string,mixed> $callback */
    private function handleCallback(array $callback): void
    {
        $from = is_array($callback['from'] ?? null) ? $callback['from'] : [];
        $message = is_array($callback['message'] ?? null) ? $callback['message'] : [];
        $chat = is_array($message['chat'] ?? null) ? $message['chat'] : [];
        $telegramId = (string) ($from['id'] ?? '');
        $chatId = (string) ($chat['id'] ?? '');
        $callbackId = (string) ($callback['id'] ?? '');
        if (($chat['type'] ?? '') !== 'private' || $telegramId === '' || $chatId !== $telegramId) {
            $this->answerCallback($callbackId, 'Private chat required', true);
            return;
        }
        $user = $this->authorizedUser($telegramId);
        if (!$user) {
            $this->answerCallback($callbackId, 'Access denied', true);
            return;
        }
        $data = (string) ($callback['data'] ?? 'menu');
        $messageId = (int) ($message['message_id'] ?? 0);
        $this->answerCallback($callbackId, 'Updated');
        if ($data === 'menu') {
            $this->editMenu($chatId, $messageId, $user);
        } elseif ($data === 'stats') {
            $this->edit($chatId, $messageId, $this->statsText(), $this->menuKeyboard());
        } elseif ($data === 'keys') {
            $this->edit($chatId, $messageId, $this->recentKeysText(), $this->recentKeysKeyboard());
        } elseif (preg_match('/^key:([1-9][0-9]*)$/D', $data, $match) === 1) {
            $this->showLegacyKey($chatId, $messageId, (int) $match[1]);
        } elseif (preg_match('/^keyact:([1-9][0-9]*):(reset|toggle)$/D', $data, $match) === 1) {
            $id = (int) $match[1];
            if ($match[2] === 'reset') {
                $this->db->prepare('UPDATE keys_code SET devices=NULL WHERE id_keys=?')->execute([$id]);
            } else {
                $this->db->prepare('UPDATE keys_code SET status=IF(status=1,0,1) WHERE id_keys=?')->execute([$id]);
            }
            Security::audit($this->db, (int) $user['id'], 'telegram_legacy_key_' . $match[2], (string) $id);
            $this->showLegacyKey($chatId, $messageId, $id);
        } elseif (preg_match('/^keydelete:([1-9][0-9]*):ask$/D', $data, $match) === 1) {
            $id = (int) $match[1];
            $this->edit($chatId, $messageId, "Delete legacy key #$id permanently?", [
                'inline_keyboard' => [
                    [['text' => 'Confirm delete', 'callback_data' => 'keydelete:' . $id . ':yes']],
                    [['text' => 'Cancel', 'callback_data' => 'key:' . $id]],
                ],
            ]);
        } elseif (preg_match('/^keydelete:([1-9][0-9]*):yes$/D', $data, $match) === 1) {
            $id = (int) $match[1];
            $this->db->prepare('DELETE FROM keys_code WHERE id_keys=?')->execute([$id]);
            Security::audit($this->db, (int) $user['id'], 'telegram_legacy_key_deleted', (string) $id);
            $this->edit($chatId, $messageId, "Legacy key #$id deleted.", $this->recentKeysKeyboard());
        } elseif ($data === 'onecore') {
            $this->edit($chatId, $messageId, 'Recent OneCore keys', $this->oneCoreKeyboard());
        } elseif (preg_match('/^oc:([1-9][0-9]*)$/D', $data, $match) === 1) {
            $this->showOneCoreKey($chatId, $messageId, (int) $match[1]);
        } elseif (preg_match('/^ocrevoke:([1-9][0-9]*):ask$/D', $data, $match) === 1) {
            $id = (int) $match[1];
            $this->edit($chatId, $messageId, "Revoke OneCore key #$id and its devices?", [
                'inline_keyboard' => [
                    [['text' => 'Confirm revoke', 'callback_data' => 'ocrevoke:' . $id . ':yes']],
                    [['text' => 'Cancel', 'callback_data' => 'oc:' . $id]],
                ],
            ]);
        } elseif (preg_match('/^ocrevoke:([1-9][0-9]*):yes$/D', $data, $match) === 1) {
            $id = (int) $match[1];
            $this->db->beginTransaction();
            try {
                $this->db->prepare("UPDATE license_keys SET status='revoked' WHERE id=?")->execute([$id]);
                $this->db->prepare("UPDATE devices d JOIN device_license_bindings b ON b.device_id=d.device_id SET d.status='revoked' WHERE b.license_key_id=?")
                    ->execute([$id]);
                Security::audit($this->db, (int) $user['id'], 'telegram_onecore_key_revoked', (string) $id);
                $this->db->commit();
            } catch (Throwable $error) {
                if ($this->db->inTransaction()) {
                    $this->db->rollBack();
                }
                throw $error;
            }
            $this->showOneCoreKey($chatId, $messageId, $id);
        } elseif ($data === 'users') {
            $this->edit($chatId, $messageId, $this->usersText(), $this->menuKeyboard());
        } elseif ($data === 'maintenance') {
            $current = (string) $this->db->query('SELECT status FROM onoff WHERE id=1')->fetchColumn();
            $next = $current === 'on' ? 'off' : 'on';
            $this->edit($chatId, $messageId, "Maintenance is $current. Confirm switching it $next?", [
                'inline_keyboard' => [
                    [['text' => 'Confirm ' . strtoupper($next), 'callback_data' => 'maintenance:' . $next]],
                    [['text' => 'Back', 'callback_data' => 'menu']],
                ],
            ]);
        } elseif (preg_match('/^maintenance:(on|off)$/D', $data, $match) === 1) {
            $this->db->prepare('UPDATE onoff SET status=? WHERE id=1')->execute([$match[1]]);
            Security::audit($this->db, (int) $user['id'], 'telegram_maintenance_' . $match[1]);
            $this->edit($chatId, $messageId, 'Maintenance is now ' . strtoupper($match[1]) . '.', $this->menuKeyboard());
        } elseif ($data === 'legacy:new') {
            $this->edit($chatId, $messageId, 'Choose a game:', $this->legacyGamesKeyboard());
        } elseif (preg_match('/^legacy_game:([1-9][0-9]*)$/D', $data, $match) === 1) {
            $game = GenerationOptions::findById($this->db, GenerationOptions::GAME, (int) $match[1]);
            if (!$game) {
                $this->edit($chatId, $messageId, 'That game is no longer available.', $this->menuKeyboard());
            } else {
                $this->edit(
                    $chatId,
                    $messageId,
                    'Game: ' . $game['option_label'] . "\nChoose a duration:",
                    $this->legacyDurationsKeyboard((int) $game['id'])
                );
            }
        } elseif (preg_match('/^legacy_make:([1-9][0-9]*):([1-9][0-9]*)$/D', $data, $match) === 1) {
            $game = GenerationOptions::findById($this->db, GenerationOptions::GAME, (int) $match[1]);
            $duration = GenerationOptions::findById($this->db, GenerationOptions::DURATION, (int) $match[2]);
            if (!$game || !$duration) {
                $this->edit($chatId, $messageId, 'That game or duration is no longer available.', $this->menuKeyboard());
            } else {
                $key = $this->createLegacyKey(
                    $user,
                    (string) $game['option_value'],
                    (int) $duration['option_value']
                );
                $this->edit(
                    $chatId,
                    $messageId,
                    "Legacy key created\nGame: {$game['option_label']}\nDuration: {$duration['option_label']} ({$duration['option_value']}h)\n$key",
                    $this->copyKeyboard($key)
                );
            }
        } elseif (preg_match('/^legacy:([1-9][0-9]{0,4})$/D', $data, $match) === 1) {
            // Compatibility for buttons in messages sent by older bot versions.
            $games = GenerationOptions::games($this->db);
            $hours = (string) ((int) $match[1]);
            if ($games === [] || !GenerationOptions::contains($this->db, GenerationOptions::DURATION, $hours)) {
                $this->edit($chatId, $messageId, 'That old option is no longer available.', $this->menuKeyboard());
            } else {
                $game = (string) array_key_first($games);
                $key = $this->createLegacyKey($user, $game, (int) $hours);
                $this->edit($chatId, $messageId, "Legacy key created\nGame: {$games[$game]}\nDuration: {$hours} hours\n$key", $this->copyKeyboard($key));
            }
        } elseif ($data === 'onecore:30') {
            $key = 'OC-' . implode('-', str_split(strtoupper(bin2hex(random_bytes(16))), 4));
            $statement = $this->db->prepare('INSERT INTO license_keys (key_hash,key_prefix,label,max_devices,expires_at) VALUES (?,?,?,?,?)');
            $statement->execute([hash('sha256', $key), substr($key, 0, 12), 'Telegram', 1, gmdate('Y-m-d H:i:s', time() + 2592000)]);
            Security::audit($this->db, (int) $user['id'], 'telegram_onecore_key_created', substr($key, 0, 12));
            $this->edit($chatId, $messageId, "OneCore key created\nDuration: 30 days\n$key", $this->copyKeyboard($key));
        } else {
            $this->editMenu($chatId, $messageId, $user);
        }
    }

    /** @return array<string,mixed>|null */
    private function authorizedUser(string $telegramId): ?array
    {
        if (preg_match('/^[1-9][0-9]{4,19}$/D', $telegramId) !== 1) {
            return null;
        }
        $allowed = array_values(array_filter(array_map('trim', explode(',', Env::get('TELEGRAM_ALLOWED_USER_IDS')))));
        if (!in_array($telegramId, $allowed, true)) {
            return null;
        }
        $statement = $this->db->prepare(
            "SELECT id,username,role,status FROM panel_users WHERE telegram_user_id=? AND status='active' AND role IN ('owner','admin') LIMIT 1"
        );
        $statement->execute([$telegramId]);
        $user = $statement->fetch();
        return is_array($user) ? $user : null;
    }

    /** @param array<string,mixed> $user */
    private function sendMenu(string $chatId, array $user): void
    {
        $this->sendMessage($chatId, $this->menuText($user), $this->menuKeyboard());
    }

    /** @param array<string,mixed> $user */
    private function editMenu(string $chatId, int $messageId, array $user): void
    {
        $this->edit($chatId, $messageId, $this->menuText($user), $this->menuKeyboard());
    }

    /** @param array<string,mixed> $user */
    private function menuText(array $user): string
    {
        return "Parallax Control\nSigned in: {$user['username']} ({$user['role']})\nChoose a secure action:";
    }

    /** @return array<string,mixed> */
    private function menuKeyboard(): array
    {
        return ['inline_keyboard' => [
            [['text' => 'Dashboard', 'callback_data' => 'stats'], ['text' => 'Recent keys', 'callback_data' => 'keys']],
            [['text' => 'New legacy key', 'callback_data' => 'legacy:new'], ['text' => 'OneCore 30d', 'callback_data' => 'onecore:30']],
            [['text' => 'OneCore list', 'callback_data' => 'onecore'], ['text' => 'Users', 'callback_data' => 'users']],
            [['text' => 'Maintenance', 'callback_data' => 'maintenance']],
            [['text' => 'Refresh menu', 'callback_data' => 'menu']],
        ]];
    }

    /** @return array<string,mixed> */
    private function legacyGamesKeyboard(): array
    {
        $rows = $this->db->query(
            "SELECT id,option_label FROM key_generation_options WHERE option_type='game' ORDER BY sort_order,id LIMIT 50"
        )->fetchAll();
        $buttons = [];
        foreach ($rows as $row) {
            $buttons[] = [[
                'text' => $this->buttonLabel((string) $row['option_label']),
                'callback_data' => 'legacy_game:' . $row['id'],
            ]];
        }
        $buttons[] = [['text' => 'Back', 'callback_data' => 'menu']];
        return ['inline_keyboard' => $buttons];
    }

    /** @return array<string,mixed> */
    private function legacyDurationsKeyboard(int $gameId): array
    {
        $rows = $this->db->query(
            "SELECT id,option_value,option_label FROM key_generation_options WHERE option_type='duration' ORDER BY sort_order,id LIMIT 50"
        )->fetchAll();
        $buttons = [];
        foreach ($rows as $row) {
            $buttons[] = [[
                'text' => $this->buttonLabel((string) $row['option_label'] . ' (' . $row['option_value'] . 'h)'),
                'callback_data' => 'legacy_make:' . $gameId . ':' . $row['id'],
            ]];
        }
        $buttons[] = [['text' => 'Back to games', 'callback_data' => 'legacy:new']];
        return ['inline_keyboard' => $buttons];
    }

    /** @param array<string,mixed> $user */
    private function createLegacyKey(array $user, string $game, int $duration): string
    {
        $key = 'TG-' . strtoupper(bin2hex(random_bytes(8)));
        $statement = $this->db->prepare(
            'INSERT INTO keys_code (game,user_key,duration,max_devices,registrator,admin_id) VALUES (?,?,?,?,?,?)'
        );
        $statement->execute([$game, $key, $duration, 1, $user['username'], $user['id']]);
        Security::audit($this->db, (int) $user['id'], 'telegram_legacy_key_created', $key);
        return $key;
    }

    private function buttonLabel(string $label): string
    {
        return strlen($label) <= 52 ? $label : substr($label, 0, 49) . '...';
    }

    /** @return array<string,mixed> */
    private function copyKeyboard(string $key): array
    {
        return ['inline_keyboard' => [
            [['text' => 'Copy key', 'copy_text' => ['text' => $key]]],
            [['text' => 'Back to menu', 'callback_data' => 'menu']],
        ]];
    }

    private function statsText(): string
    {
        $legacy = (int) $this->db->query('SELECT COUNT(*) FROM keys_code')->fetchColumn();
        $active = (int) $this->db->query('SELECT COUNT(*) FROM keys_code WHERE status=1 AND (expired_date IS NULL OR expired_date>UTC_TIMESTAMP())')->fetchColumn();
        $onecore = (int) $this->db->query('SELECT COUNT(*) FROM license_keys')->fetchColumn();
        $users = (int) $this->db->query("SELECT COUNT(*) FROM panel_users WHERE status='active'")->fetchColumn();
        $maintenance = strtoupper((string) $this->db->query('SELECT status FROM onoff WHERE id=1')->fetchColumn());
        return "Dashboard\nLegacy keys: $legacy\nActive legacy: $active\nOneCore keys: $onecore\nActive users: $users\nMaintenance: $maintenance";
    }

    private function recentKeysText(): string
    {
        $rows = $this->db->query('SELECT user_key,duration,status,expired_date FROM keys_code ORDER BY id_keys DESC LIMIT 8')->fetchAll();
        $lines = ['Recent legacy keys'];
        foreach ($rows as $row) {
            $state = (int) $row['status'] === 1 ? 'active' : 'blocked';
            $lines[] = $row['user_key'] . ' | ' . $row['duration'] . 'h | ' . $state;
        }
        return implode("\n", $lines);
    }

    /** @return array<string,mixed> */
    private function recentKeysKeyboard(): array
    {
        $rows = $this->db->query('SELECT id_keys,user_key,status FROM keys_code ORDER BY id_keys DESC LIMIT 8')->fetchAll();
        $buttons = [];
        foreach ($rows as $row) {
            $label = ((int) $row['status'] === 1 ? 'ON ' : 'OFF ') . substr((string) $row['user_key'], 0, 24);
            $buttons[] = [['text' => $label, 'callback_data' => 'key:' . $row['id_keys']]];
        }
        $buttons[] = [['text' => 'Back', 'callback_data' => 'menu']];
        return ['inline_keyboard' => $buttons];
    }

    private function showLegacyKey(string $chatId, int $messageId, int $id): void
    {
        $statement = $this->db->prepare('SELECT * FROM keys_code WHERE id_keys=?');
        $statement->execute([$id]);
        $key = $statement->fetch();
        if (!$key) {
            $this->edit($chatId, $messageId, 'Legacy key not found.', $this->recentKeysKeyboard());
            return;
        }
        $used = count(array_filter(array_map('trim', explode(',', (string) $key['devices']))));
        $text = "Legacy key #$id\n{$key['user_key']}\nGame: {$key['game']}\nDuration: {$key['duration']}h"
            . "\nDevices: $used/{$key['max_devices']}\nStatus: " . ((int) $key['status'] === 1 ? 'active' : 'blocked')
            . "\nExpires: " . ($key['expired_date'] ?: 'unused');
        $this->edit($chatId, $messageId, $text, ['inline_keyboard' => [
            [['text' => 'Reset devices', 'callback_data' => 'keyact:' . $id . ':reset'],
             ['text' => (int) $key['status'] === 1 ? 'Block' : 'Enable', 'callback_data' => 'keyact:' . $id . ':toggle']],
            [['text' => 'Delete', 'callback_data' => 'keydelete:' . $id . ':ask']],
            [['text' => 'Back', 'callback_data' => 'keys']],
        ]]);
    }

    /** @return array<string,mixed> */
    private function oneCoreKeyboard(): array
    {
        $rows = $this->db->query('SELECT id,key_prefix,label,status FROM license_keys ORDER BY id DESC LIMIT 8')->fetchAll();
        $buttons = [];
        foreach ($rows as $row) {
            $label = substr((string) $row['label'], 0, 20);
            $buttons[] = [['text' => strtoupper((string) $row['status']) . ' ' . $row['key_prefix'] . ' ' . $label,
                'callback_data' => 'oc:' . $row['id']]];
        }
        $buttons[] = [['text' => 'Back', 'callback_data' => 'menu']];
        return ['inline_keyboard' => $buttons];
    }

    private function showOneCoreKey(string $chatId, int $messageId, int $id): void
    {
        $statement = $this->db->prepare(
            'SELECT l.*,COUNT(b.id) AS device_count FROM license_keys l LEFT JOIN device_license_bindings b ON b.license_key_id=l.id WHERE l.id=? GROUP BY l.id'
        );
        $statement->execute([$id]);
        $key = $statement->fetch();
        if (!$key) {
            $this->edit($chatId, $messageId, 'OneCore key not found.', $this->oneCoreKeyboard());
            return;
        }
        $text = "OneCore key #$id\n{$key['key_prefix']}...\nLabel: {$key['label']}\nStatus: {$key['status']}"
            . "\nDevices: {$key['device_count']}/{$key['max_devices']}\nExpires: " . ($key['expires_at'] ?: 'never');
        $buttons = [];
        if ($key['status'] === 'active') {
            $buttons[] = [['text' => 'Revoke key', 'callback_data' => 'ocrevoke:' . $id . ':ask']];
        }
        $buttons[] = [['text' => 'Back', 'callback_data' => 'onecore']];
        $this->edit($chatId, $messageId, $text, ['inline_keyboard' => $buttons]);
    }

    private function usersText(): string
    {
        $rows = $this->db->query('SELECT username,role,status,telegram_user_id FROM panel_users ORDER BY id LIMIT 20')->fetchAll();
        $lines = ['Panel users'];
        foreach ($rows as $row) {
            $linked = $row['telegram_user_id'] === null ? 'not linked' : 'TG linked';
            $lines[] = $row['username'] . ' | ' . $row['role'] . ' | ' . $row['status'] . ' | ' . $linked;
        }
        return implode("\n", $lines);
    }

    /** @param array<string,mixed>|null $keyboard */
    private function sendMessage(string $chatId, string $text, ?array $keyboard = null): void
    {
        $payload = ['chat_id' => $chatId, 'text' => $text, 'disable_web_page_preview' => true];
        if ($keyboard) {
            $payload['reply_markup'] = $keyboard;
        }
        $this->api('sendMessage', $payload);
    }

    /** @param array<string,mixed> $keyboard */
    private function edit(string $chatId, int $messageId, string $text, array $keyboard): void
    {
        $this->api('editMessageText', [
            'chat_id' => $chatId,
            'message_id' => $messageId,
            'text' => $text,
            'disable_web_page_preview' => true,
            'reply_markup' => $keyboard,
        ]);
    }

    private function answerCallback(string $callbackId, string $text, bool $alert = false): void
    {
        if ($callbackId === '') {
            return;
        }
        $this->api('answerCallbackQuery', [
            'callback_query_id' => $callbackId,
            'text' => $text,
            'show_alert' => $alert,
        ]);
    }

    /** @param array<string,mixed> $payload */
    private function api(string $method, array $payload): array
    {
        $token = Env::get('TELEGRAM_BOT_TOKEN');
        if (preg_match('/^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$/D', $token) !== 1) {
            throw new RuntimeException('Telegram bot token is not configured.');
        }
        if (!extension_loaded('curl')) {
            throw new RuntimeException('PHP cURL extension is required for Telegram.');
        }
        $curl = curl_init('https://api.telegram.org/bot' . $token . '/' . $method);
        curl_setopt_array($curl, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT => 12,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        ]);
        $raw = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $error = curl_error($curl);
        curl_close($curl);
        $response = is_string($raw) ? json_decode($raw, true) : null;
        if ($status < 200 || $status >= 300 || !is_array($response) || !($response['ok'] ?? false)) {
            throw new RuntimeException('Telegram API request failed: ' . ($error ?: 'HTTP ' . $status));
        }
        return $response;
    }
}
