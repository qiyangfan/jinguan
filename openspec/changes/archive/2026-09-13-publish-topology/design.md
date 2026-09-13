# 设计:拓扑下发(publish-topology)

## D1 存储:三张规范化表(非单 JSON 文档)

`topology_providers`(name 主键: adapter/base_url/keys_json/weight/priority)、
`topology_routes`(alias 主键: candidates_json)、`topology_models`(provider+model 联合主键:
drop_params_json/thinking_lock/max_output_tokens)。与 `pricing_entries` 运营态风格一致:
结构化列承载标量、JSON TEXT 承载有序数组(candidates/keys/drop_params 有序且异构度低,
行化反而引入合成主键与排序列)。所有 JSON 列读取时经 Jackson 反序列化校验——损坏数据在读取时拒绝。

## D2 管理形态:整包 PUT(非分表 CRUD)

`PUT /admin/topology` 接收三件套整体,服务层校验通过后在**单事务内 delete-all + insert-all**。
理由:muniu 消费语义就是整包快照(ReplaceTopology 整体换新、拒绝部分拓扑),分表 CRUD 会产生
「providers 已换、routes 还指旧 provider」的中间态——被拉取循环捞走就是一次热替换故障。
整包替换还让「内容不变的重放提交」天然幂等。GET /admin/topology 返回当前三件套供编辑器回填。

## D3 校验(整包 PUT,服务层)

1. `model_routes` 非空(muniu ReplaceTopology 拒绝空 routes——「下线全部路由」不是合法稳态,两侧同规);
2. 引用完整性:routes.candidates[].provider 与 models[].provider 必须存在于本次提交的 providers 中(悬空引用 400);
3. provider.weight > 0(与 muniu 加载校验同规,config.go:388);key weight ≤0 时下发侧归一为 1;
4. drop_params/keys/candidates JSON 形状合法(window/model/env 非空)。

## D4 ETag:下发 payload 的内容哈希

ETag = 序列化后三件套 JSON 的 SHA-256 前 16 hex,**带双引号**(与 muniu 测试 fixture `"v7"` 同形态)。
条件请求:请求头 If-None-Match 与当前 ETag 相等 → 304 空体。内容寻址的好处:PUT 相同内容的重放
不改变 ETag,muniu 拉取循环自然 304 短路,零空转热替换。

## D5 空拓扑语义

未录入拓扑时下发 `200 {"providers":[],"model_routes":{},"models":[]}`(非 404/404 会以拉取错误
形式触发 muniu 告警噪音);muniu 侧应用校验拒绝空 routes → 保持文件源降级,语义自洽:
「cluster 形态上线前必须先录入拓扑」是运维前置条件,README 注明。

## D6 key 安全边界

锦官只存 **env 引用名**(`{"env":"DEEPSEEK_API_KEY_1","weight":1}`),绝不明文——与 muniu YAML
同构(provider.keys 是 KeyRef)。真实上游 key 仍由各副本经自身环境变量解析,控制面泄露不等于上游 key 泄露。

## D7 认证:沿用无认证 GET + 网络隔离

`GET /control/v1/topology` 与 /control/v1/pricing、/control/v1/key-revocations 同款:数据面 GET
无认证头(现实契约),SecurityConfig 放行并注释;拉取认证头为双方演进项。

## D8 下发负载契约(逐字段对齐 muniu TopologyPayload)

```json
{
  "providers":   [{"name":"...","adapter":"...","base_url":"...","keys":[{"env":"...","weight":1}],"weight":70,"priority":0}],
  "model_routes":{"alias":[{"provider":"...","model":"...","priority":0,"weight":0}]},
  "models":      [{"provider":"...","model":"...","drop_params":[],"thinking_lock":false,"max_output_tokens":0}]
}
```
字段名 snake_case,与 muniu `internal/config/topology_source.go` 接收形态逐字段一致;
models 三字段缺省语义:drop_params 空数组、thinking_lock false、max_output_tokens 0=不限制。

## Risks / Trade-offs

- 整包 PUT 在拓扑规模大时有全量重写成本——个人项目规模(providers ≤ 两位数)零影响;
- 下发端点无认证(网络隔离)——与三根管子同一取舍,拉取认证为双方演进项,不同此 change 处理。

## Open Questions

无。
