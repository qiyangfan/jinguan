import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// 登录页只保留统一身份入口；mock 会话恢复失败与 OIDC 已配置，不依赖后端。
test.beforeEach(async ({ page }) => {
  await page.route("**/auth/refresh", (route) =>
    route.fulfill({ status: 401, contentType: "application/json", body: "{}" }),
  );
  await page.route("**/auth/oidc/enabled", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ enabled: true }),
    }),
  );
});

test("登录页只提供统一身份入口且可键盘操作", async ({ page }) => {
  await page.goto("/");
  const unified = page.getByRole("link", { name: "使用统一身份登录" });
  await expect(unified).toBeVisible();
  await expect(page.getByLabel("密码")).toHaveCount(0);
  await unified.focus();
  await expect(unified).toBeFocused();
  await expect(unified).toHaveAttribute("href", "/auth/oidc/start");
});

test("登录页无 serious/critical 级无障碍违规", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("link", { name: "使用统一身份登录" })).toBeVisible();

  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const blocking = results.violations.filter(
    (v) => v.impact === "serious" || v.impact === "critical",
  );
  expect(
    blocking.map((v) => `${v.id} (${v.impact}): ${v.nodes.length} 处`),
    JSON.stringify(
      blocking.map((v) => ({ id: v.id, help: v.help, nodes: v.nodes })),
      null,
      2,
    ),
  ).toEqual([]);
});
