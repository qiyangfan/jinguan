import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";

// 组件与页面测试使用 jsdom;lib 测试同样兼容。
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test/setup.ts"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/lib/api-types.gen.ts"],
      reporter: ["text-summary", "html"],
      // 水位取实测值向下取整,新增代码不得拉低。
      thresholds: { statements: 38, branches: 39, functions: 29, lines: 41 },
    },
  },
});
