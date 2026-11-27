/* 共享策略包：只拥有包引用/作用域/激活版本;规则体留在服务域。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { ReasonActionDialog } from "@/components/control/reason-action-dialog";
import { POLICY_STATUS } from "@/components/control/status-meta";
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
import { errorDetail } from "@/lib/api";
import {
  activatePolicy,
  createPolicy,
  fetchPolicies,
  fetchTenants,
  type PolicyPackageView,
  rollbackPolicy,
} from "@/lib/control-api";

type Action =
  { kind: "activate"; policy: PolicyPackageView } | { kind: "rollback"; policy: PolicyPackageView };

export default function PoliciesPage() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ["control", "policies"], queryFn: fetchPolicies });
  const tenants = useQuery({ queryKey: ["control", "tenants"], queryFn: fetchTenants });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [action, setAction] = useState<Action | null>(null);
  const [draft, setDraft] = useState({
    packageKey: "",
    serviceId: "kongming",
    scope: "GLOBAL",
    tenantId: "",
    rulesetVersion: "",
    reason: "",
  });
  const [activation, setActivation] = useState({ rulesetVersion: "", reason: "" });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["control", "policies"] });
  const succeed = (message: string) => {
    setError("");
    setSuccess(message);
    void invalidate();
  };
  const fail = (err: unknown) => {
    setSuccess("");
    setError(errorDetail(err));
  };

  const create = useMutation({
    mutationFn: () =>
      createPolicy({
        packageKey: draft.packageKey,
        serviceId: draft.serviceId,
        scope: draft.scope,
        tenantId: draft.scope === "TENANT" ? draft.tenantId : undefined,
        rulesetVersion: draft.rulesetVersion || undefined,
        reason: draft.reason,
      }),
    onSuccess: () => {
      setCreateOpen(false);
      succeed("策略包已创建");
    },
    onError: fail,
  });

  const runAction = useMutation({
    mutationFn: (reason: string) =>
      action?.kind === "activate"
        ? activatePolicy(action.policy.id ?? "", activation.rulesetVersion, reason)
        : rollbackPolicy(action?.policy.id ?? "", reason),
    onSuccess: () => {
      setAction(null);
      setActivation({ rulesetVersion: "", reason: "" });
      succeed("策略包已更新");
    },
    onError: fail,
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="共享策略包"
        description="锦官只激活包引用;服务在自己的规则目录中解析规则体。"
        actions={<Button onClick={() => setCreateOpen(true)}>新建策略包</Button>}
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={policies.isLoading}
        error={policies.error}
        items={policies.data}
        emptyTitle="还没有策略包"
        emptyDescription="创建包引用后由服务拉取快照并本地执行。"
      >
        {(items) =>
          items.map((policy) => (
            <EntityCard
              key={policy.id}
              title={policy.packageKey}
              subtitle={`${policy.serviceId} · ${policy.scope}`}
              badge={<StatusBadge meta={POLICY_STATUS} value={policy.status} />}
              rows={[
                { label: "当前规则集", value: policy.activeRulesetVersion ?? "—" },
                { label: "上一版本", value: policy.previousRulesetVersion ?? "—" },
                { label: "激活序号", value: String(policy.activeVersion ?? 0) },
                { label: "租户", value: policy.tenantKey ?? "全局" },
              ]}
              actions={
                <>
                  <Button size="sm" onClick={() => setAction({ kind: "activate", policy })}>
                    激活版本
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={!policy.previousRulesetVersion}
                    onClick={() => setAction({ kind: "rollback", policy })}
                  >
                    回滚
                  </Button>
                </>
              }
            />
          ))
        }
      </ControlList>

      {createOpen ? (
        <FormSheet
          title="新建策略包"
          description="只登记包引用与作用域;不接受规则体。"
          onClose={() => setCreateOpen(false)}
          footer={
            <>
              <Button variant="outline" onClick={() => setCreateOpen(false)}>
                取消
              </Button>
              <Button
                disabled={create.isPending || !draft.packageKey || !draft.reason}
                onClick={() => create.mutate()}
              >
                {create.isPending ? "创建中…" : "创建"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label htmlFor="policy-key">package_key</Label>
            <Input
              id="policy-key"
              value={draft.packageKey}
              onChange={(event) => setDraft({ ...draft, packageKey: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label>服务</Label>
            <Select
              value={draft.serviceId}
              onValueChange={(value) => setDraft({ ...draft, serviceId: value })}
            >
              <SelectTrigger aria-label="归属服务">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="kongming">kongming</SelectItem>
                <SelectItem value="muniu">muniu</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label>作用域</Label>
            <Select
              value={draft.scope}
              onValueChange={(value) => setDraft({ ...draft, scope: value })}
            >
              <SelectTrigger aria-label="作用域">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="GLOBAL">GLOBAL</SelectItem>
                <SelectItem value="TENANT">TENANT</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {draft.scope === "TENANT" ? (
            <div className="grid gap-2">
              <Label>租户</Label>
              <Select
                value={draft.tenantId}
                onValueChange={(value) => setDraft({ ...draft, tenantId: value })}
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
          ) : null}
          <div className="grid gap-2">
            <Label htmlFor="policy-version">初始规则集版本（可选）</Label>
            <Input
              id="policy-version"
              value={draft.rulesetVersion}
              onChange={(event) => setDraft({ ...draft, rulesetVersion: event.target.value })}
              placeholder="例如 v1（服务域规则集标识）"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="policy-reason">原因（必填）</Label>
            <Input
              id="policy-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {action?.kind === "activate" ? (
        <FormSheet
          title={`激活 ${action.policy.packageKey}`}
          description="输入服务域规则集版本;激活会写入审计并递增快照版本。"
          onClose={() => setAction(null)}
          footer={
            <>
              <Button variant="outline" onClick={() => setAction(null)}>
                取消
              </Button>
              <Button
                disabled={runAction.isPending || !activation.rulesetVersion}
                onClick={() => runAction.mutate(activation.reason)}
              >
                {runAction.isPending ? "激活中…" : "确认激活"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label htmlFor="activation-version">规则集版本</Label>
            <Input
              id="activation-version"
              value={activation.rulesetVersion}
              onChange={(event) =>
                setActivation({ ...activation, rulesetVersion: event.target.value })
              }
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="activation-reason">原因（必填）</Label>
            <Input
              id="activation-reason"
              value={activation.reason}
              onChange={(event) => setActivation({ ...activation, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {action?.kind === "rollback" ? (
        <ReasonActionDialog
          title={`回滚 ${action.policy.packageKey}`}
          description={`恢复上一激活版本 ${action.policy.previousRulesetVersion ?? "—"}。`}
          confirmLabel="确认回滚"
          pending={runAction.isPending}
          onCancel={() => setAction(null)}
          onConfirm={(reason) => runAction.mutate(reason)}
        />
      ) : null}
    </div>
  );
}
