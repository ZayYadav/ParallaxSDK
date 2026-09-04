<?php

declare(strict_types=1);

namespace KESHAVXOWNERPanel;

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
file_put_contents($temporary, "# comment\nTEST_ALPHA=plain\nTEST_QUOTED=\"quoted value\"\nAPP_URL=https://panel.example.com\ninvalid-key=no\n");
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

$_SERVER['HTTP_ORIGIN'] = 'https://panel.example.com';
$_SERVER['HTTP_REFERER'] = '';
$_SERVER['HTTP_SEC_FETCH_SITE'] = 'same-site';
$assert(Security::sameOriginRequest(), 'An exact Origin must override an inconsistent Fetch Metadata hint.');
$_SERVER['HTTP_ORIGIN'] = '';
$_SERVER['HTTP_REFERER'] = 'https://panel.example.com/setup';
$_SERVER['HTTP_SEC_FETCH_SITE'] = 'none';
$assert(Security::sameOriginRequest(), 'An exact Referer must allow privacy-browser setup submissions.');
$_SERVER['HTTP_ORIGIN'] = 'https://attacker.example';
$_SERVER['HTTP_REFERER'] = '';
$_SERVER['HTTP_SEC_FETCH_SITE'] = 'same-origin';
$assert(!Security::sameOriginRequest(), 'A conflicting cross-origin source must be rejected.');
$_SERVER['HTTP_ORIGIN'] = '';
$_SERVER['HTTP_REFERER'] = '';
$_SERVER['HTTP_SEC_FETCH_SITE'] = 'cross-site';
$assert(!Security::sameOriginRequest(), 'A source-less cross-site request must be rejected.');
$_SERVER['HTTP_SEC_FETCH_SITE'] = 'same-origin';
$assert(Security::sameOriginRequest(), 'A source-less same-origin browser request must be accepted.');
unset($_SERVER['HTTP_ORIGIN'], $_SERVER['HTTP_REFERER'], $_SERVER['HTTP_SEC_FETCH_SITE']);

$schema = file_get_contents(PANEL_ROOT . '/database/schema.sql');
$assert(is_string($schema), 'Schema must be readable.');
foreach (['panel_users', 'keys_code', 'key_generation_options', 'connect_rate_limits', 'login_rate_limits', 'api_nonces', 'telegram_updates', 'audit_log'] as $table) {
    $assert(str_contains((string) $schema, 'TABLE IF NOT EXISTS ' . $table), "Schema is missing $table.");
}
$assert(!str_contains((string) $schema, 'TABLE IF NOT EXISTS license_keys'), 'Panel schema must use keys_code as its only key inventory.');
$assert(!str_contains((string) $schema, 'TABLE IF NOT EXISTS device_license_bindings'), 'Separate OneCore device bindings must not be installed.');

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
$assert(!str_contains($telegramSource, 'license_keys'), 'Telegram bot must use keys_code only.');
$assert(!str_contains(strtolower($telegramSource), 'onecore key'), 'Telegram bot must not expose a second key type.');
$appSource = (string) file_get_contents(PANEL_ROOT . '/src/App.php');
$assert(!str_contains($appSource, 'ONECORE_LEGACY_TOKEN_SECRET'), 'Plaintext shared-token licensing must be removed.');
$assert(!str_contains($appSource, 'private function connect()'), 'Plaintext /connect handler must be removed.');
$assert(str_contains($appSource, "Env::get('EXPECTED_ANDROID_CERT_SHA256')"), 'Server-side signing identity binding is missing.');
$assert(str_contains($appSource, "Env::get('MIN_ANDROID_VERSION_CODE'"), 'Minimum Loader version enforcement is missing.');
$assert(str_contains($appSource, "View::select('game'"), 'Game generation control must be a select list.');
$assert(str_contains($appSource, "View::select('duration'"), 'Duration generation control must be a select list.');
$assert(!str_contains($appSource, 'license_keys'), 'Panel must use keys_code only.');
$assert(!str_contains(strtolower($appSource), 'onecore key'), 'Panel must not expose a second key type.');
$bootstrapSource = (string) file_get_contents(PANEL_ROOT . '/src/bootstrap.php');
$assert(str_contains($bootstrapSource, 'Strict-Transport-Security'), 'Production HSTS is missing.');
$assert(str_contains($bootstrapSource, "session.use_strict_mode"), 'Strict session mode is missing.');
$securitySource = (string) file_get_contents(PANEL_ROOT . '/src/Security.php');
$assert(str_contains($securitySource, 'SESSION_IDLE_SECONDS'), 'Authenticated session idle expiry is missing.');
$assert(str_contains($securitySource, 'sameOriginRequest'), 'Same-origin CSRF validation is missing.');

echo "Standalone panel tests passed ($assertions assertions).\n";
