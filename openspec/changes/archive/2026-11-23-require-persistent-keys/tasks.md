# 任务:生产缺持久化签名密钥即拒启

- [x] 1.1 失败测试:`require-persistent-keys=true` 且 keys-dir 为空 → 拒启;
      false → 允许临时密钥
- [x] 1.2 实现:`JwtCryptoConfig` 读取开关,空目录时 fail-fast
- [x] 1.3 `application.yml` 增配置项与 env 映射;默认 false
- [x] 1.4 runbook §9 更新为已落地的 fail-fast 门禁
- [x] 1.5 `openspec validate --all --strict` + 定向测试绿
