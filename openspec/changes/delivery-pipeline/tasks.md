# 任务清单:交付管道

> 收口 `make check` 全绿 + docker build 本机实证。

## 1. API 契约链路(先修,后续 change 均受益)

- [ ] 1.1 pom 引入 springdoc-openapi-starter-webmvc-ui(版本对齐 Boot 3.4)+ springdoc-openapi-maven-plugin,构建期导出根级 openapi.json;actuator 端点排除
- [ ] 1.2 `pnpm contracts:gen` 实证跑通,api-types.gen.ts 重新生成,前端 typecheck 绿
- [ ] 1.3 CI 增 openapi 漂移门禁(重新生成后 `git diff --exit-code openapi.json`);Makefile 增 `openapi` 目标

## 2. 容器化

- [ ] 2.1 多阶段 Dockerfile(web 构建 → maven 打包 → JRE 运行层;非 root 用户;HEALTHCHECK /actuator/health);.dockerignore(target/静态产物/密钥)
- [ ] 2.2 docker-compose.yml:PG 16 + app(健康检查 depends_on、命名卷、JINGUAN_* 环境变量样例);Makefile `docker-build/up/down`(down 不带 -v,卷删除须显式指令)
- [ ] 2.3 静态产物出库:static/assets/** 入 .gitignore 并清理;`make web-build` 与 Docker 阶段①均可再生
- [ ] 2.4 实证:compose up → 注册/演示闭环 curl 冒烟 → 健康检查绿;README「运行与测试」补容器化一节

## 3. 收口

- [ ] 3.1 CI 后端/web/漂移门禁三线全绿;`make check` 全绿
