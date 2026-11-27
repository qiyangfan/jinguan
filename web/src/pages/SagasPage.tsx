/* 跨域流程：启动租户开通、查看步骤与 pending/blocked，关闭阻塞流程。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { ReasonActionDialog } from "@/components/control/reason-action-dialog";
import { SAGA_STATUS } from "@/components/control/status-meta";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorDetail } from "@/lib/api";
import { closeSaga, fetchSaga, fetchSagas, type SagaView, startSaga } from "@/lib/control-api";

export default function SagasPage() {
  const queryClient = useQueryClient();
  const sagas = useQuery({
    queryKey: ["control", "sagas"],
    queryFn: () => fetchSagas(),
    refetchInterval: 5000,
  });
  const [selected, setSelected] = useState<string | null>(null);
  const detail = useQuery({
    queryKey: ["control", "saga", selected],
    queryFn: () => fetchSaga(selected ?? ""),
    enabled: Boolean(selected),
  });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [startOpen, setStartOpen] = useState(false);
  const [draft, setDraft] = useState({
    tenantKey: "",
    name: "",
    planKey: "",
    reason: "",
    idempotencyKey: "",
  });
  const [closing, setClosing] = useState<SagaView | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["control", "sagas"] });
  const succeed = (message: string) => {
    setError("");
    setSuccess(message);
    void invalidate();
  };
  const fail = (err: unknown) => {
    setSuccess("");
    setError(errorDetail(err));
  };

  const start = useMutation({
    mutationFn: () =>
      startSaga({
        type: "tenant_onboard",
        idempotencyKey: draft.idempotencyKey || undefined,
        params: {
          tenantKey: draft.tenantKey,
          name: draft.name,
          planKey: draft.planKey,
        },
        reason: draft.reason,
        confirm: true,
      }),
    onSuccess: () => {
      setStartOpen(false);
      succeed("流程已启动");
    },
    onError: fail,
  });

  const close = useMutation({
    mutationFn: (reason: string) => closeSaga(closing?.id ?? "", reason),
    onSuccess: () => {
      setClosing(null);
      succeed("流程已关闭");
    },
    onError: fail,
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="跨域流程"
        description="管理步骤写管理数据，服务步骤发声明式命令;失败逆序补偿。"
        actions={<Button onClick={() => setStartOpen(true)}>开通租户</Button>}
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={sagas.isLoading}
        error={sagas.error}
        items={sagas.data}
        emptyTitle="暂无流程"
        emptyDescription="启动租户开通流程后会显示步骤进度。"
      >
        {(items) =>
          items.map((saga) => (
            <EntityCard
              key={saga.id}
              title={saga.sagaType}
              subtitle={saga.idempotencyKey}
              badge={<StatusBadge meta={SAGA_STATUS} value={saga.status} />}
              rows={[
                { label: "租户", value: saga.context?.tenantKey ?? "—" },
                { label: "原因", value: saga.reason ?? "—" },
                { label: "更新", value: saga.updatedAt ?? "—" },
                {
                  label: "阻塞原因",
                  value: saga.steps?.find((step) => step.status === "BLOCKED")?.error ?? "—",
                },
              ]}
              actions={
                <>
                  <Button size="sm" variant="outline" onClick={() => setSelected(saga.id ?? "")}>
                    步骤
                  </Button>
                  {saga.status === "BLOCKED" ? (
                    <Button size="sm" variant="outline" onClick={() => setClosing(saga)}>
                      关闭
                    </Button>
                  ) : null}
                </>
              }
            />
          ))
        }
      </ControlList>

      {startOpen ? (
        <FormSheet
          title="开通租户（tenant_onboard）"
          description="创建租户 → 绑定套餐 → 通知 kongming/muniu 执行声明式命令。"
          onClose={() => setStartOpen(false)}
          footer={
            <>
              <Button variant="outline" onClick={() => setStartOpen(false)}>
                取消
              </Button>
              <Button
                disabled={
                  start.isPending ||
                  !draft.tenantKey ||
                  !draft.name ||
                  !draft.planKey ||
                  !draft.reason
                }
                onClick={() => start.mutate()}
              >
                {start.isPending ? "启动中…" : "启动流程"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label htmlFor="saga-tenant-key">tenant_key</Label>
            <Input
              id="saga-tenant-key"
              value={draft.tenantKey}
              onChange={(event) => setDraft({ ...draft, tenantKey: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="saga-name">租户名称</Label>
            <Input
              id="saga-name"
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="saga-plan">plan_key</Label>
            <Input
              id="saga-plan"
              value={draft.planKey}
              onChange={(event) => setDraft({ ...draft, planKey: event.target.value })}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="saga-reason">原因（必填）</Label>
            <Input
              id="saga-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="saga-idempotency">幂等键（可选）</Label>
            <Input
              id="saga-idempotency"
              value={draft.idempotencyKey}
              onChange={(event) => setDraft({ ...draft, idempotencyKey: event.target.value })}
            />
          </div>
        </FormSheet>
      ) : null}

      {selected ? (
        <FormSheet
          title="流程步骤"
          description="服务命令 pending 表示等待服务领取;blocked 需人工处理。"
          wide
          onClose={() => setSelected(null)}
          footer={
            <Button variant="outline" onClick={() => setSelected(null)}>
              关闭
            </Button>
          }
        >
          {detail.isLoading ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : detail.error ? (
            <p className="text-sm text-destructive">{errorDetail(detail.error)}</p>
          ) : (
            <div className="grid gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge meta={SAGA_STATUS} value={detail.data?.status} />
                <span className="text-xs text-muted-foreground">
                  更新：{detail.data?.updatedAt ?? "—"}
                </span>
              </div>
              {(detail.data?.steps ?? []).map((step) => (
                <div key={step.seq} className="rounded-md border p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">
                      {step.seq}. {step.name}
                    </span>
                    <StatusBadge meta={SAGA_STATUS} value={step.status} />
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {step.kind} · {step.action ?? "管理步骤"} · 尝试 {step.attempts ?? 0}
                  </div>
                  {step.error ? (
                    <p className="mt-1 text-xs text-destructive">{step.error}</p>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </FormSheet>
      ) : null}

      {closing ? (
        <ReasonActionDialog
          title="关闭阻塞流程"
          description="关闭后按保留期清理;审计记录保留。"
          confirmLabel="确认关闭"
          pending={close.isPending}
          onCancel={() => setClosing(null)}
          onConfirm={(reason) => close.mutate(reason)}
        />
      ) : null}
    </div>
  );
}
