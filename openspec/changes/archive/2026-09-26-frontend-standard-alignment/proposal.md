# 双仓前端标准件对齐 + FormGrid 收编(frontend-standard-alignment)

## Why

全库风格审计(2026-09-26)证实规范体系完备且执行干净(色彩/间距/布局/组件四线零违规),
但双仓标准件存在**双向漂移**:jinguan 落后 kongming 的 mobile-experience B1.1–B1.3
(按钮/开关 44px 等效触控热区、dialog 窄屏全屏+钉底 Footer、select 移动端 16px 防 iOS
聚焦缩放、index.css 触控基线与 safe-area 工具类),反向 pagination 的移动端 44px 加高
又未回流 kongming——违反宪法 Article 5「语义级改动先落 kongming,再对齐其余仓」。
另有筛选表单行模式 `flex flex-col gap-4 md:flex-row md:flex-wrap md:items-end md:gap-3`
在四页逐字重复,踩宪法 Article 2「第 2 处即收编」红线。

## What Changes

- ui/ 四件(button/toggle/dialog/select)自 kongming 同步,ui/ 目录双仓恢复逐字节一致;
  pagination 自 jinguan 回流 kongming,双向漂移清零。
- index.css 移植 B1.1 移动基线(去触控闪斑、嵌套滚动隔离、`pb-safe`/`pl-safe`/`pr-safe`
  安全区工具类),并按本仓 stylelint 口径归一(`:root` 合并、env 兜底 `0` 无单位);
  index.html 补 `interactive-widget=resizes-content`(软键盘压缩布局视口,底部弹层表单
  随流抬升不被遮挡)。
- 新增 `components/form-grid.tsx` 全站单源(FormRow 的响应式容器),收编
  Keys/Payments/Wallet/Pricing 四页重复模式;PricingPage 的编辑表单形态
  (保存键独立成行)与查询行形态(按钮即行尾)经 div 栅格形态统一承载,渲染逐像素等价。
- 治理件随批:聚合层 `FRONTEND-CONSTITUTION.md` 与 `scripts/sync-frontend-standard.sh`
  由并行会话落盘,本 change 以脚本预览模式(0 差异)实证对齐成果。

## Impact

- 规格:零 capability spec 变更(本批属标准件/规范执行,不动四管子契约);openapi.json 零变化。
- 用户可感知:Select 移动端聚焦不再触发 iOS 页面缩放;按钮/开关获得等效触控热区(视觉不变);
  底部弹层表单键盘不遮挡;桌面端渲染零变化(md: 档位逐字节保留)。
- kongming 侧随批回流 pagination 一文件,双仓门禁(typecheck/lint/stylelint)各自全绿。
