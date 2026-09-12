import * as React from "react";

import { FieldLabel } from "@/components/field-label";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/**
 * 表单字段行(全站单源):Label + 控件,纵向间距 gap-1.5 唯一出处(宪法 Article 6)。
 * help 传入时渲染 FieldLabel(问号 Tooltip),否则裸 Label;错误提示等附加内容放 children 尾部。
 */
export function FormRow({
  label,
  htmlFor,
  help,
  children,
  className,
}: {
  label: string;
  htmlFor?: string;
  help?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("grid gap-1.5", className)}>
      {help ? (
        <FieldLabel htmlFor={htmlFor} help={help}>
          {label}
        </FieldLabel>
      ) : (
        <Label htmlFor={htmlFor}>{label}</Label>
      )}
      {children}
    </div>
  );
}
