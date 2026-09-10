<?php
declare(strict_types=1);

final class Router
{
    /** @param array<string,string> $routes */
    public function __construct(
        private readonly string $root,
        private readonly array $routes
    ) {
    }

    public function dispatch(?string $uri = null): never
    {
        $path = $this->normalize($uri ?? (string) ($_SERVER['REQUEST_URI'] ?? '/'));
        $target = $this->routes[$path] ?? null;

        if ($target === null && str_ends_with($path, '.php')) {
            $target = basename($path);
        }

        if ($target === null) {
            http_response_code(404);
            require $this->root . '/app/views/errors/404.php';
            exit;
        }

        $file = realpath($this->root . '/' . ltrim($target, '/'));
        $root = realpath($this->root);
        if ($file === false || $root === false || !str_starts_with($file, $root) || !is_file($file)) {
            http_response_code(404);
            require $this->root . '/app/views/errors/404.php';
            exit;
        }

        $_SERVER['SDK_PANEL_ROUTE_TARGET'] = basename($file);
        if (!defined('SDK_PANEL_ROUTE_TARGET')) {
            define('SDK_PANEL_ROUTE_TARGET', basename($file));
        }
        require $file;
        exit;
    }

    private function normalize(string $uri): string
    {
        $path = parse_url($uri, PHP_URL_PATH);
        $path = is_string($path) ? $path : '/';

        $scriptDir = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? ''))), '/');
        if ($scriptDir !== '' && $scriptDir !== '/') {
            if ($path === $scriptDir) {
                $path = '/';
            } elseif (str_starts_with($path, $scriptDir . '/')) {
                $path = substr($path, strlen($scriptDir));
            }
        }

        $path = trim(rawurldecode($path), '/');
        return $path === '' ? '/' : $path;
    }
}
