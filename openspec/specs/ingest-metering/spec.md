## Purpose

计量接收 → 账本扣账:对齐 muniu `UsageRow`(PascalCase,Go 无 json tag,漂移即静默丢字段),按 APIKeyID 绑定归账,Bearer 静态 token。

## Requirements

### Requirement: ingest 行契约

`POST /ingest/usage` 必须接受 `{"rows":[UsageRow]}` 负载,行字段名逐字段对齐 muniu UsageRow(PascalCase record,禁止改 camelCase);认证必须 (MUST) 经 Bearer 静态 token(`jinguan.ingest.token`),token 比对必须 (MUST) 使用常数时间比较(如 MessageDigest.isEqual),消除时序侧信道。

#### Scenario: 错误 token 拒绝
- **WHEN** Bearer token 与配置不符
- **THEN** 401,整批不处理;比对耗时与错误位置无关

### Requirement: 逐行独立事务与部分成功

批量 SHALL 逐行独立处理,业务欠费 SHALL 只影响该行,返回既有 IngestResult(received/accepted/replayed/skippedUnbound/skippedFree/skippedInsufficient)。系统 SHALL NOT 因欠费整批回滚或永久阻塞其他行。每行 SHALL 在资金处理前可靠保存计费事实;2xx SHALL 表示需要处理的用量已可靠接收,不表示全部已结算。存储故障或同 ID 不同事实 SHALL 保持非 2xx 错误,不回滚此前已提交的其他行。

#### Scenario: 毒丸行不阻断

- **WHEN** 批内含一行余额不足的行,其余正常
- **THEN** 正常行全部扣账成功,欠费行计 skippedInsufficient 并可靠保留

#### Scenario: 可靠接收后允许暂缓扣账

- **GIVEN** 欠费行已可靠保存
- **WHEN** 接收端返回 2xx
- **THEN** 数据面可结束运输重试,控制面继续持有该行结算责任

#### Scenario: 明细保存失败不得确认成功

- **WHEN** 用量事实未能可靠保存
- **THEN** 不返回可靠接收的 2xx,不为该未保存行新增资金分录

### Requirement: 归账与幂等

行 SHALL 经 key 绑定确定归属;未绑定行保持 skippedUnbound,非成功或非正费用行保持 skippedFree。首次合法解析的用户 SHALL 固定,后续重绑 SHALL NOT 转移既有用量。相同用量 ID 的不可变计费事实 SHALL 一致,不一致 SHALL 在资金写入前报幂等冲突。扣账 refId SHALL 保持 usage:{UsageRow.ID},在线、恢复和重投 SHALL 共用同一资金通道且最多一笔分录。

#### Scenario: 重投幂等

- **WHEN** 同一批行被数据面重投
- **THEN** 已扣行计 replayed,余额不变

#### Scenario: 同 ID 不同事实拒绝

- **GIVEN** 一个用量 ID 已可靠记录
- **WHEN** 同 ID 携带不同 key、费用、币种或其他不可变计费事实
- **THEN** 返回幂等冲突,旧事实、归属与资金分录不改变

#### Scenario: 重绑不能转嫁历史用量

- **GIVEN** 一行欠费或已结算用量已固定到用户 A
- **WHEN** 同一 key 被改绑到用户 B,之后重投或恢复该旧行
- **THEN** 旧行仍属于 A,不向 B 扣款

### Requirement: 用量明细留存

每一行 SHALL 先于扣账可靠保存到既有用量明细,usage_id 全局唯一,原始计费事实不可改写。处理投影 SHALL 支持 pending 及既有 accepted/replayed/skipped_unbound/skipped_free/skipped_insufficient;pending 表示已接收但处理未完成。accepted/replayed SHALL 作为已结算终态,后续失败不得降级。恢复 SHALL 更新原行投影与尝试统计,不新建重复用量;每次接收或恢复尝试 SHALL 只计一次。

#### Scenario: 每行有解释

- **WHEN** 任意一行用量被处理,无论结果
- **THEN** 明细可解释已结算、待结算、未绑定、免费或失败状态及尝试记录

#### Scenario: 已结算投影不降级

- **GIVEN** 一行投影为 accepted 或 replayed
- **WHEN** 后续重试遭遇余额不足或绑定变化
- **THEN** 结算终态和原归属保持不变

#### Scenario: 扣款成功后修复投影

- **GIVEN** 资金分录已提交,但明细投影尚未更新时进程中断
- **WHEN** 恢复器再次处理
- **THEN** 根据同一幂等分录修复投影,不产生重复扣款

### Requirement: 账单查询

用户必须 (MUST) 能分页查询本人用量明细(新→旧);ADMIN 必须 (MUST) 能按用户查询;明细与账本流水可互相印证(USAGE 分录 ↔ accepted 明细行,经 refId="usage:{id}" 关联)。

#### Scenario: 争议对账
- **WHEN** 用户质疑一笔 USAGE 扣账
- **THEN** 经分录 refId 定位到明细行,核对模型/token/费用

### Requirement: 欠费停服联动

余额不足行 SHALL 计 skippedInsufficient,未成功时不新增账本分录,并作为已固定归属的待结算事实保留。该用户 SHALL 被置位业务停服,名单按既有拉取周期收敛。充值提交后系统 SHALL 在下一轮有界恢复中继续处理这些事实,不依赖数据面重投;仍不足 SHALL 保持或重新置位业务停服。业务停服 SHALL NOT 改成永久吊销。

#### Scenario: 闭环收敛

- **WHEN** 欠费用户的用量行到达
- **THEN** 行被跳过计数,用户进入停服,其 key 最迟下一拉取周期被数据面拒绝

#### Scenario: 充值后自动补扣

- **GIVEN** 欠费行已可靠接收,数据面已经结束运输重试
- **WHEN** Mock 支付或管理员入账提交,金额足以结算
- **THEN** 下一轮恢复完成补扣,原行变为已结算,最多产生一笔 usage 分录

#### Scenario: 充值不足仍可继续恢复

- **GIVEN** 充值不足以结清全部历史候选
- **WHEN** 恢复器处理
- **THEN** 只提交余额允许的扣款,余项保留,业务停服继续按既有名单收敛

### Requirement: 待结算恢复可重启且有界

恢复 SHALL 从已可靠保存且固定归属的 pending/欠费用量执行,按用户与原接收顺序有界处理。进程重启、通知缺失、单个长期欠费用户 SHALL NOT 导致其他用户永久饥饿;成功行退出候选后,其已经发生的用户尝试历史 SHALL 继续参与轮转,SHALL NOT 把仍有未触及用量的用户重新视为首次用户。未绑定、免费、已结算或归属不可验证记录 SHALL NOT 被当成新扣款候选。后台恢复 SHALL NOT 伪造 ingest 接收计数。

#### Scenario: 重启恢复未完成处理

- **GIVEN** 用量预留后或补扣期间进程退出
- **WHEN** 服务重启并执行首轮恢复
- **THEN** 未完成行继续处理,资金仍最多一次生效

#### Scenario: 长期欠费不阻塞其他用户

- **GIVEN** 一个用户余额不足,另一个用户可完成结算
- **WHEN** 执行有界恢复轮次
- **THEN** 余额不足项保留,另一个用户继续结算,扫描不无限循环

#### Scenario: 未绑定历史不自动追收

- **GIVEN** 一行记录为 skipped_unbound
- **WHEN** 之后新增绑定或周期恢复运行
- **THEN** 本恢复器不自动向新绑定用户扣该历史费用,记录继续可解释

#### Scenario: 成功行退出候选不重置用户轮转

- **GIVEN** 某用户本轮触及用量均已成功结算,但还有超过单轮上限的未触及 pending 行,其他用户也有候选
- **WHEN** 下一轮选择用户
- **THEN** 该用户已发生的尝试历史仍参与排序,不因成功行退出候选而再次获得首次优先级
