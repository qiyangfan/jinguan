## MODIFIED Requirements

### Requirement: 拉取型管子的活性可见

三根拉取型下发端点(`GET /control/v1/pricing`、`GET /control/v1/key-revocations`、`GET /control/v1/topology`)每次**成功产出应答**必须 (MUST) 计入 `jinguan.control.pull{pipe,outcome}`,`outcome` 必须 (MUST) 区分整包下发(`full`)与条件请求命中(`not_modified`,仅 topology 具备 ETag 语义);计数点必须在端点层且后置到成包之后,管理页复用 service 的路径不得计入。读库或装配故障导致本轮没有发出任何价目/名单/拓扑时,禁止 (MUST NOT) 计 `full`——否则控制面持续 5xx 期间该序列仍在增长,「增速归零即链路断」的判据永不成立;故障形态的可读性由既有 `http_server_requests_*`(status 维度)承担,禁止 (MUST NOT) 为本指标新增失败桶。三管的计数时点必须 (MUST) 同口径,不得一处入口一处出口。

#### Scenario: 数据面停止拉取可被发现
- **WHEN** muniu 侧停止拉取超过一个告警周期
- **THEN** `jinguan_control_pull_total` 增速归零,与 `accepted` 仍增长(或同时归零)可组合成「下发链路断」的告警判据

#### Scenario: 控制面故障不得伪装成链路活跃
- **WHEN** 拉取请求到达但读库失败,端点返回 5xx 且未发出任何下发内容
- **THEN** `jinguan_control_pull_total` 不增长(既不计 `full` 也不计 `not_modified`),使 `increase(...)==0` 在故障期同样成立

#### Scenario: 三管计数时点同口径
- **WHEN** 检查三根 `*IngressController` 的计数位置
- **THEN** 均在应答内容装配完成之后计数,不存在「两管计在业务体之前、一管计在之后」的分叉
