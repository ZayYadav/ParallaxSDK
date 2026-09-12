SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- TeamDark-style management features adapted to the existing Parallax SDK panel.
-- This migration is additive: it does not change connect.php, API v2/v3 crypto,
-- license/device validation, request fields, or response envelopes.

-- ---------- users ----------
ALTER TABLE users
  MODIFY role ENUM('owner','admin','reseller','user') NOT NULL DEFAULT 'user';

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='balance'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN balance BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER role',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='referred_by'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN referred_by BIGINT UNSIGNED NULL AFTER balance',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='telegram_chat_id'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN telegram_chat_id BIGINT NULL AFTER is_online',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='telegram_2fa_enabled'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN telegram_2fa_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER telegram_chat_id',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='telegram_2fa_enabled_at'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN telegram_2fa_enabled_at DATETIME NULL AFTER telegram_2fa_enabled',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='auth_version'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN auth_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER telegram_2fa_enabled_at',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='login_not_before'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN login_not_before DATETIME NULL AFTER auth_version',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='last_login_at'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN last_login_at DATETIME NULL AFTER auth_version',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='last_login_ip'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(45) NULL AFTER last_login_at',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND INDEX_NAME='uq_users_telegram_chat'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD UNIQUE INDEX uq_users_telegram_chat(telegram_chat_id)',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

-- Add the self-reference only when it is not already present. Existing orphaned
-- values remain NULL because the migration never backfills referred_by.
SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='users'
    AND CONSTRAINT_NAME='fk_users_referred_by'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE users ADD CONSTRAINT fk_users_referred_by FOREIGN KEY (referred_by) REFERENCES users(id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

-- ---------- referral upgrades ----------
ALTER TABLE referral_codes
  MODIFY assigned_to ENUM('admin','reseller','user') NOT NULL DEFAULT 'user';

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='referral_codes' AND COLUMN_NAME='grant_balance'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE referral_codes ADD COLUMN grant_balance BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER assigned_to',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='referral_codes' AND COLUMN_NAME='max_uses'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE referral_codes ADD COLUMN max_uses INT UNSIGNED NOT NULL DEFAULT 1 AFTER grant_balance',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='referral_codes' AND COLUMN_NAME='use_count'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE referral_codes ADD COLUMN use_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER max_uses',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='referral_codes' AND COLUMN_NAME='expires_at'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE referral_codes ADD COLUMN expires_at DATETIME NULL AFTER use_count',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='referral_codes' AND COLUMN_NAME='revoked_at'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE referral_codes ADD COLUMN revoked_at DATETIME NULL AFTER expires_at',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

-- Preserve historical one-use referrals. If an old code was consumed, its new
-- counter begins at one; otherwise it remains available once.
UPDATE referral_codes
SET use_count = CASE WHEN used_by IS NULL THEN use_count ELSE GREATEST(use_count,1) END,
    max_uses = GREATEST(max_uses,1);

CREATE TABLE IF NOT EXISTS referral_code_uses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  referral_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_referral_use_user (referral_id,user_id),
  KEY idx_referral_use_referral (referral_id),
  KEY idx_referral_use_user (user_id),
  KEY idx_referral_use_time (used_at),
  CONSTRAINT fk_referral_use_referral FOREIGN KEY (referral_id)
    REFERENCES referral_codes(id) ON DELETE CASCADE,
  CONSTRAINT fk_referral_use_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO referral_code_uses(referral_id,user_id,used_at)
SELECT id,used_by,COALESCE(used_at,created_at)
FROM referral_codes
WHERE used_by IS NOT NULL;

-- ---------- activity / balances ----------
CREATE TABLE IF NOT EXISTS panel_activity_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT UNSIGNED NULL,
  target_user_id BIGINT UNSIGNED NULL,
  action VARCHAR(80) NOT NULL,
  result VARCHAR(32) NOT NULL DEFAULT 'success',
  ip_address VARCHAR(45) NOT NULL DEFAULT '',
  metadata JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_panel_activity_created (created_at),
  KEY idx_panel_activity_actor (actor_user_id,created_at),
  KEY idx_panel_activity_target (target_user_id,created_at),
  KEY idx_panel_activity_action (action,created_at),
  CONSTRAINT fk_activity_actor FOREIGN KEY (actor_user_id)
    REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_activity_target FOREIGN KEY (target_user_id)
    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS balance_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  actor_user_id BIGINT UNSIGNED NULL,
  delta BIGINT NOT NULL,
  balance_after BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(160) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_balance_user_time (user_id,created_at),
  KEY idx_balance_actor_time (actor_user_id,created_at),
  CONSTRAINT fk_balance_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_balance_actor FOREIGN KEY (actor_user_id)
    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Telegram account layer ----------
CREATE TABLE IF NOT EXISTS telegram_users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  chat_id BIGINT NOT NULL,
  first_name VARCHAR(100) NOT NULL DEFAULT '',
  last_name VARCHAR(100) NOT NULL DEFAULT '',
  username VARCHAR(64) NOT NULL DEFAULT '',
  language_code VARCHAR(16) NOT NULL DEFAULT '',
  linked_user_id BIGINT UNSIGNED NULL,
  first_seen_at DATETIME NOT NULL,
  last_seen_at DATETIME NOT NULL,
  guest_last_key_at DATETIME NULL,
  guest_key_count INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_telegram_chat (chat_id),
  UNIQUE KEY uq_telegram_linked_user (linked_user_id),
  KEY idx_telegram_last_seen (last_seen_at),
  KEY idx_telegram_guest_key (guest_last_key_at),
  CONSTRAINT fk_telegram_linked_user FOREIGN KEY (linked_user_id)
    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telegram_link_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  requested_chat_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_telegram_link_hash (token_hash),
  KEY idx_telegram_link_user (user_id,expires_at),
  CONSTRAINT fk_telegram_link_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telegram_2fa_activation_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_telegram_2fa_activation_hash (token_hash),
  KEY idx_telegram_2fa_activation_user (user_id,expires_at),
  CONSTRAINT fk_telegram_2fa_activation_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telegram_login_challenges (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  code_hash CHAR(64) NOT NULL,
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_telegram_login_user (user_id,expires_at),
  CONSTRAINT fk_telegram_login_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS telegram_update_replays (
  update_id BIGINT NOT NULL,
  seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (update_id),
  KEY idx_telegram_replay_seen (seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- announcement queue ----------
CREATE TABLE IF NOT EXISTS announcement_broadcasts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  announcement_id BIGINT UNSIGNED NULL,
  message TEXT NOT NULL,
  target_panel_users TINYINT(1) NOT NULL DEFAULT 1,
  target_linked_telegram TINYINT(1) NOT NULL DEFAULT 0,
  target_guest_telegram TINYINT(1) NOT NULL DEFAULT 0,
  status ENUM('queued','sending','complete','failed') NOT NULL DEFAULT 'queued',
  sent_count INT UNSIGNED NOT NULL DEFAULT 0,
  failed_count INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_announcement_broadcast_status (status,created_at),
  CONSTRAINT fk_broadcast_announcement FOREIGN KEY (announcement_id)
    REFERENCES announcements(id) ON DELETE SET NULL,
  CONSTRAINT fk_broadcast_creator FOREIGN KEY (created_by)
    REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcement_recipients (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  broadcast_id BIGINT UNSIGNED NOT NULL,
  telegram_user_id BIGINT UNSIGNED NULL,
  chat_id BIGINT NOT NULL,
  status ENUM('queued','sending','sent','failed') NOT NULL DEFAULT 'queued',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NOT NULL DEFAULT '',
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_broadcast_chat (broadcast_id,chat_id),
  KEY idx_broadcast_recipient_status (broadcast_id,status,id),
  CONSTRAINT fk_recipient_broadcast FOREIGN KEY (broadcast_id)
    REFERENCES announcement_broadcasts(id) ON DELETE CASCADE,
  CONSTRAINT fk_recipient_telegram_user FOREIGN KEY (telegram_user_id)
    REFERENCES telegram_users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- owner-controlled panel settings ----------
INSERT INTO panel_settings(setting_key,setting_value) VALUES
  ('panel_online','1'),
  ('registration_open','1'),
  ('generation_open','1'),
  ('maintenance_message','The panel is temporarily under maintenance. Please try again later.'),
  ('site_announcement',''),
  ('splash_enabled','0'),
  ('splash_title','PARALLAX SDK'),
  ('splash_subtitle','Secure control plane'),
  ('splash_duration_ms','2000'),
  ('splash_version','1'),
  ('key_cost_per_day','1'),
  ('guest_key_enabled','1'),
  ('telegram_linked_generation_enabled','0')
ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key);

-- Keep replay/OTP tables bounded without requiring a scheduler.
DELETE FROM telegram_update_replays WHERE seen_at < UTC_TIMESTAMP() - INTERVAL 2 DAY;
DELETE FROM telegram_login_challenges WHERE expires_at < UTC_TIMESTAMP() - INTERVAL 1 DAY;
DELETE FROM telegram_link_tokens WHERE expires_at < UTC_TIMESTAMP() - INTERVAL 1 DAY;
DELETE FROM telegram_2fa_activation_tokens WHERE expires_at < UTC_TIMESTAMP() - INTERVAL 1 DAY;

-- Written last: runtime only enables the feature suite after the full migration completes.
CREATE TABLE IF NOT EXISTS sdk_feature_suite_meta (
  feature_key VARCHAR(64) NOT NULL,
  feature_version INT UNSIGNED NOT NULL,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (feature_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sdk_feature_suite_meta(feature_key,feature_version) VALUES('teamdark_feature_parity',1)
ON DUPLICATE KEY UPDATE feature_version=VALUES(feature_version);
