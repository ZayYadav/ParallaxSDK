<?php

declare(strict_types=1);

namespace ParallaxPanel;

final class View
{
    /** @param array<string,mixed>|null $user */
    public static function page(string $title, string $body, ?array $user = null): never
    {
        $flash = $_SESSION['flash'] ?? null;
        unset($_SESSION['flash']);
        $nav = '';
        if ($user) {
            $nav = '<nav><a href="' . url('dashboard') . '">Dashboard</a>'
                . '<a href="' . url('keys') . '">Legacy Keys</a>'
                . '<a href="' . url('licenses') . '">OneCore Keys</a>'
                . '<a href="' . url('settings') . '">Settings</a>';
            if ($user['role'] === 'owner') {
                $nav .= '<a href="' . url('users') . '">Users</a>';
            }
            $nav .= '<a href="' . url('account') . '">Account</a><form method="post" action="' . url('logout') . '">'
                . Security::csrfField() . '<button class="link" type="submit">Logout</button></form></nav>';
        }
        $notice = '';
        if (is_array($flash)) {
            $type = in_array($flash['type'] ?? '', ['success', 'danger', 'warning'], true)
                ? $flash['type'] : 'warning';
            $notice = '<div class="notice ' . $type . '">' . h($flash['message'] ?? '') . '</div>';
        }
        $username = $user ? '<span class="user">' . h($user['username']) . ' · ' . h($user['role']) . '</span>' : '';
        $asset = url('assets/app.css');
        echo '<!doctype html><html lang="en"><head><meta charset="utf-8">'
            . '<meta name="viewport" content="width=device-width,initial-scale=1">'
            . '<title>' . h($title) . ' · Parallax Panel</title><link rel="stylesheet" href="' . $asset . '"></head>'
            . '<body><header><a class="brand" href="' . url('') . '">PARALLAX <b>CONTROL</b></a>'
            . $username . '</header>' . $nav . '<main>' . $notice . $body . '</main>'
            . '<footer>Standalone PHP control panel · UTC server time ' . h(gmdate('Y-m-d H:i:s')) . '</footer></body></html>';
        exit;
    }

    public static function input(string $name, string $label, string $type = 'text', string $value = '', string $extra = ''): string
    {
        return '<label><span>' . h($label) . '</span><input type="' . h($type) . '" name="' . h($name)
            . '" value="' . h($value) . '" ' . $extra . '></label>';
    }

    /** @param array<string,string> $options */
    public static function select(string $name, string $label, array $options, string $selected = ''): string
    {
        $html = '<label><span>' . h($label) . '</span><select name="' . h($name) . '">';
        foreach ($options as $value => $text) {
            $html .= '<option value="' . h($value) . '"' . ($value === $selected ? ' selected' : '') . '>'
                . h($text) . '</option>';
        }
        return $html . '</select></label>';
    }

    public static function metric(string $label, int|string $value): string
    {
        return '<div class="metric"><span>' . h($label) . '</span><strong>' . h($value) . '</strong></div>';
    }
}
