## Purpose

muniu key 与控制面用户的绑定关系(计量归账的身份映射),及其向数据面的吊销下发(三根管子之二)。

## Requirements

### Requirement: key 绑定

ADMIN SHALL 能将数据面真实 key_id 绑定到用户;ingest SHALL 以该标识匹配计量 APIKeyID。绑定页 SHALL 明确该值是源端记录标识,可从数据面密钥列表、签发结果或 CLI 取得,不是一次性明文凭据或展示前缀。标识 SHALL 作为不透明字符串原值传递,支持既有合法自定义值,SHALL NOT 限定为 UUID 或统一拒绝某个前缀。既有管理员权限与 userId 校验 SHALL 保持。

#### Scenario: 绑定后归账
- **WHEN** ADMIN 将 k-1 绑定到 userId=1
- **THEN** 后续 APIKeyID=k-1 的用量归账到 userId=1

#### Scenario: 使用真实标识完成绑定
- **GIVEN** 数据面提供同一条密钥的记录 ID 与明文凭据
- **WHEN** 管理员按页面指引复制记录 ID 并绑定
- **THEN** 绑定值与该密钥产生的 APIKeyID 相同,不使用明文或前缀替代

#### Scenario: 自定义标识不被 UUID 规则拒绝
- **GIVEN** 数据面合法平台 key 的 ID 为 k-1 或其他非 UUID 值
- **WHEN** 管理员绑定该标识
- **THEN** 按原值绑定,后续计量可命中,不增加格式强校验

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
