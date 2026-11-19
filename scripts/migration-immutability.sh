#!/usr/bin/env bash
# 迁移文件只增不改:相对基线出现修改/删除/重命名即失败(Flyway 校验和失配会在部署时才爆炸)。
set -euo pipefail

dirs=("src/main/resources/db/migration" "src/main/java/db/migration")
base="${MIGRATION_BASE:-}"
zero="0000000000000000000000000000000000000000"

# 新分支 push 的 before 是全零 sha,视同未提供基线。
if [ -z "$base" ] || [ "$base" = "$zero" ]; then
  if [ -n "${GITHUB_BASE_REF:-}" ]; then
    base="origin/${GITHUB_BASE_REF}"
  elif [ -n "${GITHUB_EVENT_BEFORE:-}" ] && [ "${GITHUB_EVENT_BEFORE}" != "$zero" ]; then
    base="${GITHUB_EVENT_BEFORE}"
  elif git rev-parse --verify --quiet "origin/main^{commit}" >/dev/null; then
    base="origin/main"
  else
    echo "[migration-immutability] 未提供可用基线;请设 MIGRATION_BASE" >&2
    exit 2
  fi
fi

if ! git rev-parse --verify --quiet "${base}^{commit}" >/dev/null; then
  echo "[migration-immutability] 基线不存在:${base}" >&2
  exit 2
fi

committed="$(git diff --name-status "${base}...HEAD" -- "${dirs[@]}")"
working="$(git diff --name-status HEAD -- "${dirs[@]}")"
bad="$(printf '%s\n%s\n' "$committed" "$working" | awk '$1 ~ /^(M|D|R)/ {print}')"

if [ -n "$bad" ]; then
  echo "[migration-immutability] 既有迁移被修改/删除/重命名:" >&2
  printf '%s\n' "$bad" >&2
  exit 1
fi

echo "[migration-immutability] 通过:既有迁移未改动"
