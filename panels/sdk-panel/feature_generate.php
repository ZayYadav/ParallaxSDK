<?php
declare(strict_types=1);
require_once __DIR__ . '/conn.php';
require_once __DIR__ . '/panel_helper.php';

if (!isset($_SESSION['user_id'])) {
    header('Location: login.php');
    exit;
}
if (!sdk_feature_installed($conn)) {
    http_response_code(503);
    exit('FEATURE_MIGRATION_REQUIRED');
}
$actor = sdk_feature_current_user($conn);
if (!$actor) {
    header('Location: login.php');
    exit;
}
$P = get_panel_settings($conn);
$settings = sdk_feature_settings($conn);
$error = '';
$result = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    try {
        $result = sdk_feature_generate_license($conn, $actor, $_POST, 'panel_feature_generator');
        $actor = sdk_feature_current_user($conn) ?: $actor;
    } catch (Throwable $e) {
        $error = $e->getMessage();
    }
}
$cost = max(0, (int)$settings['key_cost_per_day']);
$isOwner = (string)$actor['role'] === 'owner';
?>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Generate Access · <?= htmlspecialchars($P['panel_name'] ?? 'Parallax SDK') ?></title>
<style>
<?= panel_css_vars($P) ?>
*{box-sizing:border-box}body{margin:0;font-family:Inter,system-ui,sans-serif;background:#050810;color:#f8fafc}.wrap{max-width:1040px;margin:auto;padding:34px 18px 90px}.top{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap}.top a{color:#bfdbfe;text-decoration:none}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:22px}.card{padding:24px;border-radius:22px;background:rgba(15,23,42,.86);border:1px solid rgba(255,255,255,.1);box-shadow:0 20px 55px rgba(0,0,0,.28)}label{display:block;font-size:.78rem;color:#a7b0c2;margin:14px 0 7px}input,select{width:100%;padding:13px 14px;border:1px solid rgba(255,255,255,.13);border-radius:12px;background:#070d19;color:#fff;font:inherit}button{width:100%;margin-top:20px;padding:14px;border:0;border-radius:13px;background:linear-gradient(135deg,#c9a84c,#4f8ef7);color:#07111f;font-weight:900;cursor:pointer}.pill{display:inline-flex;padding:7px 11px;border-radius:999px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.1);font-size:.76rem}.ok,.err{padding:14px;border-radius:13px;margin-top:16px}.ok{background:rgba(52,211,153,.1);border:1px solid rgba(52,211,153,.28)}.err{background:rgba(251,113,133,.1);border:1px solid rgba(251,113,133,.28)}code{word-break:break-all;color:#fde68a}.muted{color:#94a3b8;line-height:1.55}.kpi{font-size:2rem;font-weight:900}@media(max-width:760px){.grid{grid-template-columns:1fr}.wrap{padding-top:22px}}
</style></head><body><main class="wrap">
<div class="top"><div><div class="pill"><?= htmlspecialchars(strtoupper((string)$actor['role'])) ?></div><h1>Balance-aware license generator</h1><p class="muted">Uses the existing Parallax SDK license table and validation contract. Only management/balance behavior is added.</p></div><a href="dashboard.php">← Dashboard</a></div>
<div class="grid"><section class="card">
<h2>Create license</h2>
<?php if($error!==''): ?><div class="err"><?= htmlspecialchars($error) ?></div><?php endif; ?>
<?php if($result): ?><div class="ok"><b>License created</b><p><code><?= htmlspecialchars($result['license_key']) ?></code></p><div>Expires: <?= htmlspecialchars(sdk_feature_display_time((string)$result['expiry_date'])) ?></div><div>Cost: <?= (int)$result['cost'] ?> · Balance: <?= (int)$result['balance_after'] ?></div></div><?php endif; ?>
<form method="post">
<label>Client / App name</label><input name="client_name" maxlength="120" value="Parallax Access" required>
<label>Days</label><select name="days"><option>1</option><option>7</option><option selected>30</option><option>90</option><option>365</option></select>
<label>Maximum devices</label><select name="max_devices"><option value="1">1</option><option value="10">10</option><option value="20">20</option><option value="30">30</option><option value="50">50</option><option value="100">100</option><option value="500">500</option><option value="1000">1000</option></select>
<label>Android package (optional — blank = ANY)</label><input name="package_name" maxlength="191" placeholder="com.example.app">
<label>Custom key (optional)</label><input name="custom_key" maxlength="96" placeholder="Minimum 16 characters">
<button type="submit">Generate license</button></form></section>
<section class="card"><h2>Account</h2><div class="kpi"><?= $isOwner ? 'Unlimited' : (int)$actor['balance'] ?></div><p class="muted">Current balance</p><hr style="border-color:rgba(255,255,255,.08)"><p class="muted">Cost per day: <b><?= $isOwner ? '0 (Owner)' : $cost ?></b></p><p class="muted">Owner keeps unlimited generation. Admin/Reseller/User generation obeys the owner generation switch and available balance.</p><p class="muted">Expiration is stored in UTC for SDK correctness and displayed in India time (IST) in the panel.</p></section></div>
</main></body></html>
