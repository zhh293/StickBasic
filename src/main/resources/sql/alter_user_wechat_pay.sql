-- User table adjustments for WeChat Pay integration
-- Provide both PostgreSQL and MySQL variants. Use the one matching your DB.

-- PostgreSQL
-- ALTER TABLE "user" ADD COLUMN wechat_openid VARCHAR(64);
-- ALTER TABLE "user" ADD COLUMN wechat_unionid VARCHAR(64);
-- ALTER TABLE "user" ADD COLUMN phone VARCHAR(20);
-- ALTER TABLE "user" ADD COLUMN vip_level INT DEFAULT 0;
-- ALTER TABLE "user" ADD COLUMN vip_expire_at TIMESTAMP NULL;
-- ALTER TABLE "user" ADD COLUMN total_paid NUMERIC(12,2) DEFAULT 0.00;
-- ALTER TABLE "user" ADD COLUMN last_paid_at TIMESTAMP NULL;

-- MySQL
-- ALTER TABLE `user` ADD COLUMN `wechat_openid` VARCHAR(64) NULL;
-- ALTER TABLE `user` ADD COLUMN `wechat_unionid` VARCHAR(64) NULL;
-- ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(20) NULL;
-- ALTER TABLE `user` ADD COLUMN `vip_level` INT DEFAULT 0;
-- ALTER TABLE `user` ADD COLUMN `vip_expire_at` DATETIME NULL;
-- ALTER TABLE `user` ADD COLUMN `total_paid` DECIMAL(12,2) DEFAULT 0.00;
-- ALTER TABLE `user` ADD COLUMN `last_paid_at` DATETIME NULL;