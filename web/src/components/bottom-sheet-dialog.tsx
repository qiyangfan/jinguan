import * as React from "react";

import { SheetContent } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";

/** 固定使用底部 Sheet，不允许调用方更改方向。 */
type BottomSheetContentProps = Omit<React.ComponentProps<typeof SheetContent>, "side">;

/** 基于官方 Sheet：窄屏贴底，大屏居中；桌面宽度由调用方设置。 */
export function BottomSheetContent({ className, ...props }: BottomSheetContentProps) {
  return (
    <SheetContent
      side="bottom"
      {...props}
      className={cn(
        "max-h-[88dvh] overflow-y-auto",
        "[&>[data-slot=sheet-header]]:p-0 [&>[data-slot=sheet-footer]]:p-0",
        /* 窄屏贴底，留出安全区。 */
        "max-md:w-full max-md:max-w-none",
        "max-md:rounded-b-none max-md:rounded-t-2xl max-md:border-x-0 max-md:border-b-0",
        "max-md:p-4 max-md:pb-[max(1rem,env(safe-area-inset-bottom))]",
        /* 大屏居中；宽度由调用方设置。 */
        "md:inset-x-auto md:bottom-auto md:top-1/2 md:left-1/2 md:w-full",
        "md:-translate-x-1/2 md:-translate-y-1/2",
        "md:rounded-lg md:border-x md:border-b md:p-6 md:duration-200",
        /* 大屏改用与 Dialog 一致的淡入缩放动画。 */
        "md:data-[state=open]:fade-in-0 md:data-[state=open]:zoom-in-95 md:data-[state=open]:slide-in-from-bottom-0",
        "md:data-[state=closed]:fade-out-0 md:data-[state=closed]:zoom-out-95 md:data-[state=closed]:slide-out-to-bottom-0",
        className,
      )}
    />
  );
}
