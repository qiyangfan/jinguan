# 锦官控制面镜像:三阶段构建(web 产物入 jar,jar 自包含交付)
# 构建期即生成前端产物,仓库不提交任何静态构建残留(与 .gitignore 的 static/ 规则一致)

# ── 阶段一:前端构建(pnpm 版本由 web/package.json 的 packageManager 钉死)──
FROM docker.io/library/node:24-alpine AS web
WORKDIR /web
# 国内构建加速:npm/pnpm 注册表走 npmmirror(仅构建期,不影响交付物);
# COREPACK_NPM_REGISTRY 管 pnpm 本体的下载源(npm config 管不到 corepack)
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry https://registry.npmmirror.com && corepack enable
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
# 仓库 vite.config 的 outDir 指向 ../src/main/resources/static(本机 jar 流程);
# 容器内显式覆盖到 /web/dist,与阶段二的 COPY 路径对齐
RUN pnpm typecheck && pnpm exec vite build --outDir /web/dist --emptyOutDir

# ── 阶段二:后端打包(依赖层缓存:先拉依赖再拷源码;central 走阿里云镜像)──
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /app
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src/ src/
COPY --from=web /web/dist/ src/main/resources/static/
# 测试在 CI 门禁跑,镜像构建不重复
RUN mvn -B -q package -DskipTests

# ── 阶段三:运行层(非 root;健康检查打 /actuator/health)──
FROM docker.io/library/eclipse-temurin:25-jre
# apt 换国内源(temurin 运行层 Ubuntu/Debian 基,两类源文件都兜);仅装 curl 供健康检查
RUN sed -i -E 's#https?://(archive\.ubuntu\.com|security\.ubuntu\.com|ports\.ubuntu\.com|deb\.debian\.org)#https://mirrors.aliyun.com#g' \
      /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --uid 1001 jinguan
WORKDIR /app
COPY --from=backend /app/target/jinguan-*.jar /app/app.jar
USER jinguan
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
    CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
