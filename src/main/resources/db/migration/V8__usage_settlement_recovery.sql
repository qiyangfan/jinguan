-- 锦官 V8:待结算恢复(recover-pending-usage-settlement)——控制面持有结算责任。
-- pending = 已可靠接收、尚未完成首次资金处理(预留后/扣账后中断均可恢复);
-- 恢复器候选 = final_outcome IN ('pending','skipped_insufficient') 且 user_id 已固定归属。
-- last_settlement_attempt_at 为内部轮转时间(每次实际结算尝试刷新,含已退出候选的行),
-- 供用户间公平排序,不进入请求契约;user_id 索引补强候选扫描与轮转聚合。
ALTER TABLE usage_records ADD COLUMN last_settlement_attempt_at TIMESTAMP NULL;
CREATE INDEX idx_usage_records_candidate ON usage_records (final_outcome, user_id);
CREATE INDEX idx_usage_records_user_settle ON usage_records (user_id, last_settlement_attempt_at);
