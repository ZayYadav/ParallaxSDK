<?php
session_start();
include('conn.php');
include('panel_helper.php');
$P = get_panel_settings($conn);

// Handle registration
if (isset($_POST['register'])) {
    $username = trim((string) ($_POST['username'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');
    $referral = strtoupper(trim((string) ($_POST['referral_code'] ?? '')));

    if (!panel_rate_limit($conn, 'register|' . panel_client_ip(), 5)) {
        $reg_error = 'Too many attempts. Wait one minute and try again.';
    } elseif (preg_match('/^[A-Za-z0-9_.-]{3,32}$/D', $username) !== 1) {
        $reg_error = 'Username must be 3-32 safe characters.';
    } elseif (strlen($password) < 10 || strlen($password) > 128) {
        $reg_error = 'Password must be 10-128 characters.';
    } elseif (preg_match('/^[A-Z0-9-]{4,64}$/D', $referral) !== 1) {
        $reg_error = "Invalid or already used referral code!";
    } else {
        $conn->begin_transaction();
        $stmt = $conn->prepare('SELECT id FROM referral_codes WHERE code = ? AND used_by IS NULL FOR UPDATE');
        $stmt->bind_param('s', $referral);
        $stmt->execute();
        $referralRow = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$referralRow) {
            $conn->rollback();
            $reg_error = 'Invalid or already used referral code!';
        } else {
        $hashed = password_hash($password, PASSWORD_DEFAULT);
            $insert = $conn->prepare('INSERT INTO users (username, password) VALUES (?, ?)');
            $insert->bind_param('ss', $username, $hashed);
            $insertOk = $insert->execute();
            $insert->close();
        if ($insertOk) {
            $user_id = mysqli_insert_id($conn);
                $update = $conn->prepare('UPDATE referral_codes SET used_by = ? WHERE id = ? AND used_by IS NULL');
                $referralId = (int) $referralRow['id'];
                $update->bind_param('ii', $user_id, $referralId);
                $update->execute();
                $claimed = $update->affected_rows === 1;
                $update->close();
                if (!$claimed) {
                    $conn->rollback();
                    $reg_error = 'Referral code was already used.';
                } else {
                    $conn->commit();
            header("Location: login.php?registered=1");
            exit();
                }
        } else {
                $conn->rollback();
            $reg_error = "Username already exists!";
        }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<title><?= htmlspecialchars($P['panel_name']) ?> · Register</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Montserrat:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css" rel="stylesheet">
<style>
:root {
    --gold: <?= $P['theme_primary'] ?>;
    --gold-light: #F0D080;
    --accent: <?= $P['theme_accent'] ?>;
    --bg-deep: <?= $P['theme_bg1'] ?>;
    --bg-card: rgba(12, 16, 28, 0.82);
    --border: rgba(201,168,76,0.18);
    --text-primary: #F0EDE8;
    --text-muted: rgba(240,237,232,0.45);
    --error: #FF5F6D;
    --radius: 26px;
    --radius-sm: 14px;
    --input-h: 56px;
}
*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; -webkit-font-smoothing:antialiased; }
html, body { height:100%; font-family:'Montserrat', -apple-system, BlinkMacSystemFont, sans-serif; background:var(--bg-deep); color:var(--text-primary); overflow:hidden; }

/* Background layers */
.bg-wrap { position:fixed; inset:0; z-index:0; overflow:hidden; }
.bg-mesh {
    position:absolute; inset:0;
    background:
        radial-gradient(ellipse 80% 60% at 20% 10%, rgba(79,142,247,0.12) 0%, transparent 60%),
        radial-gradient(ellipse 60% 50% at 80% 85%, rgba(124,58,237,0.10) 0%, transparent 55%),
        radial-gradient(ellipse 50% 40% at 50% 50%, rgba(201,168,76,0.06) 0%, transparent 60%),
        linear-gradient(160deg, #050810 0%, #080D1A 40%, #06091A 100%);
}
.bg-orb { position:absolute; border-radius:50%; filter:blur(90px); animation:orbFloat 12s ease-in-out infinite; opacity:0; animation-fill-mode:forwards; }
.orb1 { width:500px; height:500px; top:-150px; left:-100px; background:radial-gradient(circle, rgba(201,168,76,0.14), transparent 70%); animation-delay:0s; animation-duration:14s; }
.orb2 { width:400px; height:400px; bottom:-120px; right:-80px; background:radial-gradient(circle, rgba(79,142,247,0.12), transparent 70%); animation-delay:3s; animation-duration:18s; }
.orb3 { width:300px; height:300px; top:50%; left:50%; transform:translate(-50%,-50%); background:radial-gradient(circle, rgba(124,58,237,0.08), transparent 70%); animation-delay:6s; animation-duration:16s; }
@keyframes orbFloat { 0%{opacity:0;transform:scale(1) translateY(0)} 10%{opacity:1} 50%{transform:scale(1.08) translateY(-20px)} 90%{opacity:1} 100%{opacity:0.6;transform:scale(1) translateY(0)} }
.bg-grid { position:absolute; inset:0; background-image:linear-gradient(rgba(201,168,76,0.035) 1px, transparent 1px),linear-gradient(90deg, rgba(201,168,76,0.035) 1px, transparent 1px); background-size:60px 60px; mask-image:radial-gradient(ellipse 70% 70% at 50% 50%, black 30%, transparent 80%); }

/* Page layout */
.page { position:relative; z-index:10; min-height:100vh; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:24px 16px; overflow-y:auto; }

/* Card */
.card {
    width:100%; max-width:420px;
    background:var(--bg-card);
    border:1px solid var(--border);
    border-radius:36px;
    padding:44px 36px 38px;
    backdrop-filter:blur(36px) saturate(180%);
    -webkit-backdrop-filter:blur(36px) saturate(180%);
    box-shadow: 0 0 0 1px rgba(255,255,255,0.04) inset, 0 40px 80px rgba(0,0,0,0.7), 0 0 60px rgba(201,168,76,0.05);
    animation:cardIn 0.7s cubic-bezier(0.34, 1.2, 0.64, 1) both;
}
@keyframes cardIn { from{opacity:0;transform:translateY(32px) scale(0.97)} to{opacity:1;transform:translateY(0) scale(1)} }

/* Logo */
.logo-wrap { display:flex; flex-direction:column; align-items:center; margin-bottom:28px; }
.logo-ring { position:relative; width:90px; height:90px; display:flex; align-items:center; justify-content:center; margin-bottom:18px; }
.logo-ring::before { content:''; position:absolute; inset:-3px; border-radius:50%; background:conic-gradient(from 0deg, var(--gold), var(--accent), var(--gold-light), var(--gold)); animation:spinRing 4s linear infinite; }
@keyframes spinRing { to { transform:rotate(360deg); } }
.logo-ring::after { content:''; position:absolute; inset:-1px; border-radius:50%; background:var(--bg-deep); }
.logo-img { position:relative; z-index:1; width:78px; height:78px; border-radius:50%; object-fit:cover; background:rgba(255,255,255,.05); }
.logo-fallback { position:relative; z-index:1; font-size:2rem; color:var(--gold); }

.brand-badge { display:inline-flex; align-items:center; gap:7px; background:rgba(201,168,76,0.1); border:1px solid rgba(201,168,76,0.22); border-radius:50px; padding:6px 16px; margin-bottom:12px; }
.brand-badge i { color:var(--gold); font-size:.8rem; }
.brand-badge span { font-size:.72rem; font-weight:700; letter-spacing:.18em; color:var(--gold); text-transform:uppercase; }

.page-heading { font-size:1.85rem; font-weight:800; letter-spacing:.04em; text-align:center; margin-bottom:4px; background:linear-gradient(135deg, var(--gold) 0%, var(--gold-light) 50%, var(--gold) 100%); background-size:200% auto; -webkit-background-clip:text; -webkit-text-fill-color:transparent; background-clip:text; animation:shimmer 4s linear infinite; }
@keyframes shimmer { to { background-position:200% center; } }
.page-sub { font-size:.82rem; color:var(--text-muted); text-align:center; margin-bottom:32px; }

/* Inputs */
.input-group-wrap { position:relative; margin-bottom:18px; }
.input-icon { position:absolute; left:18px; top:50%; transform:translateY(-50%); color:rgba(255,255,255,.4); font-size:1rem; z-index:2; pointer-events:none; transition:color .2s; }
.form-input {
    width:100%; height:var(--input-h);
    background:rgba(255,255,255,0.05);
    border:1.5px solid rgba(255,255,255,0.1);
    border-radius:var(--radius-sm);
    padding:0 48px 0 48px;
    color:var(--text-primary);
    font-family:'Montserrat',sans-serif;
    font-size:.92rem; font-weight:500;
    transition:border-color .22s, box-shadow .22s, background .22s;
    -webkit-font-smoothing:antialiased;
}
.form-input:focus { outline:none; background:rgba(255,255,255,.08); border-color:var(--gold); box-shadow:0 0 0 3px rgba(201,168,76,.15), 0 0 16px rgba(201,168,76,.1); }
.form-input:focus + .input-icon, .input-group-wrap:focus-within .input-icon { color:var(--gold); }
.form-input::placeholder { color:rgba(255,255,255,.28); font-weight:400; }

/* Eye toggle */
.eye-btn { position:absolute; right:16px; top:50%; transform:translateY(-50%); background:none; border:none; color:rgba(255,255,255,.35); cursor:pointer; font-size:1rem; transition:color .2s; z-index:2; }
.eye-btn:hover { color:var(--gold); }

/* Error */
.error-bar { display:flex; align-items:center; gap:10px; background:rgba(255,95,109,0.12); border:1px solid rgba(255,95,109,0.3); border-radius:12px; padding:12px 16px; margin-bottom:20px; font-size:.82rem; color:#ff8f96; }
.error-bar i { flex-shrink:0; }

/* Submit button */
.btn-submit {
    position:relative; overflow:hidden;
    width:100%; height:54px; border:none; border-radius:var(--radius-sm);
    background:linear-gradient(135deg, var(--gold) 0%, var(--gold-light) 50%, var(--gold) 100%);
    background-size:200% 200%;
    color:#1a1000; font-family:'Montserrat',sans-serif;
    font-size:.95rem; font-weight:800; letter-spacing:.06em;
    cursor:pointer; margin-top:8px;
    box-shadow:0 4px 24px rgba(201,168,76,.35);
    transition:transform .25s cubic-bezier(.34,1.5,.64,1), box-shadow .25s;
    text-transform:uppercase;
}
.btn-submit::before { content:''; position:absolute; inset:0; background:linear-gradient(90deg,transparent,rgba(255,255,255,.35),transparent); transform:translateX(-100%); transition:transform .6s ease; }
.btn-submit:hover { transform:translateY(-3px) scale(1.02); box-shadow:0 12px 32px rgba(201,168,76,.55); }
.btn-submit:hover::before { transform:translateX(100%); }
.btn-submit:active { transform:translateY(0) scale(.98); }

/* Divider */
.divider { display:flex; align-items:center; gap:12px; margin:20px 0; }
.divider::before,.divider::after { content:''; flex:1; height:1px; background:rgba(255,255,255,0.08); }
.divider span { font-size:.7rem; color:rgba(255,255,255,.22); font-weight:600; letter-spacing:.1em; }

/* Login link */
.login-link { text-align:center; margin-top:18px; }
.login-link a { display:inline-flex; align-items:center; gap:8px; color:rgba(255,255,255,.55); font-size:.82rem; font-weight:600; text-decoration:none; padding:8px 20px; border-radius:50px; background:rgba(255,255,255,.04); border:1px solid rgba(255,255,255,.1); transition:all .25s; }
.login-link a:hover { background:rgba(201,168,76,.1); border-color:rgba(201,168,76,.3); color:var(--gold); }

/* Watermark */
.watermark { position:fixed; bottom:12px; left:0; right:0; text-align:center; font-size:.6rem; letter-spacing:.15em; text-transform:uppercase; color:rgba(255,255,255,0.07); z-index:1; pointer-events:none; }
</style>
</head>
<body>

<div class="bg-wrap">
    <div class="bg-mesh"></div>
    <div class="bg-orb orb1"></div>
    <div class="bg-orb orb2"></div>
    <div class="bg-orb orb3"></div>
    <div class="bg-grid"></div>
</div>

<div class="page">
    <div class="card">
        <div class="logo-wrap">
            <div class="logo-ring">
                <img class="logo-img" src="<?= htmlspecialchars($P['sidebar_logo_url']) ?>" alt="Logo"
                     onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling.style.display='block';">
                <i class="fas fa-box-open logo-fallback" style="display:none;"></i>
            </div>
            <div class="brand-badge">
                <i class="fas fa-shield-halved"></i>
                <span><?= htmlspecialchars($P['login_badge_text'] ?? $P['panel_name']) ?></span>
            </div>
        </div>

        <div class="page-heading">Create Account</div>
        <div class="page-sub">Register with your referral code</div>

        <?php if (!empty($reg_error)): ?>
        <div class="error-bar">
            <i class="fas fa-circle-exclamation"></i>
            <?= htmlspecialchars($reg_error) ?>
        </div>
        <?php endif; ?>

        <?php if (isset($_GET['registered'])): ?>
        <div class="error-bar" style="background:rgba(52,211,153,.12);border-color:rgba(52,211,153,.3);color:#34d399;">
            <i class="fas fa-circle-check"></i> Account created! You can now login.
        </div>
        <?php endif; ?>

        <form method="POST" action="">
            <div class="input-group-wrap">
                <input type="text" name="username" class="form-input" placeholder="Username" required autocomplete="username">
                <i class="fas fa-user input-icon"></i>
            </div>

            <div class="input-group-wrap">
                <input type="password" name="password" id="passwordField" class="form-input" placeholder="Password" required autocomplete="new-password">
                <i class="fas fa-lock input-icon"></i>
                <button type="button" class="eye-btn" id="eyeBtn" onclick="togglePwd()" aria-label="Toggle password">
                    <i class="fas fa-eye" id="eyeIcon"></i>
                </button>
            </div>

            <div class="input-group-wrap">
                <input type="text" name="referral_code" class="form-input" placeholder="Referral Code" required autocomplete="off" style="text-transform:uppercase;" oninput="this.value=this.value.toUpperCase()">
                <i class="fas fa-ticket input-icon"></i>
            </div>

            <button type="submit" name="register" class="btn-submit">
                <i class="fas fa-user-plus me-2"></i> Register
            </button>
        </form>

        <div class="divider"><span>OR</span></div>

        <div class="login-link">
            <a href="login.php">
                <i class="fas fa-right-to-bracket"></i>
                Already have an account? Sign in
            </a>
        </div>
    </div>
</div>

<div class="watermark"><?= htmlspecialchars($P['watermark_text']) ?></div>

<script>
function togglePwd() {
    const f = document.getElementById('passwordField');
    const i = document.getElementById('eyeIcon');
    if (f.type === 'password') {
        f.type = 'text';
        i.classList.replace('fa-eye','fa-eye-slash');
    } else {
        f.type = 'password';
        i.classList.replace('fa-eye-slash','fa-eye');
    }
}
</script>
</body>
</html>
