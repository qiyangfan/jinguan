## MODIFIED Requirements

### Requirement: ingest 行契约

`POST /ingest/usage` 必须接受 `{"rows":[UsageRow]}` 负载,行字段名逐字段对齐 muniu UsageRow(PascalCase record,禁止改 camelCase);认证必须 (MUST) 经 Bearer 静态 token(`jinguan.ingest.token`),token 比对必须 (MUST) 使用常数时间比较(如 MessageDigest.isEqual),消除时序侧信道。

#### Scenario: 错误 token 拒绝
- **WHEN** Bearer token 与配置不符
- **THEN** 401,整批不处理;比对耗时与错误位置无关
