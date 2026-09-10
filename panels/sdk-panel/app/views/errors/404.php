<?php
declare(strict_types=1);

header('Content-Type: text/html; charset=UTF-8');
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Route Not Found</title>
    <style>
        body{margin:0;min-height:100vh;display:grid;place-items:center;background:#050814;color:#f8fafc;font-family:Inter,system-ui,sans-serif}
        main{width:min(520px,calc(100% - 32px));padding:28px;border:1px solid rgba(255,255,255,.12);border-radius:18px;background:rgba(12,20,38,.9);box-shadow:0 18px 60px rgba(0,0,0,.36)}
        h1{margin:0 0 8px;font-size:1.35rem}p{color:#9ca3af;line-height:1.55}a{color:#93c5fd}
    </style>
</head>
<body>
<main>
    <h1>Route not found</h1>
    <p>The SDK panel route you opened does not exist. Go back to <a href="dashboard.php">Dashboard</a>.</p>
</main>
</body>
</html>
