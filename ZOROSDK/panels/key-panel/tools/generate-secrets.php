<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

echo 'SETUP_TOKEN=' . bin2hex(random_bytes(32)) . PHP_EOL;
echo 'TELEGRAM_WEBHOOK_SECRET=' . bin2hex(random_bytes(32)) . PHP_EOL;
