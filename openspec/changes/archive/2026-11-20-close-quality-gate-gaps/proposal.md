# 变更提案：质量门禁缺口收口（架构 + 前端 + 文档）

## Why

六仓门禁盘点后，本仓剩三类缺口：Java 侧只有 Modulith 验证，缺通用分层/循环的字节码级约束；
前端缺 e2e/a11y、重复率与体积预算；文档虽有中文规范但无机器校验。同时 Spotless 与
Checkstyle 在格式化/空白/import 排序上重复，属可拆除的过度防御。

## What Changes

1. **ArchUnit**：新增 `ArchitectureTest`，约束 common 不反向依赖业务域、Controller 不直连
   Repository、业务域切片无环；为满足约束把 key 吊销/绑定用例收进 `KeyRevocationQueryService`。
2. **前端 e2e + a11y**：Playwright 驱动本机 Chrome，mock `/auth` 等前缀，登录页壳层冒烟 +
   axe（serious/critical）。
3. **前端 jscpd + size-limit**：阈值取实测基线（重复率 0.72% → 阈值 1.2%；体积 158.02/10.31 kB）。
4. **文档 lint/link**：markdownlint（README/AGENTS/contracts，MD013/MD060 关闭）+ 零网络
   相对链接与锚点检查。
5. **Checkstyle 去重**：只保留命名/Javadoc/语义/行长，格式与 import 交给 Spotless。

## Non-goals

- 不改业务语义；key 绑定与吊销快照行为保持不变。
- 不在本变更引入视觉回归、变异测试或浏览器矩阵。

## Impact

- 门禁文件：`pom.xml`、`rulesets/checkstyle.xml`、`web/` 配置、`Makefile`、CI。
- 生产代码：仅 `ingest` 两处控制器改为经 service 访问仓储。
