import { defineConfig } from "vitest/config";

// 测试只使用 fetch stub，无需 DOM。
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
