<?php

declare(strict_types=1);

namespace ParallaxPanel;

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';

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

$schema = file_get_contents(PANEL_ROOT . '/database/schema.sql');
$assert(is_string($schema), 'Schema must be readable.');
foreach (['panel_users', 'keys_code', 'license_keys', 'device_license_bindings', 'connect_rate_limits', 'login_rate_limits', 'audit_log'] as $table) {
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

echo "Standalone panel tests passed ($assertions assertions).\n";
