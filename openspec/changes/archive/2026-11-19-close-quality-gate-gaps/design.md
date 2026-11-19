## 决策

- **漏洞扫描选型**:osv-scanner 2.7.1,固定版本 + `SHA256SUMS` 校验后执行 `scan source -r .`。
  Maven 侧按 pom.xml 解析传递依赖(deps.dev 数据源),npm 侧直接读 `pnpm-lock.yaml`;
  发现漏洞即非 0 退出。定时任务(每周一)覆盖"代码没变但漏洞库新增"的场景。
- **依赖修复方式**:Spring Boot 抬到 4.1.2 补丁版(由 BOM 带出 tomcat/jackson 修复),不手工钉
  tomcat/jackson 版本;前端 `pnpm update` 走语义范围内最新,仅将 `@vitest/coverage-v8` 精确对齐
  vitest 5.1.1。全部修复后复扫为 0,不新增任何忽略条目。
- **ESLint 深度**:启用 `recommendedTypeChecked`(correctness 型类型感知规则),不启用
  `strictTypeChecked`(含大量风格/隐式规则,超出缺口修复范围)。a11y 用官方 flat config,
  import 排序用 simple-import-sort(Prettier 不排序,二者互补)。
- **双仓标准件处理**:`src/components/ui/**` 与 9 个共享标准件以 kongming 为源逐字节同步
  (宪法 Article 5)。单边重排 import 会在下次 `sync-frontend-standard.sh` 被覆盖,故仅对这批文件
  关闭 `simple-import-sort`,a11y/类型/正确性规则照常生效。这是同步纪律,不是质量豁免。
- **覆盖率水位**:实测 10.07/6.31/5.03/9.83(语句/分支/函数/行),阈值取整为 9/6/5/9;
  作用是"新增代码不得拉低",不是达标声明。仅 `src/lib` 有测试是现状,补测后必须上调。
- **迁移守卫**:perl 去注释后按规则名逐条匹配,行号保留;基线文件按 `<文件> <规则>` 登记,
  V6 的单位改名(micros→credits)是唯一历史条目。守卫只拦"数据丢失/已部署代码失配"类语句,
  不覆盖性能类建议(锁表、索引),避免引入与账本纪律无关的噪音。
- **分支保护**:只设 required checks(`backend`/`web`/`secrets`/`deps`)与禁 force push/删除,
  不启用 PR 强制、不启用管理员强制。直接推送流程不变,PR 合并必须四作业全绿。

## 备选与否决

| 备选 | 否决理由 |
|---|---|
| OWASP dependency-check | NVD 全量数据下载 + API key,CI 时长与密钥管理成本高;osv 覆盖同等生态 |
| Dependabot/Renovate | 需第三方 App 授权;升级评审仍需人工,先保证"发现即红" |
| squawk 迁移 linter | 通用规则集噪音大,需逐条关闭;自有守卫与账本纪律逐条对应 |
| 关掉 `unit-test` 类或 a11y 规则凑绿 | 门禁价值归零;75 条全部改代码消化 |
| 一次性补组件测试冲覆盖率 | 属测试建设批次;本批只设只升不降水位线 |

## 风险

- Spring Boot 补丁升级:102 个测试全绿,OpenAPI 重导出零 diff(契约未漂移)。
- 前端 minor 升级(vite 8.3.8 / vitest 5.1.1 / eslint 10.15):typecheck、lint、测试、build 全绿。
- 类型感知 lint 报出的 75 条中,`no-misused-promises`/`no-floating-promises` 的修复触及运行时行为
  (`void` 标注、mock 返回 Promise 化),以 14 个前端测试与构建结果为准。
- 分支保护是新共享状态:若后续调整作业名,需同步更新 required checks,否则 PR 会被旧作业名卡住。
