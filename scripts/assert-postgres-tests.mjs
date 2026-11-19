#!/usr/bin/env node
// PostgreSQL 用例在无 Docker 时会被 Testcontainers 静默跳过;此断言防止门禁假绿。

import { readFileSync } from "node:fs";

const REPORT_DIR = "target/surefire-reports";
const REQUIRED = [
  { name: "PostgresMigrationTest", minTests: 3 },
  { name: "PostgresBusinessFlowTest", minTests: 3 },
];

function reportPath(name) {
  return `${REPORT_DIR}/TEST-com.longzhong.jinguan.${name}.xml`;
}

function attr(xml, name) {
  const match = new RegExp(`\\b${name}="(\\d+)"`).exec(xml);
  return match ? Number(match[1]) : 0;
}

const failures = [];
const hardFailures = [];
let total = 0;

for (const { name, minTests } of REQUIRED) {
  const path = reportPath(name);
  let xml;
  try {
    xml = readFileSync(path, "utf8");
  } catch {
    failures.push(`${name}: 缺少报告 ${path},未执行`);
    continue;
  }
  const tests = attr(xml, "tests");
  const skipped = attr(xml, "skipped");
  const errors = attr(xml, "errors");
  const failed = attr(xml, "failures");
  total += tests;
  const message = `${name}: tests=${tests} skipped=${skipped} errors=${errors} failures=${failed}`;
  // 跳过可由本地 ALLOW_PG_SKIP 放行;真实失败与用例缺失始终红。
  if (errors !== 0 || failed !== 0 || (skipped === 0 && tests < minTests)) {
    hardFailures.push(message);
  } else if (skipped !== 0 || tests < minTests) {
    failures.push(message);
  }
}

if (hardFailures.length > 0) {
  console.error(`[pg-guard] PostgreSQL 用例失败:\n  ${hardFailures.join("\n  ")}`);
  process.exit(1);
}

if (failures.length > 0) {
  const message = `[pg-guard] PostgreSQL 用例未全部执行:\n  ${failures.join("\n  ")}`;
  if (process.env.ALLOW_PG_SKIP === "1") {
    console.warn(`${message}\n(ALLOW_PG_SKIP=1,仅本地放行)`);
    process.exit(0);
  }
  console.error(`${message}\n请配置 DOCKER_HOST 后重跑`);
  process.exit(1);
}

console.log(`[pg-guard] PostgreSQL 用例 ${total} 个,未跳过`);
