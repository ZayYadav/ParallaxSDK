<?php

declare(strict_types=1);

namespace KESHAVXOWNERPanel;

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
        // Safe upgrades for installations created by earlier standalone releases.
        try {
            self::connection()->exec('ALTER TABLE panel_users ADD COLUMN telegram_user_id BIGINT NULL AFTER password_hash');
        } catch (\Throwable) {
            // Column already exists.
        }
        try {
            self::connection()->exec('CREATE UNIQUE INDEX uq_panel_users_telegram ON panel_users (telegram_user_id)');
        } catch (\Throwable) {
            // Index already exists.
        }
        self::upgrade();
    }

    public static function upgrade(): void
    {
        $db = self::connection();
        $db->exec(
            "CREATE TABLE IF NOT EXISTS key_generation_options (
                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                option_type ENUM('game','duration') NOT NULL,
                option_value VARCHAR(64) NOT NULL,
                option_label VARCHAR(100) NOT NULL,
                sort_order SMALLINT UNSIGNED NOT NULL DEFAULT 1,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uq_generation_option (option_type,option_value),
                KEY idx_generation_option_order (option_type,sort_order,id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
        self::seedGenerationOptions($db);
    }

    private static function seedGenerationOptions(PDO $db): void
    {
        $statement = $db->prepare(
            'INSERT INTO key_generation_options (option_type,option_value,option_label,sort_order) VALUES (?,?,?,?)'
        );
        $count = $db->prepare('SELECT COUNT(*) FROM key_generation_options WHERE option_type=?');
        $defaults = [
            'game' => [
                ['PUBG', 'PUBG'],
            ],
            'duration' => [
                ['1', '1 Hour'],
                ['6', '6 Hours'],
                ['12', '12 Hours'],
                ['24', '1 Day'],
                ['72', '3 Days'],
                ['168', '7 Days'],
                ['360', '15 Days'],
                ['720', '30 Days'],
            ],
        ];
        foreach ($defaults as $type => $options) {
            $count->execute([$type]);
            if ((int) $count->fetchColumn() > 0) {
                continue;
            }
            foreach ($options as $index => [$value, $label]) {
                $statement->execute([$type, $value, $label, $index + 1]);
            }
        }
    }
}
