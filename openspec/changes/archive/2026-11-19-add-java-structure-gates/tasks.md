# 实施任务

门禁类 change(skip_specs)。判据 = 基线可量化 → 热点重构 → 两侧门禁阻断生效 → 负向自证。

## 1. 门禁落地

- [x] 1.1 新增 `rulesets/pmd-rules.xml`:PMD 7 官方类别全集、阈值取官方默认;结构类规则全启用,排除项逐条注记。
- [x] 1.2 新增 `rulesets/checkstyle.xml`(官方 google_checks 10.26.1 副本,中文句号本地化)+ `rulesets/checkstyle-suppressions.xml`(仅 IngestUsageRow 契约命名)。
- [x] 1.3 `pom.xml`:PMD 指向新 ruleset;接入 maven-checkstyle-plugin 3.6.0(checkstyle 10.26.1),`violationSeverity=warning`、`failOnViolation=true`,绑定 verify。
      验证:`mvn verify` 日志中 pmd/checkstyle 均在 verify 阶段执行。

## 2. 基线量化

- [x] 2.1 原始基线(默认参数、零裁剪):PMD 691 条、Checkstyle 252 条;结构命中 = TopologyService(GodClass/Cyclomatic/NPath/Ncss/Cognitive)、WalletService(TooManyMethods)、UsageSettlementScheduler(CognitiveComplexity)。
- [x] 2.2 修复后:`mvn pmd:check` 0 条、`mvn checkstyle:check` 0 条(阻断模式 exit 0)。

## 3. 修复(先拆热点)

- [x] 3.1 拆 `TopologyService` → `TopologyPayloadValidator` / `TopologyCodec` / `TopologyWriter`;GodClass、CyclomaticComplexity、NPathComplexity、NcssCount、CognitiveComplexity 归零。
- [x] 3.2 拆 `WalletService` → `UserLockRegistry`(外层事务提交后解锁语义原样)+ `LedgerEntryWriter`(refId 幂等裁决);TooManyMethods、ExceptionAsFlowControl 归零。
- [x] 3.3 拆 `UsageSettlementScheduler.recoverOnce` → `settleRow` 单行结算;CognitiveComplexity 归零。
- [x] 3.4 小项:serialVersionUID ×6 + 复合主键、花括号 ×2、重复字面量(常量提取)、命名冲突(字段/常量/复合主键改名)、LooseCoupling(TreeSet→Set)、volatile→AtomicLong、Pricing 工厂参数对象、SyncWatermark 字段改名(列名不变,JPQL 同步)。
- [x] 3.5 补 35 处类型级 Javadoc(Checkstyle MissingJavadocType),零 `// NOPMD`、零新增 `@SuppressWarnings`。

## 4. 负向验收

- [x] 4.1 植入 `GateProbe`(11 分支方法 + 194 字符行):`mvn pmd:check` **exit 1**(13 条,含 CyclomaticComplexity/NPathComplexity),`mvn checkstyle:check` **exit 1**(LineLength 194>100)。
- [x] 4.2 删除探针后复跑:`mvn pmd:check` **exit 0**(0 条)、`mvn checkstyle:check` **exit 0**(0 条)。

## 5. 回归

- [x] 5.1 `mvn -B verify` **BUILD SUCCESS**:95 测试 0 失败,JaCoCo 60%/50%、PMD、SpotBugs、Spotless、Checkstyle 全绿。
- [x] 5.2 契约零漂移:端点签名与 `openapi.json` 零改动(仅控制器字段、实体字段与内部结构变更)。

## 6. openspec

- [x] 6.1 `openspec validate 2026-11-19-add-java-structure-gates --strict` 通过(skip_specs)。
