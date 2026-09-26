import * as React from "react";

import { SheetContent } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

/** side 由本组件锁死为 "bottom",调用方不可覆写。 */
type BottomSheetContentProps = Omit<React.ComponentProps<typeof SheetContent>, "side">;

/**
 * 底部弹层(全站单源):窄屏(md 以下)贴底滑出——移动端表单/选择类交互的
 * 平台惯例形态(键盘不遮输入、拇指可达);md 起回落居中模态。
 * 基座为 ui/sheet 官方件(side="bottom"),本组件仅做响应式覆写:
 * 窄屏恒满宽(中性化调用点宽度类) + 贴底圆角与安全区底距,md+ 回官方居中
 * 模态并抹平侧滑动画(改走 dialog 同款 fade+zoom/时长),禁自造浮层。
 * 桌面宽度由调用点以 md:max-w-* 声明(组件不再设 md:max-w,避免压掉调用点);
 * 官方 SheetHeader/SheetFooter 内置 p-4 已由下方子代选择器统一清零,调用点无需自理。
 */
export function BottomSheetContent({ className, ...props }: BottomSheetContentProps) {
  return (
    <SheetContent
      side="bottom"
      {...props}
      className={cn(
        "max-h-[88dvh] overflow-y-auto",
        "[&>[data-slot=sheet-header]]:p-0 [&>[data-slot=sheet-footer]]:p-0",
        /* 窄屏:恒满宽 + 贴底圆角顶 + 安全区底距 + 表单密度 padding */
        "max-md:w-full max-md:max-w-none",
        "max-md:rounded-b-none max-md:rounded-t-2xl max-md:border-x-0 max-md:border-b-0",
        "max-md:p-4 max-md:pb-[max(1rem,env(safe-area-inset-bottom))]",
        /* md+:回官方居中模态(dialog 同款定位/形状/padding/时长;宽度归调用点 md:max-w-*) */
        "md:inset-x-auto md:bottom-auto md:top-1/2 md:left-1/2 md:w-full",
        "md:-translate-x-1/2 md:-translate-y-1/2",
        "md:rounded-lg md:border-x md:border-b md:p-6 md:duration-200",
        /* md+ 抹平侧滑(fade+zoom 变量与官方 slide 变量同族,靠响应式层序覆盖) */
        "md:data-[state=open]:fade-in-0 md:data-[state=open]:zoom-in-95 md:data-[state=open]:slide-in-from-bottom-0",
        "md:data-[state=closed]:fade-out-0 md:data-[state=closed]:zoom-out-95 md:data-[state=closed]:slide-out-to-bottom-0",
        className,
      )}
    />
  );
}
