<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="theme-color" content="#070b14">
    <title><?= esc(BASE_NAME) ?> · Activation portal</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@600;700&display=swap" rel="stylesheet">
    <style>
        *{box-sizing:border-box}body{min-height:100vh;margin:0;display:grid;place-items:center;padding:24px;color:#f8fafc;font-family:Inter,sans-serif;background:radial-gradient(circle at 15% 5%,rgba(247,184,75,.16),transparent 32rem),radial-gradient(circle at 90% 90%,rgba(59,130,246,.12),transparent 28rem),#070b14}.shell{width:min(100%,760px);padding:clamp(24px,5vw,54px);border:1px solid rgba(148,163,184,.16);border-radius:28px;background:rgba(15,23,42,.8);box-shadow:0 32px 100px rgba(0,0,0,.4);backdrop-filter:blur(20px)}.brand{display:flex;align-items:center;gap:12px}.logo{display:grid;width:46px;height:46px;place-items:center;border-radius:14px;color:#17110a;font:700 22px 'Space Grotesk';background:linear-gradient(135deg,#f7c75f,#ff833d)}.eyebrow{margin:0;color:#f7b84b;font-size:11px;font-weight:700;letter-spacing:.18em;text-transform:uppercase}h1{margin:28px 0 12px;font:700 clamp(34px,7vw,58px)/1.02 'Space Grotesk';letter-spacing:-.04em}p{color:#94a3b8;line-height:1.7}.steps{display:grid;gap:12px;margin:30px 0}.step{display:flex;gap:14px;padding:16px;border:1px solid rgba(148,163,184,.12);border-radius:16px;background:rgba(2,6,23,.42)}.num{display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;border-radius:10px;color:#f7b84b;background:rgba(247,184,75,.1);font-weight:700}.step strong{display:block;margin-bottom:4px;color:#fff}.step span{color:#94a3b8;font-size:13px;line-height:1.5}.actions{display:flex;flex-wrap:wrap;gap:12px}.button{display:inline-flex;min-height:46px;align-items:center;justify-content:center;padding:0 18px;border-radius:13px;font-weight:700;text-decoration:none}.primary{color:#17110a;background:linear-gradient(135deg,#f7c75f,#ff833d)}.secondary{color:#e2e8f0;border:1px solid rgba(148,163,184,.18);background:rgba(30,41,59,.6)}.note{margin-top:24px;padding-top:20px;border-top:1px solid rgba(148,163,184,.12);font-size:12px}@media(max-width:520px){.actions .button{width:100%}}
    </style>
</head>
<body>
    <main class="shell">
        <div class="brand"><span class="logo">P</span><div><p class="eyebrow"><?= esc(BASE_NAME) ?></p><strong>Secure activation portal</strong></div></div>
        <h1>Unlock your OneCore access.</h1>
        <p>OneCore uses device-bound activation keys. Purchase or request a key only from an authorized Parallax reseller, then paste it into the APK.</p>
        <section class="steps" aria-label="Activation steps">
            <div class="step"><span class="num">1</span><div><strong>Get an OC key</strong><span>Valid keys begin with <code>OC-</code> and are displayed only once when issued.</span></div></div>
            <div class="step"><span class="num">2</span><div><strong>Paste it in the APK</strong><span>The official app securely binds the key to the device identity during verification.</span></div></div>
            <div class="step"><span class="num">3</span><div><strong>Keep it private</strong><span>Do not post activation keys publicly or install builds from untrusted sources.</span></div></div>
        </section>
        <div class="actions">
            <?php if ($isSignedIn) : ?><a class="button primary" href="<?= site_url('licenses') ?>">Manage licenses</a><?php else : ?><a class="button primary" href="<?= site_url('login') ?>">Reseller sign in</a><?php endif; ?>
            <a class="button secondary" href="mailto:support@parallaxserver.online">Contact support</a>
        </div>
        <p class="note">This page never asks for your device password, OTP, payment PIN, or private account credentials.</p>
    </main>
</body>
</html>
