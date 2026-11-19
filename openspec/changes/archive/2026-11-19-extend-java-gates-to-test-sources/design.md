## 决策

- **覆盖方式**:沿用现有 PMD/Checkstyle 配置,仅打开 `includeTests` / `includeTestSourceDirectory`;
  规则集、阈值、SuppressionFilter 全部不动,测试代码与主代码同一口径。
- **修复优先于抑制**:353 + 50 条全部以改代码消化。逐条处置:测试方法 camelCase 命名、断言补消息、
  单测试断言过多则拆分为多个 @Test、重复字面量提为常量、裸线程改虚拟线程执行器、静态可变状态改
  实例字段/final、数字字面量加下划线、资源补关闭。
- **语义不变的判据**:只允许改名/拆分/加消息/提取常量/改并发原语;每步后跑全量测试,95 个用例结果不变。

## 备选与否决

| 备选 | 否决理由 |
|---|---|
| 测试目录级 allowlist | 把缺口登记成永久豁免,正是本批要消除的问题 |
| 测试专用宽松规则集 | 双口径;放松的规则点会成为新违规的避风港 |
| 上调 `UnitTestContainsTooManyAsserts` 等阈值 | 默认参数有默认的道理;50 条拆分是数据问题不是规则问题 |
| 仅修 PMD 不修 Checkstyle | 门禁对称性破坏,两个都进 verify,修一半等于没修 |

## 风险

- 测试方法改名:无反射/字符串引用(已核,测试间不互调);风险可忽略。
- 单测试拆分:可能重复初始化开销,但保持断言事实不变;以全量测试绿为准。
- 并发测试改执行器:`DoNotUseThreads` 命中的裸线程改为 `Executors.newVirtualThreadPerTaskExecutor`
  (Java 25 默认)后需确认竞态语义不变;以 `WalletConcurrencyTest` 等并发用例结果为准。
