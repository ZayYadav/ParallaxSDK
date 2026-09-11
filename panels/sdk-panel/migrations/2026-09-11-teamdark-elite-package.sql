-- TeamDark Elite one-time package binding migration
-- Scope is intentionally narrow: one SDK license + the existing TeamDark release certificate.
-- Run on the SDK panel database before using the com.team.dark.elite build.

START TRANSACTION;

UPDATE licenses
SET package_name = 'com.team.dark.elite'
WHERE license_key = 'SDK-8FB9E9C2AF9126A7C74250E5'
  AND package_name = 'com.team.dark'
  AND UPPER(package_mode) = 'SPECIFIC'
  AND UPPER(REPLACE(COALESCE(signing_cert_sha256, ''), ':', '')) =
      '95D42274430C198E20056DA00E5E4DCAFD5935D93D2E4380E2788B1B7FF8A32F'
  AND status = 1;

-- Move the existing device row to the new package identity. This is needed
-- because AndroidKeyStore creates a fresh app-scoped proof key for the new
-- applicationId. The API will then perform its normal same-package secure rebind.
UPDATE devices d
INNER JOIN licenses l ON l.license_key = d.license_key
SET d.package_name = 'com.team.dark.elite'
WHERE d.license_key = 'SDK-8FB9E9C2AF9126A7C74250E5'
  AND d.package_name = 'com.team.dark'
  AND (
      UPPER(l.package_mode) = 'ANY'
      OR l.package_name = 'com.team.dark.elite'
  )
  AND UPPER(REPLACE(COALESCE(l.signing_cert_sha256, ''), ':', '')) =
      '95D42274430C198E20056DA00E5E4DCAFD5935D93D2E4380E2788B1B7FF8A32F'
  AND l.status = 1;

COMMIT;

-- Verification only; these SELECTs do not expose secrets.
SELECT license_key, package_name, package_mode, signing_mode, signing_cert_sha256
FROM licenses
WHERE license_key = 'SDK-8FB9E9C2AF9126A7C74250E5';

SELECT device_id, package_name, status
FROM devices
WHERE license_key = 'SDK-8FB9E9C2AF9126A7C74250E5';
