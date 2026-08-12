<?php

namespace App\Models;

use CodeIgniter\Model;
use RuntimeException;
use Throwable;

class IntegrityLicenseModel extends Model
{
    protected $DBGroup = 'integrity';
    protected $table = 'license_keys';
    protected $primaryKey = 'id';
    protected $returnType = 'array';
    protected $useTimestamps = false;
    protected $allowedFields = [
        'key_hash',
        'key_prefix',
        'label',
        'status',
        'max_devices',
        'expires_at',
        'last_used_at',
    ];

    public function dashboardRows(int $limit = 500): array
    {
        $limit = max(1, min(1000, $limit));

        return $this->db->query(
            'SELECT l.id, l.key_prefix, l.label, l.status, l.max_devices,
                    l.expires_at, l.last_used_at, l.created_at, l.updated_at,
                    COUNT(b.id) AS devices_used
             FROM license_keys l
             LEFT JOIN device_license_bindings b ON b.license_key_id = l.id
             GROUP BY l.id, l.key_prefix, l.label, l.status, l.max_devices,
                      l.expires_at, l.last_used_at, l.created_at, l.updated_at
             ORDER BY l.id DESC
             LIMIT ' . $limit
        )->getResultArray();
    }

    public function metrics(): array
    {
        $row = $this->db->query(
            "SELECT COUNT(*) AS total,
                    COALESCE(SUM(status = 'active' AND expires_at > UTC_TIMESTAMP()), 0) AS active,
                    COALESCE(SUM(status = 'revoked'), 0) AS revoked,
                    COALESCE(SUM(status = 'active' AND expires_at <= UTC_TIMESTAMP()), 0) AS expired
             FROM license_keys"
        )->getRowArray() ?? [];

        return [
            'total' => (int) ($row['total'] ?? 0),
            'active' => (int) ($row['active'] ?? 0),
            'revoked' => (int) ($row['revoked'] ?? 0),
            'expired' => (int) ($row['expired'] ?? 0),
        ];
    }

    public function createActivationKeys(
        string $label,
        int $maxDevices,
        int $validDays,
        int $quantity
    ): array {
        $plainKeys = [];
        $expiresAt = gmdate('Y-m-d H:i:s', time() + ($validDays * 86400));

        $this->db->transStart();
        try {
            for ($index = 0; $index < $quantity; $index++) {
                $plainKey = self::generateActivationKey();
                $inserted = $this->insert([
                    'key_hash' => hash('sha256', $plainKey),
                    'key_prefix' => substr($plainKey, 0, 12),
                    'label' => $quantity > 1 ? $label . ' #' . ($index + 1) : $label,
                    'status' => 'active',
                    'max_devices' => $maxDevices,
                    'expires_at' => $expiresAt,
                ], false);

                if ($inserted === false) {
                    throw new RuntimeException('The activation key could not be stored.');
                }
                $plainKeys[] = $plainKey;
            }
        } catch (Throwable $throwable) {
            $this->db->transRollback();
            throw $throwable;
        }
        $this->db->transComplete();

        if ($this->db->transStatus() === false) {
            throw new RuntimeException('The activation-key transaction failed.');
        }

        return $plainKeys;
    }

    public function revokeWithDevices(int $licenseId): bool
    {
        $this->db->transStart();
        $this->db->query(
            "UPDATE license_keys SET status = 'revoked' WHERE id = ?",
            [$licenseId]
        );
        $this->db->query(
            "UPDATE devices d
             INNER JOIN device_license_bindings b ON b.device_id = d.device_id
             SET d.status = 'revoked'
             WHERE b.license_key_id = ?",
            [$licenseId]
        );
        $this->db->transComplete();

        return $this->db->transStatus();
    }

    public static function generateActivationKey(): string
    {
        $hex = strtoupper(bin2hex(random_bytes(16)));
        return 'OC-' . implode('-', str_split($hex, 4));
    }
}
