.PHONY: check web-build web-dev clean

# 全量门禁:后端 mvn verify(spotless + test/jacoco + pmd + spotbugs 一条龙)
# + 前端四项(typecheck/eslint/stylelint/prettier)
check:
	mvn -q verify
	cd web && pnpm typecheck && pnpm lint && pnpm lint:css && pnpm format:check

# 前端构建(pnpm install 首次执行;产物输出 src/main/resources/static)
web-build:
	cd web && pnpm install --frozen-lockfile && pnpm build

# 前端开发服务(5180 端口,API 代理至 8090 控制面)
web-dev:
	cd web && pnpm dev

clean:
	mvn -q clean
	rm -rf web/dist web/node_modules
