/* 租户与成员目录：读写共享控制数据，写操作要求原因并经审计。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { ReasonActionDialog } from "@/components/control/reason-action-dialog";
import { TENANT_STATUS } from "@/components/control/status-meta";
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
  addMember,
  changeMemberEnabled,
  changeMemberRole,
  changeTenantEnabled,
  createTenant,
  fetchTenants,
  type TenantView,
} from "@/lib/control-api";

const ROLES = ["OWNER", "ADMIN", "MEMBER"];

type TenantAction =
  | { kind: "enable" | "disable"; tenant: TenantView }
  | { kind: "member-enable" | "member-disable"; tenant: TenantView; subject: string }
  | { kind: "member-role"; tenant: TenantView; subject: string; role: string };

export default function TenantsPage() {
  const queryClient = useQueryClient();
  const tenants = useQuery({ queryKey: ["control", "tenants"], queryFn: fetchTenants });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [memberFor, setMemberFor] = useState<TenantView | null>(null);
  const [action, setAction] = useState<TenantAction | null>(null);
  const [draft, setDraft] = useState({ tenantKey: "", name: "", reason: "" });
  const [memberDraft, setMemberDraft] = useState({ subject: "", role: "MEMBER", reason: "" });
  const [roleDraft, setRoleDraft] = useState<Record<string, string>>({});

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["control", "tenants"] });
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
    mutationFn: () => createTenant(draft),
    onSuccess: () => {
      setCreateOpen(false);
      setDraft({ tenantKey: "", name: "", reason: "" });
      succeed("租户已创建");
    },
    onError: fail,
  });

  const add = useMutation({
    mutationFn: () =>
      addMember(memberFor?.id ?? "", {
        subject: memberDraft.subject,
        role: memberDraft.role,
        reason: memberDraft.reason,
      }),
    onSuccess: () => {
      setMemberDraft({ subject: "", role: "MEMBER", reason: "" });
      succeed("成员已加入");
    },
    onError: fail,
  });

  const runAction = useMutation({
    mutationFn: (reason: string) => {
      if (!action) throw new Error("没有待执行操作");
      switch (action.kind) {
        case "enable":
        case "disable":
          return changeTenantEnabled(action.tenant.id, action.kind === "enable", reason);
        case "member-enable":
        case "member-disable":
          return changeMemberEnabled(
            action.tenant.id,
            action.subject,
            action.kind === "member-enable",
            reason,
          );
        case "member-role":
          return changeMemberRole(action.tenant.id, action.subject, {
            role: action.role,
            reason,
          });
      }
    },
    onSuccess: () => {
      setAction(null);
      succeed("变更已生效");
    },
    onError: fail,
  });

  const memberTenant = tenants.data?.find((tenant) => tenant.id === memberFor?.id);

  return (
    <div className="space-y-4">
      <PageHeader
        title="租户与成员"
        description="锦官是租户/成员/粗角色的唯一写者；团队与细权限留在服务域。"
        actions={
          <Button onClick={() => setCreateOpen(true)} className="min-h-11 md:min-h-9">
            新建租户
          </Button>
        }
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={tenants.isLoading}
        error={tenants.error}
        items={tenants.data}
        emptyTitle="还没有租户"
        emptyDescription="创建第一个租户后即可绑定套餐与开通服务。"
      >
        {(items) =>
          items.map((tenant) => (
            <EntityCard
              key={tenant.id}
              title={tenant.name}
              subtitle={tenant.tenantKey}
              badge={<StatusBadge meta={TENANT_STATUS} value={tenant.status} />}
              rows={[
                { label: "成员", value: `${tenant.members?.length ?? 0} 人` },
                {
                  label: "成员列表",
                  value:
                    tenant.members
                      ?.map((member) => `${member.subject}(${member.role})`)
                      .join("、") || "—",
                },
                { label: "更新", value: tenant.updatedAt ?? "—" },
              ]}
              actions={
                <>
                  <Button variant="outline" size="sm" onClick={() => setMemberFor(tenant)}>
                    成员
                  </Button>
                  <Button
                    variant={tenant.status === "ACTIVE" ? "outline" : "secondary"}
                    size="sm"
                    onClick={() =>
                      setAction({
                        kind: tenant.status === "ACTIVE" ? "disable" : "enable",
                        tenant,
                      })
                    }
                  >
                    {tenant.status === "ACTIVE" ? "停用" : "启用"}
                  </Button>
                </>
              }
            />
          ))
        }
      </ControlList>

      {createOpen ? (
        <FormSheet
          title="新建租户"
          description="tenant_key 创建后不可修改;原因会写入审计。"
          onClose={() => setCreateOpen(false)}
          footer={
            <>
              <Button variant="outline" onClick={() => setCreateOpen(false)}>
                取消
              </Button>
              <Button
                disabled={create.isPending || !draft.tenantKey || !draft.name || !draft.reason}
                onClick={() => create.mutate()}
              >
                {create.isPending ? "创建中…" : "创建"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label htmlFor="tenant-key">tenant_key</Label>
            <Input
              id="tenant-key"
              value={draft.tenantKey}
              onChange={(event) => setDraft({ ...draft, tenantKey: event.target.value })}
              placeholder="小写字母/数字/下划线/短横线"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="tenant-name">名称</Label>
            <Input
              id="tenant-name"
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="tenant-reason">原因（必填）</Label>
            <Input
              id="tenant-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {memberTenant ? (
        <FormSheet
          title={`成员 · ${memberTenant.name}`}
          description="粗角色只覆盖控制面/产品入口;成员变更写审计。"
          wide
          onClose={() => setMemberFor(null)}
          footer={
            <Button variant="outline" onClick={() => setMemberFor(null)}>
              关闭
            </Button>
          }
        >
          <div className="grid gap-3">
            {memberTenant.members?.length ? (
              memberTenant.members.map((member) => (
                <div key={member.subject} className="grid gap-2 rounded-md border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="min-w-0 truncate font-medium">{member.subject}</span>
                    <StatusBadge meta={TENANT_STATUS} value={member.status} />
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Select
                      value={roleDraft[member.subject] ?? member.role}
                      onValueChange={(value) =>
                        setRoleDraft({ ...roleDraft, [member.subject]: value })
                      }
                    >
                      <SelectTrigger className="w-32" aria-label="成员角色">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {ROLES.map((role) => (
                          <SelectItem key={role} value={role}>
                            {role}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() =>
                        setAction({
                          kind: "member-role",
                          tenant: memberTenant,
                          subject: member.subject,
                          role: roleDraft[member.subject] ?? member.role,
                        })
                      }
                    >
                      保存角色
                    </Button>
                    <Button
                      size="sm"
                      variant={member.status === "ACTIVE" ? "outline" : "secondary"}
                      onClick={() =>
                        setAction({
                          kind: member.status === "ACTIVE" ? "member-disable" : "member-enable",
                          tenant: memberTenant,
                          subject: member.subject,
                        })
                      }
                    >
                      {member.status === "ACTIVE" ? "停用" : "启用"}
                    </Button>
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-muted-foreground">暂无成员</p>
            )}
          </div>
          <div className="grid gap-2 rounded-md border p-3">
            <p className="text-sm font-medium">新增成员</p>
            <Input
              value={memberDraft.subject}
              onChange={(event) => setMemberDraft({ ...memberDraft, subject: event.target.value })}
              placeholder="Keycloak subject"
            />
            <Select
              value={memberDraft.role}
              onValueChange={(value) => setMemberDraft({ ...memberDraft, role: value })}
            >
              <SelectTrigger aria-label="新成员角色">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLES.map((role) => (
                  <SelectItem key={role} value={role}>
                    {role}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              value={memberDraft.reason}
              onChange={(event) => setMemberDraft({ ...memberDraft, reason: event.target.value })}
              placeholder="变更原因（必填）"
              maxLength={512}
            />
            <Button
              size="sm"
              disabled={
                add.isPending ||
                !memberDraft.subject ||
                !memberDraft.reason ||
                memberTenant.status !== "ACTIVE"
              }
              onClick={() => add.mutate()}
            >
              {add.isPending ? "添加中…" : "添加成员"}
            </Button>
          </div>
        </FormSheet>
      ) : null}

      {action ? (
        <ReasonActionDialog
          title={
            action.kind === "member-role"
              ? "变更成员角色"
              : action.kind.startsWith("member")
                ? "成员状态变更"
                : "租户状态变更"
          }
          description={`目标：${action.tenant.tenantKey}${"subject" in action ? ` / ${action.subject}` : ""}`}
          confirmLabel="确认执行"
          pending={runAction.isPending}
          onCancel={() => setAction(null)}
          onConfirm={(reason) => runAction.mutate(reason)}
        />
      ) : null}
    </div>
  );
}
