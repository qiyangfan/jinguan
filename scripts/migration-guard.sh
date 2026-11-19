#!/usr/bin/env bash
# 迁移破坏性变更守卫:Flyway 迁移只增不改(AGENTS 纪律 6)。
# 删表/删列/清空/改名/改列类型会造成数据丢失或已部署代码失配,
# 新迁移命中即失败;确属必要须在 migration-guard-baseline.txt 登记文件与规则并写明理由。
set -euo pipefail

DIR="${1:-src/main/resources/db/migration}"
JAVA_DIR="${2:-src/main/java/db/migration}"
BASE="$(cd "$(dirname "$0")" && pwd)/migration-guard-baseline.txt"

# 规则名|ERE(在去除 SQL 注释后的文本上匹配,大小写不敏感)。
RULES=(
  "drop_table|\\bDROP[[:space:]]+TABLE\\b"
  "drop_column|\\bDROP[[:space:]]+COLUMN\\b"
  "truncate|\\bTRUNCATE\\b"
  "delete_from|\\bDELETE[[:space:]]+FROM\\b"
  "rename|\\bRENAME[[:space:]]+(TABLE|COLUMN)\\b"
  "alter_type|\\bALTER[[:space:]]+COLUMN\\b[^;]*\\bTYPE\\b"
)

allowed() { # $1=文件名 $2=规则名
  [ -f "$BASE" ] || return 1
  grep -qxF "$1 $2" "$BASE"
}

found=0

# SQL 迁移与 Java 迁移都可能是破坏性 DDL 的入口,按文件类型分别去除注释后扫描。
scan_tree() { # $1=目录 $2=文件名模式 $3=perl 去注释表达式
  local dir="$1" pattern="$2" strip="$3"
  [ -d "$dir" ] || return 0
  while IFS= read -r file; do
    local name cleaned entry rule regex matches
    name="$(basename "$file")"
    cleaned="$(perl -pe "$strip" "$file")"

    for entry in "${RULES[@]}"; do
      rule="${entry%%|*}"
      regex="${entry#*|}"
      allowed "$name" "$rule" && continue
      matches="$(printf '%s\n' "$cleaned" | grep -nEi "$regex" || true)"
      [ -z "$matches" ] && continue
      while IFS= read -r hit; do
        echo "✗ ${file}:${hit%%:*} [${rule}] 破坏性迁移语句(须登记基线并写明理由)" >&2
        echo "    ${hit#*:}" >&2
        found=1
      done <<< "$matches"
    done
  done < <(find "$dir" -type f -name "$pattern" | sort)
}

scan_tree "$DIR" 'V*.sql' 's{/\*.*?\*/}{}gs; s{--.*$}{}'
scan_tree "$JAVA_DIR" 'V*.java' 's{/\*.*?\*/}{}gs; s{//.*$}{}'

if [ "$found" -ne 0 ]; then
  echo "迁移守卫失败:账本 schema 只增不减。" >&2
  exit 1
fi

echo "迁移守卫通过:无未登记的破坏性变更。"
