## Purpose

muniu key 与控制面用户的绑定关系(计量归账的身份映射),及其向数据面的吊销下发(三根管子之二)。

## Requirements

### Requirement: key 绑定

ADMIN 可经 `POST /admin/key-bindings`(ADMIN 强制)建立 key_id → user_id 绑定,key_id 全局唯一主键;绑定是 ingest 归账的唯一身份依据。

#### Scenario: 绑定后归账
- **WHEN** key "k-1" 绑定到 userId=2 后 ingest 到达 APIKeyID="k-1" 的行
- **THEN** 扣账落在 userId=2 的账户

### Requirement: 吊销下发未实现(现状)

`GET /control/v1/key-revocations` 已在 README 与 SecurityConfig 登记为三根管子之一(permitAll 放行),但当前 (SHALL) 无 Controller/数据模型/管理端点实现——契约缺位:muniu 拉取循环已就绪(全量快照替换、version 单调防回退、默认 60s),控制面无供给。

#### Scenario: 现状缺口
- **WHEN** muniu 按 ADR-0006 配置 revocations_url 指向锦官
- **THEN** 404,吊销同步无法建立;欠费/泄露 key 均无法从控制面叫停

### Requirement: muniu 侧消费契约(对齐基准)

数据面消费语义必须 (MUST) 对齐 muniu revocationLoop:GET 返回 `{"version":int64,"revoked":["key_id",...]}`,**全量快照替换**(非增量),version 单调(`>=` 才应用,防旧快照洗白),拉取失败保持上次快照,默认 60s 周期,启动即拉一次;拉取当前不带认证头(依赖网络隔离,双方演进项)。
