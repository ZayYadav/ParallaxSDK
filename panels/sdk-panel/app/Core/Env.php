<?php
declare(strict_types=1);

final class Env
{
    public static function load(string $path): void
    {
        if (!is_file($path) || !is_readable($path)) {
            return;
        }

        $lines = file($path, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        if (!is_array($lines)) {
            return;
        }

        foreach ($lines as $line) {
            $line = trim($line);
            if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) {
                continue;
            }

            [$key, $value] = explode('=', $line, 2);
            $key = trim($key);
            if (preg_match('/^[A-Z][A-Z0-9_]{1,80}$/D', $key) !== 1) {
                continue;
            }

            $value = self::stripInlineComment(trim($value));
            $value = self::unquote($value);
            $_ENV[$key] = $value;
            $_SERVER[$key] = $value;
            putenv($key . '=' . $value);
        }
    }

    /** @return array<string,mixed> */
    public static function panelConfigFromEnvironment(): array
    {
        $config = [];
        foreach ([
            'DB_HOST', 'DB_NAME', 'DB_USER', 'DB_PASSWORD',
            'ENCRYPTION_KEY', 'PANEL_DATA_KEY',
            'TELEGRAM_BOT_TOKEN', 'TELEGRAM_WEBHOOK_SECRET',
            'TELEGRAM_DEFAULT_ADMIN_CHAT_ID', 'TELEGRAM_OWNER_CHAT_ID',
        ] as $key) {
            $value = self::get($key);
            if ($value !== null) {
                $config[$key] = $value;
            }
        }

        foreach ([
            'DB_PORT', 'RATE_LIMIT_PER_MINUTE', 'API_REPLAY_WINDOW_SECONDS',
            'SESSION_IDLE_SECONDS', 'SESSION_ABSOLUTE_SECONDS', 'MAX_POST_BYTES',
        ] as $key) {
            $value = self::get($key);
            if ($value !== null && preg_match('/^\d+$/D', $value) === 1) {
                $config[$key] = (int) $value;
            }
        }

        foreach (['LEGACY_API_ENABLED', 'API_V2_ENABLED', 'REQUIRE_HTTPS', 'TRUST_PROXY'] as $key) {
            $value = self::get($key);
            if ($value !== null) {
                $config[$key] = self::boolValue($value);
            }
        }

        $trustedProxies = self::csv('TRUSTED_PROXIES');
        if ($trustedProxies !== []) {
            $config['TRUSTED_PROXIES'] = $trustedProxies;
        }

        $allowedPackages = self::csv('ALLOWED_PACKAGES');
        if ($allowedPackages !== []) {
            $config['ALLOWED_PACKAGES'] = $allowedPackages;
        }

        $apiV3Json = self::get('API_V3_KEYS_JSON');
        if ($apiV3Json !== null && $apiV3Json !== '') {
            $decoded = json_decode($apiV3Json, true);
            if (is_array($decoded)) {
                $config['API_V3_KEYS'] = $decoded;
            }
        } else {
            $keyId = self::get('API_V3_KEY_ID') ?: '';
            $ecdh = self::get('API_V3_ECDH_PRIVATE_KEY_FILE') ?: '';
            $signing = self::get('API_V3_SIGNING_PRIVATE_KEY_FILE') ?: '';
            if ($keyId !== '' && $ecdh !== '' && $signing !== '') {
                $config['API_V3_KEYS'] = [
                    $keyId => [
                        'ecdh_private_key_file' => $ecdh,
                        'signing_private_key_file' => $signing,
                    ],
                ];
            }
        }

        return $config;
    }

    private static function get(string $key): ?string
    {
        $value = getenv($key);
        if ($value === false) {
            return null;
        }
        return trim((string) $value);
    }

    /** @return list<string> */
    private static function csv(string $key): array
    {
        $value = self::get($key);
        if ($value === null || $value === '') {
            return [];
        }
        return array_values(array_filter(array_map('trim', explode(',', $value)), static fn(string $item): bool => $item !== ''));
    }

    private static function boolValue(string $value): bool
    {
        return in_array(strtolower(trim($value)), ['1', 'true', 'yes', 'on'], true);
    }

    private static function unquote(string $value): string
    {
        if (strlen($value) >= 2) {
            $first = $value[0];
            $last = $value[strlen($value) - 1];
            if (($first === '"' && $last === '"') || ($first === "'" && $last === "'")) {
                return stripcslashes(substr($value, 1, -1));
            }
        }
        return $value;
    }

    private static function stripInlineComment(string $value): string
    {
        $inQuote = null;
        $length = strlen($value);
        for ($i = 0; $i < $length; $i++) {
            $char = $value[$i];
            if (($char === '"' || $char === "'") && ($i === 0 || $value[$i - 1] !== '\\')) {
                $inQuote = $inQuote === $char ? null : ($inQuote ?? $char);
            }
            if ($char === '#' && $inQuote === null && ($i === 0 || ctype_space($value[$i - 1]))) {
                return rtrim(substr($value, 0, $i));
            }
        }
        return $value;
    }
}
