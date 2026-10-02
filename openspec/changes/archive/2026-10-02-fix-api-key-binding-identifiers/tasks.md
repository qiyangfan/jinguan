# 实施任务

每项不超过两小时,资金关联与契约用例先红后绿。

- [x] 1.1 补绑定表单文案、ID 原值传递、自定义非 UUID 标识与空白输入测试。
  → 前端 `keys-api.test.ts` 4 形态(k-1/mk-test-1/UUID/大小写混合)断言提交体与入参逐字节一致;后端 `ApiKeyBindingContractTest`(自定义 ID 绑定→计量命中归账、mk- 前缀不拒、空白 400)。
- [x] 1.2 修正既有表单标签、帮助与提交语义,不改管理员权限或绑定字段形态。
  → `KeysPage`:标签「密钥标识（Key ID）」+ 问号帮助(从木牛密钥列表/签发结果/CLI keys list 复制,不填 mk- 明文凭据或前缀)+ placeholder「粘贴木牛密钥记录 ID,如 k-1」;提交前 trim、纯空白禁用;`BindRequest` 字段与 ADMIN 权限未动。
- [x] 1.3 与木牛同名提案验证复制 ID→绑定→请求→用量归账;覆盖本地 ID 与平台自定义 ID。
  → 跨仓实证:`muniu keys create` → `keys list` 记录 ID `01a0fa5d-abba-…` 绑定 e2e-user → 明文 mk-PL3boi… 鉴权请求 → 锦官用量行 keyId=记录 ID、归账命中;平台自定义 ID(k-1)经 `ApiKeyBindingContractTest` 与真机走查双覆盖。
- [x] 1.4 核验 OpenAPI/生成类型及跨仓契约形态保持,运行相关前端测试与 `make check`。
  → 真机 `/v3/api-docs` ↔ `openapi.json` 深度相等(零漂移,前端无需重导出);前端五门禁 + `make check` 全绿。
- [x] 1.5 按 UI 闸门完成 375/768/1280 亮暗、长 ID、键盘与失败提示走查;完成内置代码/安全审查。
  → 六态截图走查零横向溢出(scrollWidth=视口宽),49 字符长 ID 不撑破容器;帮助浮层文案实证;表单回车/提交可达;失败提示(不存在的用户 999→text-destructive)与空白禁用实证;独立子代理审查(见 review)。
- [x] 1.6 双方 HANDOFF 互引,记录跨仓验收后同步 delta 并归档;收款继续 Mock。
  → 锦官/木牛 HANDOFF 同日互引登记验收链路;key-lifecycle delta 合并主规格后归档 `archive/2026-10-02-fix-api-key-binding-identifiers`。
