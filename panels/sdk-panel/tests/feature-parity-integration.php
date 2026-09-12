<?php
declare(strict_types=1);

$root = dirname(__DIR__);
require_once $root . '/conn.php';

function fail_test(string $message): never
{
    fwrite(STDERR, "FAIL: {$message}\n");
    exit(1);
}

function assert_test(bool $condition, string $message): void
{
    if (!$condition) {
        fail_test($message);
    }
}

// This suite is destructive and must never point at a production database.
$dbName = (string)panel_config('DB_NAME', '');
assert_test($dbName === 'sdk_test', 'Refusing to run outside sdk_test database.');
assert_test(sdk_feature_installed($conn), 'Feature suite migration is not installed.');

$conn->query("DELETE FROM announcement_recipients");
$conn->query("DELETE FROM announcement_broadcasts");
$conn->query("DELETE FROM referral_code_uses");
$conn->query("DELETE FROM referral_codes");
$conn->query("DELETE FROM balance_history");
$conn->query("DELETE FROM panel_activity_logs");
$conn->query("DELETE FROM devices");
$conn->query("DELETE FROM licenses");
$conn->query("DELETE FROM telegram_users");
$conn->query("DELETE FROM users");

$password = password_hash('AuditPassword!123', PASSWORD_DEFAULT);
$ownerName = 'audit_owner';
$ownerRole = 'owner';
$active = 1;
$ownerBalance = 0;
$stmt = $conn->prepare('INSERT INTO users(username,password,role,balance,status) VALUES(?,?,?,?,?)');
$stmt->bind_param('sssii', $ownerName, $password, $ownerRole, $ownerBalance, $active);
assert_test($stmt->execute(), 'Could not create owner fixture.');
$ownerId = (int)$conn->insert_id;
$stmt->close();

$resellerName = 'audit_reseller';
$resellerRole = 'reseller';
$resellerBalance = 100;
$stmt = $conn->prepare('INSERT INTO users(username,password,role,balance,status) VALUES(?,?,?,?,?)');
$stmt->bind_param('sssii', $resellerName, $password, $resellerRole, $resellerBalance, $active);
assert_test($stmt->execute(), 'Could not create reseller fixture.');
$resellerId = (int)$conn->insert_id;
$stmt->close();

$owner = [
    'id' => $ownerId,
    'username' => $ownerName,
    'role' => $ownerRole,
    'balance' => 0,
];
$reseller = [
    'id' => $resellerId,
    'username' => $resellerName,
    'role' => $resellerRole,
    'balance' => $resellerBalance,
];

assert_test(sdk_feature_save_setting($conn, 'panel_online', '1', $ownerId), 'Could not enable panel.');
assert_test(sdk_feature_save_setting($conn, 'generation_open', '1', $ownerId), 'Could not enable generation.');
assert_test(sdk_feature_save_setting($conn, 'key_cost_per_day', '2', $ownerId), 'Could not set generation cost.');

$result = sdk_feature_generate_license($conn, $reseller, [
    'days' => 7,
    'max_devices' => 10,
    'client_name' => 'Runtime Audit',
    'package_name' => 'com.audit.runtime',
    'custom_key' => 'SDK-RUNTIME-AUDIT-0001',
], 'ci_runtime');

assert_test((int)$result['cost'] === 14, 'Expected 14 credits for 7 days at 2/day.');
assert_test((int)$result['balance_after'] === 86, 'Expected reseller balance to become 86.');

$stmt = $conn->prepare('SELECT owner_user_id,generated_by,max_devices,package_name FROM licenses WHERE license_key=? LIMIT 1');
$stmt->bind_param('s', $result['license_key']);
assert_test($stmt->execute(), 'Could not read generated license.');
$license = $stmt->get_result()->fetch_assoc();
$stmt->close();
assert_test((int)($license['owner_user_id'] ?? 0) === $resellerId, 'Generated license ownership was not bound to reseller.');
assert_test((string)($license['generated_by'] ?? '') === $resellerName, 'generated_by username was not retained.');
assert_test((int)($license['max_devices'] ?? 0) === 10, 'Device limit was not stored.');
assert_test((string)($license['package_name'] ?? '') === 'com.audit.runtime', 'Package policy was not stored.');

$balanceRow = $conn->query('SELECT balance FROM users WHERE id=' . $resellerId)->fetch_assoc();
assert_test((int)($balanceRow['balance'] ?? -1) === 86, 'Database balance debit is incorrect.');
$historyRow = $conn->query('SELECT delta,balance_after FROM balance_history WHERE user_id=' . $resellerId . ' ORDER BY id DESC LIMIT 1')->fetch_assoc();
assert_test((int)($historyRow['delta'] ?? 0) === -14, 'Balance history debit is incorrect.');
assert_test((int)($historyRow['balance_after'] ?? -1) === 86, 'Balance history ending balance is incorrect.');

// Verify debit + insert are one transaction: an insufficient balance must not
// create a license or alter the account balance.
$reseller['balance'] = 86;
$threw = false;
try {
    sdk_feature_generate_license($conn, $reseller, [
        'days' => 365,
        'max_devices' => 1,
        'client_name' => 'Should Fail',
        'custom_key' => 'SDK-RUNTIME-AUDIT-FAIL',
    ], 'ci_runtime');
} catch (RuntimeException $e) {
    $threw = str_contains($e->getMessage(), 'Insufficient balance');
}
assert_test($threw, 'Insufficient-balance generation did not fail closed.');
$failedCount = (int)($conn->query("SELECT COUNT(*) c FROM licenses WHERE license_key='SDK-RUNTIME-AUDIT-FAIL'")->fetch_assoc()['c'] ?? 0);
assert_test($failedCount === 0, 'Failed generation left a license row behind.');
$balanceRow = $conn->query('SELECT balance FROM users WHERE id=' . $resellerId)->fetch_assoc();
assert_test((int)($balanceRow['balance'] ?? -1) === 86, 'Failed generation changed balance.');

$referral = sdk_feature_create_referral($conn, $owner, [
    'role' => 'reseller',
    'grant_balance' => 50,
    'max_uses' => 3,
    'expiry_days' => 7,
    'code' => 'SDK-AUDIT-REFERRAL',
]);
assert_test($referral === 'SDK-AUDIT-REFERRAL', 'Referral function returned unexpected code.');
$ref = $conn->query("SELECT assigned_to,grant_balance,max_uses,status FROM referral_codes WHERE code='SDK-AUDIT-REFERRAL'")->fetch_assoc();
assert_test((string)($ref['assigned_to'] ?? '') === 'reseller', 'Referral role was not stored.');
assert_test((int)($ref['grant_balance'] ?? 0) === 50, 'Referral balance grant was not stored.');
assert_test((int)($ref['max_uses'] ?? 0) === 3, 'Referral max uses was not stored.');
assert_test((int)($ref['status'] ?? 0) === 1, 'Referral should start active.');

$ownerResult = sdk_feature_generate_license($conn, $owner, [
    'days' => 30,
    'max_devices' => 1,
    'client_name' => 'Owner Unlimited',
    'custom_key' => 'SDK-RUNTIME-OWNER-0001',
], 'ci_runtime');
assert_test((int)$ownerResult['cost'] === 0, 'Owner generation must remain unlimited/free.');
$ownerLicense = $conn->query("SELECT owner_user_id FROM licenses WHERE license_key='SDK-RUNTIME-OWNER-0001'")->fetch_assoc();
assert_test((int)($ownerLicense['owner_user_id'] ?? 0) === $ownerId, 'Owner license ownership was not stored.');

echo "Feature parity runtime integration: PASS\n";
