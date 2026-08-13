<?php

declare(strict_types=1);

namespace ParallaxPanel;

use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class App
{
    private PDO $db;

    public function run(): void
    {
        $path = $this->path();
        try {
            $this->db = Database::connection();
            if ($path === '/setup') {
                $this->setup();
            }
            if (!Database::installed()) {
                redirect('setup');
            }
            if ($path === '/connect') {
                $this->connect();
            }
            if ($path === '/' || $path === '/login') {
                $this->login();
            }
            if ($path === '/logout' && $this->isPost()) {
                Security::verifyCsrf();
                Security::logout();
                redirect('login');
            }
            $routes = [
                '/dashboard' => 'dashboard',
                '/keys' => 'keys',
                '/keys/create' => 'createKeys',
                '/keys/action' => 'keyAction',
                '/licenses' => 'licenses',
                '/licenses/create' => 'createLicenses',
                '/licenses/revoke' => 'revokeLicense',
                '/settings' => 'settings',
                '/account' => 'account',
                '/users' => 'users',
                '/users/create' => 'createUser',
                '/users/action' => 'userAction',
            ];
            if (isset($routes[$path])) {
                $method = $routes[$path];
                $this->{$method}();
            }
            http_response_code(404);
            View::page('Not found', '<div class="card"><h1>404</h1><p>Page not found.</p></div>', Security::user());
        } catch (Throwable $error) {
            error_log((string) $error);
            if ($path === '/connect') {
                $this->json(['status' => false, 'reason' => 'SERVER ERROR'], 500);
            }
            $message = Env::get('APP_ENV') === 'development'
                ? $error->getMessage() : 'The panel could not complete this request. Check runtime/php-error.log.';
            View::page('Server error', '<div class="notice danger">' . h($message) . '</div>', Security::user());
        }
    }

    private function path(): string
    {
        $path = '/' . trim((string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH), '/');
        $base = '/' . trim(Env::get('APP_BASE_PATH'), '/');
        if ($base !== '/' && ($path === $base || str_starts_with($path, $base . '/'))) {
            $path = substr($path, strlen($base)) ?: '/';
        }
        return $path === '//' ? '/' : $path;
    }

    private function isPost(): bool
    {
        return ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST';
    }

    private function setup(): never
    {
        $installed = Database::installed();
        $hasOwner = false;
        if ($installed) {
            $hasOwner = (int) $this->db->query('SELECT COUNT(*) FROM panel_users')->fetchColumn() > 0;
        }
        if ($hasOwner) {
            http_response_code(404);
            View::page('Setup disabled', '<div class="card"><h1>Setup is locked</h1><p>An owner already exists.</p></div>');
        }
        if ($this->isPost()) {
            Security::verifyCsrf();
            $setupToken = Env::get('SETUP_TOKEN');
            $provided = (string) ($_POST['setup_token'] ?? '');
            $username = trim((string) ($_POST['username'] ?? ''));
            $password = (string) ($_POST['password'] ?? '');
            if (strlen($setupToken) < 32 || !hash_equals($setupToken, $provided)) {
                flash('danger', 'Invalid setup token.');
                redirect('setup');
            }
            if (preg_match('/^[A-Za-z0-9_]{4,32}$/D', $username) !== 1 || strlen($password) < 12) {
                flash('danger', 'Username must be 4-32 safe characters and password at least 12 characters.');
                redirect('setup');
            }
            Database::install();
            $statement = $this->db->prepare(
                "INSERT INTO panel_users (username,password_hash,role,status) VALUES (?,?,'owner','active')"
            );
            $statement->execute([$username, password_hash($password, PASSWORD_DEFAULT)]);
            flash('success', 'Owner created. Delete or rotate SETUP_TOKEN, then sign in.');
            redirect('login');
        }
        $body = '<section class="auth"><div class="card"><h1>First-time setup</h1>'
            . '<p class="lead">This installs the standalone schema and creates the first owner.</p>'
            . '<form method="post">' . Security::csrfField()
            . View::input('setup_token', 'SETUP_TOKEN from .env', 'password', '', 'required autocomplete="off"')
            . View::input('username', 'Owner username', 'text', '', 'required minlength="4" maxlength="32"')
            . View::input('password', 'Owner password', 'password', '', 'required minlength="12" autocomplete="new-password"')
            . '<button type="submit">Install panel</button></form></div></section>';
        View::page('Setup', $body);
    }

    private function login(): never
    {
        if (Security::user()) {
            redirect('dashboard');
        }
        if ($this->isPost()) {
            Security::verifyCsrf();
            if (!$this->allowLoginAttempt()) {
                flash('danger', 'Too many login attempts. Wait 15 minutes and try again.');
                redirect('login');
            }
            if (Security::login(trim((string) ($_POST['username'] ?? '')), (string) ($_POST['password'] ?? ''))) {
                $this->db->prepare('DELETE FROM login_rate_limits WHERE rate_key=?')
                    ->execute([hash('sha256', Security::clientIp())]);
                redirect('dashboard');
            }
            flash('danger', 'Invalid username or password.');
            redirect('login');
        }
        $body = '<section class="auth"><div class="card"><h1>Control panel</h1>'
            . '<p class="lead">Sign in to manage licenses and server access.</p><form method="post">'
            . Security::csrfField()
            . View::input('username', 'Username', 'text', '', 'required autocomplete="username"')
            . View::input('password', 'Password', 'password', '', 'required autocomplete="current-password"')
            . '<button type="submit">Sign in</button></form></div></section>';
        View::page('Sign in', $body);
    }

    private function dashboard(): never
    {
        $user = Security::requireUser();
        $legacy = (int) $this->db->query('SELECT COUNT(*) FROM keys_code')->fetchColumn();
        $active = (int) $this->db->query('SELECT COUNT(*) FROM keys_code WHERE status=1 AND (expired_date IS NULL OR expired_date > UTC_TIMESTAMP())')->fetchColumn();
        $modern = (int) $this->db->query('SELECT COUNT(*) FROM license_keys')->fetchColumn();
        $devices = (int) $this->db->query('SELECT COUNT(*) FROM device_license_bindings')->fetchColumn();
        $recent = $this->db->query('SELECT action_name,target_value,ip_address,created_at FROM audit_log ORDER BY id DESC LIMIT 12')->fetchAll();
        $rows = '';
        foreach ($recent as $row) {
            $rows .= '<tr><td>' . h($row['action_name']) . '</td><td class="mono">' . h($row['target_value'])
                . '</td><td>' . h($row['ip_address']) . '</td><td>' . h($row['created_at']) . '</td></tr>';
        }
        $body = '<h1>Dashboard</h1><p class="lead">License and access overview.</p><div class="grid">'
            . View::metric('Legacy keys', $legacy) . View::metric('Active legacy', $active)
            . View::metric('OneCore keys', $modern) . View::metric('Device bindings', $devices)
            . '</div><section class="card"><h2>Recent audit activity</h2><div class="table-wrap"><table>'
            . '<thead><tr><th>Action</th><th>Target</th><th>IP</th><th>Time UTC</th></tr></thead><tbody>'
            . ($rows ?: '<tr><td colspan="4" class="empty">No activity yet.</td></tr>') . '</tbody></table></div></section>';
        View::page('Dashboard', $body, $user);
    }

    private function keys(): never
    {
        $user = Security::requireUser();
        $where = $user['role'] === 'reseller' ? ' WHERE registrator = ?' : '';
        $statement = $this->db->prepare('SELECT * FROM keys_code' . $where . ' ORDER BY id_keys DESC LIMIT 500');
        $statement->execute($where ? [$user['username']] : []);
        $rows = '';
        foreach ($statement->fetchAll() as $key) {
            $status = (int) $key['status'] === 1 ? 'active' : 'blocked';
            $used = count(array_filter(array_map('trim', explode(',', (string) $key['devices']))));
            $rows .= '<tr><td>#' . h($key['id_keys']) . '<br><span class="pill ' . $status . '">' . $status . '</span></td>'
                . '<td>' . h($key['game']) . '</td><td class="mono">' . h($key['user_key']) . '</td>'
                . '<td>' . h($key['duration']) . 'h<br>' . h($key['expired_date'] ?: 'unused') . '</td>'
                . '<td>' . $used . '/' . h($key['max_devices']) . '</td><td>' . h($key['registrator']) . '</td>'
                . '<td><div class="actions">' . $this->postButton('keys/action', 'reset', (int) $key['id_keys'], 'Reset', 'secondary')
                . $this->postButton('keys/action', 'toggle', (int) $key['id_keys'], (int) $key['status'] ? 'Block' : 'Enable', 'secondary')
                . $this->postButton('keys/action', 'delete', (int) $key['id_keys'], 'Delete', 'danger') . '</div></td></tr>';
        }
        $form = '<section class="card"><h2>Generate legacy keys</h2><form method="post" action="' . url('keys/create') . '"><div class="form-grid">'
            . Security::csrfField() . View::input('game', 'Game', 'text', 'PUBG', 'required maxlength="64"')
            . View::input('duration', 'Duration (hours)', 'number', '24', 'required min="1" max="87600"')
            . View::input('max_devices', 'Max devices', 'number', '1', 'required min="1" max="100"')
            . View::input('quantity', 'Quantity', 'number', '1', 'required min="1" max="100"')
            . View::input('custom_key', 'Custom key (optional)', 'text', '', 'maxlength="128"')
            . '<button type="submit">Generate</button></div></form></section>';
        $created = $_SESSION['created_keys'] ?? [];
        unset($_SESSION['created_keys']);
        $output = $created ? '<div class="keys-output mono">' . h(implode("\n", $created)) . '</div>' : '';
        $body = '<h1>Legacy keys</h1><p class="lead">Keys accepted by the pinned <code>/connect</code> endpoint.</p>' . $output . $form
            . '<section class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>Game</th><th>Key</th>'
            . '<th>Expiry</th><th>Devices</th><th>Owner</th><th>Actions</th></tr></thead><tbody>'
            . ($rows ?: '<tr><td colspan="7" class="empty">No keys.</td></tr>') . '</tbody></table></div></section>';
        View::page('Legacy keys', $body, $user);
    }

    private function createKeys(): never
    {
        $user = Security::requireUser();
        $this->requirePost();
        $game = strtoupper(trim((string) ($_POST['game'] ?? '')));
        $duration = (int) ($_POST['duration'] ?? 0);
        $maxDevices = (int) ($_POST['max_devices'] ?? 0);
        $quantity = (int) ($_POST['quantity'] ?? 0);
        $custom = strtoupper(trim((string) ($_POST['custom_key'] ?? '')));
        if (preg_match('/^[A-Z0-9_-]{2,64}$/D', $game) !== 1 || $duration < 1 || $duration > 87600
            || $maxDevices < 1 || $maxDevices > 100 || $quantity < 1 || $quantity > 100
            || ($custom !== '' && ($quantity !== 1 || preg_match('/^[A-Z0-9_-]{4,128}$/D', $custom) !== 1))) {
            flash('danger', 'Invalid key fields. Custom keys can only be created one at a time.');
            redirect('keys');
        }
        $this->db->beginTransaction();
        try {
            if ($user['role'] === 'reseller') {
                $balance = $this->db->prepare('SELECT balance_credits FROM panel_users WHERE id=? FOR UPDATE');
                $balance->execute([(int) $user['id']]);
                if ((int) $balance->fetchColumn() < $quantity) {
                    $this->db->rollBack();
                    flash('danger', 'Insufficient credits. Each generated legacy key costs 1 credit.');
                    redirect('keys');
                }
                $this->db->prepare('UPDATE panel_users SET balance_credits=balance_credits-? WHERE id=?')
                    ->execute([$quantity, (int) $user['id']]);
            }
            $statement = $this->db->prepare('INSERT INTO keys_code (game,user_key,duration,max_devices,registrator,admin_id) VALUES (?,?,?,?,?,?)');
            $keys = [];
            for ($index = 0; $index < $quantity; $index++) {
                $key = $custom !== '' ? $custom : $game . '-' . strtoupper(bin2hex(random_bytes(8)));
                $statement->execute([$game, $key, $duration, $maxDevices, $user['username'], $user['id']]);
                $keys[] = $key;
            }
            Security::audit($this->db, (int) $user['id'], 'legacy_keys_created', $game . ':' . $quantity);
            $this->db->commit();
            $_SESSION['created_keys'] = $keys;
            flash('success', $quantity . ' key(s) created. Copy them now.');
        } catch (Throwable $error) {
            $this->db->rollBack();
            flash('danger', 'Key creation failed. A custom key may already exist.');
        }
        redirect('keys');
    }

    private function keyAction(): never
    {
        $user = Security::requireUser();
        $this->requirePost();
        $id = (int) ($_POST['id'] ?? 0);
        $action = (string) ($_POST['action'] ?? '');
        $statement = $this->db->prepare('SELECT * FROM keys_code WHERE id_keys=?');
        $statement->execute([$id]);
        $key = $statement->fetch();
        if (!$key || ($user['role'] === 'reseller' && $key['registrator'] !== $user['username'])) {
            flash('danger', 'Key not found or access denied.');
            redirect('keys');
        }
        if ($action === 'reset') {
            $this->db->prepare('UPDATE keys_code SET devices=NULL WHERE id_keys=?')->execute([$id]);
        } elseif ($action === 'toggle') {
            $this->db->prepare('UPDATE keys_code SET status=IF(status=1,0,1) WHERE id_keys=?')->execute([$id]);
        } elseif ($action === 'delete') {
            $this->db->prepare('DELETE FROM keys_code WHERE id_keys=?')->execute([$id]);
        } else {
            flash('danger', 'Unsupported action.');
            redirect('keys');
        }
        Security::audit($this->db, (int) $user['id'], 'legacy_key_' . $action, (string) $id);
        flash('success', 'Key updated.');
        redirect('keys');
    }

    private function licenses(): never
    {
        $user = Security::requireUser();
        $this->requireAdminRole($user);
        $rows = '';
        $licenses = $this->db->query(
            'SELECT l.*, COUNT(b.id) AS device_count FROM license_keys l '
            . 'LEFT JOIN device_license_bindings b ON b.license_key_id=l.id '
            . 'GROUP BY l.id ORDER BY l.id DESC LIMIT 500'
        )->fetchAll();
        foreach ($licenses as $license) {
            $effective = $license['status'];
            if ($license['expires_at'] && strtotime($license['expires_at'] . ' UTC') <= time()) {
                $effective = 'expired';
            }
            $rows .= '<tr><td>#' . h($license['id']) . '<br><span class="pill ' . h($effective) . '">' . h($effective) . '</span></td>'
                . '<td>' . h($license['label']) . '</td><td class="mono">' . h($license['key_prefix']) . '…</td>'
                . '<td>' . h($license['device_count']) . '/' . h($license['max_devices']) . '</td>'
                . '<td>' . h($license['expires_at'] ?: 'never') . '</td><td>' . h($license['last_used_at'] ?: 'unused') . '</td>'
                . '<td>' . ($license['status'] === 'active'
                    ? $this->postButton('licenses/revoke', 'revoke', (int) $license['id'], 'Revoke', 'danger') : '') . '</td></tr>';
        }
        $created = $_SESSION['created_onecore_keys'] ?? [];
        unset($_SESSION['created_onecore_keys']);
        $output = $created ? '<div class="keys-output mono">' . h(implode("\n", $created)) . '</div>' : '';
        $form = '<section class="card"><h2>Create OneCore activation keys</h2><form method="post" action="' . url('licenses/create') . '">'
            . '<div class="form-grid">' . Security::csrfField()
            . View::input('label', 'Customer / label', 'text', '', 'required maxlength="100"')
            . View::input('valid_days', 'Valid days', 'number', '30', 'required min="1" max="3650"')
            . View::input('max_devices', 'Max devices', 'number', '1', 'required min="1" max="100"')
            . View::input('quantity', 'Quantity', 'number', '1', 'required min="1" max="100"')
            . '<button type="submit">Create</button></div></form></section>';
        $body = '<h1>OneCore keys</h1><p class="lead">Full keys are shown once; the database stores SHA-256 hashes only.</p>'
            . $output . $form . '<section class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>Label</th>'
            . '<th>Prefix</th><th>Devices</th><th>Expiry UTC</th><th>Last used</th><th>Action</th></tr></thead><tbody>'
            . ($rows ?: '<tr><td colspan="7" class="empty">No OneCore keys.</td></tr>') . '</tbody></table></div></section>';
        View::page('OneCore keys', $body, $user);
    }

    private function createLicenses(): never
    {
        $user = Security::requireUser();
        $this->requireAdminRole($user);
        $this->requirePost();
        $label = trim((string) ($_POST['label'] ?? ''));
        $days = (int) ($_POST['valid_days'] ?? 0);
        $maxDevices = (int) ($_POST['max_devices'] ?? 0);
        $quantity = (int) ($_POST['quantity'] ?? 0);
        if ($label === '' || strlen($label) > 100 || $days < 1 || $days > 3650
            || $maxDevices < 1 || $maxDevices > 100 || $quantity < 1 || $quantity > 100) {
            flash('danger', 'Invalid activation-key fields.');
            redirect('licenses');
        }
        $expires = gmdate('Y-m-d H:i:s', time() + ($days * 86400));
        $statement = $this->db->prepare(
            'INSERT INTO license_keys (created_by_user_id,key_hash,key_prefix,label,max_devices,expires_at) VALUES (?,?,?,?,?,?)'
        );
        $keys = [];
        $this->db->beginTransaction();
        try {
            for ($index = 0; $index < $quantity; $index++) {
                $parts = str_split(strtoupper(bin2hex(random_bytes(16))), 4);
                $key = 'OC-' . implode('-', $parts);
                // Keep this compatible with an Integrity database whose
                // created_by_user_id may reference its own dashboard_users table.
                $statement->execute([null, hash('sha256', $key), substr($key, 0, 12), $label, $maxDevices, $expires]);
                $keys[] = $key;
            }
            Security::audit($this->db, (int) $user['id'], 'onecore_keys_created', $label . ':' . $quantity);
            $this->db->commit();
            $_SESSION['created_onecore_keys'] = $keys;
            flash('success', $quantity . ' activation key(s) created. Copy them now.');
        } catch (Throwable) {
            $this->db->rollBack();
            flash('danger', 'Activation keys could not be created.');
        }
        redirect('licenses');
    }

    private function revokeLicense(): never
    {
        $user = Security::requireUser();
        $this->requireAdminRole($user);
        $this->requirePost();
        $id = (int) ($_POST['id'] ?? 0);
        $this->db->beginTransaction();
        try {
            $this->db->prepare("UPDATE license_keys SET status='revoked' WHERE id=?")->execute([$id]);
            $this->db->prepare("UPDATE devices d JOIN device_license_bindings b ON b.device_id=d.device_id SET d.status='revoked' WHERE b.license_key_id=?")
                ->execute([$id]);
            Security::audit($this->db, (int) $user['id'], 'onecore_key_revoked', (string) $id);
            $this->db->commit();
            flash('success', 'Activation key and bound devices revoked.');
        } catch (Throwable) {
            $this->db->rollBack();
            flash('danger', 'License revoke failed.');
        }
        redirect('licenses');
    }

    private function settings(): never
    {
        $user = Security::requireUser();
        if ($this->isPost()) {
            Security::verifyCsrf();
            if ($user['role'] === 'reseller') {
                http_response_code(403);
                exit('Admin access required.');
            }
            $maintenance = isset($_POST['maintenance']) ? 'on' : 'off';
            $message = substr(trim((string) ($_POST['message'] ?? 'Maintenance in progress')), 0, 255);
            $modname = substr(trim((string) ($_POST['modname'] ?? 'Parallax')), 0, 100);
            $credit = substr(trim((string) ($_POST['credit'] ?? 'Parallax')), 0, 255);
            $features = ['ESP','Item','AIM','SilentAim','BulletTrack','Floating','Memory','Setting'];
            $this->db->beginTransaction();
            $this->db->prepare('UPDATE onoff SET status=?,myinput=? WHERE id=1')->execute([$maintenance, $message]);
            $this->db->prepare('UPDATE modname SET modname=? WHERE id=1')->execute([$modname]);
            $this->db->prepare('UPDATE `_ftext` SET `_ftext`=? WHERE id=1')->execute([$credit]);
            $values = array_map(static fn (string $feature): string => isset($_POST[$feature]) ? 'on' : 'off', $features);
            $this->db->prepare('UPDATE `Feature` SET ESP=?,Item=?,AIM=?,SilentAim=?,BulletTrack=?,Floating=?,Memory=?,Setting=? WHERE id=1')
                ->execute($values);
            Security::audit($this->db, (int) $user['id'], 'server_settings_updated');
            $this->db->commit();
            flash('success', 'Server settings saved.');
            redirect('settings');
        }
        $server = $this->db->query('SELECT o.status,o.myinput,m.modname,f.`_ftext` FROM onoff o JOIN modname m ON m.id=1 JOIN `_ftext` f ON f.id=1 WHERE o.id=1')->fetch();
        $features = $this->db->query('SELECT * FROM `Feature` WHERE id=1')->fetch();
        $checks = '';
        foreach (['ESP','Item','AIM','SilentAim','BulletTrack','Floating','Memory','Setting'] as $feature) {
            $checks .= '<label><input type="checkbox" name="' . h($feature) . '" value="on" '
                . (($features[$feature] ?? 'off') === 'on' ? 'checked' : '') . '> ' . h($feature) . '</label>';
        }
        $disabled = $user['role'] === 'reseller' ? ' disabled' : '';
        $body = '<h1>Server settings</h1><p class="lead">Values returned by the legacy checker.</p><section class="card">'
            . '<form method="post"><fieldset' . $disabled . '><div class="form-grid">' . Security::csrfField()
            . View::input('modname', 'Mod name', 'text', (string) $server['modname'], 'required maxlength="100"')
            . View::input('credit', 'Credit text', 'text', (string) $server['_ftext'], 'maxlength="255"')
            . View::input('message', 'Maintenance message', 'text', (string) $server['myinput'], 'maxlength="255"')
            . '</div><p><label><input type="checkbox" name="maintenance" value="on" '
            . ($server['status'] === 'on' ? 'checked' : '') . '> Maintenance mode</label></p>'
            . '<div class="grid">' . $checks . '</div><p><button type="submit">Save settings</button></p></fieldset></form></section>';
        View::page('Settings', $body, $user);
    }

    private function users(): never
    {
        $user = Security::requireUser(true);
        $rows = '';
        foreach ($this->db->query('SELECT id,username,role,balance_credits,status,last_login_at,created_at FROM panel_users ORDER BY id')->fetchAll() as $account) {
            $rows .= '<tr><td>#' . h($account['id']) . '</td><td>' . h($account['username']) . '</td><td>' . h($account['role'])
                . '</td><td>' . h($account['balance_credits']) . '</td><td><span class="pill ' . h($account['status']) . '">' . h($account['status'])
                . '</span></td><td>' . h($account['last_login_at'] ?: 'never') . '</td><td>'
                . ((int) $account['id'] !== (int) $user['id']
                    ? $this->postButton('users/action', 'toggle', (int) $account['id'], $account['status'] === 'active' ? 'Suspend' : 'Activate', 'secondary') : '')
                . '</td></tr>';
        }
        $body = '<h1>Panel users</h1><p class="lead">Only owners can create or suspend panel accounts.</p>'
            . '<section class="card"><h2>Create user</h2><form method="post" action="' . url('users/create') . '"><div class="form-grid">'
            . Security::csrfField() . View::input('username', 'Username', 'text', '', 'required minlength="4" maxlength="32"')
            . View::input('password', 'Temporary password', 'password', '', 'required minlength="12"')
            . View::select('role', 'Role', ['admin'=>'Admin','reseller'=>'Reseller'], 'reseller')
            . View::input('balance', 'Credits', 'number', '0', 'min="0" max="100000000"')
            . '<button type="submit">Create user</button></div></form></section><section class="card"><div class="table-wrap"><table>'
            . '<thead><tr><th>ID</th><th>Username</th><th>Role</th><th>Credits</th><th>Status</th><th>Last login</th><th>Action</th></tr></thead><tbody>'
            . $rows . '</tbody></table></div></section>';
        View::page('Users', $body, $user);
    }

    private function createUser(): never
    {
        $user = Security::requireUser(true);
        $this->requirePost();
        $username = trim((string) ($_POST['username'] ?? ''));
        $password = (string) ($_POST['password'] ?? '');
        $role = (string) ($_POST['role'] ?? 'reseller');
        $balance = (int) ($_POST['balance'] ?? 0);
        if (preg_match('/^[A-Za-z0-9_]{4,32}$/D', $username) !== 1 || strlen($password) < 12
            || !in_array($role, ['admin','reseller'], true) || $balance < 0 || $balance > 100000000) {
            flash('danger', 'Invalid user fields.');
            redirect('users');
        }
        try {
            $statement = $this->db->prepare('INSERT INTO panel_users (username,password_hash,role,balance_credits) VALUES (?,?,?,?)');
            $statement->execute([$username, password_hash($password, PASSWORD_DEFAULT), $role, $balance]);
            Security::audit($this->db, (int) $user['id'], 'panel_user_created', $username);
            flash('success', 'Panel user created.');
        } catch (Throwable) {
            flash('danger', 'Username already exists or could not be created.');
        }
        redirect('users');
    }

    private function userAction(): never
    {
        $user = Security::requireUser(true);
        $this->requirePost();
        $id = (int) ($_POST['id'] ?? 0);
        if ($id < 1 || $id === (int) $user['id']) {
            flash('danger', 'You cannot change your own status.');
            redirect('users');
        }
        $this->db->prepare("UPDATE panel_users SET status=IF(status='active','suspended','active') WHERE id=? AND role!='owner'")->execute([$id]);
        Security::audit($this->db, (int) $user['id'], 'panel_user_status_toggled', (string) $id);
        flash('success', 'User status updated.');
        redirect('users');
    }

    private function account(): never
    {
        $user = Security::requireUser();
        if ($this->isPost()) {
            Security::verifyCsrf();
            $current = (string) ($_POST['current_password'] ?? '');
            $password = (string) ($_POST['new_password'] ?? '');
            $confirm = (string) ($_POST['confirm_password'] ?? '');
            $statement = $this->db->prepare('SELECT password_hash FROM panel_users WHERE id=?');
            $statement->execute([(int) $user['id']]);
            $hash = (string) $statement->fetchColumn();
            if (!password_verify($current, $hash) || strlen($password) < 12 || $password !== $confirm) {
                flash('danger', 'Current password is wrong, or the new passwords do not match the 12-character minimum.');
                redirect('account');
            }
            $this->db->prepare('UPDATE panel_users SET password_hash=? WHERE id=?')
                ->execute([password_hash($password, PASSWORD_DEFAULT), (int) $user['id']]);
            Security::audit($this->db, (int) $user['id'], 'password_changed');
            session_regenerate_id(true);
            flash('success', 'Password changed.');
            redirect('account');
        }
        $body = '<h1>Account security</h1><p class="lead">Change the password for ' . h($user['username']) . '.</p>'
            . '<section class="card"><form method="post"><div class="form-grid">' . Security::csrfField()
            . View::input('current_password', 'Current password', 'password', '', 'required autocomplete="current-password"')
            . View::input('new_password', 'New password', 'password', '', 'required minlength="12" autocomplete="new-password"')
            . View::input('confirm_password', 'Confirm new password', 'password', '', 'required minlength="12" autocomplete="new-password"')
            . '<button type="submit">Change password</button></div></form></section>';
        View::page('Account', $body, $user);
    }

    private function connect(): never
    {
        if (!$this->isPost()) {
            $body = '<section class="auth"><div class="card"><h1>Activation portal</h1>'
                . '<p class="lead">Enter your key inside the official Parallax loader. Keys are bound to the first permitted device and expire according to their purchased duration.</p>'
                . '<a class="button" href="mailto:support@parallaxserver.online">Contact support</a></div></section>';
            View::page('Activation portal', $body, Security::user());
        }
        header('Cache-Control: no-store, max-age=0');
        $secret = Env::get('ONECORE_LEGACY_TOKEN_SECRET');
        if (strlen($secret) < 32 || strlen($secret) > 256) {
            error_log('ONECORE_LEGACY_TOKEN_SECRET is missing or invalid.');
            $this->json(['status' => false, 'reason' => 'SERVER CONFIGURATION ERROR'], 500);
        }
        $game = trim((string) ($_POST['game'] ?? ''));
        $userKey = trim((string) ($_POST['user_key'] ?? ''));
        $serial = trim((string) ($_POST['serial'] ?? ''));
        if ($game === '' || $userKey === '' || $serial === '' || strlen($game) > 64
            || strlen($userKey) > 128 || strlen($serial) > 256 || str_contains($serial, ',')) {
            $this->json(['status' => false, 'reason' => 'INVALID PARAMETER']);
        }
        if (!$this->allowConnectRequest($userKey)) {
            $this->json(['status' => false, 'reason' => 'TOO MANY REQUESTS'], 429);
        }
        $this->db->beginTransaction();
        try {
            $statement = $this->db->prepare('SELECT * FROM keys_code WHERE user_key=? AND game=? LIMIT 1 FOR UPDATE');
            $statement->execute([$userKey, $game]);
            $key = $statement->fetch();
            if (!$key) {
                $this->db->rollBack();
                $this->json(['status' => false, 'reason' => 'USER OR GAME NOT REGISTERED']);
            }
            if ((int) $key['status'] !== 1) {
                $this->db->rollBack();
                $this->json(['status' => false, 'reason' => 'USER BLOCKED']);
            }
            $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
            $expiry = $key['expired_date']
                ? new DateTimeImmutable($key['expired_date'], new DateTimeZone('UTC')) : null;
            if ($expiry && $expiry <= $now) {
                $this->db->rollBack();
                $this->json(['status' => false, 'reason' => 'EXPIRED KEY']);
            }
            if (!$expiry) {
                $expiry = $now->modify('+' . max(1, (int) $key['duration']) . ' hours');
                $this->db->prepare('UPDATE keys_code SET expired_date=? WHERE id_keys=?')
                    ->execute([$expiry->format('Y-m-d H:i:s'), $key['id_keys']]);
            }
            $devices = array_values(array_unique(array_filter(array_map('trim', explode(',', (string) $key['devices'])))));
            if (!in_array($serial, $devices, true)) {
                if (count($devices) >= max(1, (int) $key['max_devices'])) {
                    $this->db->rollBack();
                    $this->json(['status' => false, 'reason' => 'MAX DEVICE REACHED']);
                }
                $devices[] = $serial;
                $this->db->prepare('UPDATE keys_code SET devices=? WHERE id_keys=?')
                    ->execute([implode(',', $devices), $key['id_keys']]);
            }
            $server = $this->db->query('SELECT modname FROM modname WHERE id=1')->fetch() ?: [];
            $copy = $this->db->query('SELECT `_status`,`_ftext` FROM `_ftext` WHERE id=1')->fetch() ?: [];
            $feature = $this->db->query('SELECT * FROM `Feature` WHERE id=1')->fetch() ?: [];
            $maintenance = $this->db->query('SELECT status,myinput FROM onoff WHERE id=1')->fetch() ?: [];
            $this->db->commit();
            if (($maintenance['status'] ?? 'off') === 'on') {
                $this->json(['status' => true, 'reason' => (string) ($maintenance['myinput'] ?? 'Maintenance in progress')]);
            }
            $expiryString = $expiry->format('Y-m-d H:i:s');
            $token = md5($game . '-' . $userKey . '-' . $serial . '-' . $secret);
            $this->json(['status' => true, 'data' => [
                'token' => $token,
                'modname' => (string) ($server['modname'] ?? ''),
                'mod_status' => (string) ($copy['_status'] ?? ''),
                'credit' => (string) ($copy['_ftext'] ?? ''),
                'ESP' => (string) ($feature['ESP'] ?? 'off'),
                'Item' => (string) ($feature['Item'] ?? 'off'),
                'AIM' => (string) ($feature['AIM'] ?? 'off'),
                'SilentAim' => (string) ($feature['SilentAim'] ?? 'off'),
                'BulletTrack' => (string) ($feature['BulletTrack'] ?? 'off'),
                'Floating' => (string) ($feature['Floating'] ?? 'off'),
                'Memory' => (string) ($feature['Memory'] ?? 'off'),
                'Setting' => (string) ($feature['Setting'] ?? 'off'),
                'expired_date' => $expiryString,
                'EXP' => $expiryString,
                'exdate' => $expiryString,
                'device' => max(1, (int) $key['max_devices']),
                'rng' => time(),
            ]]);
        } catch (Throwable $error) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }
            throw $error;
        }
    }

    private function allowConnectRequest(string $userKey): bool
    {
        $window = gmdate('Y-m-d H:i:00');
        $rateKey = hash('sha256', Security::clientIp() . '|' . strtoupper($userKey));
        $statement = $this->db->prepare(
            'INSERT INTO connect_rate_limits (rate_key,window_started_at,request_count) VALUES (?,?,1) '
            . 'ON DUPLICATE KEY UPDATE request_count=request_count+1'
        );
        $statement->execute([$rateKey, $window]);
        $check = $this->db->prepare('SELECT request_count FROM connect_rate_limits WHERE rate_key=? AND window_started_at=?');
        $check->execute([$rateKey, $window]);
        if (random_int(1, 100) === 1) {
            $this->db->exec("DELETE FROM connect_rate_limits WHERE window_started_at < UTC_TIMESTAMP() - INTERVAL 1 DAY");
        }
        return (int) $check->fetchColumn() <= 30;
    }

    private function allowLoginAttempt(): bool
    {
        $windowTimestamp = intdiv(time(), 900) * 900;
        $window = gmdate('Y-m-d H:i:s', $windowTimestamp);
        $rateKey = hash('sha256', Security::clientIp());
        $statement = $this->db->prepare(
            'INSERT INTO login_rate_limits (rate_key,window_started_at,attempt_count) VALUES (?,?,1) '
            . 'ON DUPLICATE KEY UPDATE attempt_count=attempt_count+1'
        );
        $statement->execute([$rateKey, $window]);
        $check = $this->db->prepare('SELECT attempt_count FROM login_rate_limits WHERE rate_key=? AND window_started_at=?');
        $check->execute([$rateKey, $window]);
        return (int) $check->fetchColumn() <= 10;
    }

    /** @param array<string,mixed> $user */
    private function requireAdminRole(array $user): void
    {
        if (!in_array($user['role'], ['owner', 'admin'], true)) {
            http_response_code(403);
            exit('Admin access required.');
        }
    }

    private function requirePost(): void
    {
        if (!$this->isPost()) {
            http_response_code(405);
            exit('Method not allowed.');
        }
        Security::verifyCsrf();
    }

    private function postButton(string $path, string $action, int $id, string $label, string $class): string
    {
        return '<form method="post" action="' . url($path) . '">' . Security::csrfField()
            . '<input type="hidden" name="action" value="' . h($action) . '">'
            . '<input type="hidden" name="id" value="' . $id . '">'
            . '<button type="submit" class="' . h($class) . '">' . h($label) . '</button></form>';
    }

    /** @param array<string,mixed> $payload */
    private function json(array $payload, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=UTF-8');
        echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        exit;
    }
}
