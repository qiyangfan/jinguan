# Tasks

## 1. 身份目录与开通

- [ ] 1.1 服务/方向/client/roles/scopes/environment 目录。验证：覆盖双向；不含用户身份。
- [ ] 1.2 每服务/方向独立 Keycloak client/role，最小权限。验证：越权 scope 拒绝。
- [ ] 1.3 密钥注入 env/secret store。验证：仓库/命令行/日志无长期密钥。

## 2. 轮换、吊销与本地

- [ ] 2.1 短时 token + 双 secret 重叠轮换。验证：轮换不停机；旧 secret 窗口后失效。
- [ ] 2.2 吊销 client/role。验证：新 token/注册/claim/上报 401/403；旧 token 到期失效。
- [ ] 2.3 本地 pre-shared localhost 限制。验证：远程拒绝；不进 managed。

## 3. 接入与验收

- [ ] 3.1 注册/快照/命令/用量/只读 API 使用对应身份。验证：最小 scope；缺失 fail-closed。
- [ ] 3.2 权限负向/审计。验证：401/403；吊销/拒绝可审计；无敏感值。
- [ ] 3.3 独立运行/降级 smoke。验证：身份缺失核心流程 last-known；不注册/不上报。
- [ ] 3.4 契约与生成客户端。验证：认证负向纳入 X-01；OpenAPI/生成物无漂移。
- [ ] 3.5 Greenfield 重置：删除长期密钥/共享 client。验证：无兼容/双轨，旧凭据不存在。

## Workflow follow-up

- 归档前跑 `openspec validate --strict`；实现完成后按仓库门禁收口。
- 本 change 是服务接入的身份前置；与 `docs/CONTROL-PLANE-PROJECT.md` §5/§8 及 `docs/CONTROL-PLANE-BOUNDARY.md` 认证表保持一致。
