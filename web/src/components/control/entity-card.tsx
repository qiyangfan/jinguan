import type { ReactNode } from "react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/** 控制台实体卡片：标题/状态徽章/键值行/操作区，窄屏单列、宽屏双列。 */
export function EntityCard({
  title,
  subtitle,
  badge,
  rows,
  actions,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  badge?: ReactNode;
  rows: Array<{ label: string; value: ReactNode }>;
  actions?: ReactNode;
}) {
  return (
    <Card className="min-w-0">
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div className="min-w-0">
          <CardTitle className="truncate text-base">{title}</CardTitle>
          {subtitle ? <CardDescription className="truncate">{subtitle}</CardDescription> : null}
        </div>
        {badge}
      </CardHeader>
      <CardContent className="grid gap-2 text-sm">
        <dl className="grid gap-1">
          {rows.map((row) => (
            <div key={row.label} className="flex min-w-0 items-start justify-between gap-3">
              <dt className="shrink-0 text-muted-foreground">{row.label}</dt>
              <dd className="min-w-0 truncate text-right">{row.value}</dd>
            </div>
          ))}
        </dl>
        {actions ? <div className="flex flex-wrap gap-2 pt-1">{actions}</div> : null}
      </CardContent>
    </Card>
  );
}
