<?php

declare(strict_types=1);

namespace ParallaxPanel;

use PDO;
use PDOException;

final class Database
{
    private static ?PDO $connection = null;

    public static function connection(): PDO
    {
        if (self::$connection) {
            return self::$connection;
        }
        $host = Env::get('DB_HOST');
        $name = Env::get('DB_NAME');
        $user = Env::get('DB_USER');
        if ($host === '' || $name === '' || $user === '') {
            throw new PDOException('Database settings are incomplete in .env.');
        }
        $dsn = sprintf(
            'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
            $host,
            max(1, (int) Env::get('DB_PORT', '3306')),
            $name
        );
        self::$connection = new PDO($dsn, $user, Env::get('DB_PASSWORD'), [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        self::$connection->exec("SET time_zone = '+00:00'");
        return self::$connection;
    }

    public static function installed(): bool
    {
        try {
            self::connection()->query('SELECT 1 FROM panel_users LIMIT 1');
            return true;
        } catch (\Throwable) {
            return false;
        }
    }

    public static function install(): void
    {
        $sql = file_get_contents(PANEL_ROOT . '/database/schema.sql');
        if ($sql === false) {
            throw new \RuntimeException('Database schema is missing.');
        }
        self::connection()->exec($sql);
    }
}
