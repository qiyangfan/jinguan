## MODIFIED Requirements

### Requirement: 下发契约端点

`GET /control/v1/pricing` 必须 (MUST) 返回 `{"pricing":[PricingView]}`,字段 snake_case 逐字段对齐 muniu:alias/source/currency/input/cached_input/output/timezone/time_windows/updated_at;金额 credit(1e-9)转去尾零十进制字符串(scale 9,0→"0",与 muniu ParsePrice 口径一致)。

#### Scenario: 逐字段契约
- **WHEN** muniu 拉取价目
- **THEN** 响应可被 muniu 同构校验直接接受,无字段漂移
