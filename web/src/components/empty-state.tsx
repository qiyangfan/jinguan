import * as React from "react";
import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

/** 空态组件，可用于表格单元格或独立容器。 */
export function EmptyState({
  icon: Icon,
  children,
  className,
}: {
  icon?: LucideIcon;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-2 py-12 text-sm text-muted-foreground",
        className,
      )}
    >
      {Icon ? <Icon className="size-5" aria-hidden="true" /> : null}
      <div>{children}</div>
    </div>
  );
}
