#!/usr/bin/env bash
# 控制面恢复:解密 + pg_restore,要求审批单号与显式确认;恢复完成后写审计。
# 保护:目标库必须显式 PGDATABASE;不做任何默认库回退。
set -euo pipefail

: "${JINGUAN_BACKUP_PASSPHRASE:?必须设置 JINGUAN_BACKUP_PASSPHRASE}"
: "${PGDATABASE:?必须设置 PGDATABASE(恢复目标库)}"
backup="${1:?用法: control-restore.sh <备份文件>}"
: "${RESTORE_APPROVAL_ID:?必须提供 RESTORE_APPROVAL_ID(审批单号)}"

if [[ "${RESTORE_CONFIRM:-}" != "restore-now" ]]; then
  echo "恢复是破坏性操作:请设置 RESTORE_CONFIRM=restore-now 并复核目标库 $PGDATABASE" >&2
  exit 2
fi
if [[ ! "$RESTORE_APPROVAL_ID" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
  echo "RESTORE_APPROVAL_ID 只允许字母数字._-" >&2
  exit 2
fi
if [[ ! -f "$backup" ]]; then
  echo "备份文件不存在: $backup" >&2
  exit 2
fi

echo "开始恢复: target=$PGDATABASE approval=$RESTORE_APPROVAL_ID backup=$backup"
openssl enc -d -aes-256-cbc -pbkdf2 -pass env:JINGUAN_BACKUP_PASSPHRASE -in "$backup" |
  pg_restore --clean --if-exists --no-owner --no-privileges -d "$PGDATABASE"

psql "$PGDATABASE" -v ON_ERROR_STOP=1 -v approval="$RESTORE_APPROVAL_ID" --quiet <<'SQL'
INSERT INTO control_audit_log
  (id, occurred_at, actor, actor_kind, actor_role, action, target_type, target_id,
   tenant_key, reason, summary_json, result, request_id)
VALUES
  (gen_random_uuid()::text, CURRENT_TIMESTAMP, :'approval', 'SYSTEM', 'OPS',
   'RESTORE', 'database', current_database(), NULL, 'control plane restore',
   '{"source":"backup"}', 'SUCCESS', NULL);
SQL

echo "恢复完成并已写审计: approval=$RESTORE_APPROVAL_ID"
