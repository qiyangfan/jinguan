/* 控制面 API：租户/套餐/策略/目录/服务/命令/saga/审计/告警;JsonNode 端点用宽松体发送。 */

import { client, getAccessToken, refreshSession, unwrap } from "./api";

export interface MemberView {
  subject: string;
  role: string;
  status: string;
}

export interface TenantView {
  id: string;
  tenantKey: string;
  name: string;
  status: string;
  updatedAt: string;
  members: MemberView[];
}

export interface QuotaView {
  dimension: string;
  unit: string;
  limit: number;
}

export interface PlanView {
  id: string;
  planKey: string;
  name: string;
  status: string;
  version: number;
  updatedAt: string;
  quotas: QuotaView[];
  entitlementKeys: string[];
}

export interface BindingView {
  tenantId: string;
  tenantKey: string;
  planKey: string;
  status: string;
  boundAt: string;
}

export interface PolicyPackageView {
  id: string;
  packageKey: string;
  serviceId: string;
  scope: string;
  tenantKey: string | null;
  activeRulesetVersion: string | null;
  previousRulesetVersion: string | null;
  activeVersion: number;
  status: string;
  updatedAt: string;
}

export interface CatalogEntryView {
  id: string;
  entryKey: string;
  botKey: string;
  botTenantKey: string | null;
  title: string;
  summary: string | null;
  visibility: string;
  tenantKey: string | null;
  entitlementKey: string | null;
  enabled: boolean;
  featured: boolean;
  availability: string;
  updatedAt: string;
}

export interface InstanceView {
  instanceId: string;
  environment: string;
  version: string;
  lastSeenAt: string;
  health: string;
}

export interface ServiceView {
  serviceId: string;
  health: string;
  instances: InstanceView[];
  capabilities: string[];
  adminCapabilities: string[];
  registeredAt: string;
}

export interface ProjectionView {
  serviceId: string;
  state: string;
  mappingConfigured: boolean;
  version: string | null;
  etag: string | null;
  observedAt: string | null;
  fetchedAt: string | null;
  sections: unknown;
}

export interface DirectoryEntry {
  clientId: string;
  serviceId: string;
  scopes: string[];
  environment: string;
  disabled: boolean;
}

export interface CommandView {
  id: string;
  serviceId: string;
  action: string;
  actionSchemaVersion: number;
  params: unknown;
  reason: string;
  actor: string;
  status: string;
  attempts: number;
  maxAttempts: number;
  claimedBy: string | null;
  result: unknown;
  error: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}

export interface StepView {
  seq: number;
  name: string;
  kind: string;
  action: string | null;
  status: string;
  commandId: string | null;
  attempts: number;
  error: string | null;
  params: unknown;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface SagaView {
  id: string;
  sagaType: string;
  idempotencyKey: string;
  status: string;
  reason: string | null;
  actor: string | null;
  context: { tenantKey?: string | undefined } | null;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  closeReason: string | null;
  steps: StepView[];
}

export interface AuditView {
  id: string;
  occurredAt: string;
  actor: string;
  actorKind: string;
  actorRole: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  tenantKey: string | null;
  reason: string | null;
  summaryJson: string | null;
  result: string;
  requestId: string | null;
}

export interface AlertView {
  id: string;
  severity: string;
  source: string;
  type: string;
  status: string;
}

export function fetchTenants(): Promise<TenantView[]> {
  return unwrap<TenantView[]>(client.GET("/admin/tenants"));
}

export function createTenant(input: {
  tenantKey: string;
  name: string;
  reason: string;
}): Promise<TenantView> {
  return unwrap<TenantView>(client.POST("/admin/tenants", { body: input }));
}

export function updateTenant(id: string, name: string, reason: string): Promise<TenantView> {
  return unwrap<TenantView>(
    client.PATCH("/admin/tenants/{id}", { params: { path: { id } }, body: { name, reason } }),
  );
}

export function changeTenantEnabled(
  id: string,
  enabled: boolean,
  reason: string,
): Promise<TenantView> {
  return enabled
    ? unwrap<TenantView>(
        client.POST("/admin/tenants/{id}/enable", { params: { path: { id } }, body: { reason } }),
      )
    : unwrap<TenantView>(
        client.POST("/admin/tenants/{id}/disable", { params: { path: { id } }, body: { reason } }),
      );
}

export function addMember(
  tenantId: string,
  input: { subject: string; role: string; reason: string },
): Promise<TenantView> {
  return unwrap<TenantView>(
    client.POST("/admin/tenants/{id}/members", {
      params: { path: { id: tenantId } },
      body: input,
    }),
  );
}

export function changeMemberRole(
  tenantId: string,
  subject: string,
  input: { role: string; reason: string },
): Promise<TenantView> {
  return unwrap<TenantView>(
    client.PUT("/admin/tenants/{id}/members/{subject}", {
      params: { path: { id: tenantId, subject } },
      body: input,
    }),
  );
}

export function changeMemberEnabled(
  tenantId: string,
  subject: string,
  enabled: boolean,
  reason: string,
): Promise<TenantView> {
  return enabled
    ? unwrap<TenantView>(
        client.POST("/admin/tenants/{id}/members/{subject}/enable", {
          params: { path: { id: tenantId, subject } },
          body: { reason },
        }),
      )
    : unwrap<TenantView>(
        client.POST("/admin/tenants/{id}/members/{subject}/disable", {
          params: { path: { id: tenantId, subject } },
          body: { reason },
        }),
      );
}

export interface QuotaInput {
  dimension: string;
  unit: string;
  limit: number;
}

export function fetchPlans(): Promise<PlanView[]> {
  return unwrap<PlanView[]>(client.GET("/admin/plans"));
}

export function createPlan(input: {
  planKey: string;
  name: string;
  quotas: QuotaInput[];
  entitlementKeys: string[];
  reason: string;
}): Promise<PlanView> {
  return unwrap<PlanView>(client.POST("/admin/plans", { body: input }));
}

export function updatePlan(
  id: string,
  input: { name: string; quotas: QuotaInput[]; entitlementKeys: string[]; reason: string },
): Promise<PlanView> {
  return unwrap<PlanView>(
    client.PUT("/admin/plans/{id}", { params: { path: { id } }, body: input }),
  );
}

export function deletePlan(id: string, reason: string): Promise<PlanView> {
  return unwrap<PlanView>(
    client.POST("/admin/plans/{id}/delete", { params: { path: { id } }, body: { reason } }),
  );
}

export function fetchBindings(): Promise<BindingView[]> {
  return unwrap<BindingView[]>(client.GET("/admin/tenant-bindings"));
}

export function bindPlan(tenantId: string, planKey: string, reason: string): Promise<BindingView> {
  return unwrap<BindingView>(
    client.POST("/admin/tenants/{tenantId}/plan", {
      params: { path: { tenantId } },
      body: { planKey, reason },
    }),
  );
}

export function unbindPlan(tenantId: string, reason: string): Promise<BindingView> {
  return unwrap<BindingView>(
    client.POST("/admin/tenants/{tenantId}/plan/remove", {
      params: { path: { tenantId } },
      body: { reason },
    }),
  );
}

export function fetchPolicies(): Promise<PolicyPackageView[]> {
  return unwrap<PolicyPackageView[]>(client.GET("/admin/policies"));
}

export function createPolicy(input: {
  packageKey: string;
  serviceId: string;
  scope: string;
  tenantId?: string | undefined;
  rulesetVersion?: string | undefined;
  reason: string;
}): Promise<PolicyPackageView> {
  return unwrap<PolicyPackageView>(client.POST("/admin/policies", { body: input as never }));
}

export function activatePolicy(
  id: string,
  rulesetVersion: string,
  reason: string,
): Promise<PolicyPackageView> {
  return unwrap<PolicyPackageView>(
    client.POST("/admin/policies/{id}/activate", {
      params: { path: { id } },
      body: { rulesetVersion, reason, confirm: true } as never,
    }),
  );
}

export function rollbackPolicy(id: string, reason: string): Promise<PolicyPackageView> {
  return unwrap<PolicyPackageView>(
    client.POST("/admin/policies/{id}/rollback", {
      params: { path: { id } },
      body: { reason, confirm: true } as never,
    }),
  );
}

export function fetchCatalog(): Promise<CatalogEntryView[]> {
  return unwrap<CatalogEntryView[]>(client.GET("/admin/catalog"));
}

export function createCatalogEntry(input: {
  entryKey: string;
  botKey: string;
  botTenantKey?: string | undefined;
  title: string;
  summary?: string | undefined;
  visibility: string;
  tenantId?: string | undefined;
  entitlementKey?: string | undefined;
  featured: boolean;
  reason: string;
}): Promise<CatalogEntryView> {
  return unwrap<CatalogEntryView>(client.POST("/admin/catalog", { body: input as never }));
}

export function updateCatalogEntry(
  id: string,
  input: {
    title: string;
    summary?: string | undefined;
    visibility: string;
    tenantId?: string | undefined;
    entitlementKey?: string | undefined;
    featured: boolean;
    reason: string;
  },
): Promise<CatalogEntryView> {
  return unwrap<CatalogEntryView>(
    client.PUT("/admin/catalog/{id}", { params: { path: { id } }, body: input as never }),
  );
}

export function changeCatalogEnabled(
  id: string,
  enabled: boolean,
  reason: string,
): Promise<CatalogEntryView> {
  return enabled
    ? unwrap<CatalogEntryView>(
        client.POST("/admin/catalog/{id}/enable", { params: { path: { id } }, body: { reason } }),
      )
    : unwrap<CatalogEntryView>(
        client.POST("/admin/catalog/{id}/disable", { params: { path: { id } }, body: { reason } }),
      );
}

export function fetchServices(): Promise<ServiceView[]> {
  return unwrap<ServiceView[]>(client.GET("/admin/services"));
}

export function fetchProjection(serviceId: string): Promise<ProjectionView> {
  return unwrap<ProjectionView>(
    client.GET("/admin/services/{serviceId}/projection", { params: { path: { serviceId } } }),
  );
}

export function fetchServiceIdentities(): Promise<DirectoryEntry[]> {
  return unwrap<DirectoryEntry[]>(client.GET("/admin/service-identities"));
}

export function fetchCommands(serviceId?: string, status?: string): Promise<CommandView[]> {
  const query: { serviceId?: string; status?: string } = {};
  if (serviceId) query.serviceId = serviceId;
  if (status) query.status = status;
  return unwrap<CommandView[]>(client.GET("/admin/commands", { params: { query } }));
}

export function submitCommand(input: {
  serviceId: string;
  action: string;
  actionSchemaVersion: number;
  params: Record<string, unknown>;
  reason: string;
  idempotencyKey?: string | undefined;
}): Promise<CommandView> {
  return unwrap<CommandView>(client.POST("/admin/commands", { body: input as never }));
}

export function fetchSagas(status?: string): Promise<SagaView[]> {
  const query: { status?: string } = {};
  if (status) query.status = status;
  return unwrap<SagaView[]>(client.GET("/admin/sagas", { params: { query } }));
}

export function fetchSaga(id: string): Promise<SagaView> {
  return unwrap<SagaView>(client.GET("/admin/sagas/{id}", { params: { path: { id } } }));
}

export function startSaga(input: {
  type: string;
  idempotencyKey?: string | undefined;
  params: Record<string, unknown>;
  reason: string;
  confirm: boolean;
}): Promise<SagaView> {
  return unwrap<SagaView>(client.POST("/admin/sagas", { body: input as never }));
}

export function closeSaga(id: string, reason: string): Promise<SagaView> {
  return unwrap<SagaView>(
    client.POST("/admin/sagas/{id}/close", {
      params: { path: { id } },
      body: { reason } as never,
    }),
  );
}

export interface AuditFilter {
  actor?: string | undefined;
  action?: string | undefined;
  result?: string | undefined;
  from?: string | undefined;
  to?: string | undefined;
}

export function fetchAudit(filter: AuditFilter, page: number, size: number): Promise<AuditView[]> {
  const query: {
    actor?: string;
    action?: string;
    result?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
  } = { page, size };
  if (filter.actor) query.actor = filter.actor;
  if (filter.action) query.action = filter.action;
  if (filter.result) query.result = filter.result;
  if (filter.from) query.from = filter.from;
  if (filter.to) query.to = filter.to;
  return unwrap<AuditView[]>(client.GET("/admin/audit-events", { params: { query } }));
}

export function fetchAlerts(): Promise<AlertView[]> {
  return unwrap<AlertView[]>(client.GET("/admin/alerts", { params: { query: { limit: 100 } } }));
}

export function ackAlert(id: string): Promise<void> {
  return unwrap<void>(client.POST("/admin/alerts/{id}/ack", { params: { path: { id } } }));
}

/** 带会话头的文件下载;401 时单飞续期后重试一次。 */
async function download(path: string, filename: string): Promise<void> {
  const attempt = async (): Promise<Response> =>
    fetch(path, {
      headers: getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {},
    });
  let response = await attempt();
  if (response.status === 401 && (await refreshSession())) {
    response = await attempt();
  }
  if (!response.ok) {
    throw new Error(`下载失败(HTTP ${response.status})`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function downloadProjection(serviceId: string): Promise<void> {
  return download(
    `/admin/services/${encodeURIComponent(serviceId)}/projection/export`,
    `${serviceId}-projection.json`,
  );
}

export function downloadAuditCsv(filter: AuditFilter): Promise<void> {
  const query = new URLSearchParams();
  if (filter.actor) query.set("actor", filter.actor);
  if (filter.action) query.set("action", filter.action);
  if (filter.result) query.set("result", filter.result);
  if (filter.from) query.set("from", filter.from);
  if (filter.to) query.set("to", filter.to);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return download(`/admin/audit-events/export.csv${suffix}`, "control-audit.csv");
}
