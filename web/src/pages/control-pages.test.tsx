/* 控制台页面：列表、创建/变更表单与只读视图的最小交互。 */

import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { renderWithQuery } from "@/test/render";

import AuditPage from "./AuditPage";
import CatalogPage from "./CatalogPage";
import CommandsPage from "./CommandsPage";
import ControlHomePage from "./ControlHomePage";
import PlansPage from "./PlansPage";
import PoliciesPage from "./PoliciesPage";
import SagasPage from "./SagasPage";
import ServicesPage from "./ServicesPage";
import TenantsPage from "./TenantsPage";

vi.mock("@/lib/control-api", () => ({
  fetchTenants: vi.fn(),
  createTenant: vi.fn(),
  updateTenant: vi.fn(),
  changeTenantEnabled: vi.fn(),
  addMember: vi.fn(),
  changeMemberRole: vi.fn(),
  changeMemberEnabled: vi.fn(),
  fetchPlans: vi.fn(),
  fetchBindings: vi.fn(),
  createPlan: vi.fn(),
  updatePlan: vi.fn(),
  deletePlan: vi.fn(),
  bindPlan: vi.fn(),
  unbindPlan: vi.fn(),
  fetchPolicies: vi.fn(),
  createPolicy: vi.fn(),
  activatePolicy: vi.fn(),
  rollbackPolicy: vi.fn(),
  fetchCatalog: vi.fn(),
  createCatalogEntry: vi.fn(),
  updateCatalogEntry: vi.fn(),
  changeCatalogEnabled: vi.fn(),
  fetchServices: vi.fn(),
  fetchProjection: vi.fn(),
  fetchServiceIdentities: vi.fn(),
  downloadProjection: vi.fn(),
  fetchCommands: vi.fn(),
  submitCommand: vi.fn(),
  fetchSagas: vi.fn(),
  fetchSaga: vi.fn(),
  startSaga: vi.fn(),
  closeSaga: vi.fn(),
  fetchAudit: vi.fn(),
  downloadAuditCsv: vi.fn(),
  fetchAlerts: vi.fn(),
  ackAlert: vi.fn(),
}));

import * as api from "@/lib/control-api";

const tenant = {
  id: "t1",
  tenantKey: "t1",
  name: "一号租户",
  status: "ACTIVE",
  updatedAt: "2026-11-27T08:00:00Z",
  members: [{ subject: "sub-1", role: "OWNER", status: "ACTIVE" }],
};
const plan = {
  id: "p1",
  planKey: "pro",
  name: "专业版",
  status: "ACTIVE",
  version: 2,
  updatedAt: "2026-11-27T08:00:00Z",
  quotas: [{ dimension: "requests_per_day", unit: "count", limit: 1000 }],
  entitlementKeys: ["bot.basic"],
};
const binding = {
  tenantId: "t1",
  tenantKey: "t1",
  planKey: "pro",
  status: "ACTIVE",
  boundAt: "2026-11-27T08:00:00Z",
};
const policy = {
  id: "po1",
  packageKey: "shared-guard",
  serviceId: "kongming",
  scope: "GLOBAL",
  tenantKey: null,
  activeRulesetVersion: "v2",
  previousRulesetVersion: "v1",
  activeVersion: 2,
  status: "ACTIVE",
  updatedAt: "2026-11-27T08:00:00Z",
};
const catalogEntry = {
  id: "c1",
  entryKey: "assistant",
  botKey: "bot-1",
  botTenantKey: null,
  title: "示例助手",
  summary: null,
  visibility: "PUBLIC",
  tenantKey: null,
  entitlementKey: "bot.basic",
  enabled: true,
  featured: true,
  availability: "AVAILABLE",
  updatedAt: "2026-11-27T08:00:00Z",
};
const service = {
  serviceId: "kongming",
  health: "ACTIVE",
  instances: [
    {
      instanceId: "kongming-0",
      environment: "local",
      version: "1.0.0",
      lastSeenAt: "2026-11-27T08:00:00Z",
      health: "ACTIVE",
    },
  ],
  capabilities: ["bot.basic"],
  adminCapabilities: ["data_source_read"],
  registeredAt: "2026-11-27T08:00:00Z",
};
const projection = {
  serviceId: "kongming",
  state: "FRESH",
  mappingConfigured: true,
  version: "1",
  etag: "e1",
  observedAt: "2026-11-27T08:00:00Z",
  fetchedAt: "2026-11-27T08:00:00Z",
  sections: { bots: [{ bot_key: "bot-1" }] },
};
const command = {
  id: "cmd-1",
  serviceId: "kongming",
  action: "reindex",
  actionSchemaVersion: 1,
  params: { target: "usage" },
  reason: "重建索引",
  actor: "admin",
  status: "PENDING",
  attempts: 0,
  maxAttempts: 3,
  claimedBy: null,
  result: null,
  error: null,
  createdAt: "2026-11-27T08:00:00Z",
  updatedAt: "2026-11-27T08:00:00Z",
  finishedAt: null,
};
const saga = {
  id: "saga-1",
  sagaType: "tenant_onboard",
  idempotencyKey: "saga-key",
  status: "RUNNING",
  reason: "开通",
  actor: "admin",
  context: { tenantKey: "t2" },
  createdAt: "2026-11-27T08:00:00Z",
  updatedAt: "2026-11-27T08:00:00Z",
  closedAt: null,
  closeReason: null,
  steps: [
    {
      seq: 1,
      name: "create_tenant",
      kind: "ADMIN",
      action: null,
      status: "SUCCEEDED",
      commandId: null,
      attempts: 1,
      error: null,
      params: {},
      startedAt: "2026-11-27T08:00:00Z",
      finishedAt: "2026-11-27T08:00:00Z",
    },
  ],
};
const audit = {
  id: "a1",
  occurredAt: "2026-11-27T08:00:00Z",
  actor: "admin",
  actorKind: "USER",
  actorRole: "ADMIN",
  action: "TENANT_CREATE",
  targetType: "tenant",
  targetId: "t1",
  tenantKey: "t1",
  reason: "开通",
  summaryJson: '{"name":"一号"}',
  result: "SUCCESS",
  requestId: "req-1",
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.fetchTenants).mockResolvedValue([tenant]);
  vi.mocked(api.createTenant).mockResolvedValue(tenant);
  vi.mocked(api.updateTenant).mockResolvedValue(tenant);
  vi.mocked(api.changeTenantEnabled).mockResolvedValue(tenant);
  vi.mocked(api.addMember).mockResolvedValue(tenant);
  vi.mocked(api.changeMemberRole).mockResolvedValue(tenant);
  vi.mocked(api.changeMemberEnabled).mockResolvedValue(tenant);
  vi.mocked(api.fetchPlans).mockResolvedValue([plan]);
  vi.mocked(api.fetchBindings).mockResolvedValue([binding]);
  vi.mocked(api.createPlan).mockResolvedValue(plan);
  vi.mocked(api.updatePlan).mockResolvedValue(plan);
  vi.mocked(api.deletePlan).mockResolvedValue(plan);
  vi.mocked(api.bindPlan).mockResolvedValue(binding);
  vi.mocked(api.unbindPlan).mockResolvedValue(binding);
  vi.mocked(api.fetchPolicies).mockResolvedValue([policy]);
  vi.mocked(api.createPolicy).mockResolvedValue(policy);
  vi.mocked(api.activatePolicy).mockResolvedValue(policy);
  vi.mocked(api.rollbackPolicy).mockResolvedValue(policy);
  vi.mocked(api.fetchCatalog).mockResolvedValue([catalogEntry]);
  vi.mocked(api.createCatalogEntry).mockResolvedValue(catalogEntry);
  vi.mocked(api.updateCatalogEntry).mockResolvedValue(catalogEntry);
  vi.mocked(api.changeCatalogEnabled).mockResolvedValue(catalogEntry);
  vi.mocked(api.fetchServices).mockResolvedValue([service]);
  vi.mocked(api.fetchProjection).mockResolvedValue(projection);
  vi.mocked(api.fetchServiceIdentities).mockResolvedValue([]);
  vi.mocked(api.downloadProjection).mockResolvedValue(undefined);
  vi.mocked(api.fetchCommands).mockResolvedValue([command]);
  vi.mocked(api.submitCommand).mockResolvedValue(command);
  vi.mocked(api.fetchSagas).mockResolvedValue([saga]);
  vi.mocked(api.fetchSaga).mockResolvedValue(saga);
  vi.mocked(api.startSaga).mockResolvedValue(saga);
  vi.mocked(api.closeSaga).mockResolvedValue(saga);
  vi.mocked(api.fetchAudit).mockResolvedValue([audit]);
  vi.mocked(api.downloadAuditCsv).mockResolvedValue(undefined);
});

describe("控制台页面", () => {
  it("租户：创建与停用", async () => {
    renderWithQuery(<TenantsPage />);
    expect(await screen.findByText("一号租户")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "新建租户" }));
    await userEvent.type(screen.getByLabelText("tenant_key"), "t9");
    await userEvent.type(screen.getByLabelText("名称"), "九号");
    await userEvent.type(screen.getByLabelText("原因（必填）"), "开通");
    await userEvent.click(screen.getByRole("button", { name: "创建" }));
    await waitFor(() =>
      expect(api.createTenant).toHaveBeenCalledWith({
        tenantKey: "t9",
        name: "九号",
        reason: "开通",
      }),
    );

    await userEvent.click(screen.getByRole("button", { name: "停用" }));
    await userEvent.type(screen.getByLabelText("变更原因（必填）"), "停用");
    await userEvent.click(screen.getByRole("button", { name: "确认执行" }));
    await waitFor(() => expect(api.changeTenantEnabled).toHaveBeenCalledWith("t1", false, "停用"));
  });

  it("套餐：编辑并保存", async () => {
    renderWithQuery(<PlansPage />);
    expect(await screen.findByText("专业版")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "编辑" }));
    await userEvent.type(screen.getByLabelText("原因（必填）"), "调额");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(api.updatePlan).toHaveBeenCalled());
  });

  it("策略包：激活版本", async () => {
    renderWithQuery(<PoliciesPage />);
    expect(await screen.findByText("shared-guard")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "激活版本" }));
    await userEvent.type(screen.getByLabelText("规则集版本"), "v3");
    await userEvent.type(screen.getByLabelText("原因（必填）"), "升级");
    await userEvent.click(screen.getByRole("button", { name: "确认激活" }));
    await waitFor(() => expect(api.activatePolicy).toHaveBeenCalledWith("po1", "v3", "升级"));
  });

  it("目录：新建条目", async () => {
    renderWithQuery(<CatalogPage />);
    expect(await screen.findByText("示例助手")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "新建条目" }));
    await userEvent.type(screen.getByLabelText("entry_key"), "new-entry");
    await userEvent.type(screen.getByLabelText("botKey"), "bot-9");
    await userEvent.type(screen.getByLabelText("标题"), "新条目");
    await userEvent.type(screen.getByLabelText("原因（必填）"), "上架");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(api.createCatalogEntry).toHaveBeenCalled());
  });

  it("服务：只读视图与导出", async () => {
    renderWithQuery(<ServicesPage />);
    expect(await screen.findByText("kongming")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "只读视图" }));
    await waitFor(() => expect(api.fetchProjection).toHaveBeenCalledWith("kongming"));
    expect(await screen.findByText("只读")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "导出（脱敏）" }));
    await waitFor(() => expect(api.downloadProjection).toHaveBeenCalledWith("kongming"));
  });

  it("命令：提交并展示列表", async () => {
    renderWithQuery(<CommandsPage />);
    expect(await screen.findByText("reindex")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "提交命令" }));
    await userEvent.type(screen.getByLabelText("原因（必填）"), "重建");
    await userEvent.click(screen.getByRole("button", { name: "提交" }));
    await waitFor(() => expect(api.submitCommand).toHaveBeenCalled());
  });

  it("流程：启动与查看步骤", async () => {
    renderWithQuery(<SagasPage />);
    expect(await screen.findByText("tenant_onboard")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "开通租户" }));
    await userEvent.type(screen.getByLabelText("tenant_key"), "t2");
    await userEvent.type(screen.getByLabelText("租户名称"), "二号");
    await userEvent.type(screen.getByLabelText("plan_key"), "pro");
    await userEvent.type(screen.getByLabelText("原因（必填）"), "开通");
    await userEvent.click(screen.getByRole("button", { name: "启动流程" }));
    await waitFor(() => expect(api.startSaga).toHaveBeenCalled());

    await userEvent.click(screen.getByRole("button", { name: "步骤" }));
    await waitFor(() => expect(api.fetchSaga).toHaveBeenCalledWith("saga-1"));
    expect(await screen.findByText(/create_tenant/)).toBeInTheDocument();
  });

  it("审计：筛选与导出", async () => {
    renderWithQuery(<AuditPage />);
    expect(await screen.findByText("TENANT_CREATE")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("actor"), "admin");
    await userEvent.click(screen.getByRole("button", { name: "应用筛选" }));
    await waitFor(() => expect(api.fetchAudit).toHaveBeenLastCalledWith({ actor: "admin" }, 0, 20));
    await userEvent.click(screen.getByRole("button", { name: "导出 CSV" }));
    await waitFor(() => expect(api.downloadAuditCsv).toHaveBeenCalled());
  });

  it("控制台导航页展示所有入口", () => {
    renderWithQuery(
      <MemoryRouter>
        <ControlHomePage />
      </MemoryRouter>,
    );
    for (const label of ["租户与成员", "套餐与权益", "共享策略包", "商店目录", "服务与只读视图"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });
});
