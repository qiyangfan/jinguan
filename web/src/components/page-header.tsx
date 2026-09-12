import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * 页面头(全站单源):标题 + 描述 + 右侧动作区。
 * 管理页/工作台页一律复用,禁再手搓 justify-between 标题行(宪法 Article 6);
 * 页面标题层级 h2 text-lg font-semibold 由此唯一约定。
 */
export function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-center justify-between gap-3", className)}>
      <div className="space-y-0.5">
        <h2 className="text-lg font-semibold">{title}</h2>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  );
}
