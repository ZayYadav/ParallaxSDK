<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>
<?php $now = time(); ?>
<div class="page-wrap space-y-6">
    <section class="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div>
            <p class="eyebrow">Legacy licensing</p>
            <h1 class="mt-2 text-3xl font-bold text-white sm:text-4xl">Key database</h1>
            <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-400">Complete game, device, duration, expiry and status data is now loaded for every key. Use OneCore Licenses for the current APK.</p>
        </div>
        <div class="flex flex-wrap gap-2">
            <?php if (($user->level ?? 99) == 1) : ?>
                <a href="<?= site_url('licenses') ?>" class="btn-secondary">OneCore licenses</a>
            <?php endif; ?>
            <a href="<?= site_url('keys/generate') ?>" class="btn-primary">Generate legacy keys</a>
        </div>
    </section>

    <?= $this->include('Layout/msgStatus') ?>

    <section class="surface overflow-hidden">
        <div class="flex flex-col justify-between gap-4 border-b border-white/10 p-5 sm:flex-row sm:items-center sm:p-6">
            <div class="flex flex-wrap gap-2">
                <button id="toggle-keys" type="button" class="btn-secondary">Reveal keys</button>
                <a href="<?= site_url('keys/download') ?>" class="btn-secondary">Download visible scope</a>
                <form action="<?= site_url('keys/cleanup-expired') ?>" method="post" data-confirm="Delete every expired legacy key in your visible scope?">
                    <?= csrf_field() ?>
                    <button class="btn-danger" type="submit">Delete expired</button>
                </form>
                <form action="<?= site_url('keys/cleanup-unused') ?>" method="post" data-confirm="Delete every legacy key that has not started?">
                    <?= csrf_field() ?>
                    <button class="btn-danger" type="submit">Delete unused</button>
                </form>
            </div>
            <label class="relative block sm:w-72">
                <span class="sr-only">Search legacy keys</span>
                <input id="key-search" class="field pl-10" type="search" placeholder="Search game, key or owner">
                <span class="pointer-events-none absolute left-3 top-3 text-slate-500">⌕</span>
            </label>
        </div>

        <?php if (empty($keylist)) : ?>
            <div class="p-12 text-center">
                <p class="text-lg font-semibold text-white">No legacy keys found</p>
                <p class="mt-2 text-sm text-slate-500">Generate a key or switch to the OneCore activation-key inventory.</p>
            </div>
        <?php else : ?>
            <div class="hidden overflow-x-auto lg:block">
                <table class="w-full text-left text-sm">
                    <thead class="bg-white/[.025] text-[11px] uppercase tracking-[.12em] text-slate-500">
                        <tr>
                            <th class="px-5 py-4">ID / Game</th>
                            <th class="px-5 py-4">License key</th>
                            <th class="px-5 py-4">Devices</th>
                            <th class="px-5 py-4">Duration</th>
                            <th class="px-5 py-4">Expiry</th>
                            <th class="px-5 py-4">Status</th>
                            <th class="px-5 py-4 text-right">Controls</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-white/5">
                        <?php foreach ($keylist as $row) :
                            $key = (array) $row;
                            $id = (int) ($key['id_keys'] ?? 0);
                            $userKey = (string) ($key['user_key'] ?? '');
                            $devices = array_values(array_filter(array_map('trim', explode(',', (string) ($key['devices'] ?? '')))));
                            $expired = !empty($key['expired_date']) && strtotime($key['expired_date']) <= $now;
                            $enabled = (int) ($key['status'] ?? 0) === 1 && !$expired;
                            $search = strtolower(implode(' ', [$key['game'] ?? '', $userKey, $key['registrator'] ?? '', $id]));
                        ?>
                            <tr class="legacy-key-row transition hover:bg-white/[.025]" data-search="<?= esc($search) ?>">
                                <td class="px-5 py-4"><p class="font-semibold text-white">#<?= $id ?></p><p class="mt-1 text-xs text-slate-500"><?= esc($key['game'] ?? '-') ?></p></td>
                                <td class="px-5 py-4">
                                    <div class="flex items-center gap-2">
                                        <code class="legacy-key max-w-xs truncate rounded-lg bg-slate-950/70 px-2.5 py-1.5 font-mono text-amber-200 blur-sm transition" data-key="<?= esc($userKey) ?>"><?= esc($userKey ?: '-') ?></code>
                                        <button type="button" class="copy-key rounded-lg border border-white/10 px-2 py-1 text-xs text-slate-400 hover:text-white" data-key="<?= esc($userKey) ?>">Copy</button>
                                    </div>
                                </td>
                                <td class="px-5 py-4 text-slate-300"><?= count($devices) ?>/<?= (int) ($key['max_devices'] ?? 0) ?></td>
                                <td class="px-5 py-4 text-slate-300"><?= esc($key['duration'] ?? 0) ?>h</td>
                                <td class="whitespace-nowrap px-5 py-4 text-slate-400"><?= esc($key['expired_date'] ?: 'Not started') ?></td>
                                <td class="px-5 py-4"><span class="badge <?= $enabled ? 'badge-success' : ($expired ? 'badge-muted' : 'badge-danger') ?>"><?= $enabled ? 'Active' : ($expired ? 'Expired' : 'Disabled') ?></span></td>
                                <td class="px-5 py-4">
                                    <div class="flex justify-end gap-2">
                                        <a href="<?= site_url('keys/' . $id) ?>" class="btn-secondary min-h-0 px-3 py-2 text-xs">Edit</a>
                                        <form action="<?= site_url('keys/reset-devices') ?>" method="post" data-confirm="Clear every registered device for this key?">
                                            <?= csrf_field() ?>
                                            <input type="hidden" name="user_key" value="<?= esc($userKey) ?>">
                                            <button class="btn-secondary min-h-0 px-3 py-2 text-xs" type="submit">Reset</button>
                                        </form>
                                        <form action="<?= site_url('keys/delete') ?>" method="post" data-confirm="Permanently delete this legacy key?">
                                            <?= csrf_field() ?>
                                            <input type="hidden" name="user_key" value="<?= esc($userKey) ?>">
                                            <button class="btn-danger min-h-0 px-3 py-2 text-xs" type="submit">Delete</button>
                                        </form>
                                    </div>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            </div>

            <div class="grid gap-3 p-4 lg:hidden">
                <?php foreach ($keylist as $row) :
                    $key = (array) $row;
                    $id = (int) ($key['id_keys'] ?? 0);
                    $userKey = (string) ($key['user_key'] ?? '');
                    $devices = array_values(array_filter(array_map('trim', explode(',', (string) ($key['devices'] ?? '')))));
                    $expired = !empty($key['expired_date']) && strtotime($key['expired_date']) <= $now;
                    $enabled = (int) ($key['status'] ?? 0) === 1 && !$expired;
                    $search = strtolower(implode(' ', [$key['game'] ?? '', $userKey, $key['registrator'] ?? '', $id]));
                ?>
                    <article class="legacy-key-row surface-soft p-4" data-search="<?= esc($search) ?>">
                        <div class="flex items-start justify-between gap-3">
                            <div><p class="font-semibold text-white">#<?= $id ?> · <?= esc($key['game'] ?? '-') ?></p><p class="mt-1 text-xs text-slate-500"><?= esc($key['registrator'] ?? '-') ?></p></div>
                            <span class="badge <?= $enabled ? 'badge-success' : ($expired ? 'badge-muted' : 'badge-danger') ?>"><?= $enabled ? 'Active' : ($expired ? 'Expired' : 'Disabled') ?></span>
                        </div>
                        <div class="mt-4 flex items-center gap-2">
                            <code class="legacy-key min-w-0 flex-1 truncate rounded-lg bg-slate-950/70 px-3 py-2 font-mono text-sm text-amber-200 blur-sm" data-key="<?= esc($userKey) ?>"><?= esc($userKey ?: '-') ?></code>
                            <button type="button" class="copy-key btn-secondary min-h-0 px-3 py-2 text-xs" data-key="<?= esc($userKey) ?>">Copy</button>
                        </div>
                        <dl class="mt-4 grid grid-cols-3 gap-3 text-xs">
                            <div><dt class="text-slate-500">Devices</dt><dd class="mt-1 text-slate-200"><?= count($devices) ?>/<?= (int) ($key['max_devices'] ?? 0) ?></dd></div>
                            <div><dt class="text-slate-500">Duration</dt><dd class="mt-1 text-slate-200"><?= esc($key['duration'] ?? 0) ?>h</dd></div>
                            <div><dt class="text-slate-500">Expiry</dt><dd class="mt-1 truncate text-slate-200"><?= esc($key['expired_date'] ?: 'Not started') ?></dd></div>
                        </dl>
                        <div class="mt-4 grid grid-cols-3 gap-2">
                            <a href="<?= site_url('keys/' . $id) ?>" class="btn-secondary min-h-0 px-2 py-2 text-xs">Edit</a>
                            <form action="<?= site_url('keys/reset-devices') ?>" method="post" data-confirm="Clear every registered device for this key?">
                                <?= csrf_field() ?><input type="hidden" name="user_key" value="<?= esc($userKey) ?>"><button class="btn-secondary min-h-0 w-full px-2 py-2 text-xs" type="submit">Reset</button>
                            </form>
                            <form action="<?= site_url('keys/delete') ?>" method="post" data-confirm="Permanently delete this legacy key?">
                                <?= csrf_field() ?><input type="hidden" name="user_key" value="<?= esc($userKey) ?>"><button class="btn-danger min-h-0 w-full px-2 py-2 text-xs" type="submit">Delete</button>
                            </form>
                        </div>
                    </article>
                <?php endforeach; ?>
            </div>
        <?php endif; ?>
    </section>
</div>
<?= $this->endSection() ?>

<?= $this->section('js') ?>
<script>
(() => {
    let keysVisible = false;
    const toggle = document.getElementById('toggle-keys');
    if (toggle) {
        toggle.addEventListener('click', () => {
            keysVisible = !keysVisible;
            document.querySelectorAll('.legacy-key').forEach(element => element.classList.toggle('blur-sm', !keysVisible));
            toggle.textContent = keysVisible ? 'Hide keys' : 'Reveal keys';
        });
    }

    document.querySelectorAll('.copy-key').forEach(button => {
        button.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(button.dataset.key);
                const original = button.textContent;
                button.textContent = 'Copied';
                setTimeout(() => button.textContent = original, 1200);
            } catch (_) {
                window.prompt('Copy this key', button.dataset.key);
            }
        });
    });

    const search = document.getElementById('key-search');
    if (search) {
        search.addEventListener('input', () => {
            const query = search.value.trim().toLowerCase();
            document.querySelectorAll('.legacy-key-row').forEach(row => {
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
