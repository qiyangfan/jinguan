/* 服务视图：注册/健康只读 + 服务域配置只读投影;不提供写入口。 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ControlList, Feedback } from "@/components/control/control-list";
import { EntityCard } from "@/components/control/entity-card";
import { FormSheet } from "@/components/control/form-sheet";
import { HEALTH_STATUS, PROJECTION_STATE } from "@/components/control/status-meta";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { errorDetail } from "@/lib/api";
import {
  downloadProjection,
  fetchProjection,
  fetchServiceIdentities,
  fetchServices,
} from "@/lib/control-api";

export default function ServicesPage() {
  const services = useQuery({ queryKey: ["control", "services"], queryFn: fetchServices });
  const identities = useQuery({
    queryKey: ["control", "service-identities"],
    queryFn: fetchServiceIdentities,
  });
  const [selected, setSelected] = useState<string | null>(null);
  const projection = useQuery({
    queryKey: ["control", "projection", selected],
    queryFn: () => fetchProjection(selected ?? ""),
    enabled: Boolean(selected),
  });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const exportProjection = useMutation({
    mutationFn: () => downloadProjection(selected ?? ""),
    onSuccess: () => {
      setError("");
      setSuccess("投影已导出（脱敏）");
    },
    onError: (err) => {
      setSuccess("");
      setError(errorDetail(err));
    },
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="服务与只读视图"
        description="健康由心跳推导;服务域配置只读展示，写操作只在服务管理台。"
      />
      <Feedback error={error} success={success} />
      <ControlList
        loading={services.isLoading}
        error={services.error}
        items={services.data}
        emptyTitle="暂无服务注册"
        emptyDescription="服务以服务身份注册后会出现在这里。"
      >
        {(items) =>
          items.map((service) => (
            <EntityCard
              key={service.serviceId}
              title={service.serviceId}
              subtitle={`实例 ${service.instances?.length ?? 0} 个`}
              badge={<StatusBadge meta={HEALTH_STATUS} value={service.health} />}
              rows={[
                {
                  label: "版本",
                  value:
                    (service.instances ?? [])
                      .map((instance) => `${instance.instanceId}@${instance.version}`)
                      .join("、") || "—",
                },
                { label: "能力键", value: (service.capabilities ?? []).join("、") || "—" },
                {
                  label: "管理能力",
                  value: (service.adminCapabilities ?? []).join("、") || "—",
                },
                {
                  label: "最后心跳",
                  value: service.instances?.[0]?.lastSeenAt ?? "—",
                },
              ]}
              actions={
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setSelected(service.serviceId ?? "")}
                >
                  只读视图
                </Button>
              }
            />
          ))
        }
      </ControlList>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold text-muted-foreground">服务身份目录</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          {(identities.data ?? []).map((entry) => (
            <EntityCard
              key={entry.clientId}
              title={entry.clientId}
              subtitle={`${entry.serviceId} · ${entry.environment}`}
              badge={
                <Badge variant={entry.disabled ? "destructive" : "success"}>
                  {entry.disabled ? "已吊销" : "启用"}
                </Badge>
              }
              rows={[{ label: "scopes", value: (entry.scopes ?? []).join(", ") || "—" }]}
            />
          ))}
        </div>
      </section>

      {selected ? (
        <FormSheet
          title={`服务只读视图 · ${selected}`}
          description="数据来自服务只读 API/投影；标记“只读”，不可在此修改。"
          wide
          onClose={() => setSelected(null)}
          footer={
            <>
              <Button variant="outline" onClick={() => setSelected(null)}>
                关闭
              </Button>
              <Button
                variant="secondary"
                disabled={exportProjection.isPending || projection.data?.state === "UNKNOWN"}
                onClick={() => exportProjection.mutate()}
              >
                导出（脱敏）
              </Button>
            </>
          }
        >
          {projection.isLoading ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : projection.error ? (
            <p className="text-sm text-destructive">{errorDetail(projection.error)}</p>
          ) : (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline">只读</Badge>
                <StatusBadge meta={PROJECTION_STATE} value={projection.data?.state} />
                <span className="text-xs text-muted-foreground">
                  observed_at: {projection.data?.observedAt ?? "—"}
                </span>
                <span className="text-xs text-muted-foreground">
                  映射: {projection.data?.mappingConfigured ? "已配置" : "缺失"}
                </span>
              </div>
              <pre className="max-h-[50dvh] overflow-auto rounded-md border bg-muted/30 p-3 text-xs whitespace-pre-wrap">
                {JSON.stringify(projection.data?.sections ?? {}, null, 2)}
              </pre>
            </>
          )}
        </FormSheet>
      ) : null}
    </div>
  );
}
