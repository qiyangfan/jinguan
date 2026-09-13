# 任务清单:control-plane-contract-pinning

> 纪律:本变更为零代码登记与巡检工具;脚本只读、不进任一仓 merge 门禁;与 muniu 仓 HANDOFF 互引。

## 1. 契约登记表

- [x] 1.1 新建 `contracts/control-plane.md`:四根管子逐根登记(端点/方向/形态权威方/字段清单/金额单位 credit 1e-9/双端实现锚点文件/当前状态);ingest 行以 muniu `internal/store/store.go` UsageRow 的 JSON 序列化形态逐字段核对后录入。
- [x] 1.2 topology 管子状态固化:未实现、触发条件=渠道管理数据模型立项、消费方(muniu)已备注记,与 README 演进登记互相引用。

## 2. 聚合层一致性脚本

- [x] 2.1 聚合目录 `scripts/contract-consistency.sh`:jq 提取本仓 `openapi.json` 的 /control/v1/pricing、/control/v1/key-revocations、/ingest/usage 三端点请求/响应字段清单 + 登记表 ingest 行清单,与 muniu 源码对应解析/序列化结构 grep 比对;漂移报红并列差异字段;脚本头注释写明职责边界(字段名级,不验语义)与用法。
- [x] 2.2 本地实跑双绿留证;故意制造一处字段改名验证报红后还原(脚本有效性自证)。

## 3. 收口

- [x] 3.1 HANDOFF 验收记录:登记表位置、脚本用法、与 muniu 仓互引;`mvn verify` + web 门禁全绿(零代码变更,确认无涟漪);归档。
