-- One-time migration for existing OneCore Integrity installations.
-- Back up the database before importing this file.

CREATE TABLE IF NOT EXISTS dashboard_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('owner', 'admin', 'user') NOT NULL DEFAULT 'user',
    balance_credits INT UNSIGNED NOT NULL DEFAULT 0,
    referral_code CHAR(16) NOT NULL,
    referred_by_user_id BIGINT UNSIGNED NULL,
    status ENUM('active', 'suspended') NOT NULL DEFAULT 'active',
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_dashboard_users_username (username),
    UNIQUE KEY uq_dashboard_users_referral (referral_code),
    KEY idx_dashboard_users_role_status (role, status),
    KEY idx_dashboard_users_referrer (referred_by_user_id),
    CONSTRAINT fk_dashboard_users_referrer
        FOREIGN KEY (referred_by_user_id) REFERENCES dashboard_users(id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS registration_invites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    assigned_role ENUM('owner', 'admin', 'user') NOT NULL DEFAULT 'user',
    initial_balance INT UNSIGNED NOT NULL DEFAULT 0,
    max_uses SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    use_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    status ENUM('active', 'revoked') NOT NULL DEFAULT 'active',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_registration_invites_hash (token_hash),
    KEY idx_registration_invites_status_expiry (status, expires_at),
    KEY idx_registration_invites_creator (created_by_user_id),
    CONSTRAINT fk_registration_invites_creator
        FOREIGN KEY (created_by_user_id) REFERENCES dashboard_users(id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS balance_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    delta_credits INT NOT NULL,
    balance_after INT UNSIGNED NOT NULL,
    reason VARCHAR(200) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_balance_transactions_user_time (user_id, created_at),
    KEY idx_balance_transactions_actor (actor_user_id),
    CONSTRAINT fk_balance_transactions_user
        FOREIGN KEY (user_id) REFERENCES dashboard_users(id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_balance_transactions_actor
        FOREIGN KEY (actor_user_id) REFERENCES dashboard_users(id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

ALTER TABLE license_keys
    ADD COLUMN created_by_user_id BIGINT UNSIGNED NULL AFTER id,
    ADD KEY idx_license_keys_creator (created_by_user_id),
    ADD CONSTRAINT fk_license_keys_creator
        FOREIGN KEY (created_by_user_id) REFERENCES dashboard_users(id)
        ON UPDATE CASCADE ON DELETE SET NULL;

INSERT INTO app_config (setting_key, setting_value) VALUES
    ('default_key_prefix', '"5OC"'),
    ('key_cost_credits', '1')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
