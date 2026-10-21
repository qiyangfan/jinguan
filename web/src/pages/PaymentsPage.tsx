/* 演示支付与订单查询；金额以元录入并换算为 credit 提交。 */

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { PageHeader } from "@/components/page-header";
import { Pagination } from "@/components/pagination";
import { FormRow } from "@/components/form-row";
import { FormGrid } from "@/components/form-grid";
import { ErrorAlert } from "@/components/error-alert";
import { EmptyState } from "@/components/empty-state";
import { StatusBadge, type StatusMeta } from "@/components/status-badge";
import { MobileCard } from "@/components/mobile-card";
import { errorDetail } from "@/lib/api";
import { createOrder, listOrders, mockPay } from "@/lib/payment-api";
import { credits2yuan, yuan2credits } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";

/* 未知状态由 StatusBadge 显示原值。 */
const STATUS_META: StatusMeta = {
  CREATED: { label: "待支付", variant: "outline" },
  PAID: { label: "已支付", variant: "secondary" },
  EXPIRED: { label: "已过期", variant: "destructive" },
};

const PAGE_SIZE = 50;

export default function PaymentsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  // API 不返回总数；满页时按可能还有下一页处理。
  const orders = useQuery({
    queryKey: ["payments", "orders", page],
    queryFn: () => listOrders(page, PAGE_SIZE),
  });
  const pageCount = page + 1 + ((orders.data?.length ?? 0) >= PAGE_SIZE ? 1 : 0);

  const pay = useMutation({
    mutationFn: (orderId: string) => mockPay(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["payments", "orders"] });
    },
  });

  function handleCreated() {
    // 新订单排在第一页。
    setPage(0);
    queryClient.invalidateQueries({ queryKey: ["payments", "orders"] });
  }

  return (
    <section className="space-y-6">
      <PageHeader title="支付" description="当前为演示渠道,不产生真实扣款" />

      <Card>
        <CardHeader>
          <CardTitle>创建支付单</CardTitle>
          <CardDescription>
            下单后通过演示收银台完成支付;订单创建后 60 分钟内未支付将自动关闭
          </CardDescription>
        </CardHeader>
        <CardContent>
          <AmountForm onCreated={handleCreated} />
          {pay.error && <p className="mt-2 text-sm text-destructive">{errorDetail(pay.error)}</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>我的订单</CardTitle>
          <CardDescription>订单创建后 60 分钟内未支付将自动关闭;支付成功即时到账</CardDescription>
        </CardHeader>
        <CardContent>
          {orders.isError ? (
            <ErrorAlert text={errorDetail(orders.error)} />
          ) : orders.isPending ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : orders.data && orders.data.length > 0 ? (
            <>
              {/* 移动端列表 */}
              <div className="space-y-2 md:hidden">
                {orders.data.map((o) => (
                  <MobileCard
                    key={o.orderId}
                    headline={
                      <span className="font-mono text-2xs" title={o.orderId}>
                        {o.orderId}
                      </span>
                    }
                    headlineClassName="truncate"
                    headlineEnd={
                      <span className="font-medium tabular-nums">
                        ¥{credits2yuan(o.amountCredits)}
                      </span>
                    }
                    rows={[
                      {
                        label: "状态",
                        value: <StatusBadge meta={STATUS_META} value={o.status} />,
                      },
                      { label: "创建时间", value: formatDateTime(o.createdAt) },
                      {
                        /* 未支付订单显示截止时间，已支付订单显示支付时间。 */
                        label: o.status === "PAID" ? "支付时间" : "关单截止",
                        value: formatDateTime(o.status === "PAID" ? o.paidAt : o.expiresAt),
                      },
                    ]}
                    footer={
                      o.status === "CREATED" ? (
                        <Button
                          className="h-11 flex-1"
                          disabled={pay.isPending}
                          onClick={() => pay.mutate(o.orderId)}
                        >
                          {pay.isPending ? "…" : "模拟支付"}
                        </Button>
                      ) : undefined
                    }
                  />
                ))}
              </div>

              {/* 桌面表格 */}
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>订单号</TableHead>
                      <TableHead className="text-right">金额(元)</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>创建时间</TableHead>
                      <TableHead>过期 / 支付时间</TableHead>
                      <TableHead>操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {orders.data.map((o) => (
                      <TableRow key={o.orderId}>
                        <TableCell>
                          {/* 截断单号，title 保留完整值。 */}
                          <span
                            className="block max-w-40 truncate font-mono text-2xs"
                            title={o.orderId}
                          >
                            {o.orderId}
                          </span>
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          ¥{credits2yuan(o.amountCredits)}
                        </TableCell>
                        <TableCell>
                          <StatusBadge meta={STATUS_META} value={o.status} />
                        </TableCell>
                        <TableCell>{formatDateTime(o.createdAt)}</TableCell>
                        <TableCell>
                          {/* 未支付订单显示截止时间，已支付订单显示支付时间。 */}
                          {formatDateTime(o.status === "PAID" ? o.paidAt : o.expiresAt)}
                        </TableCell>
                        <TableCell className="space-x-2">
                          {o.status === "CREATED" && (
                            <Button
                              size="sm"
                              disabled={pay.isPending}
                              onClick={() => pay.mutate(o.orderId)}
                            >
                              {pay.isPending ? "…" : "模拟支付"}
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                className="mt-3"
                page={page + 1}
                pageCount={pageCount}
                onPageChange={(p) => setPage(p - 1)}
              />
            </>
          ) : (
            <EmptyState>暂无订单</EmptyState>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

/* 下单成功后清空金额；失败时保留输入，便于修改重试。 */
function AmountForm({ onCreated }: { onCreated: () => void }) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const create = useMutation({
    mutationFn: (amountCredits: number) => createOrder("mock", amountCredits, "CNY"),
    onSuccess: () => {
      setAmount("");
      setError("");
      onCreated();
    },
    onError: (err) => setError(errorDetail(err)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const yuan = parseFloat(amount);
    if (!Number.isFinite(yuan) || yuan <= 0) return;
    create.mutate(yuan2credits(amount));
  }

  return (
    <form onSubmit={submit}>
      <FormGrid>
        <FormRow label="金额(元)" htmlFor="amount" className="md:w-48">
          <Input
            id="amount"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full"
            placeholder="0.01"
          />
        </FormRow>
        <Button
          type="submit"
          className="h-11 w-full md:h-9 md:w-auto"
          disabled={create.isPending || !amount}
        >
          {create.isPending ? "下单中…" : "创建支付单"}
        </Button>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </FormGrid>
    </form>
  );
}
