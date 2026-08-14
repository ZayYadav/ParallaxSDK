<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
require PANEL_ROOT . '/src/Security.php';
require PANEL_ROOT . '/src/GenerationOptions.php';
require PANEL_ROOT . '/src/View.php';

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
foreach (['panel_users', 'keys_code', 'key_generation_options', 'license_keys', 'device_license_bindings', 'connect_rate_limits', 'login_rate_limits', 'api_nonces', 'telegram_updates', 'audit_log'] as $table) {
    $assert(str_contains((string) $schema, 'TABLE IF NOT EXISTS ' . $table), "Schema is missing $table.");
}

$games = GenerationOptions::parse(GenerationOptions::GAME, "pubg|PUBG Mobile\nBGMI|Battlegrounds Mobile India");
$assert($games[0] === ['value' => 'PUBG', 'label' => 'PUBG Mobile'], 'Game option parsing failed.');
$assert($games[1] === ['value' => 'BGMI', 'label' => 'Battlegrounds Mobile India'], 'Multiple game options failed.');
$durations = GenerationOptions::parse(GenerationOptions::DURATION, "24|1 Day\n168|7 Days");
$assert($durations[0] === ['value' => '24', 'label' => '1 Day'], 'Duration option parsing failed.');
$assert(GenerationOptions::toEditorText(['PUBG' => 'PUBG Mobile']) === 'PUBG|PUBG Mobile', 'Option editor serialization failed.');
$durationSelect = View::select('duration', 'Duration', [24 => '1 Day'], '24');
$assert(str_contains($durationSelect, 'value="24" selected'), 'Numeric duration option must remain selected.');
try {
    GenerationOptions::parse(GenerationOptions::DURATION, "24|One Day\n24|Duplicate");
    $assert(false, 'Duplicate generation options must fail.');
} catch (\InvalidArgumentException) {
    $assert(true, 'Duplicate generation options rejected.');
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
$assert(!str_contains($telegramSource, 'INSERT INTO license_keys (created_by_user_id'), 'Telegram key creation must support legacy OneCore schemas.');
$appSource = (string) file_get_contents(PANEL_ROOT . '/src/App.php');
$assert(str_contains($appSource, "Env::get('ENABLE_LEGACY_CONNECT', 'false') === 'true'"), 'Legacy connect must be disabled by default.');
$assert(str_contains($appSource, "Env::get('EXPECTED_ANDROID_CERT_SHA256')"), 'Server-side signing identity binding is missing.');
$assert(str_contains($appSource, "View::select('game'"), 'Game generation control must be a select list.');
$assert(str_contains($appSource, "View::select('duration'"), 'Duration generation control must be a select list.');
$assert(!str_contains($appSource, 'INSERT INTO license_keys (created_by_user_id'), 'Panel key creation must support legacy OneCore schemas.');

echo "Standalone panel tests passed ($assertions assertions).\n";
