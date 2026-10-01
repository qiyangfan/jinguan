import * as React from "react";

import { cn } from "@/lib/utils";

/* shadcn v4 默认配方(rounded-md + ring-[3px] + aria-invalid 态),
   与 button/textarea/select 同族;禁改回旧版 rounded-lg/ring-2(前端宪法 Article 2)。
   size="sm"(h-8)与 SelectTrigger sm 同档,供紧凑筛选行,业务禁再写 h-7/h-8 高度覆盖 */
function Input({
  className,
  type,
  size = "default",
  ...props
}: Omit<React.ComponentProps<"input">, "size"> & { size?: "default" | "sm" }) {
  return (
    <input
      type={type}
      data-slot="input"
      data-size={size}
      className={cn(
        "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-destructive/20 md:text-sm dark:bg-input-fill/30 dark:aria-invalid:ring-destructive/40 data-[size=sm]:h-8",
        className,
      )}
      {...props}
    />
  );
}

export { Input };
