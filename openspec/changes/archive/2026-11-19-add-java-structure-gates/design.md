# 设计:Java 结构与风格门禁

## Context

见 proposal.md Why。基线事实(本机实测):

- PMD 7.17.0 官方全类别、默认参数:691 条(其中 `final` 修饰符类 471 条、命名偏好 70 余条);
- 结构类命中:`TopologyService`(GodClass/WMC、`replaceAll` 的 CyclomaticComplexity/NPathComplexity/NcssCount/CognitiveComplexity)、`WalletService`(TooManyMethods 默认上限 10)、`UsageSettlementScheduler.recoverOnce`(CognitiveComplexity);
- Checkstyle 10.26.1 官方 `google_checks.xml`:252 条,其中 152 条是中文句号不被识别、65 条是缺 Javadoc。

## Goals / Non-Goals

- Goals:结构问题由规则持续阻断;阈值取官方默认;修复靠重构而非抑制。
- Non-Goals:ArchUnit;调阈值;把 PMD 扩到测试代码。

## Decisions

1. **PMD 选集 = 官方类别全集 + 最小排除**。结构类规则(GodClass、CyclomaticComplexity、NPathComplexity、NcssCount、CognitiveComplexity、TooManyMethods、ExcessiveMethodLength、ExcessiveClassLength)全部保留默认参数。排除仅覆盖与语言/框架/契约冲突的 16 条,逐条注记:
   - 设计类:DataClass、TooManyFields(JPA 实体/表结构)、LawOfDemeter(fluent DSL)、UseUtilityClass(Boot 启动类)、ImmutableField(JPA 反射填充)、AvoidCatchingGenericException(调度器单行故障隔离)、SignatureDeclareThrowsException(Spring Security API);
   - 风格类:final 修饰符两条、Short/LongVariable、OnlyOneReturn、字段声明位置、包可见性注释、var、无构造器工具类、短方法名、logger 字段命名(共 10 条);
   - 逻辑类:GuardLogStatement(SLF4J 参数化)、UseConcurrentHashMap(方法内有序 Map)、AvoidDuplicateLiterals(Preset 目录中同一模型名跨条目复用,数据形态)。
2. **Checkstyle = 官方 google_checks 副本 + 两处本地化**:
   - `SummaryJavadoc.period="。"`:仓库注释为简体中文,英文句号默认值会把 152 条正确中文摘要判为违规;
   - `SuppressionFilter` 指向插件注入路径;唯一抑制条目是 `IngestUsageRow` 的 `RecordComponentName`/`AbbreviationAsWordInName`——该 record 组件名是 muniu 契约强制的 PascalCase(AGENTS 纪律 5),不可改名;
   - `MissingJavadocMethod` 关闭:仓库注释纪律是"只写有信息量的注释"(HANDOFF 2026-10-21),逐方法强制 Javadoc 会产出复述式注释;方法级契约由 openapi.json 承接。
3. **修复顺序**:先拆热点(`TopologyService` → validator/codec/writer;`WalletService` → 锁注册表/幂等写入;调度器 → 单行结算),再收小项;每次以 `pmd:check`/`checkstyle:check` 阻断模式复跑。
4. **禁用抑制**:代码中零 `// NOPMD`、零 `@SuppressWarnings` 新增;抑制仅存在于配置文件且有注记。

## Risks / Trade-offs

- 拆分 wallet 资金路径 → 由 95 个既有测试 + 并发/回放/外层事务三组专项测试背书;`withUserLock` 语义(外层事务提交后才解锁)原样迁移。
- `SyncWatermarkEntity.version` 字段改名 → JPQL 同步改为 `appliedVersion`,列名仍为 `version`(@Column 显式指定),schema 零变化。
- 新代码行数增加(拆类)→ 以可维护性换取 GodClass/复杂度归零。

## Migration Plan

合入即生效(`mvn verify` 已在 CI backend job)。若后续新增违规:先重构;确需排除时按现有格式注记理由与影响面。
