# 提案核验

状态：提案阶段，未实施。

- 已核对明文 key、prefix、记录 id 与计量 APIKeyID 的区别。
- 自定义平台 ID 的既有测试否决 UUID 强校验；保留合法 ID 值域与既有接口形态。
- 已保留原 Scenario 名，并指定双仓端到端验收。
- 收款继续 Mock；严格校验结果在本轮交接登记，实施与 UI/安全验收待执行。

## 本轮验证（2026-10-02）

- 本件及本轮全部 7 件 change 的 OpenSpec 严格校验通过；对应 key-lifecycle 的临时预合并规格严格校验通过，未写入主规格。
- MODIFIED 原 Requirement/Scenario 名称保留检查、局部文档链接、任务未勾选、文档变更范围及 `git diff --check` 均通过。
- 只读方案复核已完成，必要修正写回设计、规格和实施回归；收款继续 Mock。
- 本轮没有业务代码、依赖或迁移变更，未运行编译、业务测试、UI 实机或打包验收，不把提案通过记成实现完成。


## 实施审查(2026-10-02,实施轮)

- 契约测试:ApiKeyBindingContractTest(自定义 ID k-1 绑定→计量命中归账、mk- 前缀合法标识不拒、空白 400)3/3 绿;前端 keys-api.test.ts 4 形态断言提交体与入参逐字节一致(k-1/mk-test-1/UUID/大小写混合)。
- 独立子代理复核:`BindRequest` 仅 @NotBlank 契合「不透明原值」;trim 三重防护(mutationFn + 按钮 `keyId.trim()` 禁用 + 后端 @NotBlank);`FormRow.help` 渲染实证(浏览器读取浮层全文含 mk- 明文凭据警示);绑定字段形态/权限零改动。
- UI 走查:375/768/1280 × 亮暗六态 scrollWidth=视口宽零溢出;49 字符长 ID 不撑破;失败提示(不存在用户 999 → 500「数据写入失败」text-destructive)与纯空白禁用实证;绑定不存在用户返回通用 500 属既有语义类别(原为裸 500,现 RFC7807 形态),按提案「保持既有校验」不加新校验。
- 跨仓实证:`muniu keys create` → `keys list` 双列身份(明文 mk-PL3boi… 鉴权 / 记录 ID 01a0fa5d-abba-… 计量),绑定记录 ID 后用量行 keyId=记录 ID 归账命中——「复制真实 ID→绑定→请求→归账」端到端成立。
