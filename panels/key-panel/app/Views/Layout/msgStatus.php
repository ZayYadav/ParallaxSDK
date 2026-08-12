<?php
$flash = null;
foreach ([
    'msgDanger' => ['error', 'border-rose-400/30 bg-rose-400/10 text-rose-100'],
    'msgWarning' => ['warning', 'border-amber-300/30 bg-amber-300/10 text-amber-100'],
    'msgSuccess' => ['success', 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100'],
] as $key => $meta) {
    $message = session()->getFlashdata($key);
    if ($message) {
        $flash = ['type' => $meta[0], 'class' => $meta[1], 'message' => $message];
        break;
    }
}
?>
<?php if ($flash) : ?>
    <div class="status-alert mb-5 flex items-start justify-between gap-4 rounded-xl border p-4 <?= $flash['class'] ?>" role="status">
        <div class="flex min-w-0 items-start gap-3">
            <span class="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white/10 text-sm font-bold">
                <?= $flash['type'] === 'success' ? '✓' : ($flash['type'] === 'warning' ? '!' : '×') ?>
            </span>
            <p class="break-words text-sm font-medium leading-6"><?= esc($flash['message']) ?></p>
        </div>
        <button type="button" class="shrink-0 rounded-lg px-2 py-1 text-lg leading-none opacity-60 transition hover:bg-white/10 hover:opacity-100" aria-label="Dismiss" onclick="this.closest('.status-alert').remove()">×</button>
    </div>
<?php elseif (isset($messages) && is_array($messages) && isset($messages[0])) : ?>
    <div class="mb-5 rounded-xl border border-sky-400/20 bg-sky-400/10 p-4 text-sm text-sky-100">
        <?= esc(strip_tags((string) $messages[0])) ?>
    </div>
<?php endif; ?>
