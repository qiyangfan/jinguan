-- 锦官 V28:跨域流程 saga——状态机 + 每步幂等/超时/重试;无分布式事务/跨库写。
CREATE TABLE control_sagas (
    id              VARCHAR(36)  PRIMARY KEY,
    saga_type       VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    status          VARCHAR(16)  NOT NULL,   -- PENDING | RUNNING | SUCCEEDED | FAILED | COMPENSATING | COMPENSATED | BLOCKED
    context_json    TEXT         NOT NULL,
    reason          VARCHAR(512) NULL,
    actor           VARCHAR(64)  NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    closed_at       TIMESTAMP    NULL,
    close_reason    VARCHAR(512) NULL
);
CREATE INDEX idx_control_sagas_status ON control_sagas (status, updated_at);

CREATE TABLE saga_steps (
    id                    VARCHAR(36)  PRIMARY KEY,
    saga_id               VARCHAR(36)  NOT NULL,
    seq                   INT          NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    kind                  VARCHAR(16)  NOT NULL,   -- ADMIN | SERVICE_COMMAND
    action                VARCHAR(64)  NULL,
    action_schema_version INT          NULL,
    params_json           TEXT         NULL,
    command_id            VARCHAR(36)  NULL,
    status                VARCHAR(16)  NOT NULL,   -- PENDING | RUNNING | SUCCEEDED | FAILED | COMPENSATED | SKIPPED | BLOCKED
    attempts              INT          NOT NULL,
    error                 VARCHAR(1024) NULL,
    started_at            TIMESTAMP    NULL,
    finished_at           TIMESTAMP    NULL,
    CONSTRAINT uq_saga_step UNIQUE (saga_id, seq)
);
