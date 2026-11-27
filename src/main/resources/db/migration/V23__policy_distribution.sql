-- 锦官 V23:共享策略包下发——只存包引用/作用域/激活版本;规则体与规则类型留在服务域配置。
CREATE TABLE policy_packages (
    id                      VARCHAR(36)  PRIMARY KEY,
    package_key             VARCHAR(64)  NOT NULL UNIQUE,
    service_id              VARCHAR(64)  NOT NULL,
    scope                   VARCHAR(16)  NOT NULL,   -- GLOBAL | TENANT
    tenant_id               VARCHAR(36)  NULL,
    active_ruleset_version  VARCHAR(64)  NULL,
    previous_ruleset_version VARCHAR(64) NULL,
    active_version          INT          NOT NULL,
    status                  VARCHAR(16)  NOT NULL,   -- ACTIVE | SUSPENDED
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL
);
CREATE INDEX idx_policy_packages_service ON policy_packages (service_id, status);
