-- Run once against sky_take_out after customer_phone_auth_migration.sql.
-- Existing accounts keep their phone/display-name login fallback; new accounts receive a unique nickname.
ALTER TABLE `user` ADD COLUMN username VARCHAR(24) NULL AFTER name;
CREATE UNIQUE INDEX uk_user_username ON `user` (username);
