<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
require PANEL_ROOT . '/src/Database.php';

$assertions = 0;
$assert = static function (bool $condition, string $message) use (&$assertions): void {
    $assertions++;
    if (!$condition) {
        throw new \RuntimeException($message);
    }
};

Database::install();
$db = Database::connection();
$assert(Database::installed(), 'Installed schema was not detected.');

$password = password_hash('integration-test-password', PASSWORD_DEFAULT);
$db->prepare("INSERT INTO panel_users (username,password_hash,role) VALUES (?,?, 'owner')")
    ->execute(['ci_owner', $password]);
$ownerId = (int) $db->lastInsertId();
$assert($ownerId > 0, 'Owner insert failed.');

$db->prepare('INSERT INTO keys_code (game,user_key,duration,max_devices,registrator,admin_id) VALUES (?,?,?,?,?,?)')
    ->execute(['PUBG', 'CI-LEGACY-KEY', 24, 1, 'ci_owner', $ownerId]);
$legacy = $db->query("SELECT * FROM keys_code WHERE user_key='CI-LEGACY-KEY'")->fetch();
$assert(is_array($legacy) && $legacy['game'] === 'PUBG', 'Legacy key insert failed.');

$activationKey = 'OC-' . implode('-', str_split(strtoupper(bin2hex(random_bytes(16))), 4));
$db->prepare('INSERT INTO license_keys (created_by_user_id,key_hash,key_prefix,label,max_devices,expires_at) VALUES (NULL,?,?,?,?,?)')
    ->execute([hash('sha256', $activationKey), substr($activationKey, 0, 12), 'CI key', 1, gmdate('Y-m-d H:i:s', time() + 86400)]);
$licenseId = (int) $db->lastInsertId();
$assert($licenseId > 0, 'OneCore key insert failed.');
$assert((int) $db->query('SELECT COUNT(*) FROM onoff WHERE id=1')->fetchColumn() === 1, 'Default server settings missing.');

echo "Standalone MySQL integration passed ($assertions assertions).\n";
