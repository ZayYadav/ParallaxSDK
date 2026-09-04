<?php
declare(strict_types=1);

require_once __DIR__ . '/conn.php';
panel_require_auth();

// The project previously shipped two independent license inventory pages.
// Keep old bookmarks working while using the secured canonical implementation.
header('Location: license_list.php', true, 302);
exit;
