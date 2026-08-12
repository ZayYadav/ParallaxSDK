-- Run this only in the OneCore Integrity database if these tables do not
-- already exist. Existing tables and data are left untouched.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(191) NOT NULL,
    last_verified_at DATETIME NULL,
    status ENUM('active', 'revoked') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_devices_device_id (device_id),
    KEY idx_devices_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS license_keys (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    key_hash CHAR(64) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    label VARCHAR(100) NOT NULL,
    status ENUM('active', 'revoked') NOT NULL DEFAULT 'active',
    max_devices INT UNSIGNED NOT NULL DEFAULT 1,
    expires_at DATETIME NOT NULL,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_license_keys_hash (key_hash),
    KEY idx_license_keys_status_expires (status, expires_at),
    KEY idx_license_keys_prefix (key_prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_license_bindings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    license_key_id BIGINT UNSIGNED NOT NULL,
    device_id VARCHAR(191) NOT NULL,
    bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_license_device (license_key_id, device_id),
    KEY idx_bindings_device_id (device_id),
    CONSTRAINT fk_bindings_license
        FOREIGN KEY (license_key_id) REFERENCES license_keys(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_bindings_device
        FOREIGN KEY (device_id) REFERENCES devices(device_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
