/* 支付页:mock 渠道下单 → 模拟收银台支付 → 状态查询;订单簿以服务端列表为唯一事实源
   (useQuery + 变更后失效重取)。金额录入单位=元,提交换算 credits(1e-9,与 muniu 同基座)。 */

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
import { QueryErrorAlert } from "@/components/query-error";
import { StatusBadge, type StatusMeta } from "@/components/status-badge";
import { MobileCard } from "@/components/mobile-card";
import { errorDetail } from "@/lib/api";
import { createOrder, listOrders, mockPay } from "@/lib/payment-api";
import { credits2yuan, yuan2credits } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";

/* 契约 status 为 string(状态机:CREATED → PAID / EXPIRED,收敛扫描关闭超时单);
   未知值由 StatusBadge 回落 outline + 原文。 */
const STATUS_META: StatusMeta = {
  CREATED: { label: "待支付", variant: "outline" },
  PAID: { label: "已支付", variant: "secondary" },
  EXPIRED: { label: "已过期", variant: "destructive" },
};

const PAGE_SIZE = 50;

export default function PaymentsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  // 订单端点只回数组不回总数:满页 +1 估算是否还有下一页(与账本流水同法)
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
    // 订单按创建倒序,新单在第 1 页:翻页状态下下单后回首页才能看到新单
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
            <QueryErrorAlert text={errorDetail(orders.error)} />
          ) : orders.isPending ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : orders.data && orders.data.length > 0 ? (
            <>
              {/* 移动端(md 以下):卡片列表,金额为首要信息 */}
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
                        /* 待支付/已过期显关单截止,已支付显实际支付时间 */
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

              {/* 桌面端(md 及以上):表格 */}
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
                          {/* 长单号截断展示,悬停看全量(对账仍可复制) */}
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
                          {/* 待支付/已过期显关单截止,已支付显实际支付时间 */}
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
            <p className="text-sm text-muted-foreground">暂无订单</p>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

/* 金额表单(独立组件):受控输入与下单错误局部化;下单成功才清空金额,失败保留现值便于修正重试。 */
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
    <form
      onSubmit={submit}
      className="flex flex-col gap-4 md:flex-row md:flex-wrap md:items-end md:gap-3"
    >
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
    </form>
  );
}
