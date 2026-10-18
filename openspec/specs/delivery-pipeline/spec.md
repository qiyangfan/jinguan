# delivery-pipeline Specification

## Purpose
本 capability 约束「契约 → 构建 → 镜像 → 冒烟」的交付管道:根级 `openapi.json` 作为运行中应用导出的契约权威并接 CI 漂移门禁,前端类型由其生成;多阶段镜像构建与 compose 全链路冒烟保证发布物可运行、账实相符。

## Requirements

### Requirement: API 契约权威文件

构建期必须 (MUST) 从运行中的应用导出 OpenAPI 文档至仓库根 `openapi.json` 并入库——它是三根管子跨仓契约与前端类型生成(`pnpm contracts:gen`)的机器可读权威;CI 必须 (MUST) 设漂移门禁:重新生成的文档与入库版本不一致即红。

#### Scenario: 契约变更可评审
- **WHEN** 任意端点形状变化合入
- **THEN** openapi.json 的 diff 随 PR 可见,未同步提交时 CI 拒绝

### Requirement: 容器化交付

仓库必须 (MUST) 提供多阶段 Dockerfile(前端构建 → 后端打包 → JRE 运行层,非 root 运行,内置健康检查)与 docker-compose 本地编排(PG + 应用,数据落命名卷);镜像构建必须 (MUST) 不依赖宿主上的任何手工前置(静态产物不提交,构建期生成)。

#### Scenario: 开箱即用
- **WHEN** 全新机器上执行 compose up
- **THEN** 应用与 PG 就绪,演示闭环 curl 一条龙可通

### Requirement: 构建产物不入库

前端构建产物(src/main/resources/static/assets/**)必须 (MUST NOT) 提交仓库——jar 内静态资源由构建期注入;本地 `make web-build` 与镜像阶段①为仅有的两个产物来源。

#### Scenario: 无源之水消除
- **WHEN** 修改前端代码后构建
- **THEN** jar 内页面为新产物,仓库无陈旧构建残留

### Requirement: 工具链支撑窗口

构建与运行所依赖的关键工具链(Java LTS、Node LTS、Spring Boot 支持线、前端构建器与语言大版)必须 (MUST) 处于各自上游的 OSS 支持窗口内;升级到支撑窗口内最新稳定版必须 (MUST) 走 openspec change 流程,且不得改变任何业务与安全语义(行为等价升级),全部既有门禁保持绿。

#### Scenario: 依赖退出支持线即触发升级提案

- **WHEN** 任一关键工具链的上游版本退出 OSS 支持窗口(如 Spring Boot 3.4 终止于 3.4.13)
- **THEN** 仓库在下一个 change 内完成升级,不允许长期停留在无安全补丁的版本线

#### Scenario: 升级不携带语义变化

- **WHEN** 工具链大版升级合入(如 Boot 3.4→4.1)
- **THEN** 契约字段、资金语义、限流与认证行为不变;openapi.json 仅允许随工具链(如 OpenAPI 信封 3.0→3.1)产生的形态性重导出,且必须与旧版逐字段对照留档
