/* 价目管理(ADMIN):渠道价目运营(μ 整数录入,下发契约自动转去尾零字符串);
   time_windows 动态行编辑(multiplier ≤4 位小数字符串)。 */

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
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
import { QueryErrorAlert } from "@/components/query-error";
import { errorDetail } from "@/lib/api";
import { adminList, remove, upsert } from "@/lib/pricing-api";
import type { PricingView, TimeWindow } from "@/lib/api-types.gen";

interface UpsertForm {
  alias: string;
  currency: string;
  inputMicros: string;
  cachedInputMicros: string;
  outputMicros: string;
  timezone: string;
  windows: TimeWindow[];
}

const EMPTY: UpsertForm = {
  alias: "",
  currency: "CNY",
  inputMicros: "",
  cachedInputMicros: "0",
  outputMicros: "",
  timezone: "Asia/Shanghai",
  windows: [],
};

export default function PricingPage() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ["pricing"], queryFn: adminList });
  const [form, setForm] = useState<UpsertForm>(EMPTY);
  const [error, setError] = useState("");
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const upsertMut = useMutation({
    mutationFn: () =>
      upsert(form.alias, {
        currency: form.currency,
        inputMicros: Number(form.inputMicros),
        cachedInputMicros: Number(form.cachedInputMicros),
        outputMicros: Number(form.outputMicros),
        timezone: form.timezone || undefined,
        time_windows: form.windows,
      }),
    onSuccess: () => {
      setError("");
      setForm(EMPTY);
      void queryClient.invalidateQueries({ queryKey: ["pricing"] });
    },
    onError: (err) => setError(errorDetail(err)),
  });

  const removeMut = useMutation({
    mutationFn: remove,
    onSuccess: () => {
      setError("");
      void queryClient.invalidateQueries({ queryKey: ["pricing"] });
    },
    onError: (err) => setError(errorDetail(err)),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    upsertMut.mutate();
  }

  function editExisting(v: PricingView) {
    setForm({
      alias: v.alias,
      currency: v.currency,
      inputMicros: String(Math.round(parseFloat(v.input) * 1_000_000)),
      cachedInputMicros: String(Math.round(parseFloat(v.cached_input) * 1_000_000)),
      outputMicros: String(Math.round(parseFloat(v.output) * 1_000_000)),
      timezone: v.timezone ?? "",
      windows: [...v.time_windows],
    });
  }

  return (
    <section className="space-y-6">
      <PageHeader title="价目管理" />
      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardHeader>
          <CardTitle>价目录入 / 编辑</CardTitle>
          <CardDescription>
            单价单位 μ(每 token);time_windows multiplier ≤4 位小数(如 1.5);保存后由 muniu
            下轮拉取生效(零重启)
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <FormRow label="模型别名" htmlFor="alias">
                <Input
                  id="alias"
                  value={form.alias}
                  onChange={(e) => setForm({ ...form, alias: e.target.value })}
                  className="w-48"
                  required
                />
              </FormRow>
              <FormRow label="币种" htmlFor="currency">
                <Input
                  id="currency"
                  value={form.currency}
                  onChange={(e) => setForm({ ...form, currency: e.target.value })}
                  className="w-24"
                  required
                />
              </FormRow>
              <FormRow label="输入价(μ)" htmlFor="inputMicros">
                <Input
                  id="inputMicros"
                  type="number"
                  min="0"
                  value={form.inputMicros}
                  onChange={(e) => setForm({ ...form, inputMicros: e.target.value })}
                  className="w-32"
                  required
                />
              </FormRow>
              <FormRow label="缓存输入价(μ)" htmlFor="cachedInputMicros">
                <Input
                  id="cachedInputMicros"
                  type="number"
                  min="0"
                  value={form.cachedInputMicros}
                  onChange={(e) => setForm({ ...form, cachedInputMicros: e.target.value })}
                  className="w-32"
                />
              </FormRow>
              <FormRow label="输出价(μ)" htmlFor="outputMicros">
                <Input
                  id="outputMicros"
                  type="number"
                  min="0"
                  value={form.outputMicros}
                  onChange={(e) => setForm({ ...form, outputMicros: e.target.value })}
                  className="w-32"
                  required
                />
              </FormRow>
              <FormRow label="时区" htmlFor="timezone">
                <Input
                  id="timezone"
                  value={form.timezone}
                  onChange={(e) => setForm({ ...form, timezone: e.target.value })}
                  className="w-40"
                  placeholder="Asia/Shanghai"
                />
              </FormRow>
            </div>

            <FormRow label="分时段倍率">
              {form.windows.map((w, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    value={w.window}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        windows: form.windows.map((x, j) =>
                          j === i ? { ...x, window: e.target.value } : x,
                        ),
                      })
                    }
                    className="w-40"
                    placeholder="窗口名(peak)"
                  />
                  <Input
                    value={w.multiplier}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        windows: form.windows.map((x, j) =>
                          j === i ? { ...x, multiplier: e.target.value } : x,
                        ),
                      })
                    }
                    className="w-28"
                    placeholder="1.5"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      setForm({ ...form, windows: form.windows.filter((_, j) => j !== i) })
                    }
                  >
                    移除
                  </Button>
                </div>
              ))}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() =>
                  setForm({ ...form, windows: [...form.windows, { window: "", multiplier: "1" }] })
                }
              >
                + 加一档
              </Button>
            </FormRow>

            <Button type="submit" disabled={upsertMut.isPending}>
              {upsertMut.isPending ? "保存中…" : "保存"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>现有效期目</CardTitle>
          <CardDescription>
            input/output 为下发契约形态(去尾零十进制字符串);编辑可回填表单
          </CardDescription>
        </CardHeader>
        <CardContent>
          {list.isError ? (
            <QueryErrorAlert text={errorDetail(list.error)} />
          ) : list.isPending ? (
            <div className="space-y-2">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>别名</TableHead>
                  <TableHead>币种</TableHead>
                  <TableHead>输入(元)</TableHead>
                  <TableHead>缓存输入(元)</TableHead>
                  <TableHead>输出(元)</TableHead>
                  <TableHead>分时段</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(list.data ?? []).map((v) => (
                  <TableRow key={v.alias}>
                    <TableCell>{v.alias}</TableCell>
                    <TableCell>{v.currency}</TableCell>
                    <TableCell>¥{v.input}</TableCell>
                    <TableCell>¥{v.cached_input}</TableCell>
                    <TableCell>¥{v.output}</TableCell>
                    <TableCell>
                      {v.time_windows.length === 0
                        ? "—"
                        : v.time_windows.map((w) => `${w.window}×${w.multiplier}`).join(", ")}
                    </TableCell>
                    <TableCell className="space-x-2">
                      <Button size="sm" variant="outline" onClick={() => editExisting(v)}>
                        编辑
                      </Button>
                      <Button
                        size="sm"
                        variant="destructive"
                        onClick={() => setPendingDelete(v.alias)}
                      >
                        删除
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* 删除确认(宪法 Article 1:破坏性操作必须 AlertDialog,禁 window.confirm) */}
      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除价目</AlertDialogTitle>
            <AlertDialogDescription>
              确认删除「{pendingDelete}」的价目?删除后 muniu 下轮拉取即按新价目生效,操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={removeMut.isPending}
              onClick={() => {
                if (pendingDelete) removeMut.mutate(pendingDelete);
                setPendingDelete(null);
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
