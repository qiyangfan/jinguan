-- 锦官 V19:控制快照版本水位——所有共享控制写操作在同一事务内递增,保证单调不回退。
CREATE TABLE control_versions (
    id      VARCHAR(32) PRIMARY KEY,
    version BIGINT      NOT NULL
);
INSERT INTO control_versions (id, version) VALUES ('snapshot', 1);
