# 任务:签名密钥持久化

> 勾选以实测为准。门禁全量 `make check` 于 2026-11-20 实测 exit 0(130 测试 0 失败、真 PG 6/6)。

## 1. 密钥加载

- [x] 1.1 挂载目录加载:`active.pem`(PKCS#8 私钥)+ `retired/*.pem`;kid = RFC 7638 公钥指纹,跨重启稳定
- [x] 1.2 未挂载退化为进程内临时键 + 启动 WARN(仅开发)
- [x] 1.3 失败快速:缺 `active.pem`、非 RSA、<2048 位、加密 PKCS#8、PKCS#1 均启动失败并给明确原因
- [x] 1.4 PEM 解析用 JDK(PKCS#8/X.509),不引 BouncyCastle

## 2. 发布与轮换

- [x] 2.1 JWKS 发布 active + 退役公钥;签发只用 active;验签接受全集(退役键旧 token 可用至过期)
- [x] 2.2 退役目录内与 active 同 kid / 重复 kid 的条目忽略并告警(防重复 kid)
- [x] 2.3 README 演进登记与 compose 注释写明轮换流程与 7 天退役保留期

## 3. 测试与门禁

- [x] 3.1 `SigningKeysTest` 7/7:双键发布、kid 稳定、退役键签发 token 可验签、未挂载退化、缺键/公钥占私钥槽位/弱键/重复键拒绝
- [x] 3.2 `mvn -B verify` 全绿(PMD/Checkstyle/SpotBugs 0)
- [x] 3.3 `make check` 全绿(130 测试 0 失败,真 PG 6/6,许可证/前端/契约零漂移)

## 4. 收口

- [x] 4.1 HANDOFF 回写(当前状态/下一步/决策/验收记录)
> 部署环境的轮换演练(新 active → 旧钥入 retired → 重启验旧 token)登记在 HANDOFF 下一步,不属本件实现范围。
