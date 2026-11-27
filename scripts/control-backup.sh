#!/usr/bin/env bash
# 控制面备份:pg_dump → AES-256-CBC 加密落盘;失败向告警通道上报(BACKUP_FAILURE)。
# 依赖:pg_dump/psql、openssl;口令与数据库凭据只从环境变量读取。
set -euo pipefail

: "${JINGUAN_BACKUP_PASSPHRASE:?必须设置 JINGUAN_BACKUP_PASSPHRASE}"
: "${PGDATABASE:?必须设置 PGDATABASE(备份目标库)}"
out="${1:?用法: control-backup.sh <输出文件>}"
retention_days="${JINGUAN_BACKUP_RETENTION_DAYS:-30}"
dir="$(dirname "$out")"
mkdir -p "$dir"

report_failure() {
  local reason="$1"
  if [[ -n "${JINGUAN_ALERT_URL:-}" && -n "${JINGUAN_ALERT_REPORT_TOKEN:-}" ]]; then
    local payload
    payload="$(printf '{"type":"BACKUP_FAILURE","severity":"CRITICAL","summary":{"reason":"%s"},"runbook":"docs/control-ha.md"}' "$reason")"
    printf 'header = "Content-Type: application/json"\nheader = "X-Operational-Token: %s"\n' \
      "$JINGUAN_ALERT_REPORT_TOKEN" |
      curl --silent --show-error --max-time 5 --config - --data-binary "$payload" \
        "$JINGUAN_ALERT_URL" >/dev/null || true
  fi
}

tmp="$out.tmp"
if ! pg_dump --format=custom --no-owner --no-privileges "$PGDATABASE" |
  openssl enc -aes-256-cbc -pbkdf2 -salt -pass env:JINGUAN_BACKUP_PASSPHRASE -out "$tmp"; then
  rm -f "$tmp"
  report_failure "pg_dump/encrypt failed"
  exit 1
fi
mv "$tmp" "$out"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$out" >"$out.sha256"
else
  shasum -a 256 "$out" >"$out.sha256"
fi

find "$dir" -name '*.dump.enc' -type f -mtime +"$retention_days" -delete
echo "backup ok: $out (retention ${retention_days}d, encrypted at rest)"
