## Purpose

计量接收 → 账本扣账:对齐 muniu `UsageRow`(PascalCase,Go 无 json tag,漂移即静默丢字段),按 APIKeyID 绑定归账,Bearer 静态 token。

## Requirements

### Requirement: ingest 行契约

`POST /ingest/usage` 必须接受 `{"rows":[UsageRow]}` 负载,行字段名逐字段对齐 muniu UsageRow(PascalCase record,禁止改 camelCase);认证必须 (MUST) 经 Bearer 静态 token(`jinguan.ingest.token`,配置比对)。

#### Scenario: 错误 token 拒绝
- **WHEN** Bearer token 与配置不符
- **THEN** 401,整批不处理

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

### Requirement: 欠费行为(现状)

余额不足的行当前 (SHALL) 跳过并计数,不留账本痕迹,待用户充值后由 muniu 下轮投递自然补账;当前 (SHALL) 无任何停服/吊销联动——欠费用户可继续使用(muniu 侧无余额预检,吊销下发未实现)。

#### Scenario: 现状缺口
- **WHEN** 用户余额耗尽后继续产生用量
- **THEN** 行被跳过,但数据面继续放行该 key(资金闭环缺最后一环)

### Requirement: 用量明细留存(现状)

当前 (SHALL) 仅扣账不留存行明细——用户无法在控制面查看"钱花在哪一行",争议无对账依据。

#### Scenario: 现状缺口
- **WHEN** 用户查看钱包流水
- **THEN** 只能看到 USAGE 扣账汇总分录,无模型/token 维度明细
