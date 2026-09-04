SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS panel_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    telegram_user_id BIGINT NULL,
    role ENUM('owner','admin','reseller') NOT NULL DEFAULT 'reseller',
    balance_credits INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('active','suspended') NOT NULL DEFAULT 'active',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_panel_users_username (username),
    UNIQUE KEY uq_panel_users_telegram (telegram_user_id),
    KEY idx_panel_users_role_status (role,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS keys_code (
    id_keys BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    game VARCHAR(64) NOT NULL,
    user_key VARCHAR(128) NOT NULL,
    duration INT UNSIGNED NOT NULL,
    expired_date DATETIME NULL,
    max_devices SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    devices TEXT NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    registrator VARCHAR(32) NOT NULL,
    admin_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_keys),
    UNIQUE KEY uq_keys_code_user_key (user_key),
    KEY idx_keys_game_status_expiry (game,status,expired_date),
    KEY idx_keys_registrator (registrator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS key_generation_options (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS onoff (
    id TINYINT UNSIGNED NOT NULL,
    status ENUM('on','off') NOT NULL DEFAULT 'off',
    myinput VARCHAR(255) NOT NULL DEFAULT 'Maintenance in progress',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS modname (
    id TINYINT UNSIGNED NOT NULL,
    modname VARCHAR(100) NOT NULL DEFAULT 'Parallax',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `_ftext` (
    id TINYINT UNSIGNED NOT NULL,
    `_status` VARCHAR(32) NOT NULL DEFAULT 'on',
    `_ftext` VARCHAR(255) NOT NULL DEFAULT 'Parallax',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `Feature` (
    id TINYINT UNSIGNED NOT NULL,
    ESP ENUM('on','off') NOT NULL DEFAULT 'off',
    Item ENUM('on','off') NOT NULL DEFAULT 'off',
    AIM ENUM('on','off') NOT NULL DEFAULT 'off',
    SilentAim ENUM('on','off') NOT NULL DEFAULT 'off',
    BulletTrack ENUM('on','off') NOT NULL DEFAULT 'off',
    Floating ENUM('on','off') NOT NULL DEFAULT 'off',
    Memory ENUM('on','off') NOT NULL DEFAULT 'off',
    Setting ENUM('on','off') NOT NULL DEFAULT 'off',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS connect_rate_limits (
    rate_key CHAR(64) NOT NULL,
    window_started_at DATETIME NOT NULL,
    request_count SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (rate_key,window_started_at),
    KEY idx_connect_rate_expiry (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_rate_limits (
    rate_key CHAR(64) NOT NULL,
    window_started_at DATETIME NOT NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (rate_key,window_started_at),
    KEY idx_login_rate_expiry (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS api_nonces (
    nonce_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (nonce_hash),
    KEY idx_api_nonces_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telegram_updates (
    update_id BIGINT NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (update_id),
    KEY idx_telegram_updates_time (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action_name VARCHAR(64) NOT NULL,
    target_value VARCHAR(191) NOT NULL DEFAULT '',
    ip_address VARCHAR(45) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_actor_time (actor_user_id,created_at),
    KEY idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO onoff (id,status,myinput) VALUES (1,'off','Maintenance in progress')
    ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO modname (id,modname) VALUES (1,'Parallax') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO `_ftext` (id,`_status`,`_ftext`) VALUES (1,'on','Parallax') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO `Feature` (id) VALUES (1) ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO key_generation_options (option_type,option_value,option_label,sort_order) VALUES
    ('game','PUBG','PUBG',1),
    ('duration','1','1 Hour',1),
    ('duration','6','6 Hours',2),
    ('duration','12','12 Hours',3),
    ('duration','24','1 Day',4),
    ('duration','72','3 Days',5),
    ('duration','168','7 Days',6),
    ('duration','360','15 Days',7),
    ('duration','720','30 Days',8)
ON DUPLICATE KEY UPDATE option_label=VALUES(option_label),sort_order=VALUES(sort_order);
