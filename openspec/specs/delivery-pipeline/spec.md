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
