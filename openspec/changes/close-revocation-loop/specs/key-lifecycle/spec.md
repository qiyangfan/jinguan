## ADDED Requirements

### Requirement: 终态吊销

ADMIN 必须 (MUST) 能对任意 key 执行终态吊销(`POST /admin/keys/{keyId}/revoke`,reason 必填,记录操作人);吊销必须 (MUST) 落 append-only `key_revocations` 表,重复吊销幂等;已吊销的 key 必须 (MUST) 拒绝重新绑定(409)——吊销是 key_id 的终态。

#### Scenario: 吊销泄露 key
- **WHEN** ADMIN 吊销 key "k-1" 后该 key 出现在下发名单,且尝试重绑 "k-1"
- **THEN** 名单含 "k-1"、version 增长;重绑返回 409

### Requirement: 欠费停服(可恢复)

用户余额不足导致 ingest 扣账失败时,必须 (MUST) 自动置位该用户 `suspended_at`(幂等);该用户名下全部已绑定 key 随即进入下发名单;任何成功入账(支付/手工)必须 (MUST) 在同一事务内清除停服——充值即恢复,无需人工介入,不需要重新绑定 key。

#### Scenario: 欠费即停,充值即恢复
- **WHEN** 用户余额耗尽产生欠费行,随后完成充值
- **THEN** 欠费后名单含其全部 key,充值后名单移除且 version 单调增长

### Requirement: 吊销下发端点

`GET /control/v1/key-revocations` 必须 (MUST) 返回 `{"version":int64,"revoked":["key_id",...]}`:终态吊销 ∪ 欠费用户名下 key 的**全量快照**(ORDER BY key_id),逐字段对齐 muniu revocationLoop 消费契约(全量替换、version 单调、启动即拉、失败保持上次快照);任何名单变化必须 (MUST) 与 version bump 同事务。

#### Scenario: 契约逐字段
- **WHEN** muniu 以默认 60s 周期拉取
- **THEN** 响应被数据面直接接受;空态返回 `{"version":0,"revoked":[]}` 不报错
