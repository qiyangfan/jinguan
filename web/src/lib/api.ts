/* API 客户端(锦官控制面):openapi-fetch 类型化传输,paths 字面量/参数/请求体
   以 openapi.json 契约单源编译期核对(契约漂移由 CI openapi 漂移门禁兜底)。
   会话语义与转译层不变:access token 仅存内存(页面刷新即失,XSS 敞口 ≤ access TTL),
   refresh 凭证走 httpOnly cookie(浏览器同站自动携带,JS 不可读);
   401 统一处理:单飞刷新(并发请求只触发一次 refresh)→ 重放原请求 → 失败清态回落登录。
   错误转译单源:后端中文 detail 原样示人,其余按 HTTP 状态回落中文兜底文案(见 HTTP_MESSAGES)。 */

import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./api-types.gen";

// 同源相对请求:浏览器取真实 origin(与裸相对路径同语义);Node(测试)缺 location,
// Request 构造需绝对 URL,回退 http://localhost 仅测试态生效
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

/** 登录/注册/刷新成功后写入内存态 access(不落 localStorage)。 */
export function setAccessToken(token: string): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** 本地清态(登出/刷新失败):清内存 access 并广播会话失效。 */
export function clearAccessToken(): void {
  const had = accessToken !== null;
  accessToken = null;
  if (had) sessionClearedListeners.forEach((cb) => cb());
}

/** 静默续期:凭 httpOnly refresh cookie 换发新 access(服务端轮换新 refresh)。 */
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

/** 单飞:并发 401 只触发一次 /auth/refresh。 */
function refreshOnce(): Promise<boolean> {
  refreshing ??= refreshSession().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

/** 会话 middleware:Bearer 注入;401(认证端点除外)刷新后重放一次,重放仍 401 即清态。 */
const sessionMiddleware: Middleware = {
  onRequest({ request }) {
    if (accessToken) request.headers.set("Authorization", `Bearer ${accessToken}`);
    return request;
  },
  async onResponse({ request, response }) {
    if (response.status !== 401) return;
    const path = new URL(request.url, "http://localhost").pathname;
    if (path.startsWith("/auth/")) return; // 认证端点自身错误不进刷新链路

    const ok = await refreshOnce();
    if (!ok) {
      clearAccessToken();
      return; // 维持 401,由 unwrap 统一转译
    }
    // 重放一次:仅换新 token,其余(method/url/body/signal)原样;裸 fetch 不再进本 middleware
    const headers = new Headers(request.headers);
    headers.set("Authorization", `Bearer ${accessToken}`);
    const replay = await fetch(new Request(request, { headers }));
    if (replay.status === 401) clearAccessToken();
    return replay;
  },
};

client.use(sessionMiddleware);

/** HTTP 状态 → 中文兜底文案(detail 缺失或非中文时使用;后端 Spring Security 拒绝无响应体,也走这里)。 */
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

/**
 * detail → 用户可读文案:空/非字符串或纯 ASCII(含英文句子与错误码)一律回落
 * HTTP_MESSAGES;含 CJK 视为后端中文自然语原样放行。已核对 jinguan 后端
 * ProblemDetail.detail 全部为中文自然语(GlobalExceptionHandler + 各领域异常),
 * 无大写下划线错误码形态,故不做错误码映射。
 */
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
  // 原始 detail 留诊断用,不直接示人
  if (typeof detail === "string" && detail !== e.message) e.raw = detail;
  return e;
}

/** openapi-fetch 调用结果 → 领域返回值:错误统一转译,空体(204/void)短路为 undefined。 */
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
    // 主动取消原样透传,不冒充网络错误;fetch reject 的 TypeError 文案是浏览器英文
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    if (err instanceof TypeError) {
      const e = new Error("网络连接失败,请检查网络后重试") as Error & { raw?: string };
      e.raw = err.message;
      throw e;
    }
    throw err;
  }
}

/** PUT 并取回响应 ETag(拓扑整包替换;失败走统一错误转译)。 */
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

/** 后端 RFC 7807 {detail} → 可读文案(未知形态回落通用文案)。 */
export function errorDetail(err: unknown): string {
  return err instanceof Error && err.message ? err.message : "请求失败";
}
