import * as React from "react";

import { Badge } from "@/components/ui/badge";

/** 各域状态到展示标签和样式的映射。 */
export type StatusMeta = Record<
  string,
  { label: string; variant?: React.ComponentProps<typeof Badge>["variant"] }
>;

/** 未知状态优先显示 fallbackLabel，未提供时显示原值；样式使用 fallbackVariant。 */
export function StatusBadge({
  meta,
  value,
  fallbackLabel,
  fallbackVariant = "outline",
}: {
  meta: StatusMeta;
  value: string | null | undefined;
  fallbackLabel?: string;
  fallbackVariant?: React.ComponentProps<typeof Badge>["variant"];
}) {
  const m = value ? meta[value] : undefined;
  return (
    <Badge variant={m?.variant ?? fallbackVariant}>
      {m?.label ?? fallbackLabel ?? value ?? "—"}
    </Badge>
  );
}
