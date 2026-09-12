import { CircleHelp } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

/**
 * 表单字段的「Label + 问号说明」。
 * 格式约束、分档规则等说明一律进 Tooltip(钉后端真实规则),
 * 禁止在 Label 后括号堆砌(全站范式,先例=RagPage 表说明)。
 */
export function FieldLabel({
  htmlFor,
  help,
  children,
}: {
  htmlFor?: string;
  help: string;
  children: string;
}) {
  return (
    <span className="flex items-center gap-1">
      <Label htmlFor={htmlFor}>{children}</Label>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-5 text-muted-foreground hover:text-foreground"
            aria-label={`${children}的说明`}
          >
            <CircleHelp className="size-3.5" aria-hidden />
          </Button>
        </TooltipTrigger>
        <TooltipContent className="max-w-72">{help}</TooltipContent>
      </Tooltip>
    </span>
  );
}
