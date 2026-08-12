<?php
declare(strict_types=1);

final class Database
{
    private PDO $pdo;

    public function __construct(array $config)
    {
        $dsn = sprintf(
            'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
            $config['host'],
            $config['port'],
            $config['name']
        );

        $this->pdo = new PDO($dsn, $config['user'], $config['password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::ATTR_STRINGIFY_FETCHES => false,
        ]);
        $this->pdo->exec("SET time_zone = '+00:00'");
    }

    public function pdo(): PDO
    {
        return $this->pdo;
    }

    public function execute(string $sql, array $parameters = []): PDOStatement
    {
        $statement = $this->pdo->prepare($sql);
        $statement->execute($parameters);
        return $statement;
    }

    public function fetchOne(string $sql, array $parameters = []): ?array
    {
        $row = $this->execute($sql, $parameters)->fetch();
        return $row === false ? null : $row;
    }

    public function fetchAll(string $sql, array $parameters = []): array
    {
        return $this->execute($sql, $parameters)->fetchAll();
    }

    public function transaction(callable $callback): mixed
    {
        $this->pdo->beginTransaction();
        try {
            $result = $callback($this);
            $this->pdo->commit();
            return $result;
        } catch (Throwable $throwable) {
            if ($this->pdo->inTransaction()) {
                $this->pdo->rollBack();
            }
            throw $throwable;
        }
    }

    public function getAppConfig(string $key, mixed $default = null): mixed
    {
        $row = $this->fetchOne(
            'SELECT setting_value FROM app_config WHERE setting_key = :setting_key',
            ['setting_key' => $key]
        );
        if ($row === null) {
            return $default;
        }

        try {
            return json_decode((string) $row['setting_value'], true, 32, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            return $default;
        }
    }

    public function getAppConfigMap(): array
    {
        $result = [];
        foreach ($this->fetchAll('SELECT setting_key, setting_value FROM app_config') as $row) {
            try {
                $result[$row['setting_key']] = json_decode(
                    (string) $row['setting_value'],
                    true,
                    32,
                    JSON_THROW_ON_ERROR
                );
            } catch (JsonException) {
                $result[$row['setting_key']] = null;
            }
        }
        return $result;
    }

    public function setAppConfig(string $key, mixed $value): void
    {
        $encoded = json_encode($value, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
        $this->execute(
            'INSERT INTO app_config (setting_key, setting_value)
             VALUES (:setting_key, :setting_value)
             ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)',
            ['setting_key' => $key, 'setting_value' => $encoded]
        );
    }

    public function consumeRateLimit(
        string $rateKey,
        string $endpoint,
        int $limitPerMinute
    ): array {
        $limitPerMinute = max(1, $limitPerMinute);
        $window = gmdate('Y-m-d H:i:00');
        $hash = hash('sha256', $rateKey);

        $this->execute(
            'INSERT INTO rate_limits (rate_key, endpoint, window_started_at, request_count)
             VALUES (:rate_key, :endpoint, :window_started_at, 1)
             ON DUPLICATE KEY UPDATE request_count = request_count + 1',
            [
                'rate_key' => $hash,
                'endpoint' => $endpoint,
                'window_started_at' => $window,
            ]
        );

        $row = $this->fetchOne(
            'SELECT request_count FROM rate_limits
             WHERE rate_key = :rate_key AND endpoint = :endpoint
               AND window_started_at = :window_started_at',
            [
                'rate_key' => $hash,
                'endpoint' => $endpoint,
                'window_started_at' => $window,
            ]
        );
        $count = (int) ($row['request_count'] ?? $limitPerMinute + 1);

        if (random_int(1, 100) === 1) {
            $this->execute(
                'DELETE FROM rate_limits WHERE window_started_at < UTC_TIMESTAMP() - INTERVAL 1 DAY'
            );
        }

        return [
            'allowed' => $count <= $limitPerMinute,
            'limit' => $limitPerMinute,
            'remaining' => max(0, $limitPerMinute - $count),
            'retry_after' => max(1, 60 - (int) gmdate('s')),
        ];
    }

    public function claimNonce(
        string $nonce,
        string $deviceId,
        int $windowSeconds
    ): bool {
        $nonceHash = hash('sha256', $nonce);
        $expiresAt = (new DateTimeImmutable('now', new DateTimeZone('UTC')))
            ->modify('+' . max(1, $windowSeconds) . ' seconds')
            ->format('Y-m-d H:i:s.u');
        try {
            $this->execute(
                'INSERT INTO request_nonces (nonce_hash, device_id, expires_at)
                 VALUES (:nonce_hash, :device_id, :expires_at)',
                [
                    'nonce_hash' => $nonceHash,
                    'device_id' => $deviceId,
                    'expires_at' => $expiresAt,
                ]
            );
        } catch (PDOException $exception) {
            if ((string) $exception->getCode() === '23000') {
                return false;
            }
            throw $exception;
        }

        if (random_int(1, 100) === 1) {
            $this->execute('DELETE FROM request_nonces WHERE expires_at < UTC_TIMESTAMP(6)');
        }
        return true;
    }
}
