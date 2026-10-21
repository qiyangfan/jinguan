import * as React from "react";

import { FieldLabel } from "@/components/field-label";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/** 统一表单行布局；传入 help 时显示说明 Tooltip。 */
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
