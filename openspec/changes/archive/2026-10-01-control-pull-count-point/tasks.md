# 实施任务：`control.pull` 计数点口径归一

## 1. 代码（已随评审循环 R1 落地，本件补规格与文档）

- [x] 1.1 `PricingIngressController.pricing()`：计数从方法首行移到 `PricingEnvelope` 装配之后
- [x] 1.2 `KeyRevocationsIngressController.revocations()`：计数移到 version/名单/停用集合全部读完之后、返回之前
- [x] 1.3 `TopologyIngressController`：原已在 `current()` + `etagOf()` 之后，保持不动（成为三管统一后的基准）
- [x] 1.4 三处注释各写明「后置」的原因（读库故障不得计 `full`，否则链路断判据永不成立）

## 2. 规格

- [x] 2.1 `specs/ops-metrics/spec.md`：`拉取型管子的活性可见` 一条改为「每次成功产出应答必须计入」，
      并补两条判据——控制面 5xx 期不得增长、三管计数时点必须同口径；既有 Scenario 名原文保留
- [x] 2.2 `docs/design/ops-metrics.md` 指标表「计数点」列同步为「成功成包之后」并记原因

## 3. 验证

- [x] 3.1 `OpsMetricsIntegrationTest` 管子活性用例（三管各 +1、topology `not_modified` +1、管理页读不计数）在改后仍绿——正常应答路径逐字节等价
- [x] 3.2 `OpsMetricsReachabilityTest`（RANDOM_PORT）与 `OpsMetricsExposureTest`（白名单）全绿
- [x] 3.3 `make check` 全绿（含 PMD/SpotBugs/Spotless/Modulith/JaCoCo 与 web 五门禁）
- [x] 3.4 `openspec validate --all --strict` 通过；归档 sync 进主规格
