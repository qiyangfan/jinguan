.PHONY: check web-build web-dev openapi docker-build docker-up docker-down clean

# 全量门禁:后端 mvn verify(spotless + test/jacoco + pmd + spotbugs 一条龙)
# + 前端五项(typecheck/eslint/stylelint/prettier/vitest)
check:
	mvn -q verify
	cd web && pnpm typecheck && pnpm lint && pnpm lint:css && pnpm format:check && pnpm test

# API 契约导出(H2 兼容模式启动应用 → 根级 openapi.json);
# 前端 `pnpm contracts:gen` 与 CI 漂移门禁的数据源。需 8090 端口空闲
openapi:
	mvn -B -q -Popenapi verify

# 容器化(多阶段镜像:web 产物入 jar;本机 podman 形态)
docker-build:
	podman build --format docker -t localhost/jinguan:latest .

# 本地编排:PG 16 + 应用(数据落命名卷 jinguan-pgdata)
docker-up:
	podman compose -f docker-compose.yml up -d --build

# 停编排(保留卷;卷删除须显式 down -v,不在此提供)
docker-down:
	podman compose -f docker-compose.yml down

# 前端构建(pnpm install 首次执行;产物输出 src/main/resources/static)
web-build:
	cd web && pnpm install --frozen-lockfile && pnpm build

# 前端开发服务(5180 端口,API 代理至 8090 控制面)
web-dev:
	cd web && pnpm dev

clean:
	mvn -q clean
	rm -rf web/dist web/node_modules src/main/resources/static
