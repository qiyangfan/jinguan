/* 控制面 API 包装：路径/方法/查询参数与下载行为。 */

import { beforeEach, describe, expect, it, vi } from "vitest";

const { fetchMock } = vi.hoisted(() => ({
  fetchMock: vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>(),
}));
// openapi-fetch 在创建客户端时捕获 fetch,必须在首次导入前完成桩替换。
vi.stubGlobal("fetch", fetchMock);

function jsonResponse(status: number, body: unknown): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(() => jsonResponse(200, {}));
});

async function api() {
  return import("./control-api");
}

function lastRequest(): Request {
  const [req] = fetchMock.mock.calls.at(-1) as [Request];
  return req;
}

describe("控制面 API 路径", () => {
  it("租户读写走 /admin/tenants", async () => {
    const m = await api();
    await m.fetchTenants();
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants");

    await m.createTenant({ tenantKey: "t1", name: "一号", reason: "r" });
    expect(lastRequest().method).toBe("POST");
    expect(await lastRequest().json()).toEqual({ tenantKey: "t1", name: "一号", reason: "r" });

    await m.changeTenantEnabled("id-1", false, "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/id-1/disable");

    await m.changeTenantEnabled("id-1", true, "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/id-1/enable");

    await m.addMember("id-1", { subject: "s", role: "MEMBER", reason: "r" });
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/id-1/members");

    await m.changeMemberRole("id-1", "s", { role: "ADMIN", reason: "r" });
    expect(lastRequest().method).toBe("PUT");

    await m.changeMemberEnabled("id-1", "s", false, "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/id-1/members/s/disable");

    await m.updateTenant("id-1", "新名", "r");
    expect(lastRequest().method).toBe("PATCH");
  });

  it("套餐与绑定", async () => {
    const m = await api();
    await m.fetchPlans();
    await m.createPlan({
      planKey: "pro",
      name: "专业版",
      quotas: [{ dimension: "requests_per_day", unit: "count", limit: 10 }],
      entitlementKeys: ["bot.basic"],
      reason: "r",
    });
    await m.updatePlan("p1", { name: "v2", quotas: [], entitlementKeys: [], reason: "r" });
    await m.deletePlan("p1", "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/plans/p1/delete");
    await m.fetchBindings();
    await m.bindPlan("t1", "pro", "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/t1/plan");
    await m.unbindPlan("t1", "r");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/tenants/t1/plan/remove");
  });

  it("策略包、目录与服务视图", async () => {
    const m = await api();
    await m.fetchPolicies();
    await m.createPolicy({ packageKey: "p", serviceId: "kongming", scope: "GLOBAL", reason: "r" });
    await m.activatePolicy("p1", "v2", "r");
    expect(await lastRequest().json()).toEqual({
      rulesetVersion: "v2",
      reason: "r",
      confirm: true,
    });
    await m.rollbackPolicy("p1", "r");

    await m.fetchCatalog();
    await m.createCatalogEntry({
      entryKey: "e",
      botKey: "b",
      title: "t",
      visibility: "PUBLIC",
      featured: false,
      reason: "r",
    });
    await m.updateCatalogEntry("c1", {
      title: "t2",
      visibility: "PUBLIC",
      featured: true,
      reason: "r",
    });
    await m.changeCatalogEnabled("c1", false, "r");

    await m.fetchServices();
    await m.fetchProjection("kongming");
    expect(new URL(lastRequest().url).pathname).toBe("/admin/services/kongming/projection");
    await m.fetchServiceIdentities();
  });

  it("命令与 saga 带上过滤条件", async () => {
    const m = await api();
    await m.fetchCommands("kongming", "PENDING");
    const query = new URL(lastRequest().url).searchParams;
    expect(query.get("serviceId")).toBe("kongming");
    expect(query.get("status")).toBe("PENDING");

    await m.submitCommand({
      serviceId: "kongming",
      action: "reindex",
      actionSchemaVersion: 1,
      params: { target: "usage" },
      reason: "r",
    });
    expect(await lastRequest().json()).toEqual({
      serviceId: "kongming",
      action: "reindex",
      actionSchemaVersion: 1,
      params: { target: "usage" },
      reason: "r",
    });

    await m.fetchSagas("RUNNING");
    await m.fetchSaga("s1");
    await m.startSaga({
      type: "tenant_onboard",
      params: { tenantKey: "t2" },
      reason: "r",
      confirm: true,
    });
    await m.closeSaga("s1", "r");
  });

  it("审计筛选与告警", async () => {
    const m = await api();
    await m.fetchAudit({ actor: "a", action: "TENANT_CREATE", result: "SUCCESS" }, 2, 20);
    const query = new URL(lastRequest().url).searchParams;
    expect(query.get("actor")).toBe("a");
    expect(query.get("page")).toBe("2");
    expect(query.get("size")).toBe("20");
    await m.fetchAlerts();
    await m.ackAlert("al1");
  });

  it("下载携带会话头与文件名", async () => {
    const m = await api();
    const createObjectURL = vi.fn(() => "blob:x");
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL, revokeObjectURL }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    fetchMock.mockImplementation(() => Promise.resolve(new Response("data", { status: 200 })));
    await m.downloadProjection("kongming");
    expect(click).toHaveBeenCalled();
    await m.downloadAuditCsv({ actor: "a" });
    const raw = fetchMock.mock.calls.at(-1)?.[0];
    const target = raw instanceof Request ? raw.url : typeof raw === "string" ? raw : "http://x";
    expect(new URL(target, "http://localhost").searchParams.get("actor")).toBe("a");
  });
});
