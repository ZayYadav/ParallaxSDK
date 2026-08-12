<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="theme-color" content="#070b14">
    <meta name="color-scheme" content="dark">
    <title><?= esc(BASE_NAME) ?> · <?= esc($title ?? 'Control Center') ?></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap" rel="stylesheet">
    <script src="https://cdn.tailwindcss.com"></script>
    <?= link_tag('assets/css/natacode.css') ?>
    <?= $this->renderSection('css') ?>
    <style>
        :root {
            --bg: #070b14;
            --surface: rgba(15, 23, 42, .78);
            --surface-strong: #111827;
            --line: rgba(148, 163, 184, .15);
            --muted: #94a3b8;
            --text: #f8fafc;
            --brand: #f7b84b;
            --brand-2: #ff8a3d;
            --success: #34d399;
            --danger: #fb7185;
        }
        * { box-sizing: border-box; }
        html { scroll-behavior: smooth; }
        body {
            min-height: 100vh;
            margin: 0;
            color: var(--text);
            font-family: Inter, ui-sans-serif, system-ui, sans-serif;
            background:
                radial-gradient(circle at 12% 0%, rgba(247, 184, 75, .12), transparent 30rem),
                radial-gradient(circle at 100% 10%, rgba(59, 130, 246, .10), transparent 34rem),
                var(--bg);
            overflow-x: hidden;
        }
        body::before {
            content: "";
            position: fixed;
            inset: 0;
            pointer-events: none;
            opacity: .16;
            background-image: linear-gradient(rgba(255,255,255,.025) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.025) 1px, transparent 1px);
            background-size: 32px 32px;
            mask-image: linear-gradient(to bottom, #000, transparent 80%);
        }
        h1, h2, h3, .font-display { font-family: "Space Grotesk", Inter, sans-serif; }
        a { text-decoration: none; }
        .page-wrap { width: min(1440px, 100%); margin-inline: auto; padding: 2rem 1.25rem 4rem; }
        .surface {
            background: var(--surface);
            border: 1px solid var(--line);
            border-radius: 1.25rem;
            box-shadow: 0 24px 70px rgba(0,0,0,.28);
            backdrop-filter: blur(18px);
        }
        .surface-soft { background: rgba(15, 23, 42, .48); border: 1px solid var(--line); border-radius: 1rem; }
        .eyebrow { color: var(--brand); font-size: .72rem; font-weight: 700; letter-spacing: .18em; text-transform: uppercase; }
        .muted { color: var(--muted); }
        .btn-primary, .btn-secondary, .btn-danger {
            display: inline-flex; align-items: center; justify-content: center; gap: .5rem;
            min-height: 2.75rem; padding: .65rem 1rem; border-radius: .8rem;
            font-size: .875rem; font-weight: 700; transition: .18s ease; cursor: pointer;
        }
        .btn-primary { color: #16100a; background: linear-gradient(135deg, var(--brand), var(--brand-2)); border: 0; box-shadow: 0 10px 26px rgba(247,184,75,.2); }
        .btn-primary:hover { transform: translateY(-1px); filter: brightness(1.06); }
        .btn-secondary { color: #e2e8f0; background: rgba(30,41,59,.7); border: 1px solid var(--line); }
        .btn-secondary:hover { background: rgba(51,65,85,.85); border-color: rgba(247,184,75,.35); }
        .btn-danger { color: #fecdd3; background: rgba(190,24,93,.12); border: 1px solid rgba(251,113,133,.24); }
        .btn-danger:hover { background: rgba(190,24,93,.22); }
        .field {
            width: 100%; min-height: 2.9rem; padding: .72rem .85rem;
            color: #f8fafc; background: rgba(2,6,23,.72); border: 1px solid var(--line);
            border-radius: .8rem; outline: none; transition: .18s ease;
        }
        .field:focus { border-color: rgba(247,184,75,.62); box-shadow: 0 0 0 3px rgba(247,184,75,.10); }
        .field::placeholder { color: #64748b; }
        .label { display: block; margin-bottom: .45rem; color: #cbd5e1; font-size: .78rem; font-weight: 700; }
        .badge { display: inline-flex; align-items: center; gap: .35rem; padding: .3rem .58rem; border-radius: 999px; font-size: .72rem; font-weight: 700; }
        .badge-success { color: #a7f3d0; background: rgba(16,185,129,.12); border: 1px solid rgba(52,211,153,.2); }
        .badge-danger { color: #fecdd3; background: rgba(244,63,94,.12); border: 1px solid rgba(251,113,133,.2); }
        .badge-muted { color: #cbd5e1; background: rgba(100,116,139,.12); border: 1px solid rgba(148,163,184,.16); }
        ::selection { color: #111827; background: var(--brand); }
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-thumb { background: #334155; border-radius: 999px; }
        @media (max-width: 640px) { .page-wrap { padding: 1.25rem .8rem 3rem; } }
    </style>
</head>
<body>
    <?= $this->include('Layout/Header') ?>
    <main class="relative z-10">
        <?= $this->renderSection('content') ?>
    </main>
    <footer class="relative z-10 border-t border-white/10 py-6">
        <div class="mx-auto flex w-full max-w-7xl flex-col items-center justify-between gap-2 px-5 text-xs text-slate-500 sm:flex-row">
            <p>© <?= date('Y') ?> <?= esc(BASE_NAME) ?>. Private control center.</p>
            <p><span class="mr-1 inline-block h-2 w-2 rounded-full bg-emerald-400"></span> Systems operational</p>
        </div>
    </footer>
    <script src="https://cdn.jsdelivr.net/npm/sweetalert2@11"></script>
    <?= script_tag('assets/js/natacode.js') ?>
    <?= $this->renderSection('js') ?>
</body>
</html>
