import type { ReactNode } from "react";

import { EmptyState } from "@/components/empty-state";
import { ErrorAlert } from "@/components/error-alert";
import { Skeleton } from "@/components/ui/skeleton";
import { errorDetail, lastAuditIdValue } from "@/lib/api";

/** 列表查询三态：加载骨架、错误告警、空态；成功时渲染列表。 */
export function ControlList<T>({
  loading,
  error,
  items,
  emptyTitle,
  emptyDescription,
  children,
}: {
  loading: boolean;
  error: unknown;
  items: T[] | undefined;
  emptyTitle: string;
  emptyDescription: string;
  children: (items: T[]) => ReactNode;
}) {
  if (loading) {
    return (
      <div className="grid gap-3">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (error) {
    return <ErrorAlert text={errorDetail(error)} />;
  }
  if (!items || items.length === 0) {
    return (
      <EmptyState>
        <p className="font-medium text-foreground">{emptyTitle}</p>
        <p>{emptyDescription}</p>
      </EmptyState>
    );
  }
  return <div className="grid gap-3 sm:grid-cols-2">{children(items)}</div>;
}

/** 变更反馈：错误用 ErrorAlert，成功用状态文本。 */
export function Feedback({ error, success }: { error: string; success: string }) {
  if (error) {
    return <ErrorAlert text={error} />;
  }
  if (success) {
    const auditId = lastAuditIdValue();
    return (
      <p role="status" className="rounded-md bg-success/15 px-3 py-2 text-sm text-success">
        {success}
        {auditId ? <span className="ml-2 text-xs opacity-80">最近审计标识：{auditId}</span> : null}
      </p>
    );
  }
  return null;
}
