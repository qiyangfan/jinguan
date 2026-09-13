-- 锦官 V7:拓扑下发——三件套运营态(topology_providers / topology_routes / topology_models),
-- GET /control/v1/topology 下发 + PUT /admin/topology 整包替换;对齐 muniu TopologyPayload 接收形态
-- 列类型纪律:避开 PG 专有类型,保持 H2 测试兼容;生产 PG 权威不变

-- 供应商定义:key 只存 env 引用名(绝不明文),真实上游 key 由各副本经自身环境变量解析
CREATE TABLE topology_providers (
    name       VARCHAR(128) PRIMARY KEY,
    adapter    VARCHAR(32)  NOT NULL,
    base_url   VARCHAR(255) NOT NULL,
    keys_json  TEXT         NOT NULL,
    weight     BIGINT       NOT NULL,
    priority   BIGINT       NOT NULL DEFAULT 0,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 模型路由:alias → 有序候选数组(candidates_json 顺序即 failover 次序)
CREATE TABLE topology_routes (
    alias           VARCHAR(128) PRIMARY KEY,
    candidates_json TEXT         NOT NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 模型能力目录(声明制):(provider, model) 二元组精确声明,零内置零通配
CREATE TABLE topology_models (
    provider          VARCHAR(128) NOT NULL,
    model             VARCHAR(255) NOT NULL,
    drop_params_json  TEXT         NULL,
    thinking_lock     BOOLEAN      NOT NULL DEFAULT FALSE,
    max_output_tokens BIGINT       NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider, model)
);
