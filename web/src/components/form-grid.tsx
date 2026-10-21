import * as React from "react";

import { cn } from "@/lib/utils";

/** 响应式表单栅格：窄屏单列，md 起横向换行。 */
export function FormGrid({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      {...props}
      className={cn(
        "flex flex-col gap-4 md:flex-row md:flex-wrap md:items-end md:gap-3",
        className,
      )}
    />
  );
}
