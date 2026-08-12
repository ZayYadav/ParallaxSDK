<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>
<?php
$isOwner = (int) ($user->level ?? 99) === 1;
$deviceTotal = (int) ($key_info->total ?? 0);
$deviceList = (string) ($key_info->devices ?? '');
?>
<div class="page-wrap space-y-6">
    <section class="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div>
            <p class="eyebrow">Legacy licensing</p>
            <h1 class="mt-2 text-3xl font-bold text-white sm:text-4xl">Edit key #<?= (int) $key->id_keys ?></h1>
            <p class="mt-2 text-sm leading-6 text-slate-400">Update status, limits and registered devices from one complete view.</p>
        </div>
        <div class="flex flex-wrap gap-2">
            <a href="<?= site_url('keys') ?>" class="btn-secondary">Back to keys</a>
            <a href="<?= site_url('keys/generate') ?>" class="btn-primary">Generate key</a>
        </div>
    </section>

    <?= $this->include('Layout/msgStatus') ?>

    <form id="key-edit-form" action="<?= site_url('keys/edit') ?>" method="post" class="surface overflow-hidden">
        <?= csrf_field() ?>
        <input type="hidden" name="id_keys" value="<?= (int) $key->id_keys ?>">

        <div class="border-b border-white/10 p-5 sm:p-7">
            <div class="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                <div>
                    <p class="font-display text-xl font-semibold text-white">License settings</p>
                    <p class="mt-1 text-sm text-slate-500"><?= $isOwner ? 'Owner access: all fields can be changed.' : 'Reseller access: only the key status can be changed.' ?></p>
                </div>
                <span class="badge <?= (int) $key->status === 1 ? 'badge-success' : 'badge-danger' ?>"><?= (int) $key->status === 1 ? 'Active' : 'Disabled' ?></span>
            </div>
        </div>

        <div class="grid gap-6 p-5 sm:p-7 lg:grid-cols-2">
            <?php if ($isOwner) : ?>
                <label>
                    <span class="label">Game / product</span>
                    <select name="game" class="field" required>
                        <?php foreach ($game_list as $gameCode => $gameName) : ?>
                            <?php $gameValue = is_string($gameCode) ? $gameCode : $gameName; ?>
                            <option value="<?= esc($gameValue) ?>" <?= old('game', $key->game) === $gameValue ? 'selected' : '' ?>><?= esc($gameName) ?></option>
                        <?php endforeach; ?>
                    </select>
                </label>

                <label>
                    <span class="label">License key</span>
                    <input name="user_key" class="field font-mono" minlength="4" maxlength="64" pattern="[A-Za-z0-9_-]+" value="<?= esc(old('user_key', $key->user_key)) ?>" required>
                </label>

                <label>
                    <span class="label">Duration (hours)</span>
                    <input name="duration" type="number" min="1" class="field" value="<?= esc(old('duration', $key->duration)) ?>" required>
                </label>

                <label>
                    <span class="label">Maximum devices</span>
                    <input id="max-devices" name="max_devices" type="number" min="1" max="100" class="field" value="<?= esc(old('max_devices', $key->max_devices)) ?>" required>
                    <span id="device-usage" class="mt-2 block text-xs text-slate-500"><?= $deviceTotal ?>/<?= (int) $key->max_devices ?> slots currently used</span>
                </label>
            <?php endif; ?>

            <label>
                <span class="label">Status</span>
                <select name="status" class="field" required>
                    <option value="1" <?= (string) old('status', $key->status) === '1' ? 'selected' : '' ?>>Active</option>
                    <option value="0" <?= (string) old('status', $key->status) === '0' ? 'selected' : '' ?>>Disabled</option>
                </select>
            </label>

            <?php if ($isOwner) : ?>
                <label>
                    <span class="label">Created by / registrator</span>
                    <input name="registrator" class="field" minlength="4" value="<?= esc(old('registrator', $key->registrator)) ?>">
                </label>

                <label class="lg:col-span-2">
                    <span class="label">Expiry (UTC)</span>
                    <input name="expired_date" class="field font-mono" placeholder="YYYY-MM-DD HH:MM:SS" value="<?= esc(old('expired_date', $key->expired_date)) ?>">
                    <span class="mt-2 block text-xs text-slate-500">Leave empty when the key has not started.</span>
                </label>

                <label class="lg:col-span-2">
                    <span class="label">Registered device IDs</span>
                    <textarea name="devices" class="field min-h-32 resize-y font-mono text-sm" placeholder="Comma-separated device IDs"><?= esc(old('devices', $deviceList)) ?></textarea>
                    <span class="mt-2 block text-xs text-slate-500">Removing a device here releases its slot after saving.</span>
                </label>
            <?php endif; ?>
        </div>

        <div class="flex flex-col-reverse justify-end gap-2 border-t border-white/10 bg-slate-950/25 p-5 sm:flex-row sm:p-7">
            <a href="<?= site_url('keys') ?>" class="btn-secondary">Cancel</a>
            <button id="save-key" type="submit" class="btn-primary">Save changes</button>
        </div>
    </form>
</div>
<?= $this->endSection() ?>

<?= $this->section('js') ?>
<script>
(() => {
    const maxDevices = document.getElementById('max-devices');
    const usage = document.getElementById('device-usage');
    const used = <?= $deviceTotal ?>;
    if (maxDevices && usage) {
        maxDevices.addEventListener('input', () => {
            usage.textContent = `${used}/${maxDevices.value || 0} slots currently used`;
        });
    }
})();
</script>
<?= $this->endSection() ?>
