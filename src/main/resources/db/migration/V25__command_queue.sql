-- 锦官 V25:跨服务运行时命令队列——有限动作枚举 + 幂等键 + claim/ack;
-- 本地运行命令不进入本表。
CREATE TABLE control_commands (
    id                    VARCHAR(36)  PRIMARY KEY,
    service_id            VARCHAR(64)  NOT NULL,
    action                VARCHAR(64)  NOT NULL,
    action_schema_version INT          NOT NULL,
    params_json           TEXT         NOT NULL,
    reason                VARCHAR(512) NOT NULL,
    actor                 VARCHAR(64)  NOT NULL,
    idempotency_key       VARCHAR(128) NOT NULL UNIQUE,
    status                VARCHAR(16)  NOT NULL,   -- PENDING | CLAIMED | SUCCEEDED | FAILED | DEAD_LETTER
    attempts              INT          NOT NULL,
    max_attempts          INT          NOT NULL,
    claimed_by            VARCHAR(128) NULL,
    claimed_at            TIMESTAMP    NULL,
    result_json           TEXT         NULL,
    error                 VARCHAR(1024) NULL,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    finished_at           TIMESTAMP    NULL
);
CREATE INDEX idx_control_commands_service_status ON control_commands (service_id, status);
CREATE INDEX idx_control_commands_status_time ON control_commands (status, updated_at);
