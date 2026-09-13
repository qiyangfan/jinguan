/* 价目管理(ADMIN):渠道价目运营。录入与展示口径 =「元 / 百万 tokens」;存储 = credit/token
   (1 credit = 本币 1e-9,故 1 元/百万 = 1000 credit/token,提交 ×1000、回填 ÷1000);
   下发契约为去尾零十进制字符串(元/token,≤9 位小数,同 muniu ParsePrice)。time_windows 动态行编辑。 */

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { yuanPerMillion2credits } from "@/lib/money";
import { adminList, remove, upsert } from "@/lib/pricing-api";
import type { PricingView, TimeWindow } from "@/lib/pricing-api";

interface UpsertForm {
  alias: string;
  currency: string;
  inputCredits: string;
  cachedInputCredits: string;
  outputCredits: string;
  timezone: string;
  windows: TimeWindow[];
}

/** 时区选项只用固定偏移:数据面(muniu ParseZoneOffset)仅认 "UTC"/"±HH:MM",IANA 名会被拒。 */
const TIMEZONES = [
  { value: "UTC", label: "UTC(协调世界时)" },
  { value: "+08:00", label: "UTC+08:00(北京、新加坡)" },
  { value: "+09:00", label: "UTC+09:00(东京、首尔)" },
  { value: "+07:00", label: "UTC+07:00(曼谷、雅加达)" },
  { value: "+05:30", label: "UTC+05:30(印度)" },
  { value: "-05:00", label: "UTC-05:00(美东)" },
  { value: "-08:00", label: "UTC-08:00(美西)" },
];

const EMPTY: UpsertForm = {
  alias: "",
  currency: "CNY",
  inputCredits: "",
  cachedInputCredits: "0",
  outputCredits: "",
  timezone: "+08:00",
  windows: [],
};

/** 倍率规则与后端 PricingAdminController 同式:正数、最多 4 位小数。 */
const MULTIPLIER_RE = /^\d+(\.\d{1,4})?$/;

/** 时段窗 "HH:MM-HH:MM":起点须早于 24:00,终点可到 24:00,起止相同无效;终点早于起点=跨零点窗(与数据面 ParseWindowMinutes 一致)。 */
function validWindow(s: string): boolean {
  const m = /^(\d{2}):(\d{2})-(\d{2}):(\d{2})$/.exec(s);
  if (!m) return false;
  const [h1, m1, h2, m2] = [Number(m[1]), Number(m[2]), Number(m[3]), Number(m[4])];
  const startOk = h1 < 24 && m1 < 60;
  const endOk = (h2 < 24 && m2 < 60) || (h2 === 24 && m2 === 0);
  return startOk && endOk && h1 * 60 + m1 !== h2 * 60 + m2;
}

/** 下发契约十进制串(元/token,如 "0.000008")→ 百万 tokens 口径整数(8);先取整消浮点尾差。 */
function contract2Million(s: string): number {
  return Math.round(parseFloat(s) * 1_000_000);
}

/** 价目币种符号(契约 currency 为自由串,未知币种原样显示代码)。 */
function currencySymbol(c: string): string {
  if (c === "CNY") return "¥";
  if (c === "USD") return "$";
  return `${c} `;
}

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
        inputCredits: yuanPerMillion2credits(form.inputCredits),
        cachedInputCredits: yuanPerMillion2credits(form.cachedInputCredits),
        outputCredits: yuanPerMillion2credits(form.outputCredits),
        timezone: form.timezone || undefined,
        timeWindows: form.windows.map((w) => ({
          window: w.window.trim(),
          multiplier: w.multiplier.trim(),
        })),
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
    // 时段窗/倍率与数据面校验同规则(muniu ParseMultiplier 只认 (0,10]):
    // 不合格提前拦截,避免保存成功但 muniu 拉取时静默跳过、该价目永不生效
    const bad = form.windows.find((w) => {
      if (!validWindow(w.window.trim())) return true;
      const n = parseFloat(w.multiplier.trim());
      return !MULTIPLIER_RE.test(w.multiplier.trim()) || !(n > 0 && n <= 10);
    });
    if (bad) {
      setError(
        "分时段窗口须为 HH:MM-HH:MM(如 21:00-24:00),倍率须大于 0 且不超过 10、最多 4 位小数(如 1.5)",
      );
      return;
    }
    upsertMut.mutate();
  }

  function editExisting(v: PricingView) {
    setForm({
      alias: v.alias,
      currency: v.currency,
      inputCredits: String(contract2Million(v.input)),
      cachedInputCredits: String(contract2Million(v.cached_input)),
      outputCredits: String(contract2Million(v.output)),
      timezone: v.timezone ?? "",
      windows: [...v.time_windows],
    });
  }

  /* 时区下拉项:现值不在常用清单时原样保留(如历史 IANA 名),空值只出 placeholder */
  const timezoneOptions =
    form.timezone && !TIMEZONES.some((t) => t.value === form.timezone)
      ? [...TIMEZONES, { value: form.timezone, label: form.timezone }]
      : TIMEZONES;

  return (
    <section className="space-y-6">
      <PageHeader title="价目管理" />
      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardHeader>
          <CardTitle>价目录入 / 编辑</CardTitle>
          <CardDescription>
            单价按每百万 tokens 计;分时段倍率大于 0 且不超过 10(最多 4 位小数,如
            1.5);保存后约一分钟内自动生效
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
              <FormRow
                label="币种"
                htmlFor="currency"
                help="价目币种;数据面目前支持 CNY(人民币)与 USD(美元)"
              >
                <Select
                  value={form.currency}
                  onValueChange={(v) => setForm({ ...form, currency: v })}
                >
                  <SelectTrigger id="currency" className="w-36">
                    <SelectValue placeholder="选择币种" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CNY">CNY(人民币)</SelectItem>
                    <SelectItem value="USD">USD(美元)</SelectItem>
                  </SelectContent>
                </Select>
              </FormRow>
              <FormRow
                label="输入单价(元 / 百万 tokens)"
                htmlFor="inputCredits"
                help="每百万 tokens 收取的金额;系统以十亿分之一元(credit/token)存储,无需换算"
              >
                <Input
                  id="inputCredits"
                  type="number"
                  min="0"
                  value={form.inputCredits}
                  onChange={(e) => setForm({ ...form, inputCredits: e.target.value })}
                  className="w-32"
                  required
                />
              </FormRow>
              <FormRow
                label="缓存输入单价(元 / 百万 tokens)"
                htmlFor="cachedInputCredits"
                help="命中缓存的输入 tokens 适用此价;每百万 tokens 收取的金额,0 表示与输入同价"
              >
                <Input
                  id="cachedInputCredits"
                  type="number"
                  min="0"
                  value={form.cachedInputCredits}
                  onChange={(e) => setForm({ ...form, cachedInputCredits: e.target.value })}
                  className="w-32"
                />
              </FormRow>
              <FormRow
                label="输出单价(元 / 百万 tokens)"
                htmlFor="outputCredits"
                help="每百万 tokens 收取的金额;系统以十亿分之一元(credit/token)存储,无需换算"
              >
                <Input
                  id="outputCredits"
                  type="number"
                  min="0"
                  value={form.outputCredits}
                  onChange={(e) => setForm({ ...form, outputCredits: e.target.value })}
                  className="w-32"
                  required
                />
              </FormRow>
              <FormRow
                label="时区"
                htmlFor="timezone"
                help="分时段窗口按此时区判断;仅支持固定偏移(UTC 或 ±HH:MM),有夏令时的地区请按实际偏移填写;留空按 UTC"
              >
                <Select
                  value={form.timezone || undefined}
                  onValueChange={(v) => setForm({ ...form, timezone: v })}
                >
                  <SelectTrigger id="timezone" className="w-56">
                    <SelectValue placeholder="未设置(按 UTC)" />
                  </SelectTrigger>
                  <SelectContent>
                    {timezoneOptions.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormRow>
            </div>

            <FormRow
              label="分时段倍率"
              help="时段窗格式 HH:MM-HH:MM:起点须早于 24:00,终点可到 24:00,起止相同无效,终点早于起点表示跨零点窗口;倍率大于 0 且不超过 10,最多 4 位小数,如 1.5(高峰 1.5 倍、低谷 0.8 倍)"
            >
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
                    placeholder="21:00-24:00"
                    aria-label={`第 ${i + 1} 档时段窗口`}
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
                    aria-label={`第 ${i + 1} 档倍率`}
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
          <CardDescription>单价精确到小数点后 6 位;点「编辑」可回填表单修改</CardDescription>
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
                  <TableHead>输入单价(元 / 百万 tokens)</TableHead>
                  <TableHead>缓存输入单价(元 / 百万 tokens)</TableHead>
                  <TableHead>输出单价(元 / 百万 tokens)</TableHead>
                  <TableHead>分时段</TableHead>
                  <TableHead>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(list.data ?? []).map((v) => (
                  <TableRow key={v.alias}>
                    <TableCell>{v.alias}</TableCell>
                    <TableCell>{v.currency}</TableCell>
                    <TableCell>
                      {currencySymbol(v.currency)}
                      {contract2Million(v.input)}
                    </TableCell>
                    <TableCell>
                      {currencySymbol(v.currency)}
                      {contract2Million(v.cached_input)}
                    </TableCell>
                    <TableCell>
                      {currencySymbol(v.currency)}
                      {contract2Million(v.output)}
                    </TableCell>
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
              {`确认删除「${pendingDelete}」的价目?删除后约一分钟内数据面即按删除后价目生效,操作不可撤销。`}
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
