-- 锦官 V24:商店目录——只引用运行时 Bot 标识与运营字段,不复制 Bot 定义/版本。
CREATE TABLE catalog_entries (
    id              VARCHAR(36)  PRIMARY KEY,
    entry_key       VARCHAR(64)  NOT NULL UNIQUE,
    bot_key         VARCHAR(128) NOT NULL,
    bot_tenant_key  VARCHAR(64)  NULL,
    title           VARCHAR(128) NOT NULL,
    summary         VARCHAR(512) NULL,
    visibility      VARCHAR(16)  NOT NULL,   -- PUBLIC | TENANT
    tenant_id       VARCHAR(36)  NULL,       -- visibility=TENANT 时的可见租户
    entitlement_key VARCHAR(128) NULL,
    enabled         BOOLEAN      NOT NULL,
    featured        BOOLEAN      NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX idx_catalog_entries_visible ON catalog_entries (enabled, visibility);
