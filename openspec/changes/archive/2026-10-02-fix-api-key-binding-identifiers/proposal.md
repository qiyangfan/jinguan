# 修正密钥标识绑定引导

## Why

KeysPage 要求“粘贴以 mk- 开头的密钥”，却将输入作为 keyId 提交。木牛的明文凭据与记录 ID 是不同值，计量使用记录 ID；按提示操作会绑定错误映射，真正用量仍未绑定。平台 key 已支持 k-1 等自定义 ID，因此不能把修复变成 UUID 强校验。

## What Changes

- 在现有绑定表单明确“密钥标识（Key ID）”，说明从木牛密钥列表或 CLI 复制，不填写一次性明文凭据或前缀。
- 按源端 ID 原值传递，保留非 UUID 自定义标识，不增加新签发或跨仓浏览器鉴权接口。
- 与木牛同名提案配对，验证复制真实 ID→绑定→计量归账的端到端路径。

## Capabilities

### Modified Capabilities

- `key-lifecycle`：明确绑定的是计量记录的标识。

## Non-goals

- 不新增发卡系统、自动代签、用户自助开 key 或跨仓账户共享。
- 不修改吊销、到期、限额和合法 key ID 的值域；不一律拒绝 mk- 前缀。
- 不处理历史未绑定用量的自动追收；补账由另一独立提案处理。
- 收款继续 Mock，不接真实 PSP。

## Impact

- KeysPage、相关界面测试与 key-lifecycle delta；绑定 API 仍为 keyId/userId。
- 配对 `../muniu/openspec/changes/fix-api-key-binding-identifiers/`；双方 HANDOFF 互引。
