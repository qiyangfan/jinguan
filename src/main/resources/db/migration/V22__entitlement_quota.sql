-- 锦官 V22:套餐/配额/权益目录与租户绑定——能力键来自服务注册,不定义服务内部权限码。
CREATE TABLE plans (
    id         VARCHAR(36)  PRIMARY KEY,
    plan_key   VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    status     VARCHAR(16)  NOT NULL,   -- ACTIVE | DISABLED
    version    INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE plan_quotas (
    id          VARCHAR(36) PRIMARY KEY,
    plan_id     VARCHAR(36) NOT NULL,
    dimension   VARCHAR(64) NOT NULL,
    unit        VARCHAR(32) NOT NULL,
    limit_value BIGINT      NOT NULL,
    CONSTRAINT uq_plan_dimension UNIQUE (plan_id, dimension)
);

CREATE TABLE plan_entitlements (
    id              VARCHAR(36)  PRIMARY KEY,
    plan_id         VARCHAR(36)  NOT NULL,
    entitlement_key VARCHAR(128) NOT NULL,
    CONSTRAINT uq_plan_entitlement UNIQUE (plan_id, entitlement_key)
);

-- 每租户单行当前绑定;历史绑定变化由审计追溯。
CREATE TABLE tenant_plan_bindings (
    id         VARCHAR(36) PRIMARY KEY,
    tenant_id  VARCHAR(36) NOT NULL UNIQUE,
    plan_id    VARCHAR(36) NOT NULL,
    status     VARCHAR(16) NOT NULL,   -- ACTIVE | DISABLED
    bound_at   TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);
CREATE INDEX idx_tenant_plan_bindings_plan ON tenant_plan_bindings (plan_id);
