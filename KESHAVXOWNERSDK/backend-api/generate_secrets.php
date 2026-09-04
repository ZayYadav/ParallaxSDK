<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

echo "ENCRYPTION_KEY=" . base64_encode(random_bytes(32)) . PHP_EOL;
echo "JWT_SECRET=" . base64_encode(random_bytes(64)) . PHP_EOL;
echo "ADMIN_API_KEY=" . bin2hex(random_bytes(32)) . PHP_EOL;
