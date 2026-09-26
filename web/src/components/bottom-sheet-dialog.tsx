import * as React from "react";

import { DialogContent } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

/**
 * 底部弹层(全站单源):窄屏(md 以下)对话框贴底滑出——移动端表单/选择类交互的
 * 平台惯例形态(键盘不遮输入、拇指可达);md 起回落 ui/dialog 居中模态。
 * 复用 ui/dialog 原语仅覆写定位类,禁自造浮层;弹层内自带滚动与安全区底距。
 */
export function SheetContent({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DialogContent>) {
  return (
    <DialogContent
      {...props}
      className={cn(
        "max-h-[88dvh] overflow-y-auto",
        "max-md:top-auto max-md:bottom-0 max-md:left-0 max-md:right-0",
        "max-md:translate-x-0 max-md:translate-y-0",
        "max-md:w-full max-md:max-w-none",
        "max-md:rounded-b-none max-md:rounded-t-2xl max-md:border-x-0 max-md:border-b-0",
        "max-md:p-4 max-md:pb-[max(1rem,env(safe-area-inset-bottom))]",
        className,
      )}
    >
      {children}
    </DialogContent>
  );
}
