import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** 统一分页控件；无 total 时只显示页码和导航按钮。 */
export function Pagination({
  page,
  pageCount,
  total,
  onPageChange,
  className,
}: {
  page: number;
  pageCount: number;
  total?: number;
  onPageChange: (page: number) => void;
  className?: string;
}) {
  const navBtnClass = "h-11 px-5 md:h-8 md:px-3";
  return (
    <div
      className={cn("flex items-center justify-between text-sm text-muted-foreground", className)}
    >
      <span className="tabular-nums">{total !== undefined ? `共 ${total} 条` : ""}</span>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          className={navBtnClass}
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          上一页
        </Button>
        <span className="tabular-nums">
          {page} / {pageCount}
        </span>
        <Button
          variant="outline"
          size="sm"
          className={navBtnClass}
          disabled={page >= pageCount}
          onClick={() => onPageChange(page + 1)}
        >
          下一页
        </Button>
      </div>
    </div>
  );
}
