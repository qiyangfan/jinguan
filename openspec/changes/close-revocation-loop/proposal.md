# 补齐 key 吊销闭环:三根管子之二(欠费停服 + 终态吊销)

## Why

「充值→消费→欠费停服」这个商业闭环,当前缺的恰恰是让用户停下的一环:

- `GET /control/v1/key-revocations` 在 README 与 SecurityConfig 登记为三根管子之一,但全仓库无任何实现(无端点/无表/无管理操作)——README 自己承诺的契约缺位;
- muniu 侧拉取循环已就绪(`internal/auth/auth.go:221`,全量快照替换 + version 单调防回退 + 60s 周期),数据面在等一个不存在的接口;
- 两头一拼的现行为:用户余额耗尽 → ingest 仅 `skippedInsufficient` 跳过 → 无机制叫停 → muniu 永远放行 → **欠费用户无限免费使用**。泄露 key 同样无法从控制面叫停。

被否决备选:①欠费直接终态吊销——用户充值后须重新生成+重新绑定 key,UX 灾难且滥用吊销语义,否决;②按余额阈值推导停服——域内无"阈值"概念,事件语义(最近一次扣账失败且其后无入账)才精确,否决;③增量下发(按 version 出增量)——muniu 消费语义是全量快照替换,增量会被当作全量洗掉未吊销 key,否决。

## What Changes

- **两种停服语义分离,线上共用一个名单**(muniu 契约只有一个 revoked 列表,不动数据面):
  - **终态吊销**(安全事件,key 永久作废):append-only `key_revocations` 表(reason + 操作人),ADMIN 手动触发;
  - **欠费停服**(业务暂态,充值即恢复):`users.suspended_at` 可恢复标记,ingest 余额不足自动置位,任何入账自动清除。
- 新端点 `GET /control/v1/key-revocations`:返回 终态吊销 ∪ 欠费用户名下全部 key 的全量快照,`{"version":N,"revoked":[...]}` 逐字段对齐 muniu(全量替换、version 单调)。
- 版本源:通用 `sync_watermarks` 单行计数表(后续管子复用),任何名单变化同事务 bump。
- 管理端点 `POST /admin/keys/{keyId}/revoke`(ADMIN,记录 reason 与操作人);已终态吊销的 key 拒绝重绑。
- ingest 欠费联动:余额不足 → 置 owner `suspended_at`(幂等);恢复 choke point 在 `WalletService.apply`(一切入账必经之处)。

## Capabilities

- `key-lifecycle`:ADDED(终态吊销 / 欠费停服 / 吊销下发端点 / 重绑守卫)
- `ingest-metering`:REMOVED「欠费行为(现状)」+ ADDED「欠费停服联动」
- `wallet-ledger`:ADDED(入账清除停服)

## Non-goals(防过度设计)

- 不做 key 生成/下发(muniu 数据面签发 key,锦官只做绑定与吊销,分工不变);
- 不做增量下发协议、不做 webhook 推送(拉取契约 muniu 已实现,单向对齐);
- 不做定时解封/宽限期(充值即恢复,事件驱动,无需调度);
- 不做吊销名单缓存/分页(key 数量级为千级,全量返回无压力);
- 不给 `/control/v1/*` 加认证(维持 README 登记的网络隔离现状,双方演进项另排)。

## Impact

- 迁移:V3(key_revocations 表 + users.suspended_at 列 + sync_watermarks 表);
- 模块:key-lifecycle 逻辑落 `ingest` 模块(绑定/吊销/下发同属身份映射域)与 `user`(suspended_at 字段)、`wallet`(恢复联动);Modulith 门禁须保持绿;
- 跨仓:muniu 侧零改动(消费端已实现);联调用 muniu cluster 形态配置 revocations_url 实测收敛。
