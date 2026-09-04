SET NAMES utf8mb4;

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='mfa_enabled'), 'SELECT 1', 'ALTER TABLE users ADD COLUMN mfa_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER is_online');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='mfa_secret_enc'), 'SELECT 1', 'ALTER TABLE users ADD COLUMN mfa_secret_enc TEXT NULL AFTER mfa_enabled');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='mfa_confirmed_at'), 'SELECT 1', 'ALTER TABLE users ADD COLUMN mfa_confirmed_at DATETIME NULL AFTER mfa_secret_enc');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

-- MySQL does not support ADD COLUMN IF NOT EXISTS. Each guarded statement is
-- also accepted by MariaDB and preserves existing data on repeated imports.
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='server_settings' AND COLUMN_NAME='broadcast_version'), 'SELECT 1', 'ALTER TABLE server_settings ADD COLUMN broadcast_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER setting_value');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='package_lock'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN package_lock TINYINT(1) NOT NULL DEFAULT 1 AFTER package_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='client_name'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN client_name VARCHAR(120) NOT NULL DEFAULT \'\' AFTER license_key');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='package_mode'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN package_mode ENUM(\'SPECIFIC\',\'ANY\') NOT NULL DEFAULT \'SPECIFIC\' AFTER package_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='max_devices'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN max_devices INT UNSIGNED NOT NULL DEFAULT 1 AFTER package_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='signing_lock'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN signing_lock TINYINT(1) NOT NULL DEFAULT 1 AFTER package_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='signing_cert_sha256'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN signing_cert_sha256 CHAR(64) NULL AFTER signing_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='signing_mode'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN signing_mode ENUM(\'SPECIFIC\',\'AUTO\',\'ANY\') NOT NULL DEFAULT \'AUTO\' AFTER signing_lock');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='device_mode'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN device_mode ENUM(\'DISABLED\',\'SINGLE\',\'LIMITED\',\'UNLIMITED\') NOT NULL DEFAULT \'SINGLE\' AFTER signing_cert_sha256');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='java_native_auth'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN java_native_auth TINYINT(1) NOT NULL DEFAULT 1 AFTER max_devices');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='feature_policy'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN feature_policy JSON NULL AFTER java_native_auth');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='minimum_sdk_version'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN minimum_sdk_version INT UNSIGNED NOT NULL DEFAULT 3 AFTER feature_policy');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='latest_sdk_version'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN latest_sdk_version INT UNSIGNED NOT NULL DEFAULT 3 AFTER minimum_sdk_version');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='force_update'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN force_update TINYINT(1) NOT NULL DEFAULT 0 AFTER latest_sdk_version');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='blocked_versions'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN blocked_versions JSON NULL AFTER force_update');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='session_lifetime_seconds'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN session_lifetime_seconds INT UNSIGNED NOT NULL DEFAULT 600 AFTER blocked_versions');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='kill_switch'), 'SELECT 1', 'ALTER TABLE licenses ADD COLUMN kill_switch TINYINT(1) NOT NULL DEFAULT 0 AFTER session_lifetime_seconds');
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
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND COLUMN_NAME='client_public_key'), 'SELECT 1', 'ALTER TABLE devices ADD COLUMN client_public_key TEXT NULL AFTER blocked');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND COLUMN_NAME='client_key_fingerprint'), 'SELECT 1', 'ALTER TABLE devices ADD COLUMN client_key_fingerprint CHAR(64) NULL AFTER client_public_key');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND INDEX_NAME='uq_devices_device_id'), 'ALTER TABLE devices DROP INDEX uq_devices_device_id', 'SELECT 1');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='devices' AND INDEX_NAME='uq_devices_license_device'), 'SELECT 1', 'ALTER TABLE devices ADD UNIQUE KEY uq_devices_license_device (license_key,device_id)');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

CREATE TABLE IF NOT EXISTS user_recovery_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_recovery_user_unused (user_id,used_at),
    CONSTRAINT fk_recovery_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS api_request_ids (
    request_id_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id_hash),
    KEY idx_api_request_ids_expiry (expires_at),
    KEY idx_api_request_ids_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id CHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    license_id BIGINT UNSIGNED NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    signing_sha256 CHAR(64) NOT NULL,
    device_key_fingerprint CHAR(64) NOT NULL,
    sdk_version INT UNSIGNED NOT NULL,
    expires_at DATETIME NOT NULL,
    last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_api_sessions_session_id (session_id),
    UNIQUE KEY uq_api_sessions_token_hash (token_hash),
    KEY idx_api_sessions_license_expiry (license_id,expires_at),
    KEY idx_api_sessions_device (device_id),
    CONSTRAINT fk_api_sessions_license FOREIGN KEY (license_id)
        REFERENCES licenses (id) ON DELETE CASCADE
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

SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='license_id'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN license_id BIGINT UNSIGNED NULL AFTER details');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='client_name'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN client_name VARCHAR(120) NOT NULL DEFAULT \'\' AFTER license_id');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='package_name'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN package_name VARCHAR(191) NOT NULL DEFAULT \'\' AFTER client_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='signing_sha256'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN signing_sha256 CHAR(64) NOT NULL DEFAULT \'\' AFTER package_name');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='sdk_version'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN sdk_version INT UNSIGNED NULL AFTER signing_sha256');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;
SET @sdk_schema_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='api_audit_logs' AND COLUMN_NAME='request_id'), 'SELECT 1', 'ALTER TABLE api_audit_logs ADD COLUMN request_id VARCHAR(96) NOT NULL DEFAULT \'\' AFTER sdk_version');
PREPARE sdk_schema_statement FROM @sdk_schema_sql; EXECUTE sdk_schema_statement; DEALLOCATE PREPARE sdk_schema_statement;

UPDATE licenses
SET package_lock = 0
WHERE package_name IS NULL OR package_name = '';

UPDATE licenses
SET package_mode = IF(package_lock = 1, 'SPECIFIC', 'ANY'),
    signing_mode = CASE
        WHEN signing_lock = 0 THEN 'ANY'
        WHEN signing_cert_sha256 IS NULL OR signing_cert_sha256 = '' THEN 'AUTO'
        ELSE 'SPECIFIC'
    END,
    device_mode = CASE WHEN max_devices = 1 THEN 'SINGLE' ELSE 'LIMITED' END;

INSERT INTO server_settings (setting_key, setting_value)
VALUES ('server_mode', 'online')
ON DUPLICATE KEY UPDATE setting_value = setting_value;
