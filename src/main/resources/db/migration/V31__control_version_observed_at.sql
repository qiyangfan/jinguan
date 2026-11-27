-- 锦官 V31:快照观察时间随版本水位固化,保证同一版本 200/304 的响应体与签名一致。
ALTER TABLE control_versions ADD COLUMN observed_at TIMESTAMP;
UPDATE control_versions SET observed_at = CURRENT_TIMESTAMP WHERE observed_at IS NULL;
