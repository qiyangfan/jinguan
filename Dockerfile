# 三阶段构建锦官镜像，将前端产物打入自包含 JAR。

# 阶段一：构建前端。
FROM docker.io/library/node:24-alpine AS web
WORKDIR /web
# 构建期使用国内 npm 镜像；COREPACK_NPM_REGISTRY 配置 pnpm 下载源。
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry https://registry.npmmirror.com && corepack enable
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
# 容器内将产物写入 /web/dist，供下一阶段打包。
RUN pnpm typecheck && pnpm exec vite build --outDir /web/dist --emptyOutDir

# 阶段二：打包后端。
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /app
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src/ src/
COPY --from=web /web/dist/ src/main/resources/static/
# 测试由 CI 执行。
RUN mvn -B -q package -DskipTests

# 阶段三：以非 root 用户运行应用。
FROM docker.io/library/eclipse-temurin:25-jre
# 安装健康检查所需的 curl。
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
