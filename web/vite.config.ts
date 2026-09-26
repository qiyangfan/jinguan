import { fileURLToPath, URL } from "node:url";
import { defineConfig, type ProxyOptions } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

/** dev:API 请求代理到本机控制面;文档级导航(深链直载/刷新)回落 SPA index——
    否则后端会把构建版 index.html 原样回给浏览器,其 /assets 哈希产物在 dev
    服务器上不存在,JS 全部加载失败导致整页白屏。 */
function proxyApi(target: string): ProxyOptions {
  return {
    target,
    bypass: (req) => {
      if (req.headers.accept?.includes("text/html")) {
        return "/index.html";
      }
    },
  };
}

// dev:代理 API 到本机控制面(JINGUAN_API_TARGET 可注入,默认 8090);build:产物进 Spring Boot static(jar 自包含单服务分发)
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: 5180,
    proxy: {
      "/auth": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
      "/wallet": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
      "/payments": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
      "/admin": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
      "/control": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
      "/ingest": proxyApi(process.env.JINGUAN_API_TARGET ?? "http://127.0.0.1:8090"),
    },
  },
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
});
