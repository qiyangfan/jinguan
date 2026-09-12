import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// dev:代理 API 到本机控制面;build:产物进 Spring Boot static(jar 自包含单服务分发)
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    port: 5180,
    proxy: {
      "/auth": "http://127.0.0.1:8090",
      "/wallet": "http://127.0.0.1:8090",
      "/payments": "http://127.0.0.1:8090",
      "/admin": "http://127.0.0.1:8090",
      "/control": "http://127.0.0.1:8090",
      "/ingest": "http://127.0.0.1:8090",
    },
  },
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
});
