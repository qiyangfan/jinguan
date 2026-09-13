# 任务清单:用量明细留存

> 纪律:契约类改动先红后绿;收口 `make check` 全绿。

## 1. 明细落库

- [x] 1.1 迁移 V5:usage_records(usage_id VARCHAR PK、key_id、tenant_id、model_alias、upstream_model、status、error_class、prompt/completion/reasoning/cached_tokens、cost_micro、currency、user_id NULL、final_outcome、attempts、last_attempt_at;索引 (user_id, id))
- [x] 1.2 ingest 链路改造:每行先幂等落明细,后走既有扣账/跳过语义;重投与补账成功更新 final_outcome/attempts;测试:正常/重投/欠费后充值补账各路径的明细投影断言,先红后绿
- [x] 1.3 并发回归:WalletConcurrencyTest 毒丸行场景扩展——skipped 行明细可见,不阻断同批后续行

## 2. 查询端点与前端

- [x] 2.1 `GET /wallet/usage`(本人分页,新→旧)+ `GET /admin/usage?userId=`(ADMIN);契约测试:仅本人可见、分页边界
- [x] 2.2 前端 WalletPage 增「用量明细」视图(模型/token/费用/结果徽章);费用显示复用 money.ts μ 格式化

## 3. 收口

- [x] 3.1 DemoLoopIntegrationTest 全链路断言扩展(扣账行明细 accepted、重投行 replayed);`make check` 全绿
