.PHONY: check web-build web-dev openapi docker-build docker-up docker-down clean

# 运行后端与前端完整校验。
check:
	mvn -q verify
	cd web && pnpm typecheck && pnpm lint && pnpm lint:css && pnpm format:check && pnpm test

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
