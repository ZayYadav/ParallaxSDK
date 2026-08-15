SET NAMES utf8mb4;

ALTER TABLE server_settings
    ADD COLUMN IF NOT EXISTS broadcast_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER setting_value;

ALTER TABLE licenses
    ADD COLUMN IF NOT EXISTS package_lock TINYINT(1) NOT NULL DEFAULT 1 AFTER package_name,
    ADD COLUMN IF NOT EXISTS max_devices INT UNSIGNED NOT NULL DEFAULT 1 AFTER package_lock,
    ADD COLUMN IF NOT EXISTS force_logout TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN IF NOT EXISTS generated_by VARCHAR(64) NULL AFTER force_logout;

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS blocked TINYINT(1) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN IF NOT EXISTS app_name VARCHAR(120) NULL AFTER package_name,
    ADD COLUMN IF NOT EXISTS device_model VARCHAR(120) NULL AFTER app_name;

CREATE TABLE IF NOT EXISTS blocked_apps (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    package_name VARCHAR(191) NOT NULL,
    blocked_by VARCHAR(191) NULL,
    blocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_blocked_apps_package (package_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_nonces (
    nonce_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (nonce_hash),
    KEY idx_api_nonces_expiry (expires_at),
    KEY idx_api_nonces_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_rate_limits (
    bucket_hash CHAR(64) NOT NULL,
    window_start DATETIME NOT NULL,
    request_count INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (bucket_hash),
    KEY idx_api_rate_window (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    result VARCHAR(64) NOT NULL,
    device_id VARCHAR(128) NOT NULL DEFAULT '',
    ip_address VARCHAR(45) NOT NULL,
    details JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_api_audit_created (created_at),
    KEY idx_api_audit_device (device_id),
    KEY idx_api_audit_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE licenses
SET package_lock = 0
WHERE package_name IS NULL OR package_name = '';

INSERT INTO server_settings (setting_key, setting_value)
VALUES ('server_mode', 'online')
ON DUPLICATE KEY UPDATE setting_value = setting_value;
