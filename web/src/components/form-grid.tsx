import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * 表单字段栅格(全站单源):FormRow 的响应式容器。移动端字段单列满宽,
 * md 起横排换行、底对齐、收紧列距——唯一出处(宪法 Article 2 收编)。
 * 查询/快捷动作表单可直接以其为唯一直接子级(提交键作为行尾元素);
 * 编辑表单在其外套 form(space-y-4),保存键独立成行。
 */
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
