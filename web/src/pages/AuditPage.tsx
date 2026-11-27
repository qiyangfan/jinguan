/* 控制面审计：按 actor/动作/结果筛选，导出脱敏 CSV（导出留痕）。 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { AUDIT_RESULT } from "@/components/control/status-meta";
import { PageHeader } from "@/components/page-header";
import { Pagination } from "@/components/pagination";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorDetail } from "@/lib/api";
import { type AuditFilter, downloadAuditCsv, fetchAudit } from "@/lib/control-api";

const PAGE_SIZE = 20;

export default function AuditPage() {
  const [filter, setFilter] = useState<AuditFilter>({});
  const [draft, setDraft] = useState({ actor: "", action: "", result: "" });
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const audits = useQuery({
    queryKey: ["control", "audit", filter, page],
    queryFn: () => fetchAudit(filter, page, PAGE_SIZE),
  });
  const exportCsv = useMutation({
    mutationFn: () => downloadAuditCsv(filter),
    onSuccess: () => {
      setError("");
      setSuccess("导出已开始（脱敏）");
    },
    onError: (err) => {
      setSuccess("");
      setError(errorDetail(err));
    },
  });

  const applyFilter = () => {
    setPage(0);
    setFilter({
      actor: draft.actor || undefined,
      action: draft.action || undefined,
      result: draft.result || undefined,
    });
  };

  const items = audits.data ?? [];
  const pageCount = page + 1 + (items.length >= PAGE_SIZE ? 1 : 0);

  return (
    <div className="space-y-4">
      <PageHeader
        title="控制面审计"
        description="写操作与敏感只读留痕;在线保留 ≥180 天，导出脱敏。"
        actions={
          <Button
            variant="outline"
            disabled={exportCsv.isPending}
            onClick={() => exportCsv.mutate()}
          >
            导出 CSV
          </Button>
        }
      />
      <Feedback error={error} success={success} />
      <div className="grid gap-2 sm:grid-cols-3">
        <div className="grid gap-1">
          <Label htmlFor="audit-actor">actor</Label>
          <Input
            id="audit-actor"
            value={draft.actor}
            onChange={(event) => setDraft({ ...draft, actor: event.target.value })}
            placeholder="subject 或 service:kongming"
          />
        </div>
        <div className="grid gap-1">
          <Label htmlFor="audit-action">action</Label>
          <Input
            id="audit-action"
            value={draft.action}
            onChange={(event) => setDraft({ ...draft, action: event.target.value })}
            placeholder="TENANT_CREATE"
          />
        </div>
        <div className="grid gap-1">
          <Label htmlFor="audit-result">result</Label>
          <Input
            id="audit-result"
            value={draft.result}
            onChange={(event) => setDraft({ ...draft, result: event.target.value })}
            placeholder="SUCCESS / DENIED / FAILED"
          />
        </div>
      </div>
      <div className="flex gap-2">
        <Button variant="secondary" onClick={applyFilter}>
          应用筛选
        </Button>
      </div>
      <ControlList
        loading={audits.isLoading}
        error={audits.error}
        items={audits.data}
        emptyTitle="没有匹配的审计"
        emptyDescription="调整筛选条件后重试。"
      >
        {(rows) =>
          rows.map((audit) => (
            <EntityCard
              key={audit.id}
              title={audit.action}
              subtitle={audit.occurredAt ?? ""}
              badge={<StatusBadge meta={AUDIT_RESULT} value={audit.result} />}
              rows={[
                { label: "actor", value: `${audit.actor}（${audit.actorKind}）` },
                {
                  label: "目标",
                  value: [audit.targetType, audit.targetId].filter(Boolean).join(" / ") || "—",
                },
                { label: "租户", value: audit.tenantKey ?? "—" },
                { label: "原因", value: audit.reason ?? "—" },
                { label: "request_id", value: audit.requestId ?? "—" },
                { label: "摘要", value: audit.summaryJson ?? "—" },
              ]}
            />
          ))
        }
      </ControlList>
      <Pagination
        page={page + 1}
        pageCount={Math.max(1, pageCount)}
        onPageChange={(next) => setPage(next - 1)}
      />
    </div>
  );
}
