/* API 客户端(锦官控制面):access token 仅存内存(页面刷新即失,XSS 敞口 ≤ access TTL),
   refresh 凭证走 httpOnly cookie(浏览器同源自动携带,JS 不可读)。
   401 统一处理:单飞刷新(并发请求只触发一次 refresh)→ 重放原请求 → 失败清态回落登录。
   错误转译单源:后端中文 detail 原样示人,其余按 HTTP 状态回落中文兜底文案(见 HTTP_MESSAGES)。 */

const BASE = "";

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const sessionClearedListeners = new Set<() => void>();

export function onSessionCleared(cb: () => void): () => void {
  sessionClearedListeners.add(cb);
  return () => sessionClearedListeners.delete(cb);
}

/** 登录/注册/刷新成功后写入内存态 access(不再落 localStorage)。 */
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
    const resp = await fetch(`${BASE}/auth/refresh`, { method: "POST" });
    if (!resp.ok) return false;
    const data = (await resp.json()) as { token?: unknown };
    if (typeof data.token !== "string") return false;
    accessToken = data.token;
    return true;
  } catch {
    return false;
  }
}

async function doFetch(path: string, init?: RequestInit, retried = false): Promise<Response> {
  let resp: Response;
  try {
    resp = await fetch(`${BASE}${path}`, {
      ...init,
      headers: {
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...init?.headers,
      },
    });
  } catch (err) {
    // 主动取消原样透传,不冒充网络错误;fetch reject 的 TypeError 文案是浏览器英文
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    const e = new Error("网络连接失败,请检查网络后重试") as Error & { raw?: string };
    e.raw = err instanceof Error ? err.message : String(err);
    throw e;
  }
  // 认证端点自身(login/refresh/logout)的错误直接上抛,不进刷新链路
  if (resp.status === 401 && !path.startsWith("/auth/")) {
    if (!retried) {
      // 单飞刷新:并发 401 只触发一次 /auth/refresh
      refreshing ??= refreshSession().finally(() => {
        refreshing = null;
      });
      const ok = await refreshing;
      if (ok) return doFetch(path, init, true);
    }
    // 刷新失败或重放仍 401:清态广播回落登录,避免持死 token 空转
    clearAccessToken();
  }
  return resp;
}

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

async function toStatusError(resp: Response): Promise<Error & { status?: number; raw?: string }> {
  const fallback = HTTP_MESSAGES[resp.status] ?? `请求失败(HTTP ${resp.status})`;
  let detail: unknown;
  try {
    const body: unknown = await resp.json();
    if (body && typeof body === "object" && "detail" in body) {
      detail = (body as { detail: unknown }).detail;
    }
  } catch {
    // 响应非 JSON(如无体的 401/403):走 HTTP 状态兜底文案
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

async function unwrap<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    throw await toStatusError(resp);
  }
  // void 端点(如 DELETE 价目返回 200 空体)无 JSON 可解析,短路避免 SyntaxError
  if (resp.status === 204) return undefined as T;
  const text = await resp.text();
  if (text === "") return undefined as T;
  return JSON.parse(text) as T;
}

/** 后端 RFC 7807 {detail} → 可读文案(未知形态回落通用文案)。 */
export function errorDetail(err: unknown): string {
  return err instanceof Error && err.message ? err.message : "请求失败";
}

export const api = {
  get: <T>(path: string) => doFetch(path).then((r) => unwrap<T>(r)),
  post: <T>(path: string, body?: unknown) =>
    doFetch(path, {
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body),
    }).then((r) => unwrap<T>(r)),
  put: <T>(path: string, body?: unknown) =>
    doFetch(path, {
      method: "PUT",
      body: body === undefined ? undefined : JSON.stringify(body),
    }).then((r) => unwrap<T>(r)),
  /** PUT 并取回响应 ETag(拓扑整包替换;失败走统一错误转译)。 */
  putEtag: async (path: string, body: unknown) => {
    const resp = await doFetch(path, { method: "PUT", body: JSON.stringify(body) });
    if (!resp.ok) throw await toStatusError(resp);
    return resp.headers.get("ETag") ?? "";
  },
  del: (path: string) => doFetch(path, { method: "DELETE" }).then((r) => unwrap<void>(r)),
};
