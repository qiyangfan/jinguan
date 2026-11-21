.PHONY: check lint-config web-build web-dev openapi license-check docker-build docker-up docker-down clean

# 相对基线检查迁移只增不改;本地默认与 origin/main 比较。
MIGRATION_BASE ?= origin/main

# 运行后端与前端完整校验。
check:
	bash scripts/migration-guard.sh
	MIGRATION_BASE=$(MIGRATION_BASE) bash scripts/migration-immutability.sh
	$(MAKE) lint-config
	mvn -q verify
	node scripts/assert-postgres-tests.mjs
	$(MAKE) spec-check
	$(MAKE) license-check
	# 契约漂移:重新导出根级 openapi.json,与入库版本不一致即失败。
	mvn -B -q -Popenapi verify -DskipTests -Djacoco.skip=true -Dpmd.skip=true -Dspotbugs.skip=true
	git diff --exit-code -- openapi.json
	cd web && pnpm contracts:gen && git diff --exit-code -- src/lib/api-types.gen.ts
	cd web && pnpm typecheck && pnpm lint && pnpm lint:deps && pnpm lint:css && pnpm format:check && pnpm test:coverage && pnpm build && pnpm lint:dup && pnpm size && pnpm e2e

# 脚本、工作流与构建文件静态检查(与 CI lint 作业同源)。
## spec-check: OpenSpec 规格 strict 校验(工具缺失时跳过)
spec-check:
	@if command -v openspec >/dev/null 2>&1; then openspec validate --all --strict; else echo "[spec-check] openspec 未安装,跳过"; fi

lint-config:
	shellcheck scripts/*.sh
	for f in scripts/*.mjs; do node --check "$$f" || exit 1; done
	actionlint
	uvx yamllint==1.38.0 -c .yamllint.yml .github docker-compose.yml
	xmllint --noout pom.xml spotbugs-exclude.xml rulesets/*.xml docker/maven-settings.xml
	hadolint --config .hadolint.yaml Dockerfile
	npx --yes markdownlint-cli2@0.24.1
	python3 scripts/check_md_links.py --dir contracts

# 依赖许可证门禁与前端 SBOM:BOM 由 mvn verify 阶段的 cyclonedx 插件产出。
license-check:
	mkdir -p target
	cd web && pnpm licenses list --prod --json > ../target/web-licenses.json
	node scripts/web-sbom.mjs target/web-licenses.json target/web-bom.json
	node scripts/license-guard.mjs target/bom.json target/web-licenses.json

# 导出 OpenAPI 契约供前端生成类型和 CI 校验；需要 8090 端口空闲。
openapi:
	mvn -B -q -Popenapi verify

# 构建包含前端产物的容器镜像。
docker-build:
	podman build --format docker -t localhost/jinguan:latest .

# 启动 PostgreSQL 与应用。
docker-up:
	podman compose -f docker-compose.yml up -d --build

# 停止服务并保留数据卷。
docker-down:
	podman compose -f docker-compose.yml down

# 安装依赖并构建前端。
web-build:
	cd web && pnpm install --frozen-lockfile && pnpm build

# 启动前端开发服务器。
web-dev:
	cd web && pnpm dev

clean:
	mvn -q clean
	rm -rf web/dist web/node_modules src/main/resources/static
