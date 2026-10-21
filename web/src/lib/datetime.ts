const pad = (n: number): string => String(n).padStart(2, "0");

function toDate(v: string | Date | null | undefined): Date | null {
  if (v == null || v === "") return null;
  const d = v instanceof Date ? v : new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatDateTime(
  v: string | Date | null | undefined,
  opts?: { seconds?: boolean },
): string {
  const d = toDate(v);
  if (!d) return "—";
  const base = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return opts?.seconds ? `${base}:${pad(d.getSeconds())}` : base;
}

export function formatDate(v: string | Date | null | undefined): string {
  const d = toDate(v);
  return d ? `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` : "—";
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return "—";
  if (ms < 1000) return `${Math.min(Math.round(ms), 999)}ms`;
  const s = ms / 1000;
  if (s < 60) {
    // 不显示 60s，避免与下一档 1m0s 重复。
    const v = Math.min(s < 10 ? Number(s.toFixed(1)) : Math.round(s), 59);
    return `${v}s`;
  }
  const m = Math.floor(s / 60);
  const sec = Math.round(s % 60);
  return sec === 60 ? `${m + 1}m0s` : `${m}m${sec}s`;
}

export function formatNumber(n: number | string | null | undefined): string {
  if (n == null || n === "") return "—";
  const num = typeof n === "string" ? Number(n) : n;
  return Number.isNaN(num) ? String(n) : num.toLocaleString("zh-CN");
}
