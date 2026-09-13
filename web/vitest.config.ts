import { defineConfig } from "vitest/config";

// 会话行为测试(api/auth-store 纯模块逻辑,Node 环境 + fetch stub,无需 DOM)
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
