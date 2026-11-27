import type { ReactNode } from "react";

import { BottomSheetContent } from "@/components/bottom-sheet-dialog";
import {
  Sheet,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";

/** 控制台表单弹层：窄屏贴底、桌面居中，统一标题/描述/页脚。 */
export function FormSheet({
  title,
  description,
  children,
  footer,
  onClose,
  wide,
}: {
  title: string;
  description: string;
  children: ReactNode;
  footer: ReactNode;
  onClose: () => void;
  wide?: boolean;
}) {
  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <BottomSheetContent className={wide ? "md:max-w-2xl" : "md:max-w-lg"}>
        <SheetHeader>
          <SheetTitle>{title}</SheetTitle>
          <SheetDescription>{description}</SheetDescription>
        </SheetHeader>
        <div className="grid gap-4 md:min-w-0">{children}</div>
        <SheetFooter className="flex-col gap-2 sm:flex-row">{footer}</SheetFooter>
      </BottomSheetContent>
    </Sheet>
  );
}
