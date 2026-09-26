import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * 列表分页条(全站单源):「共 N 条」+ 上一页/页码/下一页。
 * 新列表页一律复用,禁再手搓分页行(宪法 Article 6);页大小约定 PAGE_SIZE=20。
 * 移动端按钮加高至 44px 触控底线,桌面维持 sm 紧凑档。
 */
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
      {/* 无 total 时左侧留空占位:页码已在右侧,禁再重复一份「第 x / y」 */}
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
