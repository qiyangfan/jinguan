# delivery-pipeline 变更

## ADDED Requirements

### Requirement: 工具链支撑窗口

构建与运行所依赖的关键工具链(Java LTS、Node LTS、Spring Boot 支持线、前端构建器与语言大版)必须 (MUST) 处于各自上游的 OSS 支持窗口内;升级到支撑窗口内最新稳定版必须 (MUST) 走 openspec change 流程,且不得改变任何业务与安全语义(行为等价升级),全部既有门禁保持绿。

#### Scenario: 依赖退出支持线即触发升级提案

- **WHEN** 任一关键工具链的上游版本退出 OSS 支持窗口(如 Spring Boot 3.4 终止于 3.4.13)
- **THEN** 仓库在下一个 change 内完成升级,不允许长期停留在无安全补丁的版本线

#### Scenario: 升级不携带语义变化

- **WHEN** 工具链大版升级合入(如 Boot 3.4→4.1)
- **THEN** 契约字段、资金语义、限流与认证行为不变;openapi.json 仅允许随工具链(如 OpenAPI 信封 3.0→3.1)产生的形态性重导出,且必须与旧版逐字段对照留档
