# 实施任务

门禁类 change(skip_specs)。判据 = 测试源纳入两门禁 → 违规清零(默认参数、零豁免)→ 负向必红 → 全量回归绿。

## 1. 开启覆盖

- [x] 1.1 `pom.xml`:PMD `<includeTests>true</includeTests>`、Checkstyle `<includeTestSourceDirectory>true</includeTestSourceDirectory>`。
- [x] 1.2 量基线:PMD 353 条、Checkstyle 50 条,按规则分组记录(修复前快照)。
      验证:MethodNamingConventions 94、断言消息 62、断言过多 50、重复字面量 46、裸线程 20、数字下划线 15、静态可变状态 13 等。

## 2. 修复 PMD 违规

- [x] 2.1 命名类:`MethodNamingConventions`(94)测试方法改 camelCase;`LinguisticNaming`(1)、`OneDeclarationPerLine`(1)。
- [x] 2.2 断言类:`UnitTestAssertionsShouldIncludeMessage`(62)补消息;`UnitTestContainsTooManyAsserts`(50)拆分为多个 @Test;`UnitTestShouldIncludeAssert`(28)补显式断言。
- [x] 2.3 字面量类:`AvoidDuplicateLiterals`(46)提常量;`UseUnderscoresInNumericLiterals`(15);`AvoidLiteralsInIfCondition`(2)。
- [x] 2.4 并发与资源:`DoNotUseThreads`(20)改虚拟线程执行器;`MutableStaticState`(13);`CloseResource`(5)。
- [x] 2.5 其余:`TooManyMethods`(4)拆测试类;`TooManyStaticImports`(7);`AvoidCatchingThrowable`(3);`LambdaCanBeMethodReference`(1);`EmptyCatchBlock`(1)。

## 3. 修复 Checkstyle 违规

- [x] 3.1 按 google_checks 默认逐条修复 50 条(不改配置、不加抑制)。

## 4. 负向验收

- [x] 4.1 测试源植入探针(超长行 + 高复杂度方法):`mvn pmd:check` 与 `mvn checkstyle:check` 均 exit 1。
      验证:PMD 报 12 violations(GateProbeTest);Checkstyle 报 LineLength 145>100。
- [x] 4.2 删除探针:两者 exit 0(0 条)。
      验证:PMD check 与 checkstyle check 均 BUILD SUCCESS。

## 5. 回归

- [x] 5.1 `mvn -B verify` BUILD SUCCESS:102 测试 0 失败,PMD/CPD/Checkstyle/SpotBugs/Spotless/JaCoCo 全绿。
      验证:拆分新增用例(DemoAdmin/TopologyValidation/结算并发)计入后总用例数 95→102。

## 6. CI 落点

- [x] 6.1 无需改 workflow:CI backend job 执行 `mvn -B verify`,两门禁随 verify 生效;数据面构建路径(`-Dpmd.skip=true`)不受影响。
- [x] 6.2 分支保护 required:沿用 `backend` job 作为 required check(共享设置,需仓管理员授权后执行/核对)。
      注:共享设置未改动(需仓管理员授权);backend job 已执行 `mvn -B verify`,两门禁随 verify 生效。

## 7. openspec

- [x] 7.1 `openspec validate 2026-11-19-extend-java-gates-to-test-sources --strict` 通过(skip_specs)。
