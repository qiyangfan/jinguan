-- 锦官 V3:key 吊销闭环——终态吊销(key_revocations)+ 欠费停服(users.suspended_at)+ 同步水位(sync_watermarks)
-- 列类型纪律:避开 PG 专有类型,保持 H2 测试兼容;生产 PG 权威不变

-- 终态吊销(安全事件,key 永久作废):append-only,唯一约束兜底幂等;不设 FK——吊销可先于绑定(对未绑定 key 预防性吊销)
CREATE TABLE key_revocations (
    id               BIGSERIAL PRIMARY KEY,
    key_id           VARCHAR(64)  NOT NULL UNIQUE,
    reason           VARCHAR(255) NOT NULL,
    operator_user_id BIGINT       NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 欠费停服(业务暂态,充值即恢复):ingest 余额不足自动置位,任何入账自动清除
ALTER TABLE users ADD COLUMN suspended_at TIMESTAMP NULL;

-- 控制面同步水位:任何名单变化(吊销/停服置位/恢复)必须与版本 bump 同事务——
-- muniu 仅应用 version >= 本地 的快照,漏 bump 即被数据面当旧快照忽略
CREATE TABLE sync_watermarks (
    name    VARCHAR(32) PRIMARY KEY,
    version BIGINT      NOT NULL DEFAULT 0
);
INSERT INTO sync_watermarks (name, version) VALUES ('key-revocations', 0);
