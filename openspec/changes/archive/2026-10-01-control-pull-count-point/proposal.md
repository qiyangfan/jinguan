# 提案：拉取管子活性计数点改为「成功应答」口径（`control.pull`）

## Why

`expose-ops-metrics` 落地面时，三根管子的 `jinguan.control.pull` 计数点实际是**两套口径**：
`PricingIngressController` / `KeyRevocationsIngressController` 在方法第一行计数（读库之前），
`TopologyIngressController` 在成包与 ETag 判定之后计数。同一指标同一 `outcome="full"` 标签，
一处表示「请求到过」，一处表示「整包真发出去了」——这是 06 类跨页不一致缺陷在指标面的同型物。

选「成功应答」而不是「回到入口计数」的理由不是偏好，是这条指标存在的目的：
`docs/design/ops-metrics.md` 的告警判据 `increase(jinguan_control_pull_total[10m]) == 0` 要抓的是
「价目/吊销名单/拓扑这三根管子事实上断了」。按入口口径，读库故障时端点仍返回 500，
而计数照加，`increase` 恒不为零——**判据在最该触发的时刻永不成立**，
指标于是主动掩盖了它唯一负责的那类事故。改成成包后计，控制面持续 500 与数据面停止拉取
都会让增速归零，两种断供形态一次覆盖。

主规格原句「每次被调用必须 (MUST) 计入」是入口口径的表述，与修正后的实现冲突，
本变更把这句话改成实现真正承诺的东西。

## What Changes

1. **规格措辞**：`拉取型管子的活性可见` 一条由「每次被调用必须计入」改为
   「每次**成功产出应答**必须计入」，并新增一条判据：读库/装配故障不得计 `full`。
2. **代码**（已随评审循环落地，本件补齐规格侧）：三根管统一在成包之后计数；
   `topology` 保持 `full` / `not_modified` 分桶不变。
3. **文档**：`docs/design/ops-metrics.md` 指标表的「计数点」列改为「成功成包之后」并写明为何后置。

## Non-goals

- **不新增 `outcome="error"` 桶**：故障可见性由既有的 `http_server_requests_*`（status 维度）承担，
  再造一个失败桶会把「管子活性」与「端点健康」两件事混进同一标签轴，也会扩大封闭词表。
- **不改指标名、标签键与既有取值**：`control.pull{pipe,outcome}` 的序列形状零变化，采集侧无需改查询。
- **不改管理页读 service 的路径**：管理页复用 service 仍不计入（口径不变）。

## Impact

- 对外契约：**指标序列集合不变**（同名同标签，`Outcome` 取值不变），仅计数时点在故障路径上变化。
  正常应答路径的计数行为逐字节等价——`OpsMetricsIntegrationTest` 的六桶与管子活性用例在改动前后同样通过。
- 采集侧若按「调用次数=拉取次数」建过盘，故障期读数会比 HTTP 请求数小（小出的正是 5xx 那些次）；
  这正是期望语义，且告警判据由此刻起真正成立。
- 门禁：`OpsMetricsIntegrationTest` + `OpsMetricsExposureTest` + `OpsMetricsReachabilityTest` 全绿。
