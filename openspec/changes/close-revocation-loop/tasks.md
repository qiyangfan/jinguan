# 任务清单:吊销闭环

> 纪律:1.x 先行(测试先行,先红后绿);每节收口跑 `make check` 全绿。
> 跨仓契约以 muniu `internal/auth/auth.go` revocationLoop 为权威,逐字段对齐。

## 1. 契约端点与数据模型

- [ ] 1.1 迁移 V3:`key_revocations`(id BIGSERIAL PK, key_id UNIQUE, reason VARCHAR(255), operator_user_id BIGINT NULL, created_at)+ `users.suspended_at TIMESTAMP NULL` + `sync_watermarks`(种子行 version=0);列类型维持 H2 兼容纪律
- [ ] 1.2 `GET /control/v1/key-revocations`:全量快照 `{"version":N,"revoked":[...]}`(终态 ∪ 欠费名下,ORDER BY key_id);契约测试钉死字段名/类型/单调/空态 `{"version":0,"revoked":[]}`,先红后绿
- [ ] 1.3 `sync_watermarks` bump 与状态变更同事务;并发测试:连续变更 version 严格递增

## 2. 终态吊销

- [ ] 2.1 ADMIN `POST /admin/keys/{keyId}/revoke`(reason 必填):写 key_revocations + bump,重复吊销幂等;operator_user_id 取当前 ADMIN
- [ ] 2.2 重绑守卫:绑定已吊销 key 返回 409;测试钉死
- [ ] 2.3 吊销后该 key 立即出现在下发名单;测试:吊销 → GET 名单含该 key → version 增长

## 3. 欠费停服联动

- [ ] 3.1 ingest 余额不足 → 独立事务置 owner suspended_at(幂等置位);测试:欠费行后 GET 名单含该用户全部 key
- [ ] 3.2 恢复 choke point:`WalletService.apply` 成功入账后同事务清除 suspended_at;测试:充值 → 名单移除该用户全部 key → version 增长;不产生"入账成功但停服未解除"
- [ ] 3.3 端到端集成测试(扩 DemoLoopIntegrationTest):入账→消费→耗尽→名单含 key→充值→名单移除,全链路 version 单调

## 4. 收口

- [ ] 4.1 Modulith verify() 绿(user/wallet/ingest 跨模块仅经契约类型);README 三根管子表更新 key-revocations 为「已实现」
- [ ] 4.2 联调实证(可选,muniu cluster 形态):revocations_url 指向锦官,吊销→muniu 60s 内拒该 key
- [ ] 4.3 `make check` 全绿
