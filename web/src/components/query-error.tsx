/* 查询错误态共享组件(038⑧ 清偿:全站统一,单源实现)。
   查询失败显式渲染 role=alert(不伪装空态/不静默空白);新页面一律
   复用本组件,不得各自手写错误分支或省略错误态(web-nav spec 立约)。 */

import { AlertCircle } from "lucide-react";

export function QueryErrorAlert({ text = "加载失败,请稍后重试" }: { text?: string }) {
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
