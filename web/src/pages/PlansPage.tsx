/* 套餐/配额/权益与租户绑定：能力键来自服务注册，绑定变化写快照版本。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { ReasonActionDialog } from "@/components/control/reason-action-dialog";
import { PLAN_STATUS } from "@/components/control/status-meta";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { errorDetail } from "@/lib/api";
import {
  bindPlan,
  createPlan,
  deletePlan,
  fetchBindings,
  fetchPlans,
  fetchTenants,
  type PlanView,
  type QuotaInput,
  unbindPlan,
  updatePlan,
} from "@/lib/control-api";

function parseQuotas(text: string): QuotaInput[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [dimension, unit, limit] = line.split(":").map((part) => part.trim());
      return { dimension: dimension ?? "", unit: unit ?? "", limit: Number(limit ?? 0) };
    });
}

function formatQuotas(plan: PlanView): string {
  return (plan.quotas ?? []).map((q) => `${q.dimension}:${q.unit}:${q.limit}`).join("\n");
}

type Action =
  { kind: "delete"; plan: PlanView } | { kind: "unbind"; tenantId: string; tenantKey: string };

export default function PlansPage() {
  const queryClient = useQueryClient();
  const plans = useQuery({ queryKey: ["control", "plans"], queryFn: fetchPlans });
  const bindings = useQuery({ queryKey: ["control", "bindings"], queryFn: fetchBindings });
  const tenants = useQuery({ queryKey: ["control", "tenants"], queryFn: fetchTenants });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [editing, setEditing] = useState<PlanView | "new" | null>(null);
  const [bindOpen, setBindOpen] = useState(false);
  const [action, setAction] = useState<Action | null>(null);
  const [draft, setDraft] = useState({
    planKey: "",
    name: "",
    quotas: "",
    keys: "",
    reason: "",
  });
  const [bindDraft, setBindDraft] = useState({ tenantId: "", planKey: "", reason: "" });

  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["control", "plans"] }),
      queryClient.invalidateQueries({ queryKey: ["control", "bindings"] }),
      queryClient.invalidateQueries({ queryKey: ["control", "tenants"] }),
    ]);
  const succeed = (message: string) => {
    setError("");
    setSuccess(message);
    void invalidate();
  };
  const fail = (err: unknown) => {
    setSuccess("");
    setError(errorDetail(err));
  };

  const openCreate = () => {
    setDraft({ planKey: "", name: "", quotas: "", keys: "", reason: "" });
    setEditing("new");
  };
  const openEdit = (plan: PlanView) => {
    setDraft({
      planKey: plan.planKey ?? "",
      name: plan.name ?? "",
      quotas: formatQuotas(plan),
      keys: (plan.entitlementKeys ?? []).join(","),
      reason: "",
    });
    setEditing(plan);
  };

  const save = useMutation({
    mutationFn: () => {
      const input = {
        name: draft.name,
        quotas: parseQuotas(draft.quotas),
        entitlementKeys: draft.keys
          .split(",")
          .map((key) => key.trim())
          .filter(Boolean),
        reason: draft.reason,
      };
      return editing === "new"
        ? createPlan({ planKey: draft.planKey, ...input })
        : updatePlan((editing as PlanView).id ?? "", input);
    },
    onSuccess: () => {
      setEditing(null);
      succeed("套餐已保存");
    },
    onError: fail,
  });

  const bind = useMutation({
    mutationFn: () => bindPlan(bindDraft.tenantId, bindDraft.planKey, bindDraft.reason),
    onSuccess: () => {
      setBindOpen(false);
      setBindDraft({ tenantId: "", planKey: "", reason: "" });
      succeed("套餐已绑定");
    },
    onError: fail,
  });

  const runAction = useMutation({
    mutationFn: async (reason: string) => {
      if (action?.kind === "delete") {
        await deletePlan(action.plan.id ?? "", reason);
      } else {
        await unbindPlan(action?.tenantId ?? "", reason);
      }
    },
    onSuccess: () => {
      setAction(null);
      succeed("变更已生效");
    },
    onError: fail,
  });

  const planOptions = plans.data ?? [];
  const bindingRows = bindings.data ?? [];

  return (
    <div className="space-y-4">
      <PageHeader
        title="套餐与权益"
        description="配额维度/单位显式;权益键必须已在服务注册能力目录声明。"
        actions={
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setBindOpen(true)}>
              绑定套餐
            </Button>
            <Button onClick={openCreate}>新建套餐</Button>
          </div>
        }
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={plans.isLoading}
        error={plans.error}
        items={plans.data}
        emptyTitle="还没有套餐"
        emptyDescription="先创建套餐，再绑定到租户。"
      >
        {(items) =>
          items.map((plan) => {
            const bound = bindingRows.filter(
              (binding) => binding.planKey === plan.planKey && binding.status === "ACTIVE",
            );
            return (
              <EntityCard
                key={plan.id}
                title={plan.name ?? plan.planKey}
                subtitle={plan.planKey}
                badge={<StatusBadge meta={PLAN_STATUS} value={plan.status} />}
                rows={[
                  { label: "版本", value: `v${plan.version ?? 1}` },
                  {
                    label: "配额",
                    value:
                      (plan.quotas ?? [])
                        .map((q) => `${q.dimension}(${q.unit})≤${q.limit}`)
                        .join("、") || "—",
                  },
                  { label: "权益键", value: (plan.entitlementKeys ?? []).join("、") || "—" },
                  {
                    label: "绑定租户",
                    value: bound.map((b) => b.tenantKey).join("、") || "—",
                  },
                ]}
                actions={
                  <>
                    <Button size="sm" variant="outline" onClick={() => openEdit(plan)}>
                      编辑
                    </Button>
                    {bound.map((b) => (
                      <Button
                        key={b.tenantId}
                        size="sm"
                        variant="outline"
                        onClick={() =>
                          setAction({
                            kind: "unbind",
                            tenantId: b.tenantId ?? "",
                            tenantKey: b.tenantKey ?? "",
                          })
                        }
                      >
                        解绑 {b.tenantKey}
                      </Button>
                    ))}
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setAction({ kind: "delete", plan })}
                    >
                      删除
                    </Button>
                  </>
                }
              />
            );
          })
        }
      </ControlList>

      {editing ? (
        <FormSheet
          title={editing === "new" ? "新建套餐" : "编辑套餐"}
          description="权益键校验服务注册能力目录;每次保存版本递增。"
          wide
          onClose={() => setEditing(null)}
          footer={
            <>
              <Button variant="outline" onClick={() => setEditing(null)}>
                取消
              </Button>
              <Button
                disabled={save.isPending || !draft.name || !draft.reason}
                onClick={() => save.mutate()}
              >
                {save.isPending ? "保存中…" : "保存"}
              </Button>
            </>
          }
        >
          {editing === "new" ? (
            <div className="grid gap-2">
              <Label htmlFor="plan-key">plan_key</Label>
              <Input
                id="plan-key"
                value={draft.planKey}
                onChange={(event) => setDraft({ ...draft, planKey: event.target.value })}
              />
            </div>
          ) : null}
          <div className="grid gap-2">
            <Label htmlFor="plan-name">名称</Label>
            <Input
              id="plan-name"
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="plan-quotas">配额（每行 dimension:unit:limit）</Label>
            <Textarea
              id="plan-quotas"
              value={draft.quotas}
              onChange={(event) => setDraft({ ...draft, quotas: event.target.value })}
              placeholder={"requests_per_day:count:100000\ntokens_per_month:tokens:5000000"}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="plan-keys">权益键（逗号分隔）</Label>
            <Input
              id="plan-keys"
              value={draft.keys}
              onChange={(event) => setDraft({ ...draft, keys: event.target.value })}
              placeholder="bot.basic,bot.store"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="plan-reason">原因（必填）</Label>
            <Input
              id="plan-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {bindOpen ? (
        <FormSheet
          title="绑定套餐到租户"
          description="租户须启用;绑定产生新的权益快照版本。"
          onClose={() => setBindOpen(false)}
          footer={
            <>
              <Button variant="outline" onClick={() => setBindOpen(false)}>
                取消
              </Button>
              <Button
                disabled={
                  bind.isPending || !bindDraft.tenantId || !bindDraft.planKey || !bindDraft.reason
                }
                onClick={() => bind.mutate()}
              >
                {bind.isPending ? "绑定中…" : "绑定"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label>租户</Label>
            <Select
              value={bindDraft.tenantId}
              onValueChange={(value) => setBindDraft({ ...bindDraft, tenantId: value })}
            >
              <SelectTrigger aria-label="选择租户">
                <SelectValue placeholder="选择租户" />
              </SelectTrigger>
              <SelectContent>
                {(tenants.data ?? [])
                  .filter((tenant) => tenant.status === "ACTIVE")
                  .map((tenant) => (
                    <SelectItem key={tenant.id} value={tenant.id ?? ""}>
                      {tenant.name}（{tenant.tenantKey}）
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label>套餐</Label>
            <Select
              value={bindDraft.planKey}
              onValueChange={(value) => setBindDraft({ ...bindDraft, planKey: value })}
            >
              <SelectTrigger aria-label="选择套餐">
                <SelectValue placeholder="选择套餐" />
              </SelectTrigger>
              <SelectContent>
                {planOptions
                  .filter((plan) => plan.status === "ACTIVE")
                  .map((plan) => (
                    <SelectItem key={plan.id} value={plan.planKey ?? ""}>
                      {plan.name}（{plan.planKey}）
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="bind-reason">原因（必填）</Label>
            <Input
              id="bind-reason"
              value={bindDraft.reason}
              onChange={(event) => setBindDraft({ ...bindDraft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {action ? (
        <ReasonActionDialog
          title={action.kind === "delete" ? "删除套餐" : "解绑套餐"}
          description={
            action.kind === "delete"
              ? `套餐 ${action.plan.planKey} 仅在未绑定时可停用。`
              : `从租户 ${action.tenantKey} 解除当前套餐。`
          }
          confirmLabel={action.kind === "delete" ? "停用套餐" : "解绑"}
          pending={runAction.isPending}
          onCancel={() => setAction(null)}
          onConfirm={(reason) => runAction.mutate(reason)}
        />
      ) : null}
    </div>
  );
}
