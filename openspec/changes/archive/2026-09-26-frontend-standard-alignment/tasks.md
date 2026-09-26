# 任务清单:双仓前端标准件对齐 + FormGrid 收编(frontend-standard-alignment)

> 纪律:每任务收尾 = 实现 + 门禁全绿;全程未动对外契约(四管子零变化)。

## 1. 双仓标准件对齐

- [x] 1.1 ui/ 四件(button/toggle/dialog/select)自 kongming 同步(B1.2 等效热区 +
      B1.3 窄屏全屏 dialog + select 移动 16px);`diff -rq` 全目录逐字节一致
- [x] 1.2 index.css 移植 B1.1 移动基线(tap-highlight/overscroll/safe-area 工具类),
      按本仓 stylelint 口径归一(`:root` 合并去重、env 兜底 `0` 无单位)
- [x] 1.3 index.html 补 `interactive-widget=resizes-content`(viewport-fit=cover 已在位)
- [x] 1.4 pagination 自 jinguan 回流 kongming(移动 44px 触控加高),双向漂移清零;
      `scripts/sync-frontend-standard.sh` 预览模式实证 0 差异

## 2. FormGrid 收编(宪法 Article 2)

- [x] 2.1 新增 `components/form-grid.tsx`(FormRow 响应式容器全站单源)
- [x] 2.2 Keys/Payments/Wallet/Pricing 四页收编;`--ignore-all-space` 净变更 =
      import 一行 + className 迁入包装,无其他语义行;渲染逐像素等价

## 3. 验证与收尾

- [x] 3.1 jinguan 五门禁:typecheck / eslint / stylelint / vitest 10/10 / build 全绿;
      prettier 全绿(含顺带修正 app-shell 一处格式漂移)
- [x] 3.2 kongming 三门禁:typecheck / eslint / stylelint 全绿(pagination 回流零涟漪)
- [x] 3.3 宪法自检 grep 五件套复跑全绿(裸 hex / 字面色板 / 裸元素 / 任意值间距 / 安全区写法)
- [x] 3.4 产物 CSS 实证:`--kb-safe-bottom` / `overscroll-behavior` / `pb-safe` 均编译落地
- [x] 3.5 评审迭代循环一轮收敛:候选疑点 7 项逐条溯源核验,全部判定非缺陷,零遗留
