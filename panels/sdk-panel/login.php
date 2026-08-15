<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
include 'conn.php';
include 'panel_helper.php';
$schemaProblems = sdk_panel_schema_problems($conn);
if ($schemaProblems !== []) {
    error_log(
        'SDK Panel database schema is incomplete for database '
        . (string) panel_config('DB_NAME', 'unknown') . ': ' . implode('; ', $schemaProblems)
    );
    http_response_code(503);
    exit('SERVER_DATABASE_SCHEMA_ERROR');
}
$P = get_panel_settings($conn);

if (isset($_POST['login'])) {
    $username = trim((string) ($_POST['username'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');

    if (!panel_rate_limit($conn, 'login|' . panel_client_ip(), 10)) {
        $error = 'Too many attempts. Wait one minute and try again.';
    } elseif (preg_match('/^[A-Za-z0-9_.-]{3,64}$/D', $username) !== 1 || strlen($password) > 256) {
        $error = 'Invalid username or password!';
    } else {
        $stmt = $conn->prepare('SELECT * FROM users WHERE username = ? LIMIT 1');
        if (!$stmt) {
            error_log('SDK Panel login statement failed: ' . $conn->error);
            http_response_code(503);
            $error = 'Panel database is not initialized correctly. Contact the administrator.';
            $user = null;
        } else {
            $stmt->bind_param('s', $username);
            $stmt->execute();
            $user = $stmt->get_result()->fetch_assoc();
            $stmt->close();
        }

        if (!isset($error)) {
            if ($user && (!isset($user['status']) || (int) $user['status'] === 1)
                && password_verify($password, (string) $user['password'])) {
                $userId = (int) $user['id'];
                $update = $conn->prepare('UPDATE users SET is_online = 1 WHERE id = ?');
                if (!$update) {
                    error_log('SDK Panel online-state statement failed: ' . $conn->error);
                    http_response_code(503);
                    $error = 'Panel database is not initialized correctly. Contact the administrator.';
                } else {
                    $update->bind_param('i', $userId);
                    $update->execute();
                    $update->close();
                    session_regenerate_id(true);
                    $_SESSION['user_id'] = $userId;
                    $_SESSION['username'] = $user['username'];
                    $_SESSION['logged_in'] = true;
                    $_SESSION['last_activity'] = time();
                    $_SESSION['agent_hash'] = hash('sha256', (string) ($_SERVER['HTTP_USER_AGENT'] ?? 'unknown'));
                    panel_audit($conn, 'panel_login', 'success', '', ['user_id' => $userId]);
                    header("Location: dashboard.php");
                    exit();
                }
            } else {
                panel_audit($conn, 'panel_login', 'failed', '', ['username_hash' => hash('sha256', strtolower($username))]);
                $error = "Invalid username or password!";
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title><?= htmlspecialchars($P['panel_name']) ?> · Login</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=SF+Pro+Display:wght@300;400;500;600;700&family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css" rel="stylesheet">
    <style>
        :root {
            --gold: <?= $P['theme_primary'] ?>;
            --gold-light: #F0D080;
            --gold-dark: #8B6914;
            --accent: <?= $P['theme_accent'] ?>;
            --accent2: #7C3AED;
            --bg-deep: <?= $P['theme_bg1'] ?>;
            --bg-card: rgba(12, 16, 28, 0.82);
            --border: rgba(201,168,76,0.18);
            --border-light: rgba(255,255,255,0.06);
            --text-primary: #F0EDE8;
            --text-muted: rgba(240,237,232,0.45);
            --error: #FF5F6D;
            --radius: 26px;
            --radius-sm: 14px;
            --input-h: 56px;
        }

        *, *::before, *::after {
            margin: 0; padding: 0;
            box-sizing: border-box;
            -webkit-font-smoothing: antialiased;
        }

        html, body {
            height: 100%;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            background: var(--bg-deep);
            color: var(--text-primary);
            overflow: hidden;
        }

        /* ═══ BACKGROUND LAYERS ═══ */
        .bg-wrap {
            position: fixed; inset: 0; z-index: 0;
            overflow: hidden;
        }

        .bg-mesh {
            position: absolute; inset: 0;
            background:
                radial-gradient(ellipse 80% 60% at 20% 10%, rgba(79,142,247,0.12) 0%, transparent 60%),
                radial-gradient(ellipse 60% 50% at 80% 85%, rgba(124,58,237,0.10) 0%, transparent 55%),
                radial-gradient(ellipse 50% 40% at 50% 50%, rgba(201,168,76,0.06) 0%, transparent 60%),
                linear-gradient(160deg, #050810 0%, #080D1A 40%, #06091A 100%);
        }

        .bg-orb {
            position: absolute;
            border-radius: 50%;
            filter: blur(90px);
            animation: orbFloat 12s ease-in-out infinite;
            opacity: 0;
            animation-fill-mode: forwards;
        }
        .orb1 {
            width: 500px; height: 500px;
            top: -150px; left: -100px;
            background: radial-gradient(circle, rgba(201,168,76,0.14), transparent 70%);
            animation-delay: 0s; animation-duration: 14s;
        }
        .orb2 {
            width: 400px; height: 400px;
            bottom: -120px; right: -80px;
            background: radial-gradient(circle, rgba(79,142,247,0.12), transparent 70%);
            animation-delay: 3s; animation-duration: 18s;
        }
        .orb3 {
            width: 300px; height: 300px;
            top: 50%; left: 50%;
            transform: translate(-50%,-50%);
            background: radial-gradient(circle, rgba(124,58,237,0.08), transparent 70%);
            animation-delay: 6s; animation-duration: 16s;
        }
        @keyframes orbFloat {
            0%   { opacity: 0; transform: scale(1) translateY(0); }
            10%  { opacity: 1; }
            50%  { transform: scale(1.08) translateY(-20px); }
            90%  { opacity: 1; }
            100% { opacity: 0.6; transform: scale(1) translateY(0); }
        }

        /* Grid lines */
        .bg-grid {
            position: absolute; inset: 0;
            background-image:
                linear-gradient(rgba(201,168,76,0.035) 1px, transparent 1px),
                linear-gradient(90deg, rgba(201,168,76,0.035) 1px, transparent 1px);
            background-size: 60px 60px;
            mask-image: radial-gradient(ellipse 70% 70% at 50% 50%, black 30%, transparent 80%);
        }

        /* ═══ PAGE LAYOUT ═══ */
        .page {
            position: relative; z-index: 10;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 24px 16px;
            padding-bottom: max(24px, env(safe-area-inset-bottom));
        }

        /* ═══ CARD ═══ */
        .card {
            width: 100%;
            max-width: 420px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 36px;
            padding: 44px 36px 38px;
            backdrop-filter: blur(36px) saturate(180%);
            -webkit-backdrop-filter: blur(36px) saturate(180%);
            box-shadow:
                0 0 0 1px rgba(255,255,255,0.04) inset,
                0 40px 80px rgba(0,0,0,0.7),
                0 0 60px rgba(201,168,76,0.05);
            animation: cardIn 0.7s cubic-bezier(0.34, 1.2, 0.64, 1) both;
        }
        @keyframes cardIn {
            from { opacity: 0; transform: translateY(32px) scale(0.97); }
            to   { opacity: 1; transform: translateY(0) scale(1); }
        }

        /* ═══ LOGO ═══ */
        .logo-wrap {
            display: flex;
            flex-direction: column;
            align-items: center;
            margin-bottom: 28px;
        }

        .logo-ring {
            position: relative;
            width: 90px; height: 90px;
            display: flex; align-items: center; justify-content: center;
            margin-bottom: 18px;
        }

        .logo-ring::before {
            content: '';
            position: absolute; inset: -3px;
            border-radius: 50%;
            background: conic-gradient(
                from 0deg,
                var(--gold-dark),
                var(--gold),
                var(--gold-light),
                var(--gold),
                var(--gold-dark),
                #4F8EF7,
                var(--gold-dark)
            );
            animation: spinRing 6s linear infinite;
            z-index: 0;
        }
        .logo-ring::after {
            content: '';
            position: absolute; inset: -1px;
            border-radius: 50%;
            background: var(--bg-deep);
            z-index: 1;
        }
        @keyframes spinRing {
            to { transform: rotate(360deg); }
        }

        .logo-inner {
            position: relative; z-index: 2;
            width: 78px; height: 78px;
            border-radius: 50%;
            overflow: hidden;
            background: #0a0f1e;
            border: 1px solid rgba(201,168,76,0.25);
            box-shadow: 0 0 24px rgba(201,168,76,0.15);
        }

        .logo-inner img {
            width: 100%; height: 100%;
            object-fit: cover;
            display: block;
        }

        /* Fallback icon if logo.png missing */
        .logo-inner .logo-fallback {
            width: 100%; height: 100%;
            display: flex; align-items: center; justify-content: center;
            font-size: 2rem;
            color: var(--gold);
        }

        /* ═══ BRANDING ═══ */
        .brand-title {
            font-size: 1.6rem;
            font-weight: 800;
            letter-spacing: 0.06em;
            text-transform: uppercase;
            background: linear-gradient(135deg, var(--gold-light) 0%, var(--gold) 40%, #fff8e0 60%, var(--gold) 80%, var(--gold-light) 100%);
            background-size: 200% auto;
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            animation: goldShimmer 4s linear infinite;
            text-shadow: none;
            margin-bottom: 4px;
        }
        @keyframes goldShimmer {
            to { background-position: 200% center; }
        }

        .brand-sub {
            font-size: 0.7rem;
            font-weight: 600;
            letter-spacing: 0.28em;
            text-transform: uppercase;
            color: var(--text-muted);
            margin-bottom: 2px;
        }

        /* ═══ DIVIDER ═══ */
        .divider {
            display: flex; align-items: center; gap: 12px;
            margin: 0 0 28px;
            color: rgba(201,168,76,0.3);
            font-size: 0.65rem; letter-spacing: 0.2em; text-transform: uppercase;
        }
        .divider::before, .divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: linear-gradient(90deg, transparent, rgba(201,168,76,0.2), transparent);
        }

        /* ═══ ERROR ═══ */
        .error-box {
            display: flex; align-items: center; gap: 10px;
            background: rgba(255,95,109,0.08);
            border: 1px solid rgba(255,95,109,0.2);
            border-radius: var(--radius-sm);
            padding: 13px 16px;
            margin-bottom: 20px;
            font-size: 0.85rem;
            color: #FF8A95;
            animation: shake 0.4s ease;
        }
        @keyframes shake {
            0%,100%{transform:translateX(0)}
            20%{transform:translateX(-6px)}
            40%{transform:translateX(6px)}
            60%{transform:translateX(-4px)}
            80%{transform:translateX(4px)}
        }

        /* ═══ INPUTS ═══ */
        .field {
            position: relative;
            margin-bottom: 14px;
        }

        .field-icon {
            position: absolute;
            left: 18px; top: 50%;
            transform: translateY(-50%);
            color: var(--gold);
            font-size: 0.95rem;
            z-index: 2;
            opacity: 0.7;
            transition: opacity 0.2s;
            pointer-events: none;
        }

        .input {
            width: 100%;
            height: var(--input-h);
            background: rgba(255,255,255,0.04);
            border: 1px solid rgba(255,255,255,0.08);
            border-radius: var(--radius-sm);
            padding: 0 50px 0 46px;
            color: var(--text-primary);
            font-family: inherit;
            font-size: 0.95rem;
            font-weight: 500;
            letter-spacing: 0.02em;
            transition: border-color 0.25s, box-shadow 0.25s, background 0.25s;
            outline: none;
            -webkit-appearance: none;
        }
        .input::placeholder { color: var(--text-muted); font-weight: 400; }
        .input:focus {
            background: rgba(255,255,255,0.065);
            border-color: rgba(201,168,76,0.45);
            box-shadow: 0 0 0 3px rgba(201,168,76,0.08), 0 4px 16px rgba(0,0,0,0.3);
        }
        .input:focus ~ .field-icon,
        .field:focus-within .field-icon { opacity: 1; }

        .eye-btn {
            position: absolute;
            right: 16px; top: 50%;
            transform: translateY(-50%);
            background: none; border: none;
            color: var(--text-muted);
            font-size: 1rem;
            cursor: pointer;
            padding: 6px;
            z-index: 2;
            transition: color 0.2s;
            line-height: 1;
        }
        .eye-btn:hover { color: var(--gold); }

        /* ═══ REMEMBER ═══ */
        .remember-row {
            display: flex; align-items: center; gap: 12px;
            margin: 18px 0 22px;
            cursor: pointer;
        }
        .remember-row input { display: none; }

        .toggle-track {
            width: 44px; height: 24px;
            background: rgba(255,255,255,0.08);
            border: 1px solid rgba(255,255,255,0.1);
            border-radius: 40px;
            position: relative;
            transition: background 0.25s, border-color 0.25s;
            flex-shrink: 0;
        }
        .toggle-thumb {
            position: absolute;
            width: 18px; height: 18px;
            border-radius: 50%;
            background: rgba(255,255,255,0.3);
            top: 2px; left: 2px;
            transition: transform 0.25s cubic-bezier(0.34,1.5,0.64,1), background 0.25s;
            box-shadow: 0 1px 4px rgba(0,0,0,0.4);
        }
        .remember-row input:checked ~ .toggle-track {
            background: linear-gradient(135deg, var(--gold-dark), var(--gold));
            border-color: var(--gold);
        }
        .remember-row input:checked ~ .toggle-track .toggle-thumb {
            transform: translateX(20px);
            background: white;
            box-shadow: 0 2px 8px rgba(201,168,76,0.5);
        }
        .remember-label {
            font-size: 0.875rem;
            font-weight: 500;
            color: var(--text-muted);
        }

        /* ═══ LOGIN BUTTON ═══ */
        .btn-login {
            width: 100%;
            height: 54px;
            background: linear-gradient(135deg, #b8860b 0%, var(--gold) 35%, var(--gold-light) 55%, var(--gold) 75%, #8B6914 100%);
            background-size: 200% auto;
            border: none;
            border-radius: var(--radius-sm);
            color: #1a1000;
            font-family: inherit;
            font-size: 0.95rem;
            font-weight: 800;
            letter-spacing: 0.12em;
            text-transform: uppercase;
            cursor: pointer;
            transition: background-position 0.4s, transform 0.18s, box-shadow 0.25s;
            box-shadow:
                0 1px 0 rgba(255,255,255,0.25) inset,
                0 10px 30px rgba(201,168,76,0.25),
                0 4px 12px rgba(0,0,0,0.4);
            position: relative;
            overflow: hidden;
        }
        .btn-login::before {
            content: '';
            position: absolute; inset: 0;
            background: linear-gradient(180deg, rgba(255,255,255,0.18) 0%, transparent 50%);
            border-radius: inherit;
        }
        .btn-login:hover {
            background-position: right center;
            box-shadow: 0 1px 0 rgba(255,255,255,0.25) inset, 0 14px 40px rgba(201,168,76,0.35), 0 4px 16px rgba(0,0,0,0.5);
            transform: translateY(-1px);
        }
        .btn-login:active {
            transform: translateY(0) scale(0.99);
            box-shadow: 0 1px 0 rgba(255,255,255,0.2) inset, 0 6px 20px rgba(201,168,76,0.2), 0 2px 8px rgba(0,0,0,0.4);
        }

        .btn-icon { margin-right: 8px; }

        /* ═══ REGISTER LINK ═══ */
        .footer-link {
            margin-top: 22px;
            text-align: center;
        }
        .footer-link a {
            display: inline-flex; align-items: center; gap: 7px;
            font-size: 0.82rem;
            font-weight: 500;
            color: var(--text-muted);
            text-decoration: none;
            letter-spacing: 0.04em;
            padding: 8px 20px;
            border-radius: 40px;
            border: 1px solid rgba(255,255,255,0.06);
            background: rgba(255,255,255,0.025);
            transition: color 0.2s, border-color 0.2s, background 0.2s;
        }
        .footer-link a:hover {
            color: var(--gold);
            border-color: rgba(201,168,76,0.25);
            background: rgba(201,168,76,0.05);
        }
        .footer-link a i { font-size: 0.75rem; }

        /* ═══ BADGE TOP ═══ */
        .vip-badge {
            display: inline-flex; align-items: center; gap: 6px;
            font-size: 0.6rem;
            font-weight: 700;
            letter-spacing: 0.22em;
            text-transform: uppercase;
            color: var(--gold);
            border: 1px solid rgba(201,168,76,0.3);
            border-radius: 40px;
            padding: 4px 12px;
            background: rgba(201,168,76,0.06);
            margin-bottom: 8px;
        }
        .vip-badge i { font-size: 0.55rem; }

        /* ═══ WATERMARK ═══ */
        .watermark {
            position: fixed;
            bottom: 16px; left: 0; right: 0;
            text-align: center;
            font-size: 0.65rem;
            letter-spacing: 0.15em;
            color: rgba(255,255,255,0.08);
            z-index: 5;
        }

        /* ═══ RESPONSIVE ═══ */
        @media(max-width: 460px) {
            .card { padding: 36px 24px 30px; border-radius: 28px; }
            .brand-title { font-size: 1.35rem; }
            .logo-ring { width: 78px; height: 78px; }
            .logo-inner { width: 66px; height: 66px; }
        }

/* ══════════════════════════════════════
   ✦ ONEBOX PANEL — UPGRADED BUTTONS v2
   ══════════════════════════════════════ */

/* ─ PRIMARY / ACTION BUTTON ─ */
.btn-ios,
.btn-save,
.ob-btn {
    position:relative; overflow:hidden;
    display:inline-flex; align-items:center; justify-content:center; gap:9px;
    padding:13px 28px; border:none; border-radius:14px;
    font-family:'Montserrat',sans-serif; font-size:.9rem; font-weight:700;
    letter-spacing:.04em; color:#fff; cursor:pointer;
    background: linear-gradient(135deg, var(--p-primary,#c9a84c) 0%, #e8c96a 50%, var(--p-primary,#c9a84c) 100%);
    background-size:200% 200%;
    box-shadow:0 4px 20px rgba(201,168,76,0.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s ease;
    text-decoration:none; width:auto;
}
.btn-ios::before, .btn-save::before {
    content:''; position:absolute; inset:0;
    background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);
    transform:translateX(-100%); transition:transform .55s ease;
}
.btn-ios:hover::before, .btn-save:hover::before { transform:translateX(100%); }
.btn-ios:hover, .btn-save:hover {
    transform:translateY(-3px) scale(1.02);
    box-shadow:0 12px 32px rgba(201,168,76,.55); color:#fff; text-decoration:none;
}
.btn-ios:active, .btn-save:active { transform:translateY(0) scale(.98); }

/* ─ BLUE PRIMARY (Generate License etc.) ─ */
.generate-btn .btn-ios,
.ob-btn-blue {
    background:linear-gradient(135deg,#2563eb 0%,#38bdf8 60%,#6366f1 100%) !important;
    box-shadow:0 4px 20px rgba(37,99,235,.4) !important;
}
.generate-btn .btn-ios:hover,
.ob-btn-blue:hover { box-shadow:0 12px 32px rgba(37,99,235,.55) !important; }

/* ─ GLASS ACTION BUTTONS ─ */
.glass-btn, .btn-glass {
    position:relative; padding:7px 15px; border-radius:50px;
    font-size:.78rem; font-weight:700; letter-spacing:.03em;
    border:1px solid rgba(255,255,255,.18);
    display:inline-flex; align-items:center; gap:6px;
    transition:transform .22s cubic-bezier(.34,1.5,.64,1),box-shadow .22s ease,background .22s ease;
    cursor:pointer; backdrop-filter:blur(12px); -webkit-backdrop-filter:blur(12px);
    box-shadow:0 2px 10px rgba(0,0,0,.22); background:rgba(255,255,255,.05);
    text-decoration:none; font-family:'Montserrat',sans-serif;
}
.glass-btn:hover, .btn-glass:hover { transform:translateY(-2px) scale(1.06); color:#fff; text-decoration:none; }
.glass-btn:active, .btn-glass:active { transform:scale(.97); }
.glass-btn:disabled, .btn-glass:disabled { opacity:.38; cursor:not-allowed; pointer-events:none; }

.btn-copy-glass { color:#a5b4fc; border-color:rgba(139,92,246,.35); background:rgba(99,102,241,.1); }
.btn-copy-glass:hover { background:rgba(99,102,241,.28); border-color:rgba(139,92,246,.6); box-shadow:0 0 18px rgba(99,102,241,.45); color:#e0e7ff; }

.btn-usage-glass { color:#7dd3fc; border-color:rgba(6,182,212,.35); background:rgba(6,182,212,.1); }
.btn-usage-glass:hover { background:rgba(6,182,212,.28); border-color:rgba(6,182,212,.6); box-shadow:0 0 18px rgba(6,182,212,.45); color:#e0f2fe; }

.btn-edit-glass, .btn-edit {
    color:#fde68a; border-color:rgba(234,179,8,.35); background:rgba(234,179,8,.1);
}
.btn-edit-glass:hover, .btn-edit:hover:not(:disabled) {
    background:rgba(234,179,8,.28); border-color:rgba(234,179,8,.6);
    box-shadow:0 0 18px rgba(234,179,8,.45); color:#fef3c7;
    transform:translateY(-2px) scale(1.06);
}

.btn-delete-glass, .btn-delete {
    color:#fca5a5; border-color:rgba(239,68,68,.35); background:rgba(239,68,68,.1);
}
.btn-delete-glass:hover, .btn-delete:hover:not(:disabled) {
    background:rgba(239,68,68,.28); border-color:rgba(239,68,68,.6);
    box-shadow:0 0 18px rgba(239,68,68,.45); color:#fee2e2;
    transform:translateY(-2px) scale(1.06);
}

/* ─ BACK / SECONDARY ─ */
.btn-back, .ob-btn-secondary {
    position:relative; display:inline-flex; align-items:center; gap:9px;
    padding:11px 26px; background:rgba(255,255,255,.07);
    border:1px solid rgba(255,255,255,.16); border-radius:50px;
    color:rgba(255,255,255,.88); font-size:.85rem; font-weight:600;
    font-family:'Montserrat',sans-serif; text-decoration:none; cursor:pointer;
    transition:all .25s cubic-bezier(.34,1.5,.64,1);
    backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px);
}
.btn-back:hover, .ob-btn-secondary:hover {
    background:rgba(255,255,255,.15); border-color:rgba(255,255,255,.3);
    transform:translateY(-3px); box-shadow:0 10px 24px rgba(0,0,0,.35);
    color:#fff; text-decoration:none;
}

/* ─ MODAL UPGRADE ─ */
.modal-content {
    background:rgba(10,16,34,.97) !important;
    border:1px solid rgba(201,168,76,.2) !important;
    border-radius:20px !important; backdrop-filter:blur(24px) !important;
}
.modal-header { border-bottom:1px solid rgba(255,255,255,.08) !important; }
.modal-footer { border-top:1px solid rgba(255,255,255,.08) !important; gap:10px; }
.modal-title { font-weight:700; color:var(--p-primary,#c9a84c); }

/* ─ FORM INPUT UPGRADE ─ */
.form-control,.form-select {
    background:rgba(0,0,0,.28) !important; border:1px solid rgba(255,255,255,.1) !important;
    color:#fff !important; border-radius:12px !important; padding:11px 14px !important;
    font-family:'Montserrat',sans-serif;
    transition:border-color .22s ease,box-shadow .22s ease;
}
.form-control:focus,.form-select:focus {
    background:rgba(0,0,0,.45) !important;
    border-color:var(--p-primary,#c9a84c) !important;
    box-shadow:0 0 0 3px rgba(201,168,76,.2),0 0 16px rgba(201,168,76,.15) !important;
    color:#fff !important; outline:none !important;
}
.form-control::placeholder { color:rgba(255,255,255,.35) !important; }
.form-select option { background:#1e2a45; color:#fff; }

</style>
</head>
<body>

<!-- Background -->
<div class="bg-wrap">
    <div class="bg-mesh"></div>
    <div class="bg-grid"></div>
    <div class="bg-orb orb1"></div>
    <div class="bg-orb orb2"></div>
    <div class="bg-orb orb3"></div>
</div>

<div class="page">
    <div class="card">

        <!-- Logo + Branding -->
        <div class="logo-wrap">
            <div class="logo-ring">
                <div class="logo-inner">
                    <!-- logo.png global use — falls back to icon if missing -->
                    <img src="logo.png" alt="OneBox Logo"
                         onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="logo-fallback" style="display:none;">
                        <i class="fa-solid fa-box-open"></i>
                    </div>
                </div>
            </div>

            <div class="vip-badge">
                <i class="fa-solid fa-crown"></i>
                <?= htmlspecialchars($P['login_badge_text']) ?>
            </div>

            <div class="brand-title"><?= htmlspecialchars($P['login_title']) ?></div>
            <div class="brand-sub"><?= htmlspecialchars($P['panel_tagline']) ?></div>
        </div>

        <div class="divider"><?= htmlspecialchars($P['login_subtitle']) ?></div>

        <!-- Error -->
        <?php if (isset($error)): ?>
        <div class="error-box">
            <i class="fa-solid fa-circle-exclamation"></i>
            <?php echo htmlspecialchars($error); ?>
        </div>
        <?php endif; ?>

        <!-- Form -->
        <form method="POST" action="" autocomplete="on">

            <div class="field">
                <i class="fa-solid fa-user field-icon"></i>
                <input type="text" name="username" class="input"
                       placeholder="Username" required autocomplete="username">
            </div>

            <div class="field">
                <i class="fa-solid fa-lock field-icon"></i>
                <input type="password" name="password" id="pwField" class="input"
                       placeholder="Password" required autocomplete="current-password">
                <button type="button" class="eye-btn" id="eyeBtn" aria-label="Toggle password">
                    <i class="fa-regular fa-eye" id="eyeIcon"></i>
                </button>
            </div>

            <label class="remember-row">
                <input type="checkbox" name="remember" id="rememberChk">
                <div class="toggle-track">
                    <div class="toggle-thumb"></div>
                </div>
                <span class="remember-label">Keep me signed in (15 min)</span>
            </label>

            <button type="submit" name="login" class="btn-login">
                <i class="fa-solid fa-arrow-right-to-bracket btn-icon"></i>
                Sign In
            </button>

            <div class="footer-link">
                <a href="register.php">
                    <i class="fa-solid fa-user-plus"></i>
                    Create Account
                </a>
            </div>

        </form>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text']) ?></div>

<script>
    const pwField = document.getElementById('pwField');
    const eyeIcon = document.getElementById('eyeIcon');

    document.getElementById('eyeBtn').addEventListener('click', () => {
        const show = pwField.type === 'password';
        pwField.type = show ? 'text' : 'password';
        eyeIcon.className = show ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    });

    // Remember toggle label sync
    document.getElementById('rememberChk').addEventListener('change', function() {
        document.querySelector('.remember-label').textContent =
            this.checked ? 'Keep me signed in (15 min)' : 'Session only (5 min)';
    });
</script>

<script>
/* ✦ ONEBOX Ripple v2 */
(function(){
  document.addEventListener('click',function(e){
    var btn=e.target.closest('.btn-ios,.btn-save,.ob-btn,.glass-btn,.btn-glass,.btn-back');
    if(!btn)return;
    var r=document.createElement('span');
    var d=Math.max(btn.offsetWidth,btn.offsetHeight);
    var rect=btn.getBoundingClientRect();
    r.style.cssText='position:absolute;border-radius:50%;background:rgba(255,255,255,.25);pointer-events:none;transform:scale(0);animation:obRpl .55s linear;width:'+d+'px;height:'+d+'px;left:'+(e.clientX-rect.left-d/2)+'px;top:'+(e.clientY-rect.top-d/2)+'px;';
    btn.style.position='relative';btn.style.overflow='hidden';
    btn.appendChild(r);
    setTimeout(function(){r.remove();},600);
  });
})();
</script>
<style>@keyframes obRpl{to{transform:scale(4);opacity:0;}}</style>
</body>
</html>
