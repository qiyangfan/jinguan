-- 并发续期宽限:墓碑记录轮换时刻与后继票链接,宽限窗口内同票重复提交返回同一后继票。
ALTER TABLE refresh_sessions ADD COLUMN rotated_at TIMESTAMP NULL;
ALTER TABLE refresh_sessions ADD COLUMN replaced_jti VARCHAR(64) NULL;
