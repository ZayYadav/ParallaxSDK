<?php
declare(strict_types=1);

/**
 * UI timezone bridge for the SDK panel.
 *
 * Security/API invariant:
 * - Database session and backend calculations remain UTC.
 * - Only human-facing HTML is converted to DISPLAY_TIMEZONE.
 * - datetime-local values entered in the panel are converted back to UTC
 *   before the existing page logic stores them.
 */
final class PanelTime
{
    private const DEFAULT_DISPLAY_TIMEZONE = 'Asia/Kolkata';

    /** @var DateTimeZone|null */
    private static $displayTimezone = null;

    /** @var DateTimeZone|null */
    private static $utcTimezone = null;

    private static bool $bootstrapped = false;

    public static function bootstrapForRequest(): void
    {
        if (self::$bootstrapped) {
            return;
        }
        self::$bootstrapped = true;

        if (!self::isPanelUiRequest()) {
            return;
        }

        if (strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET')) === 'POST') {
            self::normalizeIncomingDateTimeFields();
        }

        // Post-process only final HTML. JSON/API responses are returned untouched.
        ob_start([self::class, 'transformHtmlOutput']);
    }

    public static function displayTimezoneName(): string
    {
        return self::displayTimezone()->getName();
    }

    public static function now(string $format = 'Y-m-d H:i:s'): string
    {
        return (new DateTimeImmutable('now', self::utcTimezone()))
            ->setTimezone(self::displayTimezone())
            ->format($format);
    }

    public static function formatUtc(?string $value, string $format = 'd M Y H:i'): string
    {
        $value = trim((string) $value);
        if ($value === '') {
            return '';
        }

        foreach (['Y-m-d H:i:s', 'Y-m-d H:i', 'Y-m-d\\TH:i:s', 'Y-m-d\\TH:i'] as $inputFormat) {
            $date = self::parseExact($value, $inputFormat, self::utcTimezone());
            if ($date !== null) {
                return $date->setTimezone(self::displayTimezone())->format($format);
            }
        }

        return $value;
    }

    public static function transformHtmlOutput(string $html): string
    {
        if ($html === '' || (stripos($html, '<html') === false && stripos($html, '<!doctype') === false)) {
            return $html;
        }

        $protected = [];
        $html = (string) preg_replace_callback(
            '#<(script|style|pre|code|textarea)\\b[^>]*>.*?</\\1>#is',
            static function (array $match) use (&$protected): string {
                $token = '__PANEL_TIME_PROTECTED_' . count($protected) . '__';
                $protected[$token] = $match[0];
                return $token;
            },
            $html
        );

        // datetime-local fields are shown in IST while storage remains UTC.
        $html = (string) preg_replace_callback(
            '/<input\\b[^>]*\\btype=(?:"datetime-local"|\'datetime-local\')[^>]*>/i',
            static function (array $match): string {
                return self::transformDateTimeLocalTag($match[0]);
            },
            $html
        );

        // Convert rendered text nodes. Tags/attributes remain intact.
        $html = (string) preg_replace_callback(
            '/(?<=>)([^<]+)(?=<)/s',
            static function (array $match): string {
                return self::transformText($match[1]);
            },
            $html
        );

        if ($protected !== []) {
            $html = strtr($html, $protected);
        }

        return $html;
    }

    private static function transformText(string $text): string
    {
        // RFC3339/ISO UTC strings with an explicit Z marker.
        $text = (string) preg_replace_callback(
            '/\\b(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2}:\\d{2})Z\\b/',
            static function (array $m): string {
                $converted = self::convertExact($m[1] . ' ' . $m[2], 'Y-m-d H:i:s', 'Y-m-d H:i:s');
                return $converted . ' IST';
            },
            $text
        );

        // Explicit UTC labels, including the public /connect information page.
        $text = (string) preg_replace_callback(
            '/\\b(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})\\s+UTC\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'Y-m-d H:i:s', 'Y-m-d H:i:s') . ' IST';
            },
            $text
        );
        $text = (string) preg_replace_callback(
            '/\\b(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2})\\s+UTC\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'Y-m-d H:i', 'Y-m-d H:i') . ' IST';
            },
            $text
        );

        // Raw SQL-style UTC timestamps rendered by management/security pages.
        $text = (string) preg_replace_callback(
            '/\\b(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'Y-m-d H:i:s', 'Y-m-d H:i:s');
            },
            $text
        );
        $text = (string) preg_replace_callback(
            '/\\b(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'Y-m-d H:i', 'Y-m-d H:i');
            },
            $text
        );

        // Existing pretty formats used by dashboard, packages and announcements.
        $text = (string) preg_replace_callback(
            '/\\b(\\d{2} [A-Z][a-z]{2} \\d{4})\\s*·\\s*(\\d{2}:\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'd M Y H:i:s', 'd M Y · H:i:s');
            },
            $text
        );
        $text = (string) preg_replace_callback(
            '/\\b(\\d{2} [A-Z][a-z]{2} \\d{4})\\s*·\\s*(\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'd M Y H:i', 'd M Y · H:i');
            },
            $text
        );
        $text = (string) preg_replace_callback(
            '/\\b(\\d{2} [A-Z][a-z]{2} \\d{4}) (\\d{2}:\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'd M Y H:i:s', 'd M Y H:i:s');
            },
            $text
        );
        $text = (string) preg_replace_callback(
            '/\\b(\\d{2} [A-Z][a-z]{2} \\d{4}) (\\d{2}:\\d{2})\\b/',
            static function (array $m): string {
                return self::convertExact($m[1] . ' ' . $m[2], 'd M Y H:i', 'd M Y H:i');
            },
            $text
        );

        return $text;
    }

    private static function transformDateTimeLocalTag(string $tag): string
    {
        return (string) preg_replace_callback(
            '/\\bvalue=("|\')(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?)\\1/i',
            static function (array $match): string {
                $value = $match[2];
                $hasSeconds = strlen($value) === 19;
                $inputFormat = $hasSeconds ? 'Y-m-d\\TH:i:s' : 'Y-m-d\\TH:i';
                $date = self::parseExact($value, $inputFormat, self::utcTimezone());
                if ($date === null) {
                    return $match[0];
                }
                $converted = $date->setTimezone(self::displayTimezone())->format($inputFormat);
                return 'value=' . $match[1] . $converted . $match[1];
            },
            $tag
        );
    }

    private static function normalizeIncomingDateTimeFields(): void
    {
        foreach (['expiry_date', 'ann_expires'] as $field) {
            if (!isset($_POST[$field]) || is_array($_POST[$field])) {
                continue;
            }

            $value = trim((string) $_POST[$field]);
            if ($value === '') {
                continue;
            }

            $date = null;
            foreach (['Y-m-d\\TH:i:s', 'Y-m-d\\TH:i', 'Y-m-d H:i:s', 'Y-m-d H:i'] as $format) {
                $date = self::parseExact($value, $format, self::displayTimezone());
                if ($date !== null) {
                    break;
                }
            }

            if ($date !== null) {
                $_POST[$field] = $date->setTimezone(self::utcTimezone())->format('Y-m-d H:i:s');
            }
        }
    }

    private static function convertExact(string $value, string $inputFormat, string $outputFormat): string
    {
        $date = self::parseExact($value, $inputFormat, self::utcTimezone());
        if ($date === null) {
            return $value;
        }
        return $date->setTimezone(self::displayTimezone())->format($outputFormat);
    }

    private static function parseExact(string $value, string $format, DateTimeZone $timezone): ?DateTimeImmutable
    {
        $date = DateTimeImmutable::createFromFormat('!' . $format, $value, $timezone);
        if (!$date instanceof DateTimeImmutable) {
            return null;
        }

        $errors = DateTimeImmutable::getLastErrors();
        if (is_array($errors) && ((int) ($errors['warning_count'] ?? 0) > 0 || (int) ($errors['error_count'] ?? 0) > 0)) {
            return null;
        }

        if ($date->format($format) !== $value) {
            return null;
        }

        return $date;
    }

    private static function displayTimezone(): DateTimeZone
    {
        if (self::$displayTimezone instanceof DateTimeZone) {
            return self::$displayTimezone;
        }

        $configured = trim((string) (getenv('DISPLAY_TIMEZONE') ?: self::DEFAULT_DISPLAY_TIMEZONE));
        try {
            self::$displayTimezone = new DateTimeZone($configured);
        } catch (Throwable $e) {
            error_log('SDK Panel: invalid DISPLAY_TIMEZONE; falling back to Asia/Kolkata.');
            self::$displayTimezone = new DateTimeZone(self::DEFAULT_DISPLAY_TIMEZONE);
        }

        return self::$displayTimezone;
    }

    private static function utcTimezone(): DateTimeZone
    {
        if (!(self::$utcTimezone instanceof DateTimeZone)) {
            self::$utcTimezone = new DateTimeZone('UTC');
        }
        return self::$utcTimezone;
    }

    private static function isPanelUiRequest(): bool
    {
        $method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
        if (!in_array($method, ['GET', 'POST'], true)) {
            return false;
        }

        $path = parse_url((string) ($_SERVER['REQUEST_URI'] ?? ''), PHP_URL_PATH);
        $path = strtolower(is_string($path) ? rtrim($path, '/') : '');
        $script = strtolower(basename((string) ($_SERVER['SCRIPT_NAME'] ?? $_SERVER['PHP_SELF'] ?? '')));

        // Never touch API/webhook responses or their request payloads.
        foreach (['/api/connect', '/telegram/webhook', '/licenses/check'] as $suffix) {
            if ($path !== '' && str_ends_with($path, $suffix)) {
                return false;
            }
        }
        if (in_array($script, ['telegram_bot.php', 'check_license.php', 'get_devices.php', 'action.php'], true)) {
            return false;
        }

        // /connect GET is the public HTML information page; POST is the SDK API.
        if (($script === 'connect.php') || ($path !== '' && str_ends_with($path, '/connect'))) {
            return $method === 'GET';
        }

        return true;
    }
}
