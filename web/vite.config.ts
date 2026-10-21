import { fileURLToPath, URL } from "node:url";
import { defineConfig, type ProxyOptions } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

/** HTML 导航回到 Vite 入口，避免引用后端构建路径。 */
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

// 开发请求代理到控制面，构建产物写入 Spring Boot 静态目录。
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
