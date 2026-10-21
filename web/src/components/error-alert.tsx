/* 查询错误提示，使用 alert 语义。表单提交错误留在表单附近显示。 */

import { AlertCircle } from "lucide-react";

export function ErrorAlert({ text = "加载失败,请稍后重试" }: { text?: string }) {
  return (
    <div
      role="alert"
      className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
    >
      <AlertCircle className="size-4 shrink-0" aria-hidden />
      <span>{text}</span>
    </div>
  );
}
