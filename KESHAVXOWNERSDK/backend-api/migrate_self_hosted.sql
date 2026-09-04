ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS device_public_key TEXT NULL AFTER device_id,
    ADD COLUMN IF NOT EXISTS device_public_key_sha256 CHAR(64) NULL AFTER device_public_key,
    ADD KEY idx_devices_public_key_sha256 (device_public_key_sha256);

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

INSERT INTO app_config (setting_key, setting_value) VALUES
    ('verification_mode', '"self_hosted"'),
    ('require_licensed', 'false')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
