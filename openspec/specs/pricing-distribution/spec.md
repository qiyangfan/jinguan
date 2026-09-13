## Purpose

渠道/价目运营(ADMIN CRUD)与下发契约端点:对齐 muniu `admin.PricingView` 逐字段,数据面拉取生效,零重启。

## Requirements

### Requirement: 价目运营态

ADMIN 可经 `/admin/pricing` 对 alias 做 upsert(整体覆盖,刷新 updated_at)与删除;multiplier 必须匹配 `^\d+(\.\d{1,4})?$`,time_windows JSON 数组(window 非空 + multiplier 合法)在服务层与控制器双层校验,损坏数据必须 (MUST) 在读取时拒绝。

#### Scenario: 非法倍率拒绝
- **WHEN** 提交 multiplier="1.5.2"
- **THEN** 400,价目保持原值

### Requirement: 下发契约端点

`GET /control/v1/pricing` 必须 (MUST) 返回 `{"pricing":[PricingView]}`,字段 snake_case 逐字段对齐 muniu:alias/source/currency/input/cached_input/output/timezone/time_windows/updated_at;金额 credit(1e-9)转去尾零十进制字符串(scale 9,0→"0",与 muniu ParsePrice 口径一致)。

#### Scenario: 逐字段契约
- **WHEN** muniu 拉取价目
- **THEN** 响应可被 muniu 同构校验直接接受,无字段漂移

### Requirement: 拉取端点无认证(现状)

`GET /control/v1/pricing` 当前 (SHALL) 匿名放行,依赖部署网络隔离保护;muniu 侧增加拉取认证头为双方演进项(README 演进登记)。

#### Scenario: 现状形态
- **WHEN** 任意来源 GET /control/v1/pricing
- **THEN** 返回 200 与全量价目

### Requirement: 改价即时生效

价目变更语义 = 即时生效于下次数据面拉取,无版本化/生效时间窗(历史无审计,当前 (SHALL) 接受该取舍;运营级审计随需求另立)。

#### Scenario: 改价经下次拉取生效

- **GIVEN** muniu 按固定间隔拉取价目
- **WHEN** ADMIN 经 `/admin/pricing` 更新某 alias 价目
- **THEN** 数据面下一个拉取周期即取到新价并按其计费,无需锦官或 muniu 任何重启动作
