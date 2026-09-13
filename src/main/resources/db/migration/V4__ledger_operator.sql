-- 锦官 V4:资金操作审计——管理员发起的资金分录记录操作人(append-only 不变;历史行不回填)

ALTER TABLE ledger_entries ADD COLUMN operator_user_id BIGINT NULL;
