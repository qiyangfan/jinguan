# control-plane-contract-pinning 提案:控制面跨仓契约单源钉死

## Why

锦官与 muniu 之间现有**四根契约管子**,全部是双端各自手写实现、互为镜像,没有任何跨仓漂移机制(2026-09-13 聚合复查确认为聚合层真实缺陷):

| 管子 | 端点 | 方向 | 现状 |
|---|---|---|---|
| 价目下发 | `GET /control/v1/pricing` | 锦官 → muniu | 双端落地并联调 |
| key 吊销快照 | `GET /control/v1/key-revocations` | 锦官 → muniu | 双端落地并联调(吊销 502→401 实证) |
| 用量上报 | `POST /ingest/usage` | muniu → 锦官 | 双端落地;行字段为 muniu Go 结构体无 json tag 的 PascalCase 序列化,锦官 record 逐字段手写镜像 |
| 拓扑下发 | `GET /control/v1/topology` | 锦官 → muniu | muniu 消费侧已备,锦官未实现(README 登记) |

**credit 迁移恰是「纯纪律无机制」的失败实证**:本仓 AGENTS.md 明文「跨仓契约变更须双方 change 同步,禁止单边漂移」,而这次迁移在双仓各自留下了规格/文档残留(muniu ADR-0006 载体文本、双仓主 specs μ 口径)——纪律在,机制不在,漂移照样发生。当前唯一的钉死手段:锦官侧 `openapi.json` 有 CI 漂移门禁(钉住「代码→契约」),但 muniu 消费侧的解析代码(`ParsePricingRecord`、revocations 快照结构、ingest record 镜像)与锦官契约之间**零联动**——锦官改字段名,muniu 拉取解析失败后按「失败保持原目录」静默降级,不报错、不告警,漂移不可见。

## What Changes

1. **契约登记单源**:新增 `contracts/control-plane.md`,四根管子逐根登记:端点、方向、**形态权威方**(/control/v1/* 三管子权威=本仓 openapi.json 对应路径,CI 漂移门禁背书;ingest 行形态权威=muniu `UsageRow` JSON 序列化,产生方为准)、字段清单、金额单位(credit 1e-9)、双端实现锚点(文件级)、当前状态。
2. **ingest 行形态显式契约化**:PascalCase 字段清单(`ID/PromptTokens/CompletionTokens/CostCredits/Currency/Status/...`)从「双方心照不宣的镜像」升格为登记表内显式契约,字段增删改名必须走本表。
3. **聚合层一致性脚本**:聚合目录 `scripts/`(跨仓共享层,不入任一仓,前端同步脚本先例)新增 `contract-consistency.sh`:jq 提取本仓 `openapi.json` 三端点字段清单 + ingest 行字段清单,与 muniu 源码对应结构体/解析代码 grep 比对,漂移即红。职责边界:只抓字段名级漂移,不验语义。
4. **topology 管子决策固化**:登记表内明确状态=未实现、触发条件=渠道管理数据模型立项(与 README 演进登记对齐),避免「半根管子」长期无主。

## Non-goals

- 不给 /control/v1/* 加认证(双方已登记为演进项,独立于本变更;拉取认证头落地时同步更新登记表)。
- 不建契约同步基建(OpenAPI 聚合仓/契约测试框架对个人项目过度设计;grep 级脚本+登记表已覆盖「改名/删字段」这一最常见漂移形态)。
- 不实现 topology 端点本身(那是渠道数据模型的 feature,本变更只固化决策)。
- 不动 muniu 仓文件(muniu 侧如需消费面锚点核对,经聚合脚本覆盖)。

## Capabilities

### New Capabilities

- `control-plane-contracts`:跨仓契约登记单源、变更纪律、一致性校验与 ingest 行形态显式化。

## Impact

- 文件:新增 `contracts/control-plane.md`;`openapi.json` 不改(现有 CI 漂移门禁已钉)。
- 聚合层:`scripts/contract-consistency.sh`(新)。
- 跨仓:muniu 仓 HANDOFF 互引;契约变更流程从此有单源可查。
- 风险:近零(纯登记与只读脚本;脚本误报可人工复核,不进任何仓的 merge 门禁,先作巡检工具)。
