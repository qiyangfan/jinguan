/* OpenAPI 类型化客户端和会话处理：access 只存内存，refresh 使用 httpOnly cookie；401 时单飞续期并重放一次。 */

import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./api-types.gen";

// Node 测试环境没有 location。
export const client = createClient<paths>({
  baseUrl: globalThis.location?.origin ?? "http://localhost",
});

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const sessionClearedListeners = new Set<() => void>();

export function onSessionCleared(cb: () => void): () => void {
  sessionClearedListeners.add(cb);
  return () => sessionClearedListeners.delete(cb);
}

export function setAccessToken(token: string): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function clearAccessToken(): void {
  const had = accessToken !== null;
  accessToken = null;
  if (had) sessionClearedListeners.forEach((cb) => cb());
}

export async function refreshSession(): Promise<boolean> {
  try {
    const resp = await fetch("/auth/refresh", { method: "POST" });
    if (!resp.ok) return false;
    const data = (await resp.json()) as { token?: unknown };
    if (typeof data.token !== "string") return false;
    accessToken = data.token;
    return true;
  } catch {
    return false;
  }
}

/** 并发请求共用同一次续期。 */
function refreshOnce(): Promise<boolean> {
  refreshing ??= refreshSession().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

const sessionMiddleware: Middleware = {
  onRequest({ request }) {
    if (accessToken) request.headers.set("Authorization", `Bearer ${accessToken}`);
    return request;
  },
  async onResponse({ request, response }) {
    if (response.status !== 401) return;
    const path = new URL(request.url, "http://localhost").pathname;
    if (path.startsWith("/auth/")) return; // 认证端点不触发续期。

    const ok = await refreshOnce();
    if (!ok) {
      clearAccessToken();
      return; // 保留 401，由 unwrap 转译错误。
    }
    // 重放时只替换 token，避免再次进入 middleware。
    const headers = new Headers(request.headers);
    headers.set("Authorization", `Bearer ${accessToken}`);
    const replay = await fetch(new Request(request, { headers }));
    if (replay.status === 401) clearAccessToken();
    return replay;
  },
};

client.use(sessionMiddleware);

const HTTP_MESSAGES: Record<number, string> = {
  400: "请求有误,请检查后重试",
  401: "登录已过期,请重新登录",
  403: "没有执行此操作的权限",
  404: "目标不存在或已被删除",
  409: "内容有冲突,请刷新后重试",
  422: "提交的内容未通过校验,请检查后重试",
  429: "操作过于频繁,请稍后再试",
  500: "服务开小差了,请稍后重试",
  502: "服务暂时不可用,请稍后重试",
  503: "服务暂时不可用,请稍后重试",
  504: "服务响应超时,请稍后重试",
};

const CJK_RE = /[\u3400-\u4dbf\u4e00-\u9fff]/;

/** 空或非中文 detail 使用状态码对应的提示。 */
function readableDetail(detail: unknown, fallback: string): string {
  if (typeof detail !== "string" || detail.trim() === "") return fallback;
  return CJK_RE.test(detail) ? detail : fallback;
}

async function toStatusError(
  resp: Response,
  body: unknown,
): Promise<Error & { status?: number; raw?: string }> {
  const fallback = HTTP_MESSAGES[resp.status] ?? `请求失败(HTTP ${resp.status})`;
  let detail: unknown;
  if (body && typeof body === "object" && "detail" in body) {
    detail = (body as { detail: unknown }).detail;
  }
  const e = new Error(readableDetail(detail, fallback)) as Error & {
    status?: number;
    raw?: string;
  };
  e.status = resp.status;
  // 保留未展示的原始 detail，供诊断使用。
  if (typeof detail === "string" && detail !== e.message) e.raw = detail;
  return e;
}

export async function unwrap<T>(
  call: Promise<{
    data?: unknown;
    error?: unknown;
    response: Response;
  }>,
): Promise<T> {
  try {
    const { data, error, response } = await call;
    if (!response.ok) throw await toStatusError(response, error);
    return data as T;
  } catch (err) {
    // 保留主动取消错误，不将其包装为网络错误。
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    if (err instanceof TypeError) {
      const e = new Error("网络连接失败,请检查网络后重试") as Error & { raw?: string };
      e.raw = err.message;
      throw e;
    }
    throw err;
  }
}

export async function unwrapEtag(
  call: Promise<{
    data?: unknown;
    error?: unknown;
    response: Response;
  }>,
): Promise<string> {
  const { error, response } = await call;
  if (!response.ok) throw await toStatusError(response, error);
  return response.headers.get("ETag") ?? "";
}

export function errorDetail(err: unknown): string {
  return err instanceof Error && err.message ? err.message : "请求失败";
}
