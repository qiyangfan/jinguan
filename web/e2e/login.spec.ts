import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// 登录页壳层冒烟 + 无障碍:只放行 serious/critical,不依赖后端。
// /api/** 通配不覆盖 /auth/*、/wallet/* 等真实前缀,故逐前缀 mock。

const API_PREFIXES = ["/auth", "/wallet", "/payments", "/admin", "/control", "/ingest"];

test.beforeEach(async ({ page }) => {
  // 会话恢复失败即视为未登录,落在登录页。
  for (const prefix of API_PREFIXES) {
    await page.route(`${prefix}/**`, (route) =>
      route.fulfill({ status: 401, contentType: "application/json", body: "{}" }),
    );
  }
});

test("登录页渲染壳层并可通过键盘操作", async ({ page }) => {
  await page.goto("/");
  const username = page.getByLabel("用户名");
  const password = page.getByLabel("密码");
  const submit = page.getByRole("button", { name: "登录", exact: true });

  await expect(username).toBeVisible();
  await expect(password).toBeVisible();
  await expect(submit).toBeEnabled();

  // 登录/注册切换由按钮承载,切到注册后提交文案随之变化。
  await page.getByRole("button", { name: /注册/ }).click();
  await expect(page.getByRole("button", { name: "注册并登录" })).toBeVisible();
});

test("登录页无 serious/critical 级无障碍违规", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByLabel("用户名")).toBeVisible();

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
