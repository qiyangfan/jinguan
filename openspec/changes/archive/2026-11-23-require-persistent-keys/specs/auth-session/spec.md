## MODIFIED Requirements

### Requirement: 签名密钥持久化与轮换

生产签发私钥必须 (MUST) 经挂载 PKCS#8 文件提供;启用
`jinguan.security.require-persistent-keys=true` 时,密钥目录缺失或空白必须 (MUST)
拒绝启动,不得退化为进程内临时密钥;kid 必须 (MUST) 取 RFC 7638 公钥指纹;JWKS
必须 (MUST) 同时发布 active 与退役公钥,退役公钥保留至其签发 token 全部过期。

#### Scenario: 轮换后旧 token 仍可验签

- **WHEN** 旧键退役后携旧 refresh 续期
- **THEN** 验签通过直至 token 过期

#### Scenario: 生产缺密钥拒绝启动

- **GIVEN** `require-persistent-keys=true`
- **WHEN** `jwt-keys-dir` 为空或未配置
- **THEN** 应用启动失败,并明确指出缺少持久化密钥目录

#### Scenario: 开发默认允许临时密钥

- **GIVEN** `require-persistent-keys=false`(默认)
- **WHEN** `jwt-keys-dir` 为空
- **THEN** 使用进程内临时密钥并告警,应用可启动
