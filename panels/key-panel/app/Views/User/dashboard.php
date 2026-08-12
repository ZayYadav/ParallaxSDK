<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>
<?php
$metrics = $dashboardMetrics ?? [];
$expiration = !empty($user->expiration_date) ? strtotime($user->expiration_date) : null;
$daysRemaining = $expiration ? max(0, (int) ceil(($expiration - time()) / 86400)) : null;
?>
<div class="page-wrap space-y-6">
    <section class="surface overflow-hidden p-6 sm:p-8">
        <div class="flex flex-col justify-between gap-6 lg:flex-row lg:items-center">
            <div>
                <p class="eyebrow">Control center</p>
                <h1 class="mt-2 text-3xl font-bold text-white sm:text-4xl">Welcome, <?= esc($user->fullname ?: $user->username) ?></h1>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-400">Manage current OneCore activations and legacy licences from a single responsive workspace.</p>
            </div>
            <div class="flex flex-wrap gap-2">
                <?php if ((int) ($user->level ?? 99) === 1) : ?>
                    <a href="<?= site_url('licenses') ?>" class="btn-primary">OneCore licences</a>
                <?php endif; ?>
                <a href="<?= site_url('keys') ?>" class="btn-secondary">Legacy keys</a>
                <a href="<?= site_url('keys/generate') ?>" class="btn-secondary">Generate</a>
            </div>
        </div>
    </section>

    <?= $this->include('Layout/msgStatus') ?>

    <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <?php
        $cards = [
            ['label' => 'Legacy keys', 'value' => $metrics['total_keys'] ?? 0, 'hint' => 'Visible inventory', 'color' => 'text-amber-300'],
            ['label' => 'Enabled', 'value' => $metrics['enabled_keys'] ?? 0, 'hint' => 'Ready or in use', 'color' => 'text-emerald-300'],
            ['label' => 'Connected', 'value' => $metrics['connected_keys'] ?? 0, 'hint' => 'Has device binding', 'color' => 'text-sky-300'],
            ['label' => 'Unused', 'value' => $metrics['available_keys'] ?? 0, 'hint' => 'No device registered', 'color' => 'text-violet-300'],
        ];
        foreach ($cards as $card) : ?>
            <article class="surface-soft p-5 sm:p-6">
                <p class="text-xs font-semibold uppercase tracking-[.14em] text-slate-500"><?= esc($card['label']) ?></p>
                <p class="mt-3 font-display text-3xl font-bold <?= esc($card['color']) ?>"><?= number_format((int) $card['value']) ?></p>
                <p class="mt-2 text-xs text-slate-500"><?= esc($card['hint']) ?></p>
            </article>
        <?php endforeach; ?>
    </section>

    <section class="grid gap-6 lg:grid-cols-[1.2fr_.8fr]">
        <article class="surface overflow-hidden">
            <div class="border-b border-white/10 p-5 sm:p-6">
                <p class="eyebrow">Audit trail</p>
                <h2 class="mt-2 text-xl font-semibold text-white">Recent key activity</h2>
            </div>
            <?php if (empty($history)) : ?>
                <div class="p-10 text-center text-sm text-slate-500">No recent activity in your visible scope.</div>
            <?php else : ?>
                <div class="divide-y divide-white/5">
                    <?php foreach ($history as $item) :
                        $parts = array_pad(explode('|', (string) ($item->info ?? ''), 4), 4, '-');
                    ?>
                        <div class="flex flex-col justify-between gap-3 p-5 transition hover:bg-white/[.02] sm:flex-row sm:items-center sm:px-6">
                            <div>
                                <div class="flex flex-wrap items-center gap-2">
                                    <span class="badge badge-muted">#<?= (int) ($item->id_history ?? 0) ?></span>
                                    <span class="font-semibold text-white"><?= esc($parts[0]) ?></span>
                                    <code class="rounded bg-slate-950/70 px-2 py-1 text-xs text-amber-200"><?= esc($parts[1]) ?>...</code>
                                </div>
                                <p class="mt-2 text-xs text-slate-500">Created by <?= esc($item->user_do ?? '-') ?> - <?= esc($item->created_at ?? '') ?></p>
                            </div>
                            <p class="text-sm text-slate-400"><?= esc($parts[2]) ?>h / <?= esc($parts[3]) ?> device(s)</p>
                        </div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </article>

        <aside class="space-y-6">
            <article class="surface p-5 sm:p-6">
                <p class="eyebrow">Account</p>
                <h2 class="mt-2 text-xl font-semibold text-white"><?= esc(getLevel($user->level)) ?></h2>
                <dl class="mt-5 space-y-4 text-sm">
                    <div class="flex items-center justify-between gap-4"><dt class="text-slate-500">Username</dt><dd class="font-medium text-white"><?= esc($user->username) ?></dd></div>
                    <div class="flex items-center justify-between gap-4"><dt class="text-slate-500">Balance</dt><dd class="font-medium text-amber-300">$<?= number_format((float) $user->saldo, 2) ?></dd></div>
                    <?php if (($metrics['users'] ?? null) !== null) : ?><div class="flex items-center justify-between gap-4"><dt class="text-slate-500">Panel users</dt><dd class="font-medium text-white"><?= number_format((int) $metrics['users']) ?></dd></div><?php endif; ?>
                    <div class="flex items-center justify-between gap-4"><dt class="text-slate-500">Account expiry</dt><dd class="text-right font-medium text-white"><?= $expiration ? esc(gmdate('d M Y', $expiration)) : 'Not set' ?></dd></div>
                </dl>
                <?php if ($daysRemaining !== null) : ?>
                    <div class="mt-5 rounded-xl border border-white/10 bg-slate-950/40 p-4">
                        <p class="text-xs text-slate-500">Time remaining</p>
                        <p class="mt-1 font-display text-2xl font-bold <?= $daysRemaining > 7 ? 'text-emerald-300' : 'text-rose-300' ?>"><?= $daysRemaining ?> day(s)</p>
                    </div>
                <?php endif; ?>
            </article>

            <article class="surface-soft p-5 sm:p-6">
                <p class="font-semibold text-white">Which licence page should I use?</p>
                <p class="mt-2 text-sm leading-6 text-slate-400"><strong class="text-amber-200">OneCore licences</strong> create <code>OC-...</code> activation keys for the current APK. Legacy keys remain available for older clients.</p>
            </article>
        </aside>
    </section>
</div>
<?= $this->endSection() ?>
