/* API 客户端(锦官控制面):令牌策略=单 access JWT 存 localStorage(控制面单服务,
   暂无刷新链路;双 token/单飞刷新随认证演进升级,401 统一清态回落登录)。
   错误一律转译为后端 ProblemDetail.detail 文案,原始响应不直接抛给用户。 */

const BASE = "";
const TOKEN_KEY = "jinguan.access";

let accessToken: string | null = localStorage.getItem(TOKEN_KEY);
const sessionClearedListeners = new Set<() => void>();

export function onSessionCleared(cb: () => void): () => void {
  sessionClearedListeners.add(cb);
  return () => sessionClearedListeners.delete(cb);
}

export function setAccessToken(token: string): void {
  accessToken = token;
  localStorage.setItem(TOKEN_KEY, token);
}

export function getAccessToken(): string | null {
  return accessToken ?? localStorage.getItem(TOKEN_KEY);
}

export function clearAccessToken(): void {
  const had = accessToken !== null;
  accessToken = null;
  localStorage.removeItem(TOKEN_KEY);
  if (had) sessionClearedListeners.forEach((cb) => cb());
}

function doFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init?.headers,
    },
  });
}

async function toStatusError(resp: Response): Promise<Error & { status?: number }> {
  // 优先取 RFC 7807 {detail} 文案,原始 JSON 体不直接抛给用户
  let detail = `HTTP ${resp.status}`;
  try {
    const body: unknown = await resp.json();
    if (body && typeof body === "object" && "detail" in body) {
      const d = (body as { detail: unknown }).detail;
      detail = typeof d === "string" ? d : detail;
    }
  } catch {
    // 响应非 JSON:保留 HTTP 状态文案
  }
  const e = new Error(detail) as Error & { status?: number };
  e.status = resp.status;
  return e;
}

async function unwrap<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    throw await toStatusError(resp);
  }
  return (await resp.json()) as T;
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
  del: (path: string) => doFetch(path, { method: "DELETE" }).then((r) => unwrap<void>(r)),
};
