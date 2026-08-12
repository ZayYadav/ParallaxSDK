<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>
<?php
$createdKeys = session()->getFlashdata('created_activation_keys') ?? [];
$now = time();
?>
<div class="page-wrap space-y-6">
    <section class="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div>
            <p class="eyebrow">OneCore licensing</p>
            <h1 class="mt-2 text-3xl font-bold text-white sm:text-4xl">Activation control center</h1>
            <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-400">Create APK-compatible <code class="rounded bg-white/5 px-1.5 py-1 text-amber-300">OC-…</code> activation keys, monitor device usage, and revoke access immediately.</p>
        </div>
        <a href="<?= site_url('keys') ?>" class="btn-secondary">Open legacy-key database</a>
    </section>

    <?= $this->include('Layout/msgStatus') ?>

    <?php if ($configurationError) : ?>
        <section class="surface border-rose-400/20 p-5 sm:p-6">
            <p class="eyebrow text-rose-300">Configuration required</p>
            <h2 class="mt-2 text-xl font-bold text-white">Integrity database connection unavailable</h2>
            <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-400"><?= esc($configurationError) ?> Add the <code class="text-amber-300">database.integrity.*</code> values from the included <code class="text-amber-300">.env.onecore.example</code>, then refresh this page.</p>
        </section>
    <?php else : ?>
        <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <?php foreach ([
                ['Total licenses', $metrics['total'], 'text-white'],
                ['Active', $metrics['active'], 'text-emerald-300'],
                ['Expired', $metrics['expired'], 'text-amber-300'],
                ['Revoked', $metrics['revoked'], 'text-rose-300'],
            ] as $metric) : ?>
                <article class="surface p-5">
                    <p class="text-xs font-semibold uppercase tracking-[.14em] text-slate-500"><?= esc($metric[0]) ?></p>
                    <p class="mt-3 text-3xl font-bold <?= $metric[2] ?>"><?= number_format((int) $metric[1]) ?></p>
                </article>
            <?php endforeach; ?>
        </section>

        <?php if ($createdKeys) : ?>
            <section class="surface border-amber-300/25 p-5 sm:p-6" id="new-key-panel">
                <div class="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                    <div>
                        <p class="eyebrow">Shown once</p>
                        <h2 class="mt-2 text-xl font-bold text-white">Copy the new activation key<?= count($createdKeys) === 1 ? '' : 's' ?> now</h2>
                        <p class="mt-1 text-sm text-slate-400">Only SHA-256 hashes are stored. These plaintext values cannot be recovered later.</p>
                    </div>
                    <button type="button" class="btn-primary" data-copy-target="created-keys">Copy all</button>
                </div>
                <textarea id="created-keys" class="field mt-5 min-h-32 resize-y font-mono text-sm leading-7 text-amber-200" readonly><?= esc(implode("\n", $createdKeys)) ?></textarea>
            </section>
        <?php endif; ?>

        <section class="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
            <article class="surface h-fit p-5 sm:p-6">
                <p class="eyebrow">Issue access</p>
                <h2 class="mt-2 text-xl font-bold text-white">Create activation keys</h2>
                <form action="<?= site_url('licenses/create') ?>" method="post" class="mt-6 space-y-4">
                    <?= csrf_field() ?>
                    <div>
                        <label class="label" for="license-label">Customer or batch label</label>
                        <input class="field" id="license-label" name="label" value="<?= esc(old('label') ?: 'Customer') ?>" maxlength="100" required>
                    </div>
                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <label class="label" for="license-devices">Max devices</label>
                            <input class="field" id="license-devices" name="max_devices" type="number" min="1" max="100" value="<?= esc(old('max_devices') ?: '1') ?>" required>
                        </div>
                        <div>
                            <label class="label" for="license-days">Valid days</label>
                            <input class="field" id="license-days" name="valid_days" type="number" min="1" max="3650" value="<?= esc(old('valid_days') ?: '30') ?>" required>
                        </div>
                    </div>
                    <div>
                        <label class="label" for="license-quantity">Quantity</label>
                        <select class="field" id="license-quantity" name="quantity">
                            <?php foreach ([1, 5, 10, 25, 50, 100] as $quantity) : ?>
                                <option value="<?= $quantity ?>" <?= (int) old('quantity') === $quantity ? 'selected' : '' ?>><?= $quantity ?></option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                    <button class="btn-primary w-full" type="submit">Generate secure keys</button>
                    <p class="text-xs leading-5 text-slate-500">Format: OC + 128-bit cryptographic randomness. A key is bound to the first approved Android device that activates it.</p>
                </form>
            </article>

            <article class="surface min-w-0 overflow-hidden">
                <div class="flex flex-col justify-between gap-4 border-b border-white/10 p-5 sm:flex-row sm:items-center sm:p-6">
                    <div>
                        <p class="eyebrow">Live inventory</p>
                        <h2 class="mt-2 text-xl font-bold text-white">License view</h2>
                    </div>
                    <label class="relative block sm:w-72">
                        <span class="sr-only">Search licenses</span>
                        <input id="license-search" class="field pl-10" type="search" placeholder="Search prefix or label">
                        <span class="pointer-events-none absolute left-3 top-3 text-slate-500">⌕</span>
                    </label>
                </div>

                <?php if (!$licenses) : ?>
                    <div class="p-12 text-center">
                        <p class="text-lg font-semibold text-white">No activation keys yet</p>
                        <p class="mt-2 text-sm text-slate-500">Create the first OneCore-compatible key using the form.</p>
                    </div>
                <?php else : ?>
                    <div class="hidden overflow-x-auto md:block">
                        <table class="w-full text-left text-sm">
                            <thead class="bg-white/[.025] text-[11px] uppercase tracking-[.12em] text-slate-500">
                                <tr>
                                    <th class="px-5 py-4">Prefix</th>
                                    <th class="px-5 py-4">Label</th>
                                    <th class="px-5 py-4">Devices</th>
                                    <th class="px-5 py-4">Expires</th>
                                    <th class="px-5 py-4">Status</th>
                                    <th class="px-5 py-4 text-right">Action</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-white/5">
                                <?php foreach ($licenses as $license) :
                                    $expired = !empty($license['expires_at']) && strtotime($license['expires_at']) <= $now;
                                    $active = $license['status'] === 'active' && !$expired;
                                ?>
                                    <tr class="license-row transition hover:bg-white/[.025]" data-search="<?= esc(strtolower($license['key_prefix'] . ' ' . $license['label'])) ?>">
                                        <td class="px-5 py-4 font-mono font-semibold text-amber-200"><?= esc($license['key_prefix']) ?>…</td>
                                        <td class="max-w-xs px-5 py-4 text-slate-200"><span class="block truncate"><?= esc($license['label']) ?></span></td>
                                        <td class="px-5 py-4 text-slate-300"><?= (int) $license['devices_used'] ?>/<?= (int) $license['max_devices'] ?></td>
                                        <td class="whitespace-nowrap px-5 py-4 text-slate-400"><?= esc($license['expires_at'] ?: 'Never') ?></td>
                                        <td class="px-5 py-4"><span class="badge <?= $active ? 'badge-success' : ($license['status'] === 'revoked' ? 'badge-danger' : 'badge-muted') ?>"><?= $active ? 'Active' : ($license['status'] === 'revoked' ? 'Revoked' : 'Expired') ?></span></td>
                                        <td class="px-5 py-4 text-right">
                                            <?php if ($license['status'] === 'active') : ?>
                                                <form action="<?= site_url('licenses/revoke') ?>" method="post" class="inline" data-confirm="Revoke this license and every device bound to it?">
                                                    <?= csrf_field() ?>
                                                    <input type="hidden" name="license_id" value="<?= (int) $license['id'] ?>">
                                                    <button class="btn-danger min-h-0 px-3 py-2 text-xs" type="submit">Revoke</button>
                                                </form>
                                            <?php else : ?>
                                                <span class="text-xs text-slate-600">Locked</span>
                                            <?php endif; ?>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>

                    <div class="grid gap-3 p-4 md:hidden">
                        <?php foreach ($licenses as $license) :
                            $expired = !empty($license['expires_at']) && strtotime($license['expires_at']) <= $now;
                            $active = $license['status'] === 'active' && !$expired;
                        ?>
                            <article class="license-row surface-soft p-4" data-search="<?= esc(strtolower($license['key_prefix'] . ' ' . $license['label'])) ?>">
                                <div class="flex items-start justify-between gap-3">
                                    <div class="min-w-0">
                                        <p class="font-mono text-sm font-bold text-amber-200"><?= esc($license['key_prefix']) ?>…</p>
                                        <p class="mt-1 truncate text-sm font-semibold text-white"><?= esc($license['label']) ?></p>
                                    </div>
                                    <span class="badge <?= $active ? 'badge-success' : ($license['status'] === 'revoked' ? 'badge-danger' : 'badge-muted') ?>"><?= $active ? 'Active' : ($license['status'] === 'revoked' ? 'Revoked' : 'Expired') ?></span>
                                </div>
                                <dl class="mt-4 grid grid-cols-2 gap-3 text-xs">
                                    <div><dt class="text-slate-500">Devices</dt><dd class="mt-1 text-slate-200"><?= (int) $license['devices_used'] ?>/<?= (int) $license['max_devices'] ?></dd></div>
                                    <div><dt class="text-slate-500">Expires</dt><dd class="mt-1 text-slate-200"><?= esc($license['expires_at'] ?: 'Never') ?></dd></div>
                                </dl>
                                <?php if ($license['status'] === 'active') : ?>
                                    <form action="<?= site_url('licenses/revoke') ?>" method="post" class="mt-4" data-confirm="Revoke this license and every device bound to it?">
                                        <?= csrf_field() ?>
                                        <input type="hidden" name="license_id" value="<?= (int) $license['id'] ?>">
                                        <button class="btn-danger w-full" type="submit">Revoke access</button>
                                    </form>
                                <?php endif; ?>
                            </article>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>
            </article>
        </section>
    <?php endif; ?>
</div>
<?= $this->endSection() ?>

<?= $this->section('js') ?>
<script>
(() => {
    document.querySelectorAll('[data-copy-target]').forEach(button => {
        button.addEventListener('click', async () => {
            const target = document.getElementById(button.dataset.copyTarget);
            if (!target) return;
            try {
                await navigator.clipboard.writeText(target.value || target.textContent);
                const original = button.textContent;
                button.textContent = 'Copied';
                setTimeout(() => button.textContent = original, 1400);
            } catch (_) {
                target.select();
                document.execCommand('copy');
            }
        });
    });

    const search = document.getElementById('license-search');
    if (search) {
        search.addEventListener('input', () => {
            const query = search.value.trim().toLowerCase();
            document.querySelectorAll('.license-row').forEach(row => {
                row.hidden = query !== '' && !row.dataset.search.includes(query);
            });
        });
    }

    document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
})();
</script>
<?= $this->endSection() ?>
