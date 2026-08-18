SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS social_auth_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state_hash CHAR(64) NOT NULL,
    provider ENUM('google','facebook','x') NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    install_nonce_hash CHAR(64) NOT NULL,
    return_uri VARCHAR(255) NOT NULL,
    code_verifier VARCHAR(128) NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_social_auth_state (state_hash),
    KEY idx_social_auth_transaction_expiry (expires_at),
    KEY idx_social_auth_transaction_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS social_auth_users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider ENUM('google','facebook','x') NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    email VARCHAR(191) NULL,
    display_name VARCHAR(191) NOT NULL DEFAULT '',
    avatar_url VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_social_auth_provider_user (provider, provider_user_id),
    KEY idx_social_auth_user_email (email),
    KEY idx_social_auth_user_last_login (last_login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS social_auth_tickets (
    ticket_hash CHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    provider ENUM('google','facebook','x') NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    install_nonce_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_hash),
    KEY idx_social_auth_ticket_expiry (expires_at),
    KEY idx_social_auth_ticket_user (user_id),
    CONSTRAINT fk_social_auth_ticket_user FOREIGN KEY (user_id)
        REFERENCES social_auth_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS social_auth_sessions (
    session_hash CHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    package_name VARCHAR(191) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    install_nonce_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_hash),
    KEY idx_social_auth_session_expiry (expires_at),
    KEY idx_social_auth_session_user (user_id),
    KEY idx_social_auth_session_device (device_id),
    CONSTRAINT fk_social_auth_session_user FOREIGN KEY (user_id)
        REFERENCES social_auth_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
