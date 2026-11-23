# 提案:生产缺持久化签名密钥即拒启

## Why

- 现行实现:未配置 `jinguan.security.jwt-keys-dir` 时打印告警并退化为进程内临时密钥。
  开发可用,但生产一旦漏挂目录,重启即换钥、全部 access/refresh 失效,且问题只在重启后
  暴露——这是上线级事故,不应依赖运维记忆。
- 规格已要求生产私钥必须经挂载文件提供,但代码没有强制,部署脚本也未必拦得住。

## What Changes

- 新增配置 `jinguan.security.require-persistent-keys`(env
  `JINGUAN_REQUIRE_PERSISTENT_KEYS`,默认 false);为 true 时,`jwt-keys-dir` 缺失或空白
  直接抛错拒启,不再退化临时密钥。
- 生产部署必须设置该开关为 true 并提供密钥目录;开发默认 false,保留本地即起体验。
- runbook 上线门禁同步:目录/权限/轮换与 fail-fast 开关。

## Non-goals

- 不引入 KMS/外部密钥服务;不自动生成生产密钥;不改密钥文件格式与轮换算法。
- 不改变 kid/JWKS/退役公钥语义。

## Impact

- 行为变更仅限显式开启该开关的部署;默认 false,现有本地/测试不受影响。
- 生产漏配从「重启后全员掉线」提前为「启动失败」。
- 回滚:关闭开关或恢复目录;无 schema/契约变更。
