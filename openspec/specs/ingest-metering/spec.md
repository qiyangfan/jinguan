## Purpose

计量接收 → 账本扣账:对齐 muniu `UsageRow`(PascalCase,Go 无 json tag,漂移即静默丢字段),按 APIKeyID 绑定归账,Bearer 静态 token。

## Requirements

### Requirement: ingest 行契约

`POST /ingest/usage` 必须接受 `{"rows":[UsageRow]}` 负载,行字段名逐字段对齐 muniu UsageRow(PascalCase record,禁止改 camelCase);认证必须 (MUST) 经 Bearer 静态 token(`jinguan.ingest.token`),token 比对必须 (MUST) 使用常数时间比较(如 MessageDigest.isEqual),消除时序侧信道。

#### Scenario: 错误 token 拒绝
- **WHEN** Bearer token 与配置不符
- **THEN** 401,整批不处理;比对耗时与错误位置无关

### Requirement: 逐行独立事务与部分成功

批量必须 (MUST) 逐行独立处理,单行失败只影响该行,返回计数结构 IngestResult(received/accepted/replayed/skippedUnbound/skippedFree/skippedInsufficient);整批回滚被禁止 (MUST NOT)——毒丸行不得永久阻塞重投。

#### Scenario: 毒丸行不阻断
- **WHEN** 批内含一行余额不足的行,其余正常
- **THEN** 正常行全部扣账成功,欠费行计 skippedInsufficient

### Requirement: 归账与幂等

行必须 (MUST) 经 api_key_bindings 按 APIKeyID 归账到用户;未绑定行跳过(skippedUnbound);Status 非 ok 或 CostMicro≤0 的行跳过(skippedFree);扣账 refId = "usage:{UsageRow.ID}",重投幂等计 replayed 不重复扣钱。

#### Scenario: 重投幂等
- **WHEN** 同一批行被 muniu 重投
- **THEN** 已扣行计 replayed,余额不变

### Requirement: 用量明细留存

ingest 收到的每一行必须 (MUST) 先于扣账语义幂等落库到 append-only `usage_records`(usage_id 全局唯一,镜像 UsageRow 关键字段 + 解析出的归属用户),并记录处理结果投影(final_outcome:accepted/replayed/skipped_unbound/skipped_free/skipped_insufficient);重投或充值后补账成功必须 (MUST) 更新投影与尝试计数——投影可更新,不属资金事实,账本 append-only 纪律不约束本表。

#### Scenario: 每行有解释
- **WHEN** 任意一行用量被处理(无论结果)
- **THEN** usage_records 中可查到该行:钱花在哪(accepted)、为什么没花(skipped_insufficient/unbound/free)、是否重投(attempts)

### Requirement: 账单查询

用户必须 (MUST) 能分页查询本人用量明细(新→旧);ADMIN 必须 (MUST) 能按用户查询;明细与账本流水可互相印证(USAGE 分录 ↔ accepted 明细行,经 refId="usage:{id}" 关联)。

#### Scenario: 争议对账
- **WHEN** 用户质疑一笔 USAGE 扣账
- **THEN** 经分录 refId 定位到明细行,核对模型/token/费用

### Requirement: 欠费停服联动

余额不足的行仍必须 (MUST) 跳过并计 skippedInsufficient(零账本痕迹,充值后由 muniu 重投自然补账);同时必须 (MUST) 自动置位该用户欠费停服(独立小事务、幂等置位),使其名下全部 key 进入吊销下发名单——数据面在一个拉取周期(默认 60s)内停止放行。

#### Scenario: 闭环收敛
- **WHEN** 欠费用户的用量行到达
- **THEN** 行被跳过计数,用户进入停服,其 key 最迟下一拉取周期被数据面拒绝
