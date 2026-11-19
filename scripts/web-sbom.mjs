#!/usr/bin/env node
// 由 pnpm licenses(生产依赖全量)生成前端 CycloneDX SBOM;不含 integrity 哈希,组件粒度与许可证门禁同源。

import { readFileSync, writeFileSync } from "node:fs";

const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  console.error("用法: web-sbom.mjs <target/web-licenses.json> <target/web-bom.json>");
  process.exit(2);
}

function purl(name, version) {
  if (name.startsWith("@")) {
    const [scope, pkg] = name.slice(1).split("/");
    return `pkg:npm/%40${scope}/${pkg}@${version}`;
  }
  return `pkg:npm/${name}@${version}`;
}

const groups = JSON.parse(readFileSync(inputPath, "utf8"));
const components = [];
for (const packages of Object.values(groups)) {
  for (const pkg of packages) {
    for (const version of pkg.versions ?? []) {
      components.push({
        type: "library",
        name: pkg.name,
        version,
        purl: purl(pkg.name, version),
      });
    }
  }
}
components.sort((a, b) => a.purl.localeCompare(b.purl));

const bom = {
  bomFormat: "CycloneDX",
  specVersion: "1.6",
  version: 1,
  metadata: {
    timestamp: new Date().toISOString(),
    component: {
      type: "application",
      name: "jinguan-web",
      version: JSON.parse(readFileSync(new URL("../web/package.json", import.meta.url), "utf8"))
        .version,
    },
  },
  components,
};
writeFileSync(outputPath, `${JSON.stringify(bom, null, 2)}\n`);
console.log(`[web-sbom] 写入 ${components.length} 个组件 → ${outputPath}`);
