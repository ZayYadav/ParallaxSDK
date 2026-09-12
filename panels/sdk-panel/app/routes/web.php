<?php
declare(strict_types=1);

return [
    '/' => 'index.php',
    'login' => 'login.php',
    'register' => 'register.php',
    'logout' => 'logout.php',

    'dashboard' => 'dashboard.php',
    'dashboard/my' => 'feature_dashboard.php',
    'licenses' => 'license_list.php',
    'licenses/mine' => 'feature_licenses.php',
    'licenses/generate' => 'generate_ui.php',
    'licenses/self-service' => 'feature_generate.php',
    'licenses/check' => 'check_license.php',
    'licenses/edit' => 'keyEdit.php',
    'licenses/packages' => 'manage_packages.php',

    'users' => 'manage_users.php',
    'users/directory' => 'feature_users.php',
    'referrals' => 'manage_referrals.php',
    'referrals/advanced' => 'feature_referrals.php',
    'server' => 'online_server.php',
    'server/control' => 'feature_server.php',
    'announcements' => 'announcements.php',
    'announcements/inbox' => 'feature_announcements.php',
    'appearance' => 'panel_customizer.php',
    'settings' => 'settings.php',
    'security' => 'security_dashboard.php',

    'owner-console' => 'owner_console.php',
    'activity' => 'activity.php',
    'telegram-users' => 'telegram_users.php',
    'account' => 'account_security.php',
    'registration-review' => 'registration_review.php',

    'api/connect' => 'connect.php',
    'connect' => 'connect.php',
    'telegram/webhook' => 'telegram_bot.php',
];
