# jinguan · 锦官 — 执行者工作契约

> 锦官是 muniu 平台控制面:用户体系、钱包账本、支付抽象、渠道与价目下发。
> 「决敌之资,唯仰锦耳。」与数据面(muniu)仅以 HTTP+JSON 契约耦合。

## 硬性纪律

1. **语言**:文档、注释、commit message 一律简体中文。
2. **架构纪律(Modulith 门禁进 CI)**:按业务能力分模块(user/wallet/payment/
   pricing/ingest/security);跨模块仅引用对方对外契约类型(如事件 record);
   `ModulithVerificationTest` 红即禁止合入。
3. **实体纪律**:全库禁 public setter——工厂构造 + 意图方法;建模深度分级
   (wallet 核心域不变量内聚,其余支撑域从简),但骨架与词汇全库统一。
4. **账本纪律(最高级)**:`ledger_entries` append-only——任何代码路径禁止
   UPDATE/DELETE;一切资金写入必须经 `WalletService`(幂等键 + 非负不变量 +
   per-user 锁),绕过即事故。
5. **跨仓契约**:三根管子(价目下发/吊销拉取/计量 ingest)的字段名与格式
   与 muniu 侧逐字段对齐(`UsageRow` 为 PascalCase!);变更须双方 change
   同步,禁止单边漂移。
6. **schema 权威在 Flyway**:`ddl-auto=validate`;迁移文件只增不改。
7. **测试先行**:资金/契约类改动先红后绿;`mvn test` 全绿方可勾选任务。
8. **git 写操作**须用户逐次明确指令。

## 前端宪法(隆中聚合层单源)

web 前端统一遵循聚合目录 `../FRONTEND-CONSTITUTION.md`(Article 0–10);本节为执行摘要,冲突时以宪法为准。

- 元原则:**用组件不用手搓;用默认不胡乱覆盖;用 token 不用魔法值。**
- 色彩:只引用 index.css 语义 token(含 success/warning),禁 `#hex`/Tailwind 字面色板。
  暗色是一级公民:**新增 token 必须同时给 `.dark` 值**,组件禁新增 `dark:` 硬编码色(Article 3/10)。
- 组件:基础交互件一律 `components/ui/`,业务代码禁裸 `<button>/<input>/<select>/<textarea>`;
  破坏性操作必须 AlertDialog 确认;≥2 处同类 UI 必须抽公共组件;同一覆盖 ≥3 处收编进组件默认
  (Article 1/2)。13 个 ui 组件中暂未使用的(badge/select/skeleton/dialog 等)有明确落点,
  新页面先查标准件再动手。
- 页面骨架只用标准件:PageHeader / FormRow / FieldLabel / Pagination / StatCard / StatusBadge /
  QueryErrorAlert / Skeleton,禁每页手搓骨架;错误一律 text-destructive,禁装灰(Article 4/6/7)。
- 响应式:md 为折叠点;**移动端是独立设计而非桌面缩小**——md 以下由 app-shell
  提供移动原生范式(顶部栏 + 底部标签栏 + 账户底部弹层),列表用 MobileCard
  卡片双轨(桌面表格 md 起保留),表单单列满宽、金额右对齐 tabular-nums、触控目标
  ≥44px、对话框用 SheetContent 底部弹层(max-md 贴底,md+ 回落居中模态);
  flex/grid 容器注意 `min-w-0`(防最小内容宽度撑破,先例 = main);多屏验收 =
  375/768/1280 三档不横向溢出 + 亮暗两态(Article 9,2026-09-26 移动端重设计修订)。
- ui/ 与共享组件是双仓标准件(与 kongming 逐字节一致;theme-store 例外:同构不同 key):
  **先改 kongming 再同步** → 聚合目录 `bash ../scripts/sync-frontend-standard.sh`,禁单边改 ui/。
- 收尾门禁:`pnpm typecheck && pnpm build` + 宪法 Article 8 自检 grep + 亮暗两态走查改动页。
