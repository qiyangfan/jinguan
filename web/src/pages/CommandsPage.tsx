/* 跨服务命令：提交有限 action（按 X-01 schema 校验），查看 pending/结果/死信。 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { COMMAND_STATUS } from "@/components/control/status-meta";
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
import { fetchCommands, fetchServices, submitCommand } from "@/lib/control-api";

const ACTIONS = [
  "reindex",
  "cache_purge",
  "task_cancel",
  "tenant_provision",
  "tenant_deprovision",
  "plan_apply",
  "plan_revert",
  "catalog_sync",
];

export default function CommandsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const commands = useQuery({
    queryKey: ["control", "commands", status],
    queryFn: () => fetchCommands(undefined, status || undefined),
  });
  const services = useQuery({ queryKey: ["control", "services"], queryFn: fetchServices });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [submitOpen, setSubmitOpen] = useState(false);
  const [draft, setDraft] = useState({
    serviceId: "kongming",
    action: "reindex",
    params: "{}",
    reason: "",
    idempotencyKey: "",
  });
  const [paramsError, setParamsError] = useState("");

  const submit = useMutation({
    mutationFn: () => {
      let params: Record<string, unknown>;
      try {
        const parsed: unknown = JSON.parse(draft.params || "{}");
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
          throw new Error("params 必须是 JSON 对象");
        }
        params = parsed as Record<string, unknown>;
      } catch (err) {
        setParamsError(err instanceof Error ? err.message : "params 不是合法 JSON");
        throw err;
      }
      setParamsError("");
      return submitCommand({
        serviceId: draft.serviceId,
        action: draft.action,
        actionSchemaVersion: 1,
        params,
        reason: draft.reason,
        idempotencyKey: draft.idempotencyKey || undefined,
      });
    },
    onSuccess: () => {
      setSubmitOpen(false);
      setError("");
      setSuccess("命令已提交");
      void queryClient.invalidateQueries({ queryKey: ["control", "commands"] });
    },
    onError: (err) => {
      setSuccess("");
      setError(errorDetail(err));
    },
  });

  const serviceOptions =
    (services.data ?? []).length > 0
      ? (services.data ?? []).map((service) => service.serviceId ?? "")
      : ["kongming", "muniu"];

  return (
    <div className="space-y-4">
      <PageHeader
        title="运行时命令"
        description="服务恢复后 claim/ack;本地运行命令不经此队列。"
        actions={<Button onClick={() => setSubmitOpen(true)}>提交命令</Button>}
      />
      <div className="flex flex-wrap items-center gap-2">
        <Label htmlFor="command-status" className="text-sm text-muted-foreground">
          状态
        </Label>
        <Select
          value={status || "ALL"}
          onValueChange={(value) => setStatus(value === "ALL" ? "" : value)}
        >
          <SelectTrigger id="command-status" className="w-40" aria-label="按状态筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">全部</SelectItem>
            {Object.keys(COMMAND_STATUS).map((key) => (
              <SelectItem key={key} value={key}>
                {COMMAND_STATUS[key]?.label ?? key}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Feedback error={error} success={success} />
      <ControlList
        loading={commands.isLoading}
        error={commands.error}
        items={commands.data}
        emptyTitle="暂无命令"
        emptyDescription="提交 reindex/清缓存/取消任务等命令。"
      >
        {(items) =>
          items.map((command) => (
            <EntityCard
              key={command.id}
              title={command.action}
              subtitle={`${command.serviceId} · ${command.actionSchemaVersion}`}
              badge={<StatusBadge meta={COMMAND_STATUS} value={command.status} />}
              rows={[
                { label: "尝试", value: `${command.attempts ?? 0}/${command.maxAttempts ?? 1}` },
                { label: "原因", value: command.reason ?? "—" },
                { label: "领取者", value: command.claimedBy ?? "—" },
                { label: "错误", value: command.error ?? "—" },
                { label: "提交", value: command.createdAt ?? "—" },
              ]}
              actions={
                <span className="text-xs text-muted-foreground">
                  参数：{JSON.stringify(command.params ?? {})}
                </span>
              }
            />
          ))
        }
      </ControlList>

      {submitOpen ? (
        <FormSheet
          title="提交跨服务命令"
          description="action/params 按 X-01 schema 校验，未知动作或非法参数不会落库。"
          wide
          onClose={() => setSubmitOpen(false)}
          footer={
            <>
              <Button variant="outline" onClick={() => setSubmitOpen(false)}>
                取消
              </Button>
              <Button disabled={submit.isPending || !draft.reason} onClick={() => submit.mutate()}>
                {submit.isPending ? "提交中…" : "提交"}
              </Button>
            </>
          }
        >
          <div className="grid gap-2">
            <Label>目标服务</Label>
            <Select
              value={draft.serviceId}
              onValueChange={(value) => setDraft({ ...draft, serviceId: value })}
            >
              <SelectTrigger aria-label="目标服务">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {serviceOptions.map((serviceId) => (
                  <SelectItem key={serviceId} value={serviceId}>
                    {serviceId}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label>action（schema v1）</Label>
            <Select
              value={draft.action}
              onValueChange={(value) => setDraft({ ...draft, action: value })}
            >
              <SelectTrigger aria-label="命令动作">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ACTIONS.map((action) => (
                  <SelectItem key={action} value={action}>
                    {action}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="command-params">params（JSON 对象）</Label>
            <Textarea
              id="command-params"
              value={draft.params}
              onChange={(event) => setDraft({ ...draft, params: event.target.value })}
              aria-invalid={Boolean(paramsError)}
            />
            {paramsError ? <p className="text-sm text-destructive">{paramsError}</p> : null}
          </div>
          <div className="grid gap-2">
            <Label htmlFor="command-reason">原因（必填）</Label>
            <Input
              id="command-reason"
              value={draft.reason}
              onChange={(event) => setDraft({ ...draft, reason: event.target.value })}
              maxLength={512}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="command-idempotency">幂等键（可选）</Label>
            <Input
              id="command-idempotency"
              value={draft.idempotencyKey}
              onChange={(event) => setDraft({ ...draft, idempotencyKey: event.target.value })}
              placeholder="重复提交同一键返回既有命令"
            />
          </div>
        </FormSheet>
      ) : null}
    </div>
  );
}
