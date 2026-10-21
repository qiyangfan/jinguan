import * as React from "react";

import { cn } from "@/lib/utils";

/** 移动卡片中的标签和值。 */
export interface MobileCardRow {
  label: React.ReactNode;
  value: React.ReactNode;
  valueClassName?: string;
}

/** 桌面表格在窄屏下的卡片呈现，支持标题、强调值、详情行和操作区。 */
export function MobileCard({
  headline,
  headlineClassName,
  headlineEnd,
  rows,
  footer,
  className,
}: {
  headline: React.ReactNode;
  headlineClassName?: string;
  headlineEnd?: React.ReactNode;
  rows?: MobileCardRow[];
  footer?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("rounded-lg border bg-card p-3", className)}>
      <div className="flex items-baseline justify-between gap-3">
        <div className={cn("min-w-0", headlineClassName)}>{headline}</div>
        {headlineEnd !== undefined && headlineEnd !== null ? (
          <div className="shrink-0 text-right">{headlineEnd}</div>
        ) : null}
      </div>
      {rows && rows.length > 0 ? (
        <dl className="mt-2 space-y-1.5">
          {rows.map((row, i) => (
            <div key={i} className="flex items-baseline justify-between gap-3 text-sm">
              <dt className="shrink-0 text-muted-foreground">{row.label}</dt>
              <dd className={cn("min-w-0 truncate text-right", row.valueClassName)}>{row.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {footer ? <div className="mt-2.5 flex items-center gap-2">{footer}</div> : null}
    </div>
  );
}
