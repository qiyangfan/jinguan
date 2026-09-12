-- 锦官 V1:用户/账本/mock 订单/渠道价目/key 绑定
-- 列类型纪律:避开 PG 专有类型(jsonb/uuid 原生),TEXT/BIGINT/TIMESTAMP 保持 H2 测试兼容;生产 PG 权威不变

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',   -- USER | ADMIN
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 钱包账本:append-only(应用层只 INSERT,禁止 UPDATE/DELETE),余额 = SUM(amount_micros)
-- 幂等:ref_id 全局唯一——重复事件(支付回调重推/计量重投)唯一约束兜底
CREATE TABLE ledger_entries (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    amount_micros BIGINT       NOT NULL,                  -- 有符号:+入账 -扣账;余额=SUM
    reason        VARCHAR(32)  NOT NULL,                  -- MANUAL_TOPUP | PAYMENT_TOPUP | USAGE
    ref_id        VARCHAR(128) NOT NULL,                  -- 幂等键:入账单号/订单号/muniu UsageRow.ID
    note          VARCHAR(255) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ledger_ref UNIQUE (ref_id)
);
CREATE INDEX idx_ledger_user ON ledger_entries (user_id, id);

-- mock 支付订单(演示渠道;真实 PSP adapter 待商业验证,契约不变)
CREATE TABLE mock_payment_orders (
    order_id      VARCHAR(64)  PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    amount_micros BIGINT       NOT NULL,
    currency      VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    status        VARCHAR(16)  NOT NULL DEFAULT 'CREATED', -- CREATED | PAID | EXPIRED
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at       TIMESTAMP    NULL
);

-- 渠道与价目(运营态;经 /control/v1/pricing 下发给 muniu 拉取,契约见 muniu cmd/muniu/main.go)
CREATE TABLE pricing_entries (
    alias               VARCHAR(128) PRIMARY KEY,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    input_micros        BIGINT       NOT NULL,             -- μ 单价(每 token),与 muniu InputMicro 同义
    cached_input_micros BIGINT       NOT NULL DEFAULT 0,
    output_micros       BIGINT       NOT NULL,
    timezone            VARCHAR(64)  NULL,
    time_windows_json   TEXT         NULL,                 -- [{"window":"peak","multiplier":"1.5"}] JSON 数组
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- muniu key → 控制面用户绑定(计量 ingest 按 key 归账扣费)
CREATE TABLE api_key_bindings (
    key_id        VARCHAR(64)  PRIMARY KEY,                -- muniu UsageRow.APIKeyID
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    bound_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
