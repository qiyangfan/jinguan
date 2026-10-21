/* 管理供应商、模型路由和能力目录；保存时整包替换。密钥只保存环境变量名。 */

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, RefreshCw, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { BottomSheetContent } from "@/components/bottom-sheet-dialog";
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
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/page-header";
import { FormRow } from "@/components/form-row";
import { ErrorAlert } from "@/components/error-alert";
import { errorDetail } from "@/lib/api";
import {
  ADAPTERS,
  ADAPTER_LABELS,
  fetchPresets,
  fetchTopology,
  saveTopology,
  type CapabilityDef,
  type ProviderDef,
  type ProviderPreset,
  type TopologyPayload,
} from "@/lib/topology-api";

/** 数字字段以字符串保存，避免输入中途格式化。 */
interface CandidateRow {
  provider: string;
  model: string;
  priority: string;
  weight: string;
}

/** 能力目录表单态。 */
interface ModelRow {
  provider: string;
  model: string;
  drop_params: string;
  thinking_lock: boolean;
  max_output_tokens: string;
}

/** 路由表单态，保存前转换为契约 Record。 */
interface RouteEntry {
  alias: string;
  candidates: CandidateRow[];
}

interface Draft {
  providers: ProviderDef[];
  routes: RouteEntry[];
  models: ModelRow[];
}

/** 用 type 区分删除对象：路由别名与供应商名可以重名。 */
type PendingDelete = { type: "provider" | "route"; value: string } | null;

function toDraft(payload: TopologyPayload): Draft {
  return {
    providers: payload.providers,
    routes: Object.entries(payload.model_routes).map(([alias, candidates]) => ({
      alias,
      candidates: candidates.map((c) => ({
        provider: c.provider,
        model: c.model,
        priority: String(c.priority),
        weight: String(c.weight),
      })),
    })),
    models: payload.models.map((m: CapabilityDef) => ({
      provider: m.provider,
      model: m.model,
      drop_params: m.drop_params.join(","),
      thinking_lock: m.thinking_lock,
      max_output_tokens: String(m.max_output_tokens),
    })),
  };
}

function toPayload(draft: Draft): TopologyPayload {
  return {
    providers: draft.providers,
    model_routes: Object.fromEntries(
      draft.routes
        .filter((r) => r.alias.trim() !== "")
        .map((r) => [
          r.alias.trim(),
          r.candidates.map((c) => ({
            provider: c.provider,
            model: c.model,
            priority: parseNonNegative(c.priority) ?? 0,
            weight: parseNonNegative(c.weight) ?? 0,
          })),
        ]),
    ),
    models: draft.models.map((m) => ({
      provider: m.provider,
      model: m.model,
      drop_params: m.drop_params
        .split(",")
        .map((s) => s.trim())
        .filter((s) => s !== ""),
      thinking_lock: m.thinking_lock,
      max_output_tokens: parseNonNegative(m.max_output_tokens) ?? 0,
    })),
  };
}

/** 与服务端一致：拓扑数值限制在 ±Integer.MAX_VALUE。 */
const INT_DOMAIN_MAX = 2_147_483_647;

/** 供应商和 key 权重必须为正整数。 */
function parsePositive(v: string): number | null {
  const n = Number(v);
  return Number.isInteger(n) && n >= 1 && n <= INT_DOMAIN_MAX ? n : null;
}

/** priority、路由 weight 和 max_output_tokens 使用非负整数；仅 max_output_tokens 的 0 表示不限制。 */
function parseNonNegative(v: string): number | null {
  const n = Number(v);
  return Number.isInteger(n) && n >= 0 && n <= INT_DOMAIN_MAX ? n : null;
}

/** 前端预校验；后端校验仍为最终依据。 */
function validateDraft(draft: Draft): string {
  const names = new Set<string>();
  for (const p of draft.providers) {
    if (p.name.trim() === "") return "供应商名称不能为空";
    if (names.has(p.name)) return `供应商 ${p.name} 重复定义`;
    names.add(p.name);
    if (parsePositive(String(p.weight)) === null) return `供应商 ${p.name} 的 weight 必须 ≥ 1`;
    if (p.keys.length === 0) return `供应商 ${p.name} 至少需要一个 env 引用 key`;
    if (p.base_url.trim() === "") return `供应商 ${p.name} 的 base_url 不能为空`;
    for (const k of p.keys) {
      if (k.env.trim() === "") return `供应商 ${p.name} 存在空的 env 引用名`;
    }
  }
  const aliases = new Set<string>();
  for (const r of draft.routes) {
    const alias = r.alias.trim();
    // 判重与生成载荷都使用 trim 后的别名，避免同键覆盖。
    if (alias === "") {
      if (r.candidates.length > 0) return "存在别名为空的路由,请填写别名或删除该路由";
      continue;
    }
    if (aliases.has(alias)) return `路由别名 ${alias} 重复`;
    aliases.add(alias);
    if (r.candidates.length === 0) return `路由别名 ${alias} 的候选列表不能为空`;
    for (const c of r.candidates) {
      if (!names.has(c.provider)) return `路由 ${alias} 引用了未定义的供应商 ${c.provider}`;
      if (c.model.trim() === "") return `路由 ${alias} 存在空模型名`;
      if (parseNonNegative(c.priority) === null || parseNonNegative(c.weight) === null) {
        return `路由 ${alias} 的 priority/weight 必须为非负整数`;
      }
    }
  }
  if (aliases.size === 0) return "model_routes 不能为空:至少需要一个路由别名";
  const caps = new Set<string>();
  for (const m of draft.models) {
    if (!names.has(m.provider)) return `能力目录 ${m.model} 引用了未定义的供应商 ${m.provider}`;
    if (m.model.trim() === "") return "能力目录存在空模型名";
    if (parseNonNegative(m.max_output_tokens) === null) {
      return `能力目录 ${m.model} 的 max_output_tokens 必须为非负整数(0 = 不限制)`;
    }
    // 数据面以 (provider, model) 为主键；重复声明会覆盖配置。
    const cap = `${m.provider}\u0000${m.model.trim()}`;
    if (caps.has(cap)) return `能力目录 ${m.provider}/${m.model} 重复声明`;
    caps.add(cap);
  }
  return "";
}

/** 同一厂商的多个协议端点。 */
interface VendorPreset {
  name: string;
  display_name: string;
  region: string;
  env_suggestion: string;
  models: string[];
  note: string;
  variants: { adapter: string; base_url: string }[];
}

/** 将 anthropic 端点合并为厂商的协议变体。 */
function mergeByVendor(presets: ProviderPreset[]): VendorPreset[] {
  const byName = new Map<string, VendorPreset>();
  const out: VendorPreset[] = [];
  for (const p of presets) {
    const isBranch = p.name.endsWith("-anthropic") && p.adapter === "anthropic";
    const baseName = isBranch ? p.name.slice(0, -"-anthropic".length) : p.name;
    let vendor = byName.get(baseName);
    if (!vendor) {
      vendor = {
        name: baseName,
        display_name: p.display_name.replace(" · Anthropic 分支", ""),
        region: p.region,
        env_suggestion: p.env_suggestion,
        models: p.models,
        note: p.note,
        variants: [],
      };
      byName.set(baseName, vendor);
      out.push(vendor);
    }
    vendor.variants.push({ adapter: p.adapter, base_url: p.base_url });
  }
  return out;
}

/** 供应商表单态及其预设建议。 */
interface ProviderEdit {
  original: string | null;
  form: {
    name: string;
    adapter: string;
    base_url: string;
    weight: string;
    priority: string;
    keys: { env: string; weight: string }[];
  };
  presetModels: string[];
  presetVariants: { adapter: string; base_url: string }[];
}

const EMPTY_EDIT: ProviderEdit = {
  original: null,
  form: {
    name: "",
    adapter: "openai",
    base_url: "",
    weight: "1",
    priority: "0",
    keys: [{ env: "", weight: "1" }],
  },
  presetModels: [],
  presetVariants: [],
};

/** 表单内联校验提示。 */
function submitBlockedHint(edit: ProviderEdit, draft: Draft | null): string {
  const f = edit.form;
  if (f.name.trim() === "") return "";
  if (draft?.providers.some((p) => p.name === f.name.trim() && p.name !== edit.original)) {
    return `供应商 ${f.name.trim()} 已存在`;
  }
  if (parsePositive(f.weight) === null) return "weight 必须 ≥ 1 的整数";
  if (parseNonNegative(f.priority) === null) return "priority 必须 ≥ 0 的整数";
  if (f.keys.every((k) => k.env.trim() === "")) return "至少填写一个 env 引用名";
  return "";
}

export default function TopologyPage() {
  const queryClient = useQueryClient();
  const topology = useQuery({ queryKey: ["topology"], queryFn: fetchTopology });
  const presets = useQuery({ queryKey: ["topology-presets"], queryFn: fetchPresets });
  const presetVendors = mergeByVendor(presets.data?.presets ?? []);

  const [draft, setDraft] = useState<Draft | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [etag, setEtag] = useState("");

  const [edit, setEdit] = useState<ProviderEdit | null>(null);
  const [pendingDelete, setPendingDelete] = useState<PendingDelete>(null);
  const [deleteBlocked, setDeleteBlocked] = useState("");

  // 查询数据变化时同步草稿，保留未保存的编辑。
  const [hydratedFrom, setHydratedFrom] = useState<TopologyPayload | null>(topology.data ?? null);
  if (topology.data && topology.data !== hydratedFrom) {
    setHydratedFrom(topology.data);
    if (!dirty) {
      setDraft(toDraft(topology.data));
    }
  }

  function mutate(fn: (d: Draft) => Draft) {
    setDraft((prev) => (prev ? fn(prev) : prev));
    setDirty(true);
    setSaveError("");
  }

  const save = useMutation({
    mutationFn: () => {
      const err = validateDraft(draft as Draft);
      if (err !== "") {
        throw new Error(err);
      }
      return saveTopology(toPayload(draft as Draft));
    },
    onSuccess: (newEtag) => {
      setEtag(newEtag);
      setDirty(false);
      setSaveError("");
      void queryClient.invalidateQueries({ queryKey: ["topology"] });
    },
    onError: (err) => {
      setSaveError(errorDetail(err));
    },
  });

  function submitProvider(e: FormEvent) {
    e.preventDefault();
    if (!edit || !draft || submitBlockedHint(edit, draft) !== "") {
      return;
    }
    const f = edit.form;
    const weight = parsePositive(f.weight);
    const priority = parseNonNegative(f.priority);
    const def: ProviderDef = {
      name: f.name.trim(),
      adapter: f.adapter,
      base_url: f.base_url.trim(),
      keys: f.keys
        .filter((k) => k.env.trim() !== "")
        .map((k) => ({ env: k.env.trim(), weight: parsePositive(k.weight) ?? 1 })),
      weight: weight as number,
      priority: priority as number,
    };
    mutate((d) => {
      const providers =
        edit.original === null
          ? [...d.providers, def]
          : d.providers.map((p) => (p.name === edit.original ? def : p));
      const renamed = edit.original !== null && edit.original !== def.name;
      const models = d.models.map((m) =>
        renamed && m.provider === edit.original ? { ...m, provider: def.name } : m,
      );
      const routes = d.routes.map((r) => ({
        ...r,
        candidates: r.candidates.map((c) =>
          renamed && c.provider === edit.original ? { ...c, provider: def.name } : c,
        ),
      }));
      // 用最终模型名判重，避免改名后回填出重复的 (provider, model)。
      const existing = new Set(models.map((m) => `${m.provider}\u0000${m.model}`));
      for (const model of edit.presetModels) {
        const key = `${def.name}\u0000${model}`;
        if (!existing.has(key)) {
          models.push({
            provider: def.name,
            model,
            drop_params: "",
            thinking_lock: false,
            max_output_tokens: "0",
          });
          existing.add(key);
        }
      }
      return { ...d, providers, models, routes };
    });
    setEdit(null);
  }

  function requestDeleteProvider(name: string) {
    if (!draft) {
      return;
    }
    const used =
      draft.routes.some((r) => r.candidates.some((c) => c.provider === name)) ||
      draft.models.some((m) => m.provider === name);
    if (used) {
      setDeleteBlocked(`供应商 ${name} 仍被模型路由或能力目录引用,请先清理对应引用再删除`);
      return;
    }
    setPendingDelete({ type: "provider", value: name });
  }

  function confirmDeleteProvider() {
    if (pendingDelete?.type !== "provider") {
      return;
    }
    const name = pendingDelete.value;
    setPendingDelete(null);
    mutate((d) => ({
      ...d,
      providers: d.providers.filter((p) => p.name !== name),
    }));
  }

  if (topology.isPending) {
    return (
      <section className="space-y-6">
        <PageHeader title="渠道拓扑" description="供应商 · 模型路由 · 能力目录(整包下发至数据面)" />
        <Skeleton className="h-40 w-full" />
        <Skeleton className="h-64 w-full" />
      </section>
    );
  }

  if (topology.isError || draft === null) {
    return (
      <section className="space-y-6">
        <PageHeader title="渠道拓扑" />
        <ErrorAlert text={topology.isError ? errorDetail(topology.error) : undefined} />
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="渠道拓扑"
        description="供应商 · 模型路由 · 能力目录(整包下发至数据面)"
        actions={
          <>
            {dirty && (
              <Badge variant="secondary" aria-label="存在未保存修改">
                未保存
              </Badge>
            )}
            <Button className="h-11 md:h-9" onClick={() => setEdit(EMPTY_EDIT)}>
              <Plus className="mr-1 size-4" />
              添加供应商
            </Button>
          </>
        }
      />

      {saveError && <p className="text-sm text-destructive">{saveError}</p>}
      <p className="text-2xs text-muted-foreground">
        当前下发指纹(ETag):{etag || "保存后显示"} · 保存即整包替换并经拉取循环热更新至 muniu
      </p>

      <Card>
        <CardHeader>
          <CardTitle>供应商({draft.providers.length})</CardTitle>
          <CardDescription>
            上游渠道定义;key 只填环境变量引用名,真实密钥由各 muniu 副本自行解析,绝不明文入库
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2">
          {draft.providers.map((p) => (
            <div key={p.name} className="rounded-lg border bg-background p-3">
              <div className="flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{p.name}</span>
                    <Badge variant="outline">{ADAPTER_LABELS[p.adapter] ?? p.adapter}</Badge>
                  </div>
                  <p className="truncate text-2xs text-muted-foreground">{p.base_url}</p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-11 md:size-9"
                    aria-label={`编辑 ${p.name}`}
                    onClick={() =>
                      setEdit({
                        original: p.name,
                        form: {
                          name: p.name,
                          adapter: p.adapter,
                          base_url: p.base_url,
                          weight: String(p.weight),
                          priority: String(p.priority),
                          keys: p.keys.map((k) => ({ env: k.env, weight: String(k.weight) })),
                        },
                        presetModels: [],
                        presetVariants: [],
                      })
                    }
                  >
                    <RefreshCw className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-11 md:size-9"
                    aria-label={`删除 ${p.name}`}
                    onClick={() => requestDeleteProvider(p.name)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              </div>
              <p className="mt-2 truncate text-2xs text-muted-foreground">
                env:{p.keys.map((k) => k.env).join("、")} · weight {p.weight} · priority{" "}
                {p.priority}
              </p>
            </div>
          ))}
          {draft.providers.length === 0 && (
            <p className="text-sm text-muted-foreground">
              尚未配置供应商——点击右上角「添加供应商」,从官方预设一键回填开始。
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-start justify-between space-y-0">
          <div className="space-y-1.5">
            <CardTitle>模型路由({draft.routes.length})</CardTitle>
            <CardDescription>对外别名 → 候选供应商模型;别名即客户端请求的 model 名</CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            className="h-11 shrink-0 px-4 md:h-8"
            onClick={() =>
              mutate((d) => {
                let n = 1;
                while (d.routes.some((r) => r.alias === `new-alias-${n}`)) {
                  n += 1;
                }
                return {
                  ...d,
                  routes: [
                    ...d.routes,
                    {
                      alias: `new-alias-${n}`,
                      candidates: [
                        {
                          provider: d.providers[0]?.name ?? "",
                          model: "",
                          priority: "0",
                          weight: "0",
                        },
                      ],
                    },
                  ],
                };
              })
            }
          >
            <Plus className="mr-1 size-4" />
            新增别名
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          {draft.routes.map((entry, ri) => (
            <div key={`${entry.alias}-${ri}`} className="rounded-lg border p-3">
              <div className="flex items-center gap-2">
                <Input
                  value={entry.alias}
                  onChange={(e) =>
                    mutate((d) => ({
                      ...d,
                      routes: d.routes.map((r, i) =>
                        i === ri ? { ...r, alias: e.target.value } : r,
                      ),
                    }))
                  }
                  className="min-w-0 flex-1 md:w-48 md:flex-none"
                  aria-label="路由别名"
                  placeholder="别名,如 deepseek-chat"
                />
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-11 shrink-0 md:size-9"
                  aria-label={`删除别名 ${entry.alias}`}
                  onClick={() => setPendingDelete({ type: "route", value: entry.alias })}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
              <div className="mt-3 space-y-2">
                {entry.candidates.map((c, ci) => (
                  <div
                    key={ci}
                    className="grid grid-cols-2 gap-2 rounded-lg border bg-background p-2.5 [&>*]:min-w-0 md:grid-cols-[1.2fr_1.5fr_5rem_5rem_auto] md:rounded-none md:border-0 md:bg-transparent md:p-0"
                  >
                    <Select
                      value={c.provider}
                      onValueChange={(v) =>
                        mutate((d) => ({
                          ...d,
                          routes: d.routes.map((r, i) =>
                            i === ri
                              ? {
                                  ...r,
                                  candidates: r.candidates.map((x, j) =>
                                    j === ci ? { ...x, provider: v } : x,
                                  ),
                                }
                              : r,
                          ),
                        }))
                      }
                    >
                      <SelectTrigger aria-label="候选供应商" className="max-md:col-span-2">
                        <SelectValue placeholder="选择供应商" />
                      </SelectTrigger>
                      <SelectContent>
                        {draft.providers.map((p) => (
                          <SelectItem key={p.name} value={p.name}>
                            {p.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Input
                      value={c.model}
                      onChange={(e) =>
                        mutate((d) => ({
                          ...d,
                          routes: d.routes.map((r, i) =>
                            i === ri
                              ? {
                                  ...r,
                                  candidates: r.candidates.map((x, j) =>
                                    j === ci ? { ...x, model: e.target.value } : x,
                                  ),
                                }
                              : r,
                          ),
                        }))
                      }
                      className="max-md:col-span-2"
                      placeholder="上游模型 ID"
                      aria-label="上游模型"
                    />
                    <Input
                      value={c.priority}
                      onChange={(e) =>
                        mutate((d) => ({
                          ...d,
                          routes: d.routes.map((r, i) =>
                            i === ri
                              ? {
                                  ...r,
                                  candidates: r.candidates.map((x, j) =>
                                    j === ci ? { ...x, priority: e.target.value } : x,
                                  ),
                                }
                              : r,
                          ),
                        }))
                      }
                      inputMode="numeric"
                      aria-label="优先级"
                      placeholder="优先级"
                    />
                    <Input
                      value={c.weight}
                      onChange={(e) =>
                        mutate((d) => ({
                          ...d,
                          routes: d.routes.map((r, i) =>
                            i === ri
                              ? {
                                  ...r,
                                  candidates: r.candidates.map((x, j) =>
                                    j === ci ? { ...x, weight: e.target.value } : x,
                                  ),
                                }
                              : r,
                          ),
                        }))
                      }
                      inputMode="numeric"
                      aria-label="权重"
                      placeholder="权重"
                    />
                    <Button
                      variant="ghost"
                      size="icon"
                      className="max-md:col-span-2 max-md:justify-self-end"
                      aria-label="删除该候选"
                      onClick={() =>
                        mutate((d) => ({
                          ...d,
                          routes: d.routes.map((r, i) =>
                            i === ri
                              ? { ...r, candidates: r.candidates.filter((_, j) => j !== ci) }
                              : r,
                          ),
                        }))
                      }
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  variant="outline"
                  size="sm"
                  className="h-11 px-4 md:h-8"
                  onClick={() =>
                    mutate((d) => ({
                      ...d,
                      routes: d.routes.map((r, i) =>
                        i === ri
                          ? {
                              ...r,
                              candidates: [
                                ...r.candidates,
                                {
                                  provider: d.providers[0]?.name ?? "",
                                  model: "",
                                  priority: "0",
                                  weight: "0",
                                },
                              ],
                            }
                          : r,
                      ),
                    }))
                  }
                >
                  <Plus className="mr-1 size-3.5" />
                  添加候选
                </Button>
              </div>
            </div>
          ))}
          {draft.routes.length === 0 && (
            <p className="text-sm text-muted-foreground">
              尚无路由——至少需要一个别名,否则整包保存会被数据面拒绝。
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-start justify-between space-y-0">
          <div className="space-y-1.5">
            <CardTitle>能力目录({draft.models.length})</CardTitle>
            <CardDescription>
              (provider, model) 声明制;drop_params 逗号分隔,max_output_tokens 0 = 不限制
            </CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            className="h-11 shrink-0 px-4 md:h-8"
            onClick={() =>
              mutate((d) => ({
                ...d,
                models: [
                  ...d.models,
                  {
                    provider: d.providers[0]?.name ?? "",
                    model: "",
                    drop_params: "",
                    thinking_lock: false,
                    max_output_tokens: "0",
                  },
                ],
              }))
            }
          >
            <Plus className="mr-1 size-4" />
            声明模型能力
          </Button>
        </CardHeader>
        <CardContent className="space-y-2">
          {draft.models.map((m, mi) => (
            <div
              key={mi}
              className="grid grid-cols-2 gap-2 rounded-lg border bg-background p-2.5 [&>*]:min-w-0 md:grid-cols-[1.2fr_1.5fr_1.5fr_6rem_6rem_auto] md:rounded-none md:border-0 md:bg-transparent md:p-0"
            >
              <Select
                value={m.provider}
                onValueChange={(v) =>
                  mutate((d) => ({
                    ...d,
                    models: d.models.map((x, i) => (i === mi ? { ...x, provider: v } : x)),
                  }))
                }
              >
                <SelectTrigger aria-label="能力供应商" className="max-md:col-span-2">
                  <SelectValue placeholder="选择供应商" />
                </SelectTrigger>
                <SelectContent>
                  {draft.providers.map((p) => (
                    <SelectItem key={p.name} value={p.name}>
                      {p.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                value={m.model}
                onChange={(e) =>
                  mutate((d) => ({
                    ...d,
                    models: d.models.map((x, i) =>
                      i === mi ? { ...x, model: e.target.value } : x,
                    ),
                  }))
                }
                className="max-md:col-span-2"
                placeholder="模型 ID"
                aria-label="能力模型"
              />
              <Input
                value={m.drop_params}
                onChange={(e) =>
                  mutate((d) => ({
                    ...d,
                    models: d.models.map((x, i) =>
                      i === mi ? { ...x, drop_params: e.target.value } : x,
                    ),
                  }))
                }
                className="max-md:col-span-2"
                placeholder="丢弃参数,逗号分隔"
                aria-label="丢弃参数"
              />
              <Select
                value={m.thinking_lock ? "yes" : "no"}
                onValueChange={(v) =>
                  mutate((d) => ({
                    ...d,
                    models: d.models.map((x, i) =>
                      i === mi ? { ...x, thinking_lock: v === "yes" } : x,
                    ),
                  }))
                }
              >
                <SelectTrigger aria-label="思考锁">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="no">思考放开</SelectItem>
                  <SelectItem value="yes">思考锁定</SelectItem>
                </SelectContent>
              </Select>
              <Input
                value={m.max_output_tokens}
                onChange={(e) =>
                  mutate((d) => ({
                    ...d,
                    models: d.models.map((x, i) =>
                      i === mi ? { ...x, max_output_tokens: e.target.value } : x,
                    ),
                  }))
                }
                inputMode="numeric"
                aria-label="最大输出"
                placeholder="0 = 不限制"
              />
              <Button
                variant="ghost"
                size="icon"
                className="max-md:col-span-2 max-md:justify-self-end"
                aria-label="删除该能力"
                onClick={() =>
                  mutate((d) => ({ ...d, models: d.models.filter((_, i) => i !== mi) }))
                }
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
          ))}
          {draft.models.length === 0 && (
            <p className="text-sm text-muted-foreground">
              能力目录为空——选择官方预设的供应商在保存时会自动带入建议模型。
            </p>
          )}
        </CardContent>
      </Card>

      <div className="flex items-center gap-3">
        <Button
          className="h-11 px-6 md:h-9 md:px-4"
          onClick={() => save.mutate()}
          disabled={save.isPending || !dirty}
        >
          {save.isPending ? "保存中…" : "保存整包拓扑"}
        </Button>
        {dirty && (
          <span className="text-2xs text-muted-foreground">改动仅在保存后经拉取循环生效</span>
        )}
      </div>

      <Sheet open={edit !== null} onOpenChange={(open) => !open && setEdit(null)}>
        <BottomSheetContent className="md:max-w-2xl">
          <SheetHeader>
            <SheetTitle>{edit?.original === null ? "添加供应商" : "编辑供应商"}</SheetTitle>
            <SheetDescription>
              从官方预设一键回填,或直接填写;协议分支(OpenAI 兼容 / Anthropic)经适配器下拉切换
            </SheetDescription>
          </SheetHeader>
          {edit && (
            <form onSubmit={submitProvider} className="space-y-4">
              <div className="space-y-2">
                <p className="text-xs text-muted-foreground">
                  官方预设({presetVendors.length} 家,只收厂商第一方端点;点击回填):
                </p>
                {presets.isPending && <Skeleton className="h-16 w-full" />}
                {presets.isError && (
                  <ErrorAlert text={`预设目录加载失败:${errorDetail(presets.error)}`} />
                )}
                {(["cn", "global"] as const).map((region) => {
                  const list = presetVendors.filter((v) => v.region === region);
                  if (list.length === 0) {
                    return null;
                  }
                  return (
                    <div key={region} className="flex flex-wrap gap-1.5">
                      {list.map((vendor) => {
                        /* 优先使用适配器匹配的预设端点。 */
                        const variant =
                          vendor.variants.find((x) => x.adapter === edit.form.adapter) ??
                          vendor.variants[0];
                        return (
                          <Button
                            key={vendor.name}
                            type="button"
                            variant="outline"
                            size="sm"
                            className="h-11 px-3 md:h-8"
                            title={`${vendor.note}(端点 ${variant.base_url})`}
                            onClick={() =>
                              setEdit({
                                ...edit,
                                form: {
                                  ...edit.form,
                                  name: vendor.name,
                                  adapter: variant.adapter,
                                  base_url: variant.base_url,
                                  keys: [
                                    {
                                      env: vendor.env_suggestion,
                                      weight: edit.form.keys[0]?.weight ?? "1",
                                    },
                                  ],
                                },
                                presetModels: vendor.models,
                                presetVariants: vendor.variants,
                              })
                            }
                          >
                            {vendor.display_name}
                          </Button>
                        );
                      })}
                    </div>
                  );
                })}
                {edit.presetModels.length > 0 && (
                  <p className="text-2xs text-muted-foreground">
                    已选预设建议模型:{edit.presetModels.join("、")}(保存时自动写入能力目录)
                  </p>
                )}
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <FormRow label="供应商名称" htmlFor="p-name">
                  <Input
                    id="p-name"
                    value={edit.form.name}
                    onChange={(e) =>
                      setEdit({ ...edit, form: { ...edit.form, name: e.target.value } })
                    }
                    placeholder="拓扑内唯一,如 deepseek"
                    required
                  />
                </FormRow>
                <FormRow
                  label="适配器类型"
                  htmlFor="p-adapter"
                  help="数据面出站适配器;Claude 生态分支端点选「Anthropic 协议」"
                >
                  <Select
                    value={edit.form.adapter}
                    onValueChange={(v) => {
                      /* 切换适配器时更新预设端点，保留手动填写的 URL。 */
                      const variants = edit.presetVariants;
                      const target = variants.find((x) => x.adapter === v);
                      const current = edit.form.base_url.trim();
                      const swappable =
                        target !== undefined &&
                        (current === "" || variants.some((x) => x.base_url === current));
                      setEdit({
                        ...edit,
                        form: {
                          ...edit.form,
                          adapter: v,
                          base_url: swappable ? target.base_url : edit.form.base_url,
                        },
                      });
                    }}
                  >
                    <SelectTrigger id="p-adapter" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {ADAPTERS.map((a) => (
                        <SelectItem key={a} value={a}>
                          {a} · {ADAPTER_LABELS[a]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormRow>
              </div>
              <FormRow
                label="官方端点(Base URL)"
                htmlFor="p-url"
                help="OpenAI 兼容须含版本段(如 /v1);Anthropic 协议为分支基址(如 …/anthropic)"
              >
                <Input
                  id="p-url"
                  value={edit.form.base_url}
                  onChange={(e) =>
                    setEdit({ ...edit, form: { ...edit.form, base_url: e.target.value } })
                  }
                  placeholder="https://api.example.com/v1"
                  required
                />
              </FormRow>
              <div className="grid gap-3 sm:grid-cols-2">
                <FormRow label="weight(≥1)" htmlFor="p-weight">
                  <Input
                    id="p-weight"
                    value={edit.form.weight}
                    onChange={(e) =>
                      setEdit({ ...edit, form: { ...edit.form, weight: e.target.value } })
                    }
                    inputMode="numeric"
                  />
                </FormRow>
                <FormRow label="priority(≥0,小者优先)" htmlFor="p-priority">
                  <Input
                    id="p-priority"
                    value={edit.form.priority}
                    onChange={(e) =>
                      setEdit({ ...edit, form: { ...edit.form, priority: e.target.value } })
                    }
                    inputMode="numeric"
                  />
                </FormRow>
              </div>

              <div className="space-y-2">
                <p className="text-xs text-muted-foreground">
                  key 环境变量引用(只填 env 名,绝不粘贴真实密钥):
                </p>
                {edit.form.keys.map((k, ki) => (
                  <div
                    key={ki}
                    className="grid grid-cols-[1fr_auto] gap-2 sm:grid-cols-[1fr_6rem_auto]"
                  >
                    <Input
                      value={k.env}
                      onChange={(e) =>
                        setEdit({
                          ...edit,
                          form: {
                            ...edit.form,
                            keys: edit.form.keys.map((x, i) =>
                              i === ki ? { ...x, env: e.target.value } : x,
                            ),
                          },
                        })
                      }
                      className="col-span-2 sm:col-span-1"
                      placeholder="如 DEEPSEEK_API_KEY"
                      aria-label="环境变量名"
                    />
                    <Input
                      value={k.weight}
                      onChange={(e) =>
                        setEdit({
                          ...edit,
                          form: {
                            ...edit.form,
                            keys: edit.form.keys.map((x, i) =>
                              i === ki ? { ...x, weight: e.target.value } : x,
                            ),
                          },
                        })
                      }
                      inputMode="numeric"
                      aria-label="key 权重"
                      placeholder="权重"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-11 md:size-9"
                      aria-label="删除该 key 引用"
                      onClick={() =>
                        setEdit({
                          ...edit,
                          form: { ...edit.form, keys: edit.form.keys.filter((_, i) => i !== ki) },
                        })
                      }
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-11 px-4 md:h-8"
                  onClick={() =>
                    setEdit({
                      ...edit,
                      form: { ...edit.form, keys: [...edit.form.keys, { env: "", weight: "1" }] },
                    })
                  }
                >
                  <Plus className="mr-1 size-3.5" />
                  添加 key 引用
                </Button>
              </div>

              <SheetFooter className="sm:flex-row sm:items-center sm:justify-end">
                {submitBlockedHint(edit, draft) !== "" && (
                  <p className="mr-auto text-sm text-destructive">
                    {submitBlockedHint(edit, draft)}
                  </p>
                )}
                <Button
                  type="button"
                  variant="outline"
                  className="h-11 w-full sm:h-9 sm:w-auto"
                  onClick={() => setEdit(null)}
                >
                  取消
                </Button>
                <Button
                  type="submit"
                  className="h-11 w-full sm:h-9 sm:w-auto"
                  disabled={submitBlockedHint(edit, draft) !== ""}
                >
                  {edit.original === null ? "添加" : "保存修改"}
                </Button>
              </SheetFooter>
            </form>
          )}
        </BottomSheetContent>
      </Sheet>

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDelete?.type === "route"
                ? `将删除路由别名 ${pendingDelete.value} 及其全部候选(保存后生效)。`
                : `将从拓扑中移除供应商 ${pendingDelete?.value ?? ""}(保存后生效)。`}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (pendingDelete?.type === "route") {
                  const alias = pendingDelete.value;
                  setPendingDelete(null);
                  mutate((d) => ({ ...d, routes: d.routes.filter((r) => r.alias !== alias) }));
                } else {
                  confirmDeleteProvider();
                }
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 悬空引用提示 */}
      <AlertDialog
        open={deleteBlocked !== ""}
        onOpenChange={(open) => !open && setDeleteBlocked("")}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>无法删除</AlertDialogTitle>
            <AlertDialogDescription>{deleteBlocked}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction>知道了</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
