<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>
<?php $generatedKeys = session()->getFlashdata('generated_keys') ?? []; ?>
<div class="page-wrap space-y-6">
    <section class="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div>
            <p class="eyebrow">Legacy licensing</p>
            <h1 class="mt-2 text-3xl font-bold text-white sm:text-4xl">Generate legacy keys</h1>
            <p class="mt-2 text-sm text-slate-400">For current OneCore APK activation, use the dedicated OneCore Licenses page.</p>
        </div>
        <div class="flex gap-2">
            <?php if (($user->level ?? 99) == 1) : ?><a href="<?= site_url('licenses') ?>" class="btn-secondary">OneCore licenses</a><?php endif; ?>
            <a href="<?= site_url('keys') ?>" class="btn-secondary">Back to database</a>
        </div>
    </section>

    <?= $this->include('Layout/msgStatus') ?>

    <?php if ($generatedKeys) : ?>
        <section class="surface border-amber-300/25 p-5 sm:p-6">
            <div class="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
                <div>
                    <p class="eyebrow">Batch ready</p>
                    <h2 class="mt-2 text-xl font-bold text-white"><?= count($generatedKeys) ?> key<?= count($generatedKeys) === 1 ? '' : 's' ?> generated</h2>
                    <p class="mt-1 text-sm text-slate-400"><?= esc(session()->getFlashdata('game')) ?> · <?= (int) session()->getFlashdata('duration') ?>h · <?= (int) session()->getFlashdata('max_devices') ?> device(s)</p>
                </div>
                <button class="btn-primary" type="button" data-copy="generated-legacy-keys">Copy all</button>
            </div>
            <textarea id="generated-legacy-keys" class="field mt-5 min-h-32 resize-y font-mono text-sm leading-7 text-amber-200" readonly><?= esc(implode("\n", $generatedKeys)) ?></textarea>
        </section>
    <?php endif; ?>

    <section class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <article class="surface p-5 sm:p-7">
            <form action="<?= site_url('keys/generate') ?>" method="post" class="space-y-6" id="legacy-generator">
                <?= csrf_field() ?>
                <div class="grid gap-4 sm:grid-cols-2">
                    <div>
                        <label class="label" for="game">Game profile</label>
                        <?= form_dropdown(['class' => 'field', 'name' => 'game', 'id' => 'game'], $game, old('game')) ?>
                    </div>
                    <div>
                        <label class="label" for="duration">Duration</label>
                        <?= form_dropdown(['class' => 'field', 'name' => 'duration', 'id' => 'duration'], $duration, old('duration')) ?>
                    </div>
                    <div>
                        <label class="label" for="max_devices">Maximum devices</label>
                        <input class="field" type="number" name="max_devices" id="max_devices" min="1" max="100" value="<?= esc(old('max_devices') ?: '1') ?>" required>
                    </div>
                    <div>
                        <label class="label" for="quantity">Quantity</label>
                        <select class="field" name="quantity" id="quantity">
                            <?php foreach ([1, 5, 10, 25, 50, 100] as $quantity) : ?>
                                <option value="<?= $quantity ?>" <?= (int) old('quantity') === $quantity ? 'selected' : '' ?>><?= $quantity ?></option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                </div>

                <div class="surface-soft p-4">
                    <label class="flex cursor-pointer items-center justify-between gap-4" for="custom-toggle">
                        <span><span class="block text-sm font-semibold text-white">Custom key</span><span class="mt-1 block text-xs text-slate-500">Available only for single-key generation.</span></span>
                        <input id="custom-toggle" type="checkbox" class="h-5 w-5 accent-amber-400">
                    </label>
                    <div id="custom-section" class="mt-4 hidden">
                        <label class="label" for="cuslicense">Custom license value</label>
                        <input class="field font-mono" id="cuslicense" name="cuslicense" maxlength="64" value="<?= esc(old('cuslicense')) ?>" placeholder="CUSTOM-KEY-001">
                    </div>
                    <input type="hidden" id="custominput" name="custominput" value="auto">
                </div>

                <button class="btn-primary w-full" type="submit">Generate legacy batch</button>
            </form>
        </article>

        <aside class="surface h-fit p-5 sm:p-6">
            <p class="eyebrow">Cost preview</p>
            <p class="mt-3 text-4xl font-bold text-white"><span class="text-amber-300">₹</span><span id="estimation">0</span></p>
            <p class="mt-2 text-xs leading-5 text-slate-500">Total includes duration, device count, and batch quantity. Your balance is charged once after the complete database transaction succeeds.</p>
            <dl class="mt-6 space-y-3 border-t border-white/10 pt-5 text-sm">
                <div class="flex justify-between"><dt class="text-slate-500">Available balance</dt><dd class="font-semibold text-emerald-300">₹<?= esc($user->saldo) ?></dd></div>
                <div class="flex justify-between"><dt class="text-slate-500">Output type</dt><dd class="font-semibold text-white">Legacy key</dd></div>
            </dl>
        </aside>
    </section>
</div>
<?= $this->endSection() ?>

<?= $this->section('js') ?>
<script>
(() => {
    const prices = <?= json_encode(json_decode($price, true), JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_AMP | JSON_HEX_QUOT) ?> || {};
    const duration = document.getElementById('duration');
    const devices = document.getElementById('max_devices');
    const quantity = document.getElementById('quantity');
    const estimate = document.getElementById('estimation');
    const customToggle = document.getElementById('custom-toggle');
    const customSection = document.getElementById('custom-section');
    const customInput = document.getElementById('custominput');

    const calculate = () => {
        const price = Number(prices[duration.value] || 0);
        estimate.textContent = (price * Number(devices.value || 0) * Number(quantity.value || 0)).toLocaleString('en-IN');
    };
    [duration, devices, quantity].forEach(input => input.addEventListener('input', calculate));
    calculate();

    customToggle.addEventListener('change', () => {
        customSection.classList.toggle('hidden', !customToggle.checked);
        customInput.value = customToggle.checked ? 'custom' : 'auto';
        if (customToggle.checked) quantity.value = '1';
        calculate();
    });

    document.querySelectorAll('[data-copy]').forEach(button => {
        button.addEventListener('click', async () => {
            const target = document.getElementById(button.dataset.copy);
            try {
                await navigator.clipboard.writeText(target.value);
                button.textContent = 'Copied';
            } catch (_) {
                target.select();
                document.execCommand('copy');
            }
        });
    });
})();
</script>
<?= $this->endSection() ?>
