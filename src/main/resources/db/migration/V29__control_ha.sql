-- 锦官 V29:控制面连续性——调度器租约与告警投递记录;
-- 不引入副本协调,多实例 HA 为后续触发项。
CREATE TABLE scheduler_leases (
    name       VARCHAR(64)  PRIMARY KEY,
    owner      VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE alert_events (
    id           VARCHAR(36)  PRIMARY KEY,
    severity     VARCHAR(16)  NOT NULL,   -- INFO | WARNING | CRITICAL
    source       VARCHAR(64)  NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    tenant_key   VARCHAR(64)  NULL,
    service_id   VARCHAR(64)  NULL,
    dedup_key    VARCHAR(128) NOT NULL,
    observed_at  TIMESTAMP    NOT NULL,
    payload_json TEXT         NOT NULL,
    runbook      VARCHAR(255) NULL,
    status       VARCHAR(16)  NOT NULL,   -- PENDING | DELIVERED | FAILED | ACKED
    attempts     INT          NOT NULL,
    last_error   VARCHAR(1024) NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    delivered_at TIMESTAMP    NULL,
    acked_at     TIMESTAMP    NULL,
    acked_by     VARCHAR(64)  NULL
);
CREATE INDEX idx_alert_events_dedup ON alert_events (dedup_key, created_at);
CREATE INDEX idx_alert_events_pending ON alert_events (status, created_at);
