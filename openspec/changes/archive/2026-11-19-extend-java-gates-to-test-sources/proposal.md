## Why

结构门禁落地后,PMD 与 Checkstyle 只覆盖 `src/main`(两者默认不扫测试源),`src/test` 的 18 个测试文件、
约 400 条违规完全在门禁之外——门禁只保护一半代码库。结构门禁批审计已实测:纳入测试源后 PMD 353 条、
Checkstyle 50 条。

被否决的备选:①测试目录级 allowlist 排除(等于把缺口登记成永久豁免);②为测试单建宽松规则集
(双口径,规则放松点会成为新违规的避风港);③上调 `UnitTestContainsTooManyAsserts` 等阈值
(默认参数有默认的道理,不做单点放宽)。

## What Changes

- `pom.xml`:PMD `includeTests=true`、Checkstyle `includeTestSourceDirectory=true`,两门禁覆盖 `src/test`。
- 修复测试代码全部违规,保持官方默认参数与零豁免:命名、断言消息、断言拆分、重复字面量、线程用法、
  静态可变状态、资源关闭等。
- 不新增、不修改任何产品规格;不触碰 `src/main`。

## Non-goals

- 不改产品代码、迁移、契约与任何测试的业务语义(允许测试方法改名、单测试拆分、断言加消息)。
- 不引入测试专用的规则集或阈值;不做路径级豁免。
