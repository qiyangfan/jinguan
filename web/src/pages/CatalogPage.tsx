/* 商店目录：只引用运行时 Bot;引用失效显示、不静默隐藏。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { ReasonActionDialog } from "@/components/control/reason-action-dialog";
import { AVAILABILITY } from "@/components/control/status-meta";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
  type CatalogEntryView,
  changeCatalogEnabled,
  createCatalogEntry,
  fetchCatalog,
  fetchTenants,
  updateCatalogEntry,
} from "@/lib/control-api";

type Editing = CatalogEntryView | "new";

const EMPTY = {
  entryKey: "",
  botKey: "",
  botTenantKey: "",
  title: "",
  summary: "",
  visibility: "PUBLIC",
  tenantId: "",
  entitlementKey: "",
  featured: false,
  reason: "",
};

export default function CatalogPage() {
  const queryClient = useQueryClient();
  const catalog = useQuery({ queryKey: ["control", "catalog"], queryFn: fetchCatalog });
  const tenants = useQuery({ queryKey: ["control", "tenants"], queryFn: fetchTenants });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState(EMPTY);
  const [toggle, setToggle] = useState<CatalogEntryView | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["control", "catalog"] });
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
    setDraft(EMPTY);
    setEditing("new");
  };
  const openEdit = (entry: CatalogEntryView) => {
    setDraft({
      entryKey: entry.entryKey ?? "",
      botKey: entry.botKey ?? "",
      botTenantKey: entry.botTenantKey ?? "",
      title: entry.title ?? "",
      summary: entry.summary ?? "",
      visibility: entry.visibility ?? "PUBLIC",
      tenantId: entry.tenantKey ?? "",
      entitlementKey: entry.entitlementKey ?? "",
      featured: Boolean(entry.featured),
      reason: "",
    });
    setEditing(entry);
  };

  const save = useMutation({
    mutationFn: () => {
      const base = {
        title: draft.title,
        summary: draft.summary || undefined,
        visibility: draft.visibility,
        tenantId: draft.visibility === "TENANT" ? draft.tenantId : undefined,
        entitlementKey: draft.entitlementKey || undefined,
        featured: draft.featured,
        reason: draft.reason,
      };
      return editing === "new"
        ? createCatalogEntry({
            ...base,
            entryKey: draft.entryKey,
            botKey: draft.botKey,
            botTenantKey: draft.botTenantKey || undefined,
          })
        : updateCatalogEntry((editing as CatalogEntryView).id ?? "", base);
    },
    onSuccess: () => {
      setEditing(null);
      succeed("目录条目已保存");
    },
    onError: fail,
  });

  const toggleEnabled = useMutation({
    mutationFn: (reason: string) =>
      changeCatalogEnabled(toggle?.id ?? "", !toggle?.enabled, reason),
    onSuccess: () => {
      setToggle(null);
      succeed("目录状态已更新");
    },
    onError: fail,
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="商店目录"
        description="只登记 Bot 引用与运营字段;可用性来自 kongming 只读投影。"
        actions={<Button onClick={openCreate}>新建条目</Button>}
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={catalog.isLoading}
        error={catalog.error}
        items={catalog.data}
        emptyTitle="目录为空"
        emptyDescription="上架第一个商品条目。"
      >
        {(items) =>
          items.map((entry) => (
            <EntityCard
              key={entry.id}
              title={entry.title}
              subtitle={`${entry.entryKey} · ${entry.botKey}`}
              badge={<StatusBadge meta={AVAILABILITY} value={entry.availability} />}
              rows={[
                {
                  label: "可见性",
                  value: entry.visibility === "TENANT" ? `租户 ${entry.tenantKey}` : "公开",
                },
                { label: "权益键", value: entry.entitlementKey ?? "—" },
                { label: "上架", value: entry.enabled ? "是" : "否" },
                { label: "推荐", value: entry.featured ? "是" : "否" },
              ]}
              actions={
                <>
                  <Button size="sm" variant="outline" onClick={() => openEdit(entry)}>
                    编辑
                  </Button>
                  <Button
                    size="sm"
                    variant={entry.enabled ? "outline" : "secondary"}
                    onClick={() => setToggle(entry)}
                  >
                    {entry.enabled ? "下架" : "上架"}
                  </Button>
                </>
              }
            />
          ))
        }
      </ControlList>

      {editing ? (
        <FormSheet
          title={editing === "new" ? "新建目录条目" : "编辑目录条目"}
          description="不复制 Bot 定义;引用失效会显示为不可用。"
          wide
          onClose={() => setEditing(null)}
          footer={
            <>
              <Button variant="outline" onClick={() => setEditing(null)}>
                取消
              </Button>
              <Button
                disabled={save.isPending || !draft.title || !draft.reason}
                onClick={() => save.mutate()}
              >
                {save.isPending ? "保存中…" : "保存"}
              </Button>
            </>
          }
        >
          {editing === "new" ? (
            <>
              <div className="grid gap-2">
                <Label htmlFor="catalog-key">entry_key</Label>
                <Input
                  id="catalog-key"
                  value={draft.entryKey}
                  onChange={(event) => setDraft({ ...draft, entryKey: event.target.value })}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="catalog-bot">botKey</Label>
                <Input
                  id="catalog-bot"
                  value={draft.botKey}
                  onChange={(event) => setDraft({ ...draft, botKey: event.target.value })}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="catalog-bot-tenant">bot 所属租户（可选）</Label>
                <Input
                  id="catalog-bot-tenant"
                  value={draft.botTenantKey}
                  onChange={(event) => setDraft({ ...draft, botTenantKey: event.target.value })}
                />
              </div>
            </>
          ) : null}
          <div className="grid gap-2">
            <Label htmlFor="catalog-title">标题</Label>
            <Input
              id="catalog-title"
              value={draft.title}
              onChange={(event) => setDraft({ ...draft, title: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="catalog-summary">摘要</Label>
            <Textarea
              id="catalog-summary"
              value={draft.summary}
              onChange={(event) => setDraft({ ...draft, summary: event.target.value })}
              maxLength={512}
            />
          </div>
          <div className="grid gap-2">
            <Label>可见性</Label>
            <Select
              value={draft.visibility}
              onValueChange={(value) => setDraft({ ...draft, visibility: value })}
            >
              <SelectTrigger aria-label="可见性">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PUBLIC">PUBLIC</SelectItem>
                <SelectItem value="TENANT">TENANT</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {draft.visibility === "TENANT" ? (
            <div className="grid gap-2">
              <Label>可见租户</Label>
              <Select
                value={draft.tenantId}
                onValueChange={(value) => setDraft({ ...draft, tenantId: value })}
              >
                <SelectTrigger aria-label="可见租户">
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
            <Label htmlFor="catalog-entitlement">权益键（可选）</Label>
            <Input
              id="catalog-entitlement"
              value={draft.entitlementKey}
              onChange={(event) => setDraft({ ...draft, entitlementKey: event.target.value })}
            />
          </div>
          <label htmlFor="catalog-featured" className="flex min-h-11 items-center gap-2 text-sm">
            <Checkbox
              id="catalog-featured"
              checked={draft.featured}
              onCheckedChange={(checked) => setDraft({ ...draft, featured: checked === true })}
            />
            运营推荐
          </label>
          <div className="grid gap-2">
            <Label htmlFor="catalog-reason">原因（必填）</Label>
            <Input
              id="catalog-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
        </FormSheet>
      ) : null}

      {toggle ? (
        <ReasonActionDialog
          title={toggle.enabled ? "下架条目" : "上架条目"}
          description={`条目 ${toggle.entryKey} 的可见性变化不会改变运行权限。`}
          confirmLabel={toggle.enabled ? "确认下架" : "确认上架"}
          pending={toggleEnabled.isPending}
          onCancel={() => setToggle(null)}
          onConfirm={(reason) => toggleEnabled.mutate(reason)}
        />
      ) : null}
    </div>
  );
}
