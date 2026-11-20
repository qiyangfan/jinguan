# Tasks

## 1. 架构门禁

- [x] 1.1 pom.xml 增加 archunit-junit5 1.4.2；`ArchitectureTest` 三条规则通过
- [x] 1.2 key 绑定/吊销用例收进 `KeyRevocationQueryService`，Controller 不再直连仓储

## 2. 前端门禁

- [x] 2.1 Playwright + axe 登录页冒烟（2 用例通过）
- [x] 2.2 jscpd 阈值 1.2%（实测 0.72%）；size-limit 158.03/10.32 kB（实测 158.02/10.31）
- [x] 2.3 Makefile/CI 接线，test-results/playwright-report 不入库

## 3. 文档与去重

- [x] 3.1 markdownlint-cli2（3 个文件 0 issues）+ 链接/锚点检查（99 文件 0 broken）
- [x] 3.2 Checkstyle 移除格式/空白/import 排序模块，`mvn checkstyle:check` exit 0

## 4. 验证

- [x] 4.1 `mvn -Dtest=ArchitectureTest test` exit 0（JDK 25）
- [x] 4.2 web typecheck/lint/format/test（42）/build/e2e/size/dup 全绿
- [x] 4.3 actionlint 通过
- [ ] 4.4 推送后 CI 实跑（外部账单/授权）
