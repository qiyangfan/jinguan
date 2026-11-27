# Design: 跨仓控制契约测试门禁（control-contract-harness）

## Context

控制面与两个服务之间有快照、命令、用量/健康、服务只读 API/投影、服务注册五类 wire 契约。当前契约测试分散在各 change，部分 task 写“适用时”，没有统一 owner 与失败门禁。

## Goals / Non-Goals

**Goals:**
- 契约 schema/示例/生成客户端单一来源。
- 五类跨仓契约测试必跑，漂移即红。
- X-01 有明确 owner 与失败报告。

**Non-Goals:**
- 不做业务实现或 e2e 替代。
- 不做服务域配置/凭据契约。
- 不引入共享库、共享事务或跨库查询。

## Decisions

1. jinguan 拥有控制面契约 schema 与 harness；kongming/muniu 消费生成客户端并提供 provider/consumer 测试向量。
2. 契约范围：控制快照、命令 envelope、命令 action payload schema/版本、用量/健康、服务只读 API/投影、服务注册/心跳。
3. 测试层次：schema/示例校验、生成物无漂移、provider/consumer 兼容、action 参数负向、认证/错误码负向。
4. 门禁：任一契约变更触发三仓必跑；失败阻断合并；禁止以“适用时”跳过快照/命令（含 action）/用量/只读 API/注册契约。
5. 版本：只做增量兼容；破坏性变更升版本并双方同步；保留上一版本回滚路径；action schema 按 action 独立升版本。
6. 报告：失败必须给出字段、方向（provider/consumer）、契约版本与期望/实际。

## Risks / Trade-offs

- 生成客户端漂移 → 生成物入库校验；单一生成命令。
- 环境差异导致假红 → 固定 CI 版本；本地以容器化依赖跑同一套。
- 契约过度设计 → 只覆盖五类 wire 契约，不把服务域配置拉进来。
- 门禁拖慢提交 → 只对契约变更路径强制全量，其他提交跑受影响子集。

## Migration Plan

greenfield：新增 schema/harness/CI 门禁；删除“适用时”跳过路径；无业务数据迁移。
