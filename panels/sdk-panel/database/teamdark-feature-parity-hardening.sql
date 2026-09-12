SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Audit hardening migration for TeamDark-style feature parity.
-- Safe to run repeatedly after teamdark-feature-parity.sql.
-- It does not modify connect.php or the SDK activation response contract.

-- Ensure license ownership exists even if an earlier development version of the
-- feature migration was already deployed.
SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND COLUMN_NAME='owner_user_id'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE licenses ADD COLUMN owner_user_id BIGINT UNSIGNED NULL AFTER generated_by',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='licenses' AND INDEX_NAME='idx_licenses_owner_user'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE licenses ADD INDEX idx_licenses_owner_user(owner_user_id,id)',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='licenses'
    AND CONSTRAINT_NAME='fk_licenses_owner_user'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE licenses ADD CONSTRAINT fk_licenses_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

UPDATE licenses l
JOIN users u ON u.username=l.generated_by
SET l.owner_user_id=u.id
WHERE l.owner_user_id IS NULL
  AND l.generated_by IS NOT NULL
  AND l.generated_by<>'';

-- Broadcast workers claim a recipient before contacting Telegram. The claim
-- timestamp lets another request safely recover an interrupted/stale worker.
SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='announcement_recipients' AND COLUMN_NAME='claimed_at'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE announcement_recipients ADD COLUMN claimed_at DATETIME NULL AFTER attempts',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

SET @sdk_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='announcement_recipients' AND INDEX_NAME='idx_broadcast_claim_recovery'
);
SET @sdk_sql := IF(@sdk_exists=0,
  'ALTER TABLE announcement_recipients ADD INDEX idx_broadcast_claim_recovery(broadcast_id,status,claimed_at,attempts,id)',
  'SELECT 1');
PREPARE sdk_stmt FROM @sdk_sql; EXECUTE sdk_stmt; DEALLOCATE PREPARE sdk_stmt;

-- Development builds before this hardening could leave a sending row without a
-- claim timestamp. Make it retryable; the delivery function still caps attempts.
UPDATE announcement_recipients
SET status='failed', claimed_at=NULL,
    last_error=CASE WHEN last_error='' THEN 'Recovered pre-hardening delivery claim' ELSE last_error END
WHERE status='sending' AND claimed_at IS NULL;

UPDATE sdk_feature_suite_meta
SET feature_version=GREATEST(feature_version,3)
WHERE feature_key='teamdark_feature_parity';

INSERT INTO sdk_feature_suite_meta(feature_key,feature_version)
SELECT 'teamdark_feature_parity',3
WHERE NOT EXISTS (
  SELECT 1 FROM sdk_feature_suite_meta WHERE feature_key='teamdark_feature_parity'
);
