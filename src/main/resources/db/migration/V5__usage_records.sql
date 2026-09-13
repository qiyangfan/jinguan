-- 锦官 V5:用量明细留存——ingest 每行镜像落库,记录归账结果投影(final_outcome),
-- 用户「钱花在哪一行」的对账依据;账本 append-only 纪律不约束本表(投影可更新)
CREATE TABLE usage_records (
    id                BIGSERIAL PRIMARY KEY,
    usage_id          VARCHAR(64)   NOT NULL UNIQUE,   -- = muniu UsageRow.ID(账本 refId = "usage:"+ID)
    key_id            VARCHAR(64)   NOT NULL,
    tenant_id         VARCHAR(64)   NULL,
    model_alias       VARCHAR(128)  NULL,
    upstream_model    VARCHAR(128)  NULL,
    status            VARCHAR(32)   NULL,
    error_class       VARCHAR(64)   NULL,
    prompt_tokens     BIGINT        NOT NULL DEFAULT 0,
    completion_tokens BIGINT        NOT NULL DEFAULT 0,
    reasoning_tokens  BIGINT        NOT NULL DEFAULT 0,
    cached_tokens     BIGINT        NOT NULL DEFAULT 0,
    cost_micros       BIGINT        NOT NULL DEFAULT 0,
    currency          VARCHAR(8)    NULL,
    source_created_at VARCHAR(64)   NULL,              -- muniu UsageRow.CreatedAt 原文(RFC3339)
    user_id           BIGINT        NULL,              -- 归账解析结果(未绑定 NULL)
    final_outcome     VARCHAR(32)   NOT NULL,          -- accepted|replayed|skipped_unbound|skipped_free|skipped_insufficient
    attempts          INT           NOT NULL DEFAULT 1,
    last_attempt_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_usage_records_user ON usage_records (user_id, id);
