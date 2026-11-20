# 设计:签名密钥持久化

## 上下文

RS256 + JWKS 已落地(`d19a395`),密钥为进程内临时生成。约束:单实例部署、refresh TTL 默认 7 天、私钥不得明文入库、Flyway 只增不改(本件不涉 schema)。

## 决策

### D1 — 密钥形态:挂载目录

`jinguan.security.jwt-keys-dir`(`JINGUAN_JWT_KEYS_DIR`)指向挂载目录:`active.pem`(PKCS#8 私钥)+ `retired/*.pem`(退役公钥)。私钥不进库,免库内加密与 KEK 管理,与部署 secret 挂载同型;不建 `signing_keys` 表。

### D2 — kid 与发布

kid 取 RFC 7638 公钥指纹(SHA-256 base64url,43 字符),跨重启稳定;JWKS 发布 active + 退役公钥,验签接受全集;签发只用 active。

### D3 — 轮换与退役保留

轮换 = 旧 `active.pem` 移入 `retired/`、新键就位、重启。退役公钥必须保留至其签发 refresh 全部过期(默认 7 天)后再移除;删除过早会使旧 token 验签失败(401 重登)。JWKS 在轮换全程同时发布新旧公钥。

### D4 — 失败策略

未挂载 → 进程内临时键 + 启动 WARN(仅开发)。挂载后:缺 `active.pem`、非 RSA、<2048 位、加密 PEM、PKCS#1 一律启动失败;退役目录内与 active 同 kid 或重复 kid 的条目忽略并告警(防 JWKS 重复 kid 造成 RP 选键歧义)。

### D5 — PEM 解析

JDK `KeyFactory`(PKCS#8 私钥 / X.509 公钥),不引 BouncyCastle;PKCS#1 给出 `openssl pkcs8 -topk8` 转换提示。

## 风险与回滚

- 退役键删早 → 旧 token 全失效;README 与 HANDOFF 写明 7 天保留。
- 不热加载:轮换需重启(单实例,秒级不可用窗口可接受)。
- 回滚:回退本仓提交即回到进程内临时键(开发形态),历史 token 全部失效属预期。
