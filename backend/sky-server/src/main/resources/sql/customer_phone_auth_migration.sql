-- Run once against sky_take_out after customer_phone_auth_preflight.sql returns no rows.
-- Empty phones represent accounts without a phone login; NULL permits multiple such rows.
UPDATE `user` SET phone = NULL WHERE phone IS NOT NULL AND TRIM(phone) = '';
ALTER TABLE `user` ADD COLUMN password VARCHAR(100) NULL AFTER openid;
CREATE UNIQUE INDEX uk_user_phone ON `user` (phone);
