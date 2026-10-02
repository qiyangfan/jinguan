/* 钱包页:余额卡 + 账本流水(react-query 服务端状态);管理员附手工入账兜底表单。 */

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/page-header";
import { Pagination } from "@/components/pagination";
import { FormRow } from "@/components/form-row";
import { FormGrid } from "@/components/form-grid";
import { QueryErrorAlert } from "@/components/query-error";
import { StatusBadge, type StatusMeta } from "@/components/status-badge";
import { MobileCard } from "@/components/mobile-card";
import { errorDetail } from "@/lib/api";
import { adminCredit, fetchBalance, fetchEntries } from "@/lib/wallet-api";
import { fetchUsage } from "@/lib/usage-api";
import { credits2yuan, yuan2credits } from "@/lib/money";
import { formatDateTime, formatNumber } from "@/lib/datetime";
import { useAuth } from "@/lib/auth-store";
import { cn } from "@/lib/utils";

const REASON_LABEL: Record<string, string> = {
  MANUAL_TOPUP: "手工入账",
  PAYMENT_TOPUP: "支付入账",
  MANUAL_ADJUST: "管理员调账",
  USAGE: "用量扣减",
};

/* 用量明细处理结果徽章:accepted/replayed 已结算;pending 待结算;skipped_* 为该行未扣费的解释。 */
const OUTCOME_META: StatusMeta = {
  accepted: { label: "已扣费", variant: "secondary" },
  replayed: { label: "重投", variant: "outline" },
  pending: { label: "待结算", variant: "outline" },
  skipped_unbound: { label: "未绑定", variant: "outline" },
  skipped_free: { label: "零成本", variant: "outline" },
  skipped_insufficient: { label: "余额不足", variant: "destructive" },
};

/* 分页响应为裸数组(无 total/页数):当前页满一页则视为可能还有下一页,页数按此估算。 */
const PAGE_SIZE = 50;

export default function WalletPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [amount, setAmount] = useState("");
  const [refId, setRefId] = useState("");
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");

  const balance = useQuery({ queryKey: ["wallet", "balance"], queryFn: fetchBalance });
  const entries = useQuery({
    queryKey: ["wallet", "entries", page],
    queryFn: () => fetchEntries(page, PAGE_SIZE),
  });
  const [usagePage, setUsagePage] = useState(0);
  const usage = useQuery({
    queryKey: ["wallet", "usage", usagePage],
    queryFn: () => fetchUsage(usagePage, PAGE_SIZE),
  });
  const usageItems = usage.data ?? [];
  const usagePageCount = usagePage + 1 + (usageItems.length >= PAGE_SIZE ? 1 : 0);

  const credit = useMutation({
    mutationFn: () =>
      adminCredit({
        userId: Number(balance.data?.userId ?? 0),
        amountCredits: yuan2credits(amount),
        refId: refId || undefined,
      }),
    onSuccess: (out) => {
      setSuccess(`已入账,当前余额 ¥${credits2yuan(out.balanceCredits)}`);
      setError("");
      setAmount("");
      setRefId("");
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    },
    onError: (err) => {
      setSuccess("");
      setError(errorDetail(err));
    },
  });

  function submitCredit(e: FormEvent) {
    e.preventDefault();
    setSuccess("");
    setError("");
    const yuan = parseFloat(amount);
    if (!Number.isFinite(yuan) || yuan <= 0) return;
    credit.mutate();
  }

  const items = entries.data ?? [];
  /* 内页 page 为 0-based,Pagination 为 1-based:此处 +1/-1 换算 */
  const pageCount = page + 1 + (items.length >= PAGE_SIZE ? 1 : 0);

  return (
    <section className="space-y-6">
      <PageHeader title="钱包" />

      <Card>
        <CardHeader>
          <CardTitle>当前余额</CardTitle>
          <CardDescription>用户 ID {balance.data?.userId ?? "…"}</CardDescription>
        </CardHeader>
        <CardContent>
          {balance.isError ? (
            <QueryErrorAlert text={errorDetail(balance.error)} />
          ) : balance.isPending ? (
            <Skeleton className="h-9 w-32" />
          ) : (
            /* 极端长金额(十亿级 + 9 位小数)窄屏降一档字号并允许断行,不顶破容器 */
            <div className="text-2xl font-semibold break-all tabular-nums md:text-3xl">
              ¥{credits2yuan(balance.data?.balanceCredits ?? 0)}
            </div>
          )}
        </CardContent>
      </Card>

      {user?.role === "ADMIN" && (
        <Card>
          <CardHeader>
            <CardTitle>手工入账(管理员兜底)</CardTitle>
            <CardDescription>账本只增不改;重复提交不会重复入账</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submitCredit}>
              <FormGrid>
                <FormRow label="金额(元)" htmlFor="amount" className="md:w-40">
                  <Input
                    id="amount"
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    className="w-full"
                    required
                  />
                </FormRow>
                <FormRow label="参考号(可选,留空自动生成)" htmlFor="ref" className="md:w-56">
                  <Input
                    id="ref"
                    value={refId}
                    onChange={(e) => setRefId(e.target.value)}
                    className="w-full"
                    placeholder="选填,如手动补录的备注"
                  />
                </FormRow>
                <Button
                  type="submit"
                  className="h-11 w-full md:h-9 md:w-auto"
                  disabled={credit.isPending || !amount || !balance.data}
                >
                  {credit.isPending ? "提交中…" : "入账"}
                </Button>
                {error && <p className="text-sm text-destructive">{error}</p>}
                {success && <span className="text-sm text-muted-foreground">{success}</span>}
              </FormGrid>
            </form>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>账本流水</CardTitle>
          <CardDescription>
            金额带正负:+ 为入账、- 为扣费;账本只增不改,重复提交不会重复入账
          </CardDescription>
        </CardHeader>
        <CardContent>
          {entries.isError ? (
            <QueryErrorAlert text={errorDetail(entries.error)} />
          ) : entries.isPending ? (
            <div className="space-y-2">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          ) : (
            <>
              {/* 移动端(md 以下):卡片列表,金额为首要信息 */}
              <div className="space-y-2 md:hidden">
                {items.map((entry) => (
                  <MobileCard
                    key={entry.id}
                    headline={REASON_LABEL[entry.reason] ?? entry.reason}
                    headlineClassName="truncate"
                    headlineEnd={
                      <span
                        className={cn(
                          "font-medium tabular-nums",
                          entry.amountCredits.startsWith("-") ? "text-destructive" : "text-success",
                        )}
                      >
                        {entry.amountCredits.startsWith("-") ? "" : "+"}
                        {credits2yuan(entry.amountCredits)}
                      </span>
                    }
                    rows={[
                      {
                        label: "流水号",
                        value: <span title={String(entry.id)}>{entry.id}</span>,
                        valueClassName: "font-mono text-2xs",
                      },
                      {
                        label: "参考号",
                        value: <span title={entry.refId}>{entry.refId}</span>,
                        valueClassName: "font-mono text-2xs",
                      },
                      {
                        label: "备注",
                        /* 长备注折行全显(触屏无 hover tooltip,截断即不可达) */
                        value: entry.note ?? "—",
                        valueClassName: "whitespace-normal break-words",
                      },
                      { label: "时间", value: formatDateTime(entry.createdAt) },
                    ]}
                  />
                ))}
                {items.length === 0 && <p className="text-sm text-muted-foreground">暂无流水</p>}
              </div>

              {/* 桌面端(md 及以上):表格 */}
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>流水号</TableHead>
                      <TableHead className="text-right">变动(元)</TableHead>
                      <TableHead>类型</TableHead>
                      <TableHead>参考号</TableHead>
                      <TableHead>备注</TableHead>
                      <TableHead>时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((entry) => (
                      <TableRow key={entry.id}>
                        <TableCell>
                          {/* 流水号为内部主键,对账时有用;截断展示、悬停看全量 */}
                          <span className="block max-w-28 truncate" title={String(entry.id)}>
                            {entry.id}
                          </span>
                        </TableCell>
                        <TableCell
                          className={cn(
                            "text-right tabular-nums",
                            entry.amountCredits.startsWith("-")
                              ? "text-destructive"
                              : "text-success",
                          )}
                        >
                          {entry.amountCredits.startsWith("-") ? "" : "+"}
                          {credits2yuan(entry.amountCredits)}
                        </TableCell>
                        <TableCell>{REASON_LABEL[entry.reason] ?? entry.reason}</TableCell>
                        <TableCell>
                          <span
                            className="block max-w-40 truncate font-mono text-2xs"
                            title={entry.refId}
                          >
                            {entry.refId}
                          </span>
                        </TableCell>
                        <TableCell>{entry.note ?? ""}</TableCell>
                        <TableCell>{formatDateTime(entry.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                {items.length === 0 && <p className="text-sm text-muted-foreground">暂无流水</p>}
              </div>
              <Pagination
                className="mt-3"
                page={page + 1}
                pageCount={pageCount}
                onPageChange={(p) => setPage(p - 1)}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>用量明细</CardTitle>
          <CardDescription>
            每次调用的模型、tokens 与费用明细,以及扣费结果;待结算与余额不足的行,充值后由系统自动补扣
          </CardDescription>
        </CardHeader>
        <CardContent>
          {usage.isError ? (
            <QueryErrorAlert text={errorDetail(usage.error)} />
          ) : usage.isPending ? (
            <div className="space-y-2">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          ) : usageItems.length === 0 ? (
            <p className="text-sm text-muted-foreground">暂无用量</p>
          ) : (
            <>
              {/* 移动端(md 以下):卡片列表,费用为首要信息 */}
              <div className="space-y-2 md:hidden">
                {usageItems.map((u) => (
                  <MobileCard
                    key={u.usageId}
                    headline={
                      /* 长模型名截断 + title(触屏长按可见全量) */
                      <span title={u.modelAlias ?? u.upstreamModel ?? "-"}>
                        {u.modelAlias ?? u.upstreamModel ?? "-"}
                      </span>
                    }
                    headlineClassName="truncate font-mono"
                    headlineEnd={
                      <span className="font-medium tabular-nums">
                        ¥{credits2yuan(u.costCredits)}
                      </span>
                    }
                    rows={[
                      {
                        label: "Tokens(入/出)",
                        value: `${formatNumber(u.promptTokens)} / ${formatNumber(u.completionTokens)}`,
                        valueClassName: "tabular-nums",
                      },
                      {
                        label: "结果",
                        value: <StatusBadge meta={OUTCOME_META} value={u.finalOutcome} />,
                      },
                      { label: "时间", value: formatDateTime(u.lastAttemptAt) },
                    ]}
                  />
                ))}
              </div>

              {/* 桌面端(md 及以上):表格 */}
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>模型</TableHead>
                      <TableHead>Tokens(输入/输出)</TableHead>
                      <TableHead className="text-right">费用(元)</TableHead>
                      <TableHead>结果</TableHead>
                      <TableHead>时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {usageItems.map((u) => (
                      <TableRow key={u.usageId}>
                        <TableCell className="font-mono text-2xs">
                          {u.modelAlias ?? u.upstreamModel ?? "-"}
                        </TableCell>
                        <TableCell className="tabular-nums">
                          {formatNumber(u.promptTokens)} / {formatNumber(u.completionTokens)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          ¥{credits2yuan(u.costCredits)}
                        </TableCell>
                        <TableCell>
                          <StatusBadge meta={OUTCOME_META} value={u.finalOutcome} />
                        </TableCell>
                        <TableCell>{formatDateTime(u.lastAttemptAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                className="mt-3"
                page={usagePage + 1}
                pageCount={usagePageCount}
                onPageChange={(p) => setUsagePage(p - 1)}
              />
            </>
          )}
        </CardContent>
      </Card>
    </section>
  );
}
