CREATE DATABASE IF NOT EXISTS onecore_integrity
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE onecore_integrity;

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(128) NOT NULL,
    device_public_key TEXT NULL,
    device_public_key_sha256 CHAR(64) NULL,
    last_verified_at DATETIME(6) NULL,
    status ENUM('active', 'revoked') NOT NULL DEFAULT 'active',
    current_token_hash CHAR(64) NULL,
    current_token_jti CHAR(32) NULL,
    token_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_devices_device_id (device_id),
    KEY idx_devices_status (status),
    KEY idx_devices_token_jti (current_token_jti),
    KEY idx_devices_public_key_sha256 (device_public_key_sha256)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS license_keys (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    key_hash CHAR(64) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    label VARCHAR(100) NOT NULL,
    status ENUM('active', 'revoked') NOT NULL DEFAULT 'active',
    max_devices SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    expires_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_license_keys_hash (key_hash),
    KEY idx_license_keys_status_expiry (status, expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS integrity_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(128) NOT NULL,
    integrity_verdict VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_success TINYINT(1) NOT NULL DEFAULT 0,
    details JSON NULL,
    PRIMARY KEY (id),
    KEY idx_integrity_logs_timestamp (timestamp),
    KEY idx_integrity_logs_device_time (device_id, timestamp),
    KEY idx_integrity_logs_success_time (is_success, timestamp)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_config (
    setting_key VARCHAR(128) NOT NULL,
    setting_value JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS revoked_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(128) NOT NULL,
    revoked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reason VARCHAR(500) NOT NULL,
    token_jti CHAR(32) NULL,
    PRIMARY KEY (id),
    KEY idx_revoked_tokens_device (device_id, revoked_at),
    KEY idx_revoked_tokens_jti (token_jti),
    CONSTRAINT fk_revoked_tokens_device
        FOREIGN KEY (device_id) REFERENCES devices(device_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS device_license_bindings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    license_key_id BIGINT UNSIGNED NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    bound_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_verified_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_license_device (license_key_id, device_id),
    KEY idx_bindings_device (device_id),
    CONSTRAINT fk_bindings_license
        FOREIGN KEY (license_key_id) REFERENCES license_keys(id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_bindings_device
        FOREIGN KEY (device_id) REFERENCES devices(device_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS security_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    severity ENUM('info', 'warning', 'critical') NOT NULL DEFAULT 'warning',
    ip_address VARCHAR(45) NOT NULL,
    details JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_security_events_time (created_at),
    KEY idx_security_events_device_time (device_id, created_at),
    KEY idx_security_events_severity_time (severity, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS request_nonces (
    nonce_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (nonce_hash),
    KEY idx_request_nonces_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS rate_limits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rate_key CHAR(64) NOT NULL,
    endpoint VARCHAR(128) NOT NULL,
    window_started_at DATETIME NOT NULL,
    request_count INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rate_limit_window (rate_key, endpoint, window_started_at),
    KEY idx_rate_limits_window (window_started_at)
) ENGINE=InnoDB;

INSERT INTO app_config (setting_key, setting_value) VALUES
    ('token_expiry_seconds', '600'),
    ('rate_limit_per_minute', '10'),
    ('allowed_cert_fingerprint', '"REPLACE_WITH_RELEASE_SIGNING_SHA256"'),
    ('verification_mode', '"self_hosted"'),
    ('required_device_verdict', '"MEETS_DEVICE_INTEGRITY"'),
    ('require_licensed', 'false')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
