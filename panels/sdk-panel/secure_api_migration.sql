SET NAMES utf8mb4;

-- MySQL does not support ADD COLUMN IF NOT EXISTS. Each guarded statement is
-- also accepted by MariaDB and preserves existing data on repeated imports.
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='server_settings' AND COLUMN_NAME='broadcast_version'), 'SELECT 1', 'ALTER TABLE server_settings ADD COLUMN broadcast_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER setting_value');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='package_lock'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN package_lock TINYINT(1) NOT NULL DEFAULT 1 AFTER package_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='max_devices'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN max_devices INT UNSIGNED NOT NULL DEFAULT 1 AFTER package_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='force_logout'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN force_logout TINYINT(1) NOT NULL DEFAULT 0 AFTER status');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='generated_by'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN generated_by VARCHAR(64) NULL AFTER force_logout');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND COLUMN_NAME='blocked'), 'SELECT 1', 'ALTER TABLE devices ADD COLUMN blocked TINYINT(1) NOT NULL DEFAULT 0 AFTER status');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND COLUMN_NAME='app_name'), 'SELECT 1', 'ALTER TABLE devices ADD COLUMN app_name VARCHAR(120) NULL AFTER package_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND COLUMN_NAME='device_model'), 'SELECT 1', 'ALTER TABLE devices ADD COLUMN device_model VARCHAR(120) NULL AFTER app_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

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
