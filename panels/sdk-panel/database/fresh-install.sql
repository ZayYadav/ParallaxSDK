SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(191) NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('owner','admin','user') NOT NULL DEFAULT 'user',
    status TINYINT(1) NOT NULL DEFAULT 1,
    is_online TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    KEY idx_users_role_status (role,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS licenses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    license_key VARCHAR(96) NOT NULL,
    expiry_date DATETIME NOT NULL,
    status TINYINT UNSIGNED NOT NULL DEFAULT 1,
    package_name VARCHAR(191) NULL,
    package_lock TINYINT(1) NOT NULL DEFAULT 1,
    max_devices INT UNSIGNED NOT NULL DEFAULT 1,
    daemon TINYINT(1) NOT NULL DEFAULT 0,
    hide_root TINYINT(1) NOT NULL DEFAULT 0,
    toggle_expiry TINYINT(1) NOT NULL DEFAULT 0,
    force_logout TINYINT(1) NOT NULL DEFAULT 0,
    generated_by VARCHAR(64) NULL,
    blocked_by_server TINYINT(1) NOT NULL DEFAULT 0,
    last_blocked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_licenses_key (license_key),
    KEY idx_licenses_status_expiry (status,expiry_date),
    KEY idx_licenses_package (package_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(128) NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    app_name VARCHAR(120) NULL,
    device_model VARCHAR(120) NULL,
    license_key VARCHAR(96) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    status ENUM('connected','disconnected','blocked') NOT NULL DEFAULT 'connected',
    blocked TINYINT(1) NOT NULL DEFAULT 0,
    connected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_devices_device_id (device_id),
    KEY idx_devices_license_status (license_key,status),
    KEY idx_devices_last_seen (last_seen),
    CONSTRAINT fk_devices_license_key FOREIGN KEY (license_key)
        REFERENCES licenses (license_key) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activated_packages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    license_id BIGINT UNSIGNED NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    device_id VARCHAR(128) NULL,
    ip_address VARCHAR(45) NULL,
    usage_count INT UNSIGNED NOT NULL DEFAULT 0,
    is_allowed TINYINT(1) NOT NULL DEFAULT 1,
    last_used DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_activated_license_package (license_id,package_name),
    KEY idx_activated_device (device_id),
    CONSTRAINT fk_activated_license FOREIGN KEY (license_id)
        REFERENCES licenses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS referral_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    assigned_to ENUM('admin','user') NOT NULL DEFAULT 'user',
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    used_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_referral_code (code),
    KEY idx_referral_available (status,used_by),
    CONSTRAINT fk_referral_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_referral_user FOREIGN KEY (used_by)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS panel_settings (
    setting_key VARCHAR(64) NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_panel_setting_user FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS server_settings (
    setting_key VARCHAR(64) NOT NULL,
    setting_value TEXT NOT NULL,
    broadcast_version INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CREATE TABLE IF NOT EXISTS does not add columns to an older table. Keep this
-- import safe when a legacy SDK database already has server_settings.
ALTER TABLE server_settings
    ADD COLUMN IF NOT EXISTS broadcast_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER setting_value;

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    type ENUM('info','warning','success','danger') NOT NULL DEFAULT 'info',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_announcements_active_expiry (is_active,expires_at),
    CONSTRAINT fk_announcement_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcement_reads (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_announcement_user (announcement_id,user_id),
    CONSTRAINT fk_announcement_read_announcement FOREIGN KEY (announcement_id)
        REFERENCES announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_read_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alert_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    alert_type VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    sent_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_alert_created (created_at)
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

INSERT INTO server_settings (setting_key,setting_value,broadcast_version) VALUES
    ('server_mode','online',0),
    ('server_status','online',0),
    ('maintenance_message','Maintenance in progress',0),
    ('broadcast_message','',0)
ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key);

INSERT INTO panel_settings (setting_key,setting_value) VALUES
    ('panel_name','Parallax SDK Control'),
    ('panel_tagline','Parallax SDK Access Portal'),
    ('login_title','Parallax Login'),
    ('login_subtitle','Secure Sign In'),
    ('login_badge_text','Parallax'),
    ('dashboard_title','PARALLAX SDK DASHBOARD'),
    ('watermark_text','PARALLAX SECURED'),
    ('key_prefix','SDK'),
    ('theme_primary','#C9A84C'),
    ('theme_accent','#4F8EF7'),
    ('theme_bg1','#050810'),
    ('theme_bg2','#0f172a'),
    ('theme_bg3','#1e3a8a'),
    ('theme_bg4','#312e81'),
    ('sidebar_logo_url','logo.png'),
    ('footer_text','Parallax')
ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key);
