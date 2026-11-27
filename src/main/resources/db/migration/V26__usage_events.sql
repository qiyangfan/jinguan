-- 锦官 V26:多来源用量接收——通用 usage 事件与日聚合;
-- 健康事件只更新服务注册表 last_seen,不进入计费行。
CREATE TABLE usage_events (
    id              VARCHAR(36)  PRIMARY KEY,
    source          VARCHAR(64)  NOT NULL,   -- kongming | muniu(须与服务身份一致)
    event_id        VARCHAR(128) NOT NULL,
    tenant_key      VARCHAR(64)  NULL,
    subject         VARCHAR(64)  NULL,
    entitlement_key VARCHAR(128) NULL,
    metric          VARCHAR(64)  NOT NULL,
    quantity        BIGINT       NOT NULL,
    unit            VARCHAR(32)  NOT NULL,
    kind            VARCHAR(16)  NOT NULL,   -- PRODUCT | LLM
    occurred_at     TIMESTAMP    NOT NULL,
    received_at     TIMESTAMP    NOT NULL,
    aggregated_at   TIMESTAMP    NULL,
    CONSTRAINT uq_usage_event UNIQUE (source, event_id, metric)
);
CREATE INDEX idx_usage_events_aggregation ON usage_events (aggregated_at, occurred_at);
CREATE INDEX idx_usage_events_dimension ON usage_events (source, tenant_key, subject);

CREATE TABLE usage_daily_aggregates (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    source     VARCHAR(64) NOT NULL,
    tenant_key VARCHAR(64) NOT NULL DEFAULT '',
    subject    VARCHAR(64) NOT NULL DEFAULT '',
    metric     VARCHAR(64) NOT NULL,
    usage_day  DATE        NOT NULL,
    quantity   BIGINT      NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_usage_daily UNIQUE (source, tenant_key, subject, metric, usage_day)
);
