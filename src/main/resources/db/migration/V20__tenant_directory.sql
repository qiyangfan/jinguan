-- 锦官 V20:租户与成员目录——锦官是租户/成员/粗角色的唯一写者;
-- 团队/部门/细粒度权限码属服务域配置,不进入本域。
CREATE TABLE tenants (
    id         VARCHAR(36)  PRIMARY KEY,
    tenant_key VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    status     VARCHAR(16)  NOT NULL,   -- ACTIVE | DISABLED
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE tenant_members (
    id         VARCHAR(36) PRIMARY KEY,
    tenant_id  VARCHAR(36) NOT NULL,
    subject    VARCHAR(64) NOT NULL,    -- Keycloak subject 为唯一身份键
    role       VARCHAR(16) NOT NULL,    -- OWNER | ADMIN | MEMBER
    status     VARCHAR(16) NOT NULL,    -- ACTIVE | DISABLED
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_tenant_member UNIQUE (tenant_id, subject)
);
CREATE INDEX idx_tenant_members_subject ON tenant_members (subject);
