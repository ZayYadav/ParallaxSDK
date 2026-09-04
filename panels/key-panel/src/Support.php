<?php

declare(strict_types=1);

namespace KESHAVXOWNERPanel;

final class Env
{
    /** @var array<string,string> */
    private static array $values = [];

    public static function load(string $path): void
    {
        if (!is_file($path)) {
            return;
        }
        foreach (file($path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [] as $line) {
            $line = trim($line);
            if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
                continue;
            }
            [$key, $value] = array_map('trim', explode('=', $line, 2));
            if ($key === '' || preg_match('/^[A-Z0-9_]+$/D', $key) !== 1) {
                continue;
            }
            if (strlen($value) >= 2 && (($value[0] === '"' && str_ends_with($value, '"'))
                || ($value[0] === "'" && str_ends_with($value, "'")))) {
                $value = substr($value, 1, -1);
            }
            self::$values[$key] = $value;
        }
    }

    public static function get(string $key, string $default = ''): string
    {
        $system = getenv($key);
        return $system !== false ? $system : (self::$values[$key] ?? $default);
    }
}
function h(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function url(string $path = ''): string
{
    $base = '/' . trim(Env::get('APP_BASE_PATH'), '/');
    $base = $base === '/' ? '' : $base;
    return $base . '/' . ltrim($path, '/');
}

function redirect(string $path): never
{
    header('Location: ' . url($path), true, 303);
    exit;
}

function flash(string $type, string $message): void
{
    $_SESSION['flash'] = ['type' => $type, 'message' => $message];
}
