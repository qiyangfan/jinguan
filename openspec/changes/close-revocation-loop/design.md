# 设计:吊销闭环

## D1 双语义拆分,一线上名单

muniu 线上协议只有一个 `revoked` 列表(ADR-0006,已实现),不动数据面。控制面内部区分两种来源,在下发端点合并:

| 语义 | 触发 | 存储 | 可逆性 |
|---|---|---|---|
| 终态吊销 | ADMIN 手动(泄露/封禁) | `key_revocations` append-only | 不可逆(key 作废,用户换新 key 重绑) |
| 欠费停服 | ingest 余额不足自动 | `users.suspended_at` | 可逆(任何入账自动清除) |

拆分理由:吊销是安全语义,停服是商业语义;混用会让"充值恢复"被迫走"重新绑 key",把可逆问题做成不可逆。muniu 侧 DeepSeek 402 的处理(归限流冷却而非 auth_failed,充值即恢复)与本设计的生命周期判断同源。

## D2 版本源:通用 sync_watermarks 单行表

muniu 仅应用 `version >= 本地` 的快照(auth.go:263),因此**任何名单变化都必须 bump 版本**,否则停服/恢复变化会被数据面当成旧快照忽略。用一张通用表承载(后续管子如拓扑下发复用):

```sql
CREATE TABLE sync_watermarks (
    name    VARCHAR(32) PRIMARY KEY,   -- 'key-revocations'
    version BIGINT      NOT NULL DEFAULT 0
);
-- 迁移种子:INSERT INTO sync_watermarks(name, version) VALUES ('key-revocations', 0)
```

bump 必须与状态变更同事务:`UPDATE sync_watermarks SET version = version + 1 WHERE name = ?`。端点先读名单再读版本存在竞态(读到新名单旧版本),可接受——数据面 60s 后重拉自然收敛,快照不丢(名单只增不减地短暂滞后一拍)。初始 version=0 返回 `{"version":0,"revoked":[]}`,muniu 启动即拉不会报错。

## D3 停服置位/清除的并发语义

- **清除(恢复)**:放 `WalletService.apply` 锁内——一切入账(支付/手工)必经之处,单 choke point,与入账同事务,不可能"入账成功但停服未解除"。
- **置位(欠费)**:ingest 捕获 InsufficientFunds 后独立小事务写 `suspended_at`(幂等:`WHERE suspended_at IS NULL` 才更新),**不进** WalletService 的 per-user 锁。最坏竞态:置位与并发入账清除交错,出现一拍"已充值但仍停服"——数据面 60s 内随下次拉取收敛,且用户再无扣账失败就不再置位。可接受,不为它把 ingest 扣账路径拉进锁竞争。

## D4 下发名单的派生查询

```
revoked = SELECT key_id FROM key_revocations
  UNION
  SELECT b.key_id FROM api_key_bindings b
    JOIN users u ON u.id = b.user_id
   WHERE u.suspended_at IS NOT NULL
ORDER BY key_id
```

终态吊销优先于绑定关系存在(即使绑定被移除,吊销记录独立生效);输出排序保证快照确定性,便于契约测试逐字段断言。

## D5 重绑守卫

`POST /admin/key-bindings` 绑定前检查 `key_revocations`:命中即 409(「key 已吊销,不可重新绑定」)。吊销是 key_id 的终态;用户须在 muniu 重新生成 key 后绑定新 key_id。欠费停服**不**阻断重绑/换绑(停服按 user 生效,新 key 同样纳入名下停服名单)。

## D6 认证维持现状

`/control/v1/key-revocations` 与 `/control/v1/pricing` 同样 permitAll + 网络隔离(README 已登记 muniu 拉取认证头为双方演进项)。本 change 不扩大范围。
