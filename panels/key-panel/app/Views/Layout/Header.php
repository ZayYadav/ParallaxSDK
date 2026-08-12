<?php
$activeSegment = service('uri')->getSegment(1);
$navClass = static function (string $segment) use ($activeSegment): string {
    return $activeSegment === $segment
        ? 'bg-amber-400/10 text-amber-300 border-amber-300/20'
        : 'text-slate-400 border-transparent hover:bg-white/5 hover:text-white';
};
?>
<header class="sticky top-0 z-50 border-b border-white/10 bg-slate-950/80 backdrop-blur-xl">
    <nav class="mx-auto w-full max-w-7xl px-4 sm:px-6" aria-label="Primary navigation">
        <div class="flex h-16 items-center justify-between gap-4">
            <a href="<?= site_url(session()->has('userid') ? 'dashboard' : '/') ?>" class="group flex min-w-0 items-center gap-3">
                <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-amber-300 to-orange-500 font-display text-lg font-bold text-slate-950 shadow-lg shadow-amber-500/10">P</span>
                <span class="min-w-0">
                    <span class="block truncate font-display text-sm font-bold tracking-wide text-white sm:text-base"><?= esc(BASE_NAME) ?></span>
                    <span class="block text-[10px] font-semibold uppercase tracking-[.18em] text-amber-300/70">Control center</span>
                </span>
            </a>

            <?php if (session()->has('userid')) : ?>
                <div class="hidden items-center gap-1 lg:flex">
                    <a href="<?= site_url('dashboard') ?>" class="rounded-lg border px-3 py-2 text-sm font-semibold transition <?= $navClass('dashboard') ?>">Overview</a>
                    <?php if (($user->level ?? 99) == 1) : ?>
                        <a href="<?= site_url('licenses') ?>" class="rounded-lg border px-3 py-2 text-sm font-semibold transition <?= $navClass('licenses') ?>">OneCore licenses</a>
                    <?php endif; ?>
                    <a href="<?= site_url('keys') ?>" class="rounded-lg border px-3 py-2 text-sm font-semibold transition <?= $navClass('keys') ?>">Legacy keys</a>
                    <a href="<?= site_url('filemanager') ?>" class="rounded-lg border border-transparent px-3 py-2 text-sm font-semibold text-slate-400 transition hover:bg-white/5 hover:text-white">Files</a>
                    <?php if (($user->level ?? 99) <= 2) : ?>
                        <a href="<?= site_url('Server') ?>" class="rounded-lg border px-3 py-2 text-sm font-semibold transition <?= $navClass('Server') ?>">Runtime</a>
                    <?php endif; ?>
                </div>

                <div class="hidden items-center gap-3 md:flex">
                    <div class="text-right">
                        <p class="text-xs font-semibold text-white"><?= esc(getName($user)) ?></p>
                        <p class="text-[10px] uppercase tracking-wider text-slate-500"><?= esc(getLevel($user->level)) ?></p>
                    </div>
                    <div class="relative">
                        <button id="user-menu-button" type="button" aria-expanded="false" class="grid h-10 w-10 place-items-center rounded-xl border border-white/10 bg-white/5 font-bold text-amber-300 transition hover:bg-white/10">
                            <?= esc(strtoupper(substr(getName($user), 0, 1))) ?>
                        </button>
                        <div id="user-dropdown" class="absolute right-0 mt-3 hidden w-56 overflow-hidden rounded-xl border border-white/10 bg-slate-950/95 p-2 shadow-2xl backdrop-blur-xl">
                            <a href="<?= site_url('settings') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5 hover:text-white">Account settings</a>
                            <?php if (($user->level ?? 99) == 1) : ?>
                                <a href="<?= site_url('admin/manage-users') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5 hover:text-white">Manage users</a>
                                <a href="<?= site_url('admin/create-referral') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5 hover:text-white">Referral codes</a>
                            <?php endif; ?>
                            <div class="my-1 border-t border-white/10"></div>
                            <a href="<?= site_url('logout') ?>" class="block rounded-lg px-3 py-2 text-sm text-rose-300 hover:bg-rose-400/10">Sign out</a>
                        </div>
                    </div>
                </div>

                <button id="mobile-menu-button" type="button" aria-controls="mobile-menu" aria-expanded="false" class="grid h-10 w-10 place-items-center rounded-xl border border-white/10 bg-white/5 text-xl text-white md:hidden">☰</button>
            <?php endif; ?>
        </div>

        <?php if (session()->has('userid')) : ?>
            <div id="mobile-menu" class="hidden space-y-1 border-t border-white/10 py-3 md:hidden">
                <a href="<?= site_url('dashboard') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5">Overview</a>
                <?php if (($user->level ?? 99) == 1) : ?>
                    <a href="<?= site_url('licenses') ?>" class="block rounded-lg px-3 py-2 text-sm text-amber-300 hover:bg-white/5">OneCore licenses</a>
                <?php endif; ?>
                <a href="<?= site_url('keys') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5">Legacy keys</a>
                <a href="<?= site_url('keys/generate') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5">Generate legacy keys</a>
                <a href="<?= site_url('settings') ?>" class="block rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-white/5">Account settings</a>
                <a href="<?= site_url('logout') ?>" class="block rounded-lg px-3 py-2 text-sm text-rose-300 hover:bg-rose-400/10">Sign out</a>
            </div>
        <?php endif; ?>
    </nav>
</header>
<script>
(() => {
    const userButton = document.getElementById('user-menu-button');
    const dropdown = document.getElementById('user-dropdown');
    if (userButton && dropdown) {
        userButton.addEventListener('click', () => {
            const opening = dropdown.classList.contains('hidden');
            dropdown.classList.toggle('hidden');
            userButton.setAttribute('aria-expanded', opening ? 'true' : 'false');
        });
        document.addEventListener('click', event => {
            if (!userButton.contains(event.target) && !dropdown.contains(event.target)) {
                dropdown.classList.add('hidden');
                userButton.setAttribute('aria-expanded', 'false');
            }
        });
    }

    const mobileButton = document.getElementById('mobile-menu-button');
    const mobileMenu = document.getElementById('mobile-menu');
    if (mobileButton && mobileMenu) {
        mobileButton.addEventListener('click', () => {
            const opening = mobileMenu.classList.contains('hidden');
            mobileMenu.classList.toggle('hidden');
            mobileButton.setAttribute('aria-expanded', opening ? 'true' : 'false');
        });
    }
})();
</script>
