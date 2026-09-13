# 交付管道:容器化与 API 契约链路

## Why

两处"上线即卡住"的工程件缺口,对照 one-api/new-api/Lago 等同类开源项目的发布底线:

1. **零容器化**:无 Dockerfile/compose,部署方式只有"本机 mvn spring-boot:run";PG 依赖全靠手工环境。同类项目全部提供开箱镜像。
2. **API 契约链路断裂**:后端无 springdoc/OpenAPI 产出;前端 `contracts:gen` 引用的 `../openapi.json` 在仓库中不存在——类型生成链路是坏的,`api-types.gen.ts` 是无源之水。锦官的立仓之本就是"与 muniu 逐字段契约对齐",契约却没有机器可读的权威文件,说不过去。

## What Changes

- 多阶段 Dockerfile:①node+pnpm 构建 web → ②maven 打包(web 产物入 jar)→ ③JRE 运行层(非 root,HEALTHCHECK /actuator/health);`docker-compose.yml`(PG 16 + app,健康检查、卷、环境变量样例)与 Makefile `docker-build/up`。
- 停止提交前端构建产物:`src/main/resources/static/assets/**` 入 .gitignore 并清理(本地 `make web-build` 与 Docker 阶段①均可再生成)。
- springdoc-openapi 接入 + `springdoc-openapi-maven-plugin` 在构建期导出 `openapi.json` 至仓库根(前端 `../openapi.json` 引用落位,`pnpm contracts:gen` 链路修复);CI 增 openapi 漂移门禁(生成物与入库文件 diff 非空即红)。

## Capabilities

### New Capabilities

- `delivery-pipeline`:容器镜像、本地编排、API 契约文件与漂移门禁。

## Non-goals(防过度设计)

- 不做 k8s manifests/Helm(单机 compose 够用;集群化是 README 已登记的演进项);
- 不做 Prometheus 指标暴露(独立 backlog 项,不在交付管道 change 夹带);
- 不做多架构镜像矩阵(CI 出 arm64 单架构,按需再扩);
- 不给 openapi.json 做版本化发布流程(入库 diff 门禁已满足"契约变更可评审")。

## Impact

- 仓库:根 Dockerfile、docker-compose.yml、.gitignore、openapi.json(生成物入库)、pom.xml(springdoc 两依赖 + 插件)、Makefile、.github/workflows/ci.yml;
- 前端:`pnpm contracts:gen` 恢复可用,`api-types.gen.ts` 重新生成;
- 安全:openapi 暴露面审查——`/control/v1/*` 与 `/ingest/**` 属跨仓契约必须入文档,actuator 排除。
