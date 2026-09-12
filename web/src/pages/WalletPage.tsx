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
import { QueryErrorAlert } from "@/components/query-error";
import { errorDetail } from "@/lib/api";
import { adminCredit, fetchBalance, fetchEntries } from "@/lib/wallet-api";
import { micros2yuan } from "@/lib/money";
import { useAuth } from "@/lib/auth-store";

const REASON_LABEL: Record<string, string> = {
  MANUAL_TOPUP: "手工入账",
  PAYMENT_TOPUP: "支付入账",
  USAGE: "用量扣减",
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

  const credit = useMutation({
    mutationFn: () =>
      adminCredit({
        userId: balance.data?.userId ?? 0,
        amountMicros: Math.round(parseFloat(amount) * 1_000_000),
        refId: refId || undefined,
      }),
    onSuccess: (out) => {
      setSuccess(`已入账,当前余额 ¥${micros2yuan(out.balanceMicros)}`);
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
          <CardDescription>用户 #{balance.data?.userId ?? "…"}</CardDescription>
        </CardHeader>
        <CardContent>
          {balance.isError ? (
            <QueryErrorAlert text={errorDetail(balance.error)} />
          ) : balance.isPending ? (
            <Skeleton className="h-9 w-32" />
          ) : (
            <div className="text-3xl font-semibold">
              ¥{micros2yuan(balance.data?.balanceMicros ?? 0)}
            </div>
          )}
        </CardContent>
      </Card>

      {user?.role === "ADMIN" && (
        <Card>
          <CardHeader>
            <CardTitle>手工入账(管理员兜底)</CardTitle>
            <CardDescription>与支付入账走同一账本,refId 幂等(重复提交不重复入账)</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submitCredit} className="flex flex-wrap items-end gap-3">
              <FormRow label="金额(元)" htmlFor="amount">
                <Input
                  id="amount"
                  type="number"
                  step="0.000001"
                  min="0"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="w-40"
                  required
                />
              </FormRow>
              <FormRow label="幂等 ref(留空自动)" htmlFor="ref">
                <Input
                  id="ref"
                  value={refId}
                  onChange={(e) => setRefId(e.target.value)}
                  className="w-56"
                  placeholder="manual-…"
                />
              </FormRow>
              <Button type="submit" disabled={credit.isPending || !amount || !balance.data}>
                {credit.isPending ? "提交中…" : "入账"}
              </Button>
              {error && <p className="text-sm text-destructive">{error}</p>}
              {success && <span className="text-sm text-muted-foreground">{success}</span>}
            </form>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>账本流水</CardTitle>
          <CardDescription>
            append-only 分录;amount 有符号(+入账/-扣账),幂等 ref 全局唯一
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
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>#</TableHead>
                    <TableHead>变动(元)</TableHead>
                    <TableHead>类型</TableHead>
                    <TableHead>幂等 ref</TableHead>
                    <TableHead>备注</TableHead>
                    <TableHead>时间</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>{entry.id}</TableCell>
                      <TableCell
                        className={entry.amountMicros >= 0 ? "text-success" : "text-destructive"}
                      >
                        {entry.amountMicros >= 0 ? "+" : ""}
                        {micros2yuan(entry.amountMicros)}
                      </TableCell>
                      <TableCell>{REASON_LABEL[entry.reason] ?? entry.reason}</TableCell>
                      <TableCell className="font-mono text-2xs">{entry.refId}</TableCell>
                      <TableCell>{entry.note ?? ""}</TableCell>
                      <TableCell>{new Date(entry.createdAt).toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {items.length === 0 && <p className="text-sm text-muted-foreground">暂无流水</p>}
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
    </section>
  );
}
