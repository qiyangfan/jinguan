-- 锦官 V21:服务注册与心跳——只存状态,不做服务发现;
-- 注册 payload 不含只读 API endpoint,地址由部署配置静态映射。
CREATE TABLE service_instances (
    id                      VARCHAR(36)  PRIMARY KEY,
    service_id              VARCHAR(64)  NOT NULL,
    instance_id             VARCHAR(128) NOT NULL,
    environment             VARCHAR(32)  NOT NULL,
    version                 VARCHAR(64)  NOT NULL,
    capabilities_json       TEXT         NOT NULL,
    admin_capabilities_json TEXT         NOT NULL,
    registered_at           TIMESTAMP    NOT NULL,
    last_seen_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uq_service_instance UNIQUE (service_id, instance_id)
);
CREATE INDEX idx_service_instances_service ON service_instances (service_id, last_seen_at);
