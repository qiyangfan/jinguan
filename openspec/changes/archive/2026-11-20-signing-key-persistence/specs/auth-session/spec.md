## ADDED Requirements

### Requirement: 签名密钥持久化与轮换

生产签发私钥必须 (MUST) 经挂载文件提供(未加密 PKCS#8,RSA ≥2048 位),不得 (MUST NOT) 以进程内临时密钥承担生产签发;未挂载时仅允许开发形态,并必须 (MUST) 启动告警。kid 必须 (MUST) 取 RFC 7638 公钥指纹以保证跨重启稳定;JWKS 必须 (MUST) 同时发布 active 与退役公钥,轮换时退役公钥必须 (MUST) 保留至其签发 token 全部过期(refresh TTL,默认 7 天)后方可移除。

#### Scenario: 重启不失效会话

- **WHEN** 以挂载密钥重启服务
- **THEN** 已签发 access/refresh token 仍可验签,RP 缓存 JWKS 的 kid 不变

#### Scenario: 轮换后旧 token 仍可验签

- **GIVEN** 旧键已退役并保留在 `retired/`
- **WHEN** 携旧键签发的 refresh 请求续期
- **THEN** 验签通过(JWKS 同时含新旧公钥),直至旧键签发 token 过期

#### Scenario: 弱/加密/缺失密钥拒绝启动

- **WHEN** 挂载目录缺少 `active.pem`、密钥不足 2048 位或为加密 PKCS#8
- **THEN** 启动失败并给出明确原因
