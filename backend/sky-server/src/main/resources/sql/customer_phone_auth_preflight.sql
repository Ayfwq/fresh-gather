-- Run this read-only check first. Resolve duplicate non-empty phone numbers
-- before running customer_phone_auth_migration.sql.
SELECT phone, COUNT(*) AS duplicate_count
FROM `user`
WHERE phone IS NOT NULL AND TRIM(phone) <> ''
GROUP BY phone
HAVING COUNT(*) > 1;
