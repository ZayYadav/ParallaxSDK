<?php
declare(strict_types=1);

$panelRoot = dirname(__DIR__);
require_once $panelRoot . '/app/Core/Router.php';

$routes = require $panelRoot . '/app/routes/web.php';
(new Router($panelRoot, $routes))->dispatch();
