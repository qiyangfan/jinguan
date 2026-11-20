// 浏览器用本机已装 Chrome(channel: "chrome"),不自带浏览器二进制。
// 首次在本机跑前执行一次 `pnpm exec playwright install chrome` 的分支不需要——
// 本文件只驱动系统 Chrome,CI runner 须预装 Chrome(见 .github/workflows/ci.yml)。
import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: "http://localhost:4183",
    trace: "retain-on-failure",
    channel: "chrome",
  },
  webServer: {
    command: "pnpm vite --port 4183 --strictPort",
    url: "http://localhost:4183",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
