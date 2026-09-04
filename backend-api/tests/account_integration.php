<?php
declare(strict_types=1);

require_once __DIR__ . '/../Database.php';
require_once __DIR__ . '/../SelfHostedVerifier.php';
require_once __DIR__ . '/../AccountManager.php';

function integrationAssert(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$database = new Database([
    'host' => getenv('TEST_DB_HOST') ?: '127.0.0.1',
    'port' => (int) (getenv('TEST_DB_PORT') ?: 3306),
    'name' => getenv('TEST_DB_NAME') ?: 'onecore_integrity',
    'user' => getenv('TEST_DB_USER') ?: 'root',
    'password' => getenv('TEST_DB_PASSWORD') ?: '',
]);
$accounts = new AccountManager($database);

integrationAssert(!$accounts->hasUsers(), 'Account database was not empty');
$owner = $accounts->bootstrapOwner('owner_test', 'OwnerSecure123');
integrationAssert($owner['role'] === AccountManager::ROLE_OWNER, 'Owner bootstrap failed');

$userInvite = $accounts->createInvite($owner, AccountManager::ROLE_USER, 5, 24, 1);
$user = $accounts->register($userInvite, 'key_user', 'UserSecure123');
integrationAssert((int) $user['balance_credits'] === 5, 'Referral balance was not applied');

$userLicense = $accounts->createLicense($user, 'Customer One', '5OC', 2, 30);
integrationAssert(
    SelfHostedVerifier::isActivationKeyFormat($userLicense['key']),
    'Generated 5OC key was invalid'
);
integrationAssert($userLicense['cost'] === 1, 'Configured key cost was not charged');
integrationAssert($userLicense['balance_after'] === 4, 'Key balance debit was not atomic');

$adminInvite = $accounts->createInvite($owner, AccountManager::ROLE_ADMIN, 2, 24, 1);
$admin = $accounts->register($adminInvite, 'operations_admin', 'AdminSecure123');
integrationAssert($admin['role'] === AccountManager::ROLE_ADMIN, 'Admin referral role failed');
try {
    $accounts->adjustBalance($admin, (int) $user['id'], 10, 'Unauthorized credit');
    throw new RuntimeException('Admin was allowed to change a balance');
} catch (AccountException) {
    // Expected: only owner controls balances.
}

$newBalance = $accounts->adjustBalance(
    $owner,
    (int) $user['id'],
    10,
    'Owner test credit'
);
integrationAssert($newBalance === 14, 'Owner balance adjustment failed');

$ownerLicense = $accounts->createLicense($owner, 'Branded Customer', 'KESHAVXOWNER', 1, 7);
integrationAssert(str_starts_with($ownerLicense['key'], 'KESHAVXOWNER-'), 'Custom key prefix failed');
integrationAssert($ownerLicense['cost'] === 0, 'Owner key generation consumed balance');

$userLicenseId = (int) ($database->fetchOne(
    'SELECT id FROM license_keys WHERE created_by_user_id = :creator ORDER BY id LIMIT 1',
    ['creator' => $user['id']]
)['id'] ?? 0);
$accounts->revokeLicense($user, $userLicenseId);
integrationAssert(
    ($database->fetchOne('SELECT status FROM license_keys WHERE id = :id', ['id' => $userLicenseId])['status'] ?? '')
        === 'revoked',
    'User could not revoke their own key'
);

$ledger = $database->fetchOne(
    'SELECT COUNT(*) AS count FROM balance_transactions WHERE user_id = :user_id',
    ['user_id' => $user['id']]
);
integrationAssert((int) ($ledger['count'] ?? 0) === 3, 'Balance ledger is incomplete');

echo "Account role/referral/balance/license integration tests passed.\n";
