<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require dirname(__DIR__) . DIRECTORY_SEPARATOR . 'conn.php';

$problems = sdk_panel_schema_problems($conn);
if ($problems !== []) {
    fwrite(STDERR, "SDK panel schema is incomplete:\n- " . implode("\n- ", $problems) . "\n");
    exit(1);
}

fwrite(STDOUT, "SDK panel database schema is ready.\n");
