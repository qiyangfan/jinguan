-- 锦官 V18:控制面管理审计——只增记录,在线保留 >=180 天;
-- 归档表承接到期行,清理只允许独立 job 在保留期后执行并留痕。
CREATE TABLE control_audit_log (
    id           VARCHAR(36)  PRIMARY KEY,
    occurred_at  TIMESTAMP    NOT NULL,
    actor        VARCHAR(128) NOT NULL,
    actor_kind   VARCHAR(16)  NOT NULL,   -- USER | SERVICE | SYSTEM
    actor_role   VARCHAR(32)  NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NULL,
    target_id    VARCHAR(128) NULL,
    tenant_key   VARCHAR(64)  NULL,
    reason       VARCHAR(512) NULL,
    summary_json TEXT         NULL,       -- 已脱敏摘要,禁止字段值/凭据
    result       VARCHAR(16)  NOT NULL,   -- SUCCESS | DENIED | FAILED
    request_id   VARCHAR(64)  NULL
);
CREATE INDEX idx_control_audit_actor_time ON control_audit_log (actor, occurred_at);
CREATE INDEX idx_control_audit_action_time ON control_audit_log (action, occurred_at);
CREATE INDEX idx_control_audit_time ON control_audit_log (occurred_at);

CREATE TABLE control_audit_archive (
    id           VARCHAR(36)  PRIMARY KEY,
    occurred_at  TIMESTAMP    NOT NULL,
    actor        VARCHAR(128) NOT NULL,
    actor_kind   VARCHAR(16)  NOT NULL,
    actor_role   VARCHAR(32)  NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NULL,
    target_id    VARCHAR(128) NULL,
    tenant_key   VARCHAR(64)  NULL,
    reason       VARCHAR(512) NULL,
    summary_json TEXT         NULL,
    result       VARCHAR(16)  NOT NULL,
    request_id   VARCHAR(64)  NULL,
    archived_at  TIMESTAMP    NOT NULL
);
