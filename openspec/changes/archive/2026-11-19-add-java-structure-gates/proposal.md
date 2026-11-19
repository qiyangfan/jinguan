# 补齐 Java 结构与风格门禁(PMD 官方类别 + Checkstyle Google)

## Why

现状:Spotless 只管格式;PMD 插件虽在 CI,但未配置 rulesets,跑的是 maven-pmd-plugin 自带的极小默认集(仅未用变量/私域一类),GodClass、圈复杂度、NPath、方法/类体量、FQN、import 卫生全部缺席;Checkstyle 完全没接。跨仓 handoff P1-Java 要求补 PMD 复杂度/类体积规则与 Checkstyle,并明确"基线不可控才跳过"。

实测基线(PMD 官方全类别、默认参数):691 条,其中结构类命中集中在 `TopologyService`(GodClass + CyclomaticComplexity + NPathComplexity + NcssCount + CognitiveComplexity)与 `WalletService`(TooManyMethods);Checkstyle google_checks 基线 252 条(含 152 条中文句号误判)。

## What Changes

- 新增 `rulesets/pmd-rules.xml`:PMD 7 官方类别全集,阈值全部取官方默认;结构类规则一律启用,存量违规靠重构消化。仅排除与语言语义/框架约定/数据契约冲突的规则,逐条注记理由。
- 新增 `rulesets/checkstyle.xml`:Checkstyle 10.26.1 官方 `google_checks.xml` 副本,仅两处本地化——`SummaryJavadoc.period` 增加中文句号、`SuppressionFilter` 指向插件注入路径;另以 `rulesets/checkstyle-suppressions.xml` 豁免唯一契约件 `IngestUsageRow` 的 PascalCase 命名。
- `pom.xml`:PMD 指向新 ruleset;新增 maven-checkstyle-plugin 3.6.0(checkstyle 10.26.1),`violationSeverity=warning`、`failOnViolation=true`,绑定 `verify`。
- 代码修复(无豁免):拆 `TopologyService` 为 validator/codec/writer/编排;拆 `WalletService` 的按用户锁与幂等写入;拆 `UsageSettlementScheduler` 单行结算;其余小项(serialVersionUID、花括号、重复字面量、FQN/命名冲突等)逐条修复。

## Capabilities

无产品语义变更(工具链与内部结构),`skip_specs: true`,不新增或修改产品规格。

## Non-goals

- 不启用 ArchUnit(handoff P1 的独立项,另行评估)。
- 不为过闸调低阈值或加 `// NOPMD`/`SuppressWarnings` 掩蔽违规。
- 不改任何对外契约(端点、字段名、openapi.json 保持不变)。

## Impact

- CI:既有 backend job 的 `mvn -B verify` 自动覆盖新增门禁,无需改 workflow。
- 代码:wallet 与 topology 模块内部拆分,行为由既有 95 个测试背书;业务语义零变化。
- 依赖:仅新增构建插件,不新增运行时依赖。
