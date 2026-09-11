-- TeamDark Elite one-time package binding migration
-- Scope: exact SDK key + exact TeamDark release signing certificate.

START TRANSACTION;

UPDATE licenses
SET package_name = 'com.team.dark.elite'
WHERE license_key = 'SDK-8FB9E9C2AF9126A7C74250E5'
  AND package_name = 'com.team.dark'
  AND UPPER(package_mode) = 'SPECIFIC'
  AND UPPER(REPLACE(COALESCE(signing_cert_sha256, ''), ':', '')) =
      '95D42274430C198E20056DA00E5E4DCAFD5935D93D2E4380E2788B1B7FF8A32F'
  AND status = 1;

UPDATE devices d
INNER JOIN licenses l ON l.license_key = d.license_key
SET d.package_name = 'com.team.dark.elite'
WHERE d.license_key = 'SDK-8FB9E9C2AF9126A7C74250E5'
  AND d.package_name = 'com.team.dark'
  AND l.package_name = 'com.team.dark.elite'
  AND UPPER(REPLACE(COALESCE(l.signing_cert_sha256, ''), ':', '')) =
      '95D42274430C198E20056DA00E5E4DCAFD5935D93D2E4380E2788B1B7FF8A32F'
  AND l.status = 1;

COMMIT;
