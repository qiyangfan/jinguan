/* 支付页:mock 渠道下单 → 模拟收银台支付 → 状态查询(本地会话订单簿;
   服务端订单列表端点为演进项)。金额录入单位=元,提交换算 μ。 */

import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
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
import { FormRow } from "@/components/form-row";
import { StatusBadge, type StatusMeta } from "@/components/status-badge";
import { errorDetail } from "@/lib/api";
import { createOrder, mockPay, queryOrder } from "@/lib/payment-api";
import type { PaymentOrderCreated } from "@/lib/api-types.gen";
import { micros2yuan } from "@/lib/money";

/* 契约 status 为 string(状态机:CREATED → PAID / EXPIRED,收敛扫描关闭超时单);
   未知值由 StatusBadge 回落 outline + 原文。 */
const STATUS_META: StatusMeta = {
  CREATED: { label: "待支付", variant: "outline" },
  PAID: { label: "已支付", variant: "secondary" },
  EXPIRED: { label: "已过期", variant: "destructive" },
};

interface LocalOrder {
  orderId: string;
  amountMicros: number;
  status: string; // CREATED | PAID | EXPIRED | unknown
}

export default function PaymentsPage() {
  const [amount, setAmount] = useState("");
  const [orders, setOrders] = useState<LocalOrder[]>([]);
  const [error, setError] = useState("");

  const create = useMutation({
    mutationFn: () => createOrder("mock", Math.round(parseFloat(amount) * 1_000_000), "CNY"),
    onSuccess: (created: PaymentOrderCreated) => {
      setOrders((prev) => [
        { orderId: created.orderId, amountMicros: created.amountMicros, status: "CREATED" },
        ...prev,
      ]);
      setAmount("");
      setError("");
    },
    onError: (err) => setError(errorDetail(err)),
  });

  const pay = useMutation({
    mutationFn: (orderId: string) => mockPay(orderId),
    onSuccess: (result) => {
      setOrders((prev) =>
        prev.map((o) => (o.orderId === result.orderId ? { ...o, status: result.status } : o)),
      );
      setError("");
    },
    onError: (err) => setError(errorDetail(err)),
  });

  const refresh = useMutation({
    mutationFn: (orderId: string) => queryOrder(orderId),
    onSuccess: (q) => {
      setOrders((prev) =>
        prev.map((o) => (o.orderId === q.orderId ? { ...o, status: q.status } : o)),
      );
      setError(""); // 与 create/pay onSuccess 成对清错:旧错误不跨成功动作残留
    },
    onError: (err) => setError(errorDetail(err)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setError(""); // 提交前预清,与 KeysPage/WalletPage 的「提交前双清」范式一致
    create.mutate();
  }

  return (
    <section className="space-y-6">
      <PageHeader title="支付(mock 渠道演示)" />
      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardHeader>
          <CardTitle>创建支付单</CardTitle>
          <CardDescription>
            下单后通过模拟收银台完成支付;订单 60 分钟未支付将过期关闭
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
            <FormRow label="金额(元)" htmlFor="amount">
              <Input
                id="amount"
                type="number"
                step="0.000001"
                min="0"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-48"
                placeholder="0.01"
              />
            </FormRow>
            <Button type="submit" disabled={create.isPending || !amount}>
              {create.isPending ? "下单中…" : "创建支付单"}
            </Button>
          </form>
        </CardContent>
      </Card>

      {orders.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>本会话订单</CardTitle>
            <CardDescription>
              状态机:CREATED → PAID / EXPIRED;超时订单由收敛扫描关闭
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>订单号</TableHead>
                  <TableHead>金额(元)</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {orders.map((o) => (
                  <TableRow key={o.orderId}>
                    <TableCell className="font-mono text-2xs">{o.orderId}</TableCell>
                    <TableCell>¥{micros2yuan(o.amountMicros)}</TableCell>
                    <TableCell>
                      <StatusBadge meta={STATUS_META} value={o.status} />
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
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={refresh.isPending}
                        onClick={() => refresh.mutate(o.orderId)}
                      >
                        查询
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </section>
  );
}
