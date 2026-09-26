import * as React from "react";

import { cn } from "@/lib/utils";

/** 移动卡片行:标签(次) + 值(主,右对齐);财务值由调用方传 valueClassName 带 tabular-nums。 */
export interface MobileCardRow {
  label: React.ReactNode;
  value: React.ReactNode;
  valueClassName?: string;
}

/**
 * 移动数据卡(全站单源):桌面表格在窄屏(md 以下)折叠为卡片列表的统一形态。
 * headline = 首行主对象(左),headlineEnd = 首行强调值如金额/状态(右);
 * rows = 次要键值对;footer = 行内动作区。列表页双轨渲染由此唯一约定,禁各页自拼卡片骨架。
 */
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
