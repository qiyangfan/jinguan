#!/usr/bin/env node
// 依赖许可证门禁:后端读 CycloneDX BOM,前端读 pnpm licenses 输出,任一组件无白名单许可证即失败。
// 双许可组件按「任一许可证在白名单内即通过」处理。

import { readFileSync } from "node:fs";

const ALLOWED = new Set([
  "0BSD",
  "Apache-2.0",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "BSD-4-Clause",
  "CC0-1.0",
  "EPL-1.0",
  "EPL-2.0",
  "GPL-2.0-with-classpath-exception", // Jakarta API 的 EPL/GPL+CPE 双许可
  "ISC",
  "LGPL-2.1-only", // Logback 的 EPL/LGPL 双许可
  "MIT",
  "MPL-2.0",
  "Unlicense",
]);

const NORMALIZE = new Map([
  ["The Apache Software License, Version 2.0", "Apache-2.0"],
  ["Apache License, Version 2.0", "Apache-2.0"],
  ["Apache License 2.0", "Apache-2.0"],
  ["MIT License", "MIT"],
  ["The MIT License", "MIT"],
  ["Eclipse Public License - v 1.0", "EPL-1.0"],
  ["Eclipse Public License v1.0", "EPL-1.0"],
  ["Eclipse Public License v2.0", "EPL-2.0"],
  ["Eclipse Public License 2.0", "EPL-2.0"],
  ["GPL2 w/ CPE", "GPL-2.0-with-classpath-exception"],
  ["CC0 1.0 Universal", "CC0-1.0"],
]);

function normalize(id) {
  const trimmed = id.trim();
  return NORMALIZE.get(trimmed) ?? trimmed;
}

function readJson(path) {
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch (error) {
    console.error(`[license-guard] 无法读取 ${path}: ${error.message}`);
    process.exit(2);
  }
}

function componentLicenses(component) {
  return (component.licenses ?? [])
    .map((entry) => entry.expression ?? entry.license?.id ?? entry.license?.name)
    .filter(Boolean)
    .map(normalize);
}

const [bomPath, webPath] = process.argv.slice(2);
if (!bomPath || !webPath) {
  console.error("用法: license-guard.mjs <target/bom.json> <target/web-licenses.json>");
  process.exit(2);
}

// 空/残缺输入必须判红,否则生成步骤失败会伪装成「全部合规」。
const MIN_BACKEND_COMPONENTS = 80;
const MIN_WEB_PACKAGES = 50;

const bom = readJson(bomPath);
const webLicenses = readJson(webPath);
if (!Array.isArray(bom.components)) {
  console.error(`[license-guard] ${bomPath} 缺少 components 数组,无法判定`);
  process.exit(2);
}
if (
  webLicenses === null ||
  typeof webLicenses !== "object" ||
  Array.isArray(webLicenses)
) {
  console.error(`[license-guard] ${webPath} 不是许可证映射对象,无法判定`);
  process.exit(2);
}

const failures = [];
let backendCount = 0;
let webCount = 0;

for (const component of bom.components) {
  backendCount += 1;
  const licenses = componentLicenses(component);
  const label = `${component.name}@${component.version}`;
  if (licenses.length === 0) {
    failures.push(`${label}: 未声明许可证`);
  } else if (!licenses.some((id) => ALLOWED.has(id))) {
    failures.push(`${label}: ${licenses.join(", ")}`);
  }
}

for (const [license, packages] of Object.entries(webLicenses)) {
  if (!Array.isArray(packages)) {
    failures.push(`前端许可证条目 ${license}: 不是包数组`);
    continue;
  }
  const id = normalize(license);
  for (const pkg of packages) {
    webCount += 1;
    if (!ALLOWED.has(id)) {
      failures.push(`${pkg.name}@${(pkg.versions ?? []).join("/")}: ${id}`);
    }
  }
}

if (backendCount < MIN_BACKEND_COMPONENTS || webCount < MIN_WEB_PACKAGES) {
  failures.push(
    `依赖清单疑似残缺: 后端 ${backendCount} 个组件(下限 ${MIN_BACKEND_COMPONENTS}) / ` +
      `前端 ${webCount} 个包(下限 ${MIN_WEB_PACKAGES})`,
  );
}

if (failures.length > 0) {
  console.error(`[license-guard] 发现 ${failures.length} 个不在白名单的依赖许可证:`);
  failures.forEach((failure) => console.error(`  - ${failure}`));
  process.exit(1);
}

console.log(
  `[license-guard] 通过: 后端 ${backendCount} 个组件 / 前端 ${webCount} 个包,许可证均在白名单内`,
);
