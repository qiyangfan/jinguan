## MODIFIED Requirements

### Requirement: key 绑定

ADMIN SHALL 能将数据面真实 key_id 绑定到用户；ingest SHALL 以该标识匹配计量 APIKeyID。绑定页 SHALL 明确该值是源端记录标识，可从数据面密钥列表、签发结果或 CLI 取得，不是一次性明文凭据或展示前缀。标识 SHALL 作为不透明字符串原值传递，支持既有合法自定义值，SHALL NOT 限定为 UUID 或统一拒绝某个前缀。既有管理员权限与 userId 校验 SHALL 保持。

#### Scenario: 绑定后归账

- **WHEN** ADMIN 将 k-1 绑定到 userId=1
- **THEN** 后续 APIKeyID=k-1 的用量归账到 userId=1

#### Scenario: 使用真实标识完成绑定

- **GIVEN** 数据面提供同一条密钥的记录 ID 与明文凭据
- **WHEN** 管理员按页面指引复制记录 ID 并绑定
- **THEN** 绑定值与该密钥产生的 APIKeyID 相同，不使用明文或前缀替代

#### Scenario: 自定义标识不被 UUID 规则拒绝

- **GIVEN** 数据面合法平台 key 的 ID 为 k-1 或其他非 UUID 值
- **WHEN** 管理员绑定该标识
- **THEN** 按原值绑定，后续计量可命中，不增加格式强校验
