import { useState } from "react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

/** 需原因的高风险操作确认：未填写原因不可提交;提交期间保持弹层。 */
export function ReasonActionDialog({
  title,
  description,
  confirmLabel,
  pending,
  onCancel,
  onConfirm,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  pending?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();
  return (
    <AlertDialog open onOpenChange={(open) => !open && onCancel()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="control-reason">变更原因（必填）</Label>
          <Textarea
            id="control-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="说明为什么执行这次变更，将写入审计"
            maxLength={512}
          />
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onCancel}>取消</AlertDialogCancel>
          <AlertDialogAction
            disabled={!trimmed || pending}
            onClick={(event) => {
              event.preventDefault();
              if (trimmed) onConfirm(trimmed);
            }}
          >
            {pending ? "处理中…" : confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
