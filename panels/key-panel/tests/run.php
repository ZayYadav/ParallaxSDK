<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
require PANEL_ROOT . '/src/Security.php';

$assertions = 0;
$assert = static function (bool $condition, string $message) use (&$assertions): void {
    $assertions++;
    if (!$condition) {
        throw new \RuntimeException($message);
    }
};

$temporary = tempnam(sys_get_temp_dir(), 'panel-env-');
if ($temporary === false) {
    throw new \RuntimeException('Could not create test file.');
}
file_put_contents($temporary, "# comment\nTEST_ALPHA=plain\nTEST_QUOTED=\"quoted value\"\ninvalid-key=no\n");
Env::load($temporary);
unlink($temporary);
$assert(Env::get('TEST_ALPHA') === 'plain', 'Plain environment values must parse.');
$assert(Env::get('TEST_QUOTED') === 'quoted value', 'Quoted environment values must parse.');
$assert(Env::get('MISSING_VALUE', 'fallback') === 'fallback', 'Environment defaults must work.');

$_SESSION = [];
$question = Security::captchaQuestion();
$assert(preg_match('/^(\d+) \+ (\d+)$/D', $question, $parts) === 1, 'CAPTCHA question must be arithmetic.');
$answer = (string) ((int) $parts[1] + (int) $parts[2]);
$assert(Security::verifyCaptcha($answer), 'Fresh CAPTCHA answer must validate.');
$assert(!Security::verifyCaptcha($answer), 'CAPTCHA must be one-time use.');

$schema = file_get_contents(PANEL_ROOT . '/database/schema.sql');
$assert(is_string($schema), 'Schema must be readable.');
foreach (['panel_users', 'keys_code', 'license_keys', 'device_license_bindings', 'connect_rate_limits', 'login_rate_limits', 'api_nonces', 'telegram_updates', 'audit_log'] as $table) {
    $assert(str_contains((string) $schema, 'TABLE IF NOT EXISTS ' . $table), "Schema is missing $table.");
}

$token = md5('PUBG-TESTKEY-SERIAL-' . str_repeat('a', 32));
$assert(hash_equals('80f33beab28376b3dba4acf5375faab0', $token), 'Legacy token compatibility vector changed.');

$iterator = new \RecursiveIteratorIterator(new \RecursiveDirectoryIterator(PANEL_ROOT . '/src'));
foreach ($iterator as $file) {
    if ($file->isFile() && $file->getExtension() === 'php') {
        $source = file_get_contents($file->getPathname());
        $assert(!str_contains((string) $source, 'CodeIgniter\\'), 'Framework dependency found in ' . $file->getFilename());
    }
}

$telegramSource = (string) file_get_contents(PANEL_ROOT . '/src/TelegramBot.php');
$assert(str_contains($telegramSource, 'HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'), 'Telegram webhook secret header validation is missing.');
$assert(str_contains($telegramSource, 'TELEGRAM_ALLOWED_USER_IDS'), 'Telegram allowlist validation is missing.');
$assert(str_contains($telegramSource, "role IN ('owner','admin')"), 'Telegram role authorization is missing.');
$appSource = (string) file_get_contents(PANEL_ROOT . '/src/App.php');
$assert(str_contains($appSource, "Env::get('ENABLE_LEGACY_CONNECT', 'false') === 'true'"), 'Legacy connect must be disabled by default.');
$assert(str_contains($appSource, "Env::get('EXPECTED_ANDROID_CERT_SHA256')"), 'Server-side signing identity binding is missing.');

echo "Standalone panel tests passed ($assertions assertions).\n";
