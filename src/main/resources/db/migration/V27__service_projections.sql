-- 锦官 V27:服务只读投影缓存——last-known + observed_at/stale 判定;
-- 只存服务只读 API 返回的白名单数据,不落凭据。
CREATE TABLE service_projections (
    service_id   VARCHAR(64)  PRIMARY KEY,
    etag         VARCHAR(128) NULL,
    version      VARCHAR(64)  NULL,
    observed_at  TIMESTAMP    NULL,
    fetched_at   TIMESTAMP    NOT NULL,
    payload_json TEXT         NOT NULL
);
