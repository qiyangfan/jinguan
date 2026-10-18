/* api 层会话行为测试:401 单飞刷新(并发只触发一次 /auth/refresh)、刷新失败清态回落、
   access 仅内存(清态后不携带 Authorization)。openapi-fetch 经全局 fetch 发请求,
   fetch 以 stub 注入即可拦截(Request 对象形态),模块态用 resetModules 隔离。 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 客户端只 base64url 解码 payload(签名无参与),借此构造可解码的假 JWT。 */
/** base64url(JSON)——TextEncoder+btoa 全平台等价(Node/Browser 同一实现)。 */
function b64uJson(o: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(o));
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function makeJwt(payload: Record<string, unknown>): string {
  return `${b64uJson({ alg: "HS256", typ: "JWT" })}.${b64uJson(payload)}.sig`;
}

async function loadApi() {
  vi.resetModules();
  return import("./api");
}

/** openapi-fetch 的请求形态:fetch(Request) 单参;路径取 url 的 pathname,头从 Request 读。 */
function pathOf(input: RequestInfo | URL): string {
  return new URL(input instanceof Request ? input.url : String(input), "http://localhost").pathname;
}

function headersOf(input: RequestInfo | URL): Headers {
  return input instanceof Request ? input.headers : new Headers();
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("api 会话链路", () => {
  it("401 单飞:并发 3 个请求只触发一次 /auth/refresh,且全部重放成功", async () => {
    let refreshCount = 0;
    let refreshed = false;
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = pathOf(input);
      if (path === "/auth/refresh") {
        refreshCount++;
        refreshed = true;
        return jsonResponse(200, { token: makeJwt({ uid: 1, sub: "u1", role: "USER" }) });
      }
      if (path === "/wallet/balance") {
        return refreshed
          ? jsonResponse(200, { userId: 1, balanceCredits: 5 })
          : jsonResponse(401, { detail: "token 过期" });
      }
      throw new Error("unexpected path: " + path);
    });

    const { client, unwrap } = await loadApi();
    const call = () => unwrap<{ balanceCredits: number }>(client.GET("/wallet/balance"));
    const results = await Promise.all([call(), call(), call()]);

    expect(refreshCount).toBe(1); // 并发 401 只触发一次刷新
    expect(results.map((r) => r.balanceCredits)).toEqual([5, 5, 5]); // 全部重放成功
  });

  it("刷新失败:清态回落(广播 sessionCleared),原请求不无限重试", async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = pathOf(input);
      if (path === "/auth/refresh") return jsonResponse(401, { detail: "refresh 无效" });
      if (path === "/wallet/balance") return jsonResponse(401, { detail: "token 过期" });
      throw new Error("unexpected path: " + path);
    });

    const { client, unwrap, onSessionCleared, setAccessToken } = await loadApi();
    const onCleared = vi.fn();
    onSessionCleared(onCleared);
    setAccessToken(makeJwt({ uid: 1, sub: "u1", role: "USER" }));

    await expect(unwrap(client.GET("/wallet/balance"))).rejects.toMatchObject({ status: 401 });
    expect(onCleared).toHaveBeenCalledTimes(1); // 清态广播恰好一次
  });

  it("登录 401 属认证端点自身错误:不得触发 /auth/refresh", async () => {
    let refreshCount = 0;
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = pathOf(input);
      if (path === "/auth/refresh") {
        refreshCount++;
        return jsonResponse(200, { token: makeJwt({ uid: 1, sub: "u1", role: "USER" }) });
      }
      if (path === "/auth/login") return jsonResponse(401, { detail: "用户名或密码错误" });
      throw new Error("unexpected path: " + path);
    });

    const { client, unwrap } = await loadApi();
    await expect(
      unwrap(client.POST("/auth/login", { body: { username: "a", password: "b" } })),
    ).rejects.toMatchObject({ status: 401 });
    expect(refreshCount).toBe(0);
  });

  it("重放后仍 401:熔断(不再第二次刷新)并清态回落", async () => {
    let refreshCount = 0;
    let balanceCalls = 0;
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = pathOf(input);
      if (path === "/auth/refresh") {
        refreshCount++;
        return jsonResponse(200, { token: makeJwt({ uid: 1, sub: "u1", role: "USER" }) });
      }
      if (path === "/wallet/balance") {
        balanceCalls++;
        return jsonResponse(401, { detail: "denied" });
      }
      throw new Error("unexpected path: " + path);
    });

    const { client, unwrap, onSessionCleared, setAccessToken } = await loadApi();
    const onCleared = vi.fn();
    onSessionCleared(onCleared);
    setAccessToken(makeJwt({ uid: 1, sub: "u1", role: "USER" }));

    await expect(unwrap(client.GET("/wallet/balance"))).rejects.toMatchObject({ status: 401 });
    expect(refreshCount).toBe(1); // 恰一次刷新
    expect(balanceCalls).toBe(2); // 原始 + 重放各一次,无二次循环
    expect(onCleared).toHaveBeenCalledTimes(1); // 重放仍 401 → 清态回落
  });

  it("access 仅内存:清态后的请求不携带 Authorization 头", async () => {
    let lastAuthHeader: string | null = null;
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const path = pathOf(input);
      if (path === "/wallet/balance") {
        lastAuthHeader = headersOf(input).get("Authorization");
        return jsonResponse(401, { detail: "denied" });
      }
      if (path === "/auth/refresh") return jsonResponse(401, { detail: "refresh 无效" });
      throw new Error("unexpected path: " + path);
    });

    const { client, unwrap, setAccessToken, clearAccessToken } = await loadApi();
    setAccessToken(makeJwt({ uid: 1, sub: "u1", role: "USER" }));
    await unwrap(client.GET("/wallet/balance")).catch(() => undefined);
    expect(lastAuthHeader).toMatch(/^Bearer /);

    clearAccessToken();
    await unwrap(client.GET("/wallet/balance")).catch(() => undefined);
    expect(lastAuthHeader).toBeNull(); // 内存态已清,无凭证外泄(Headers.get 无头即 null)
  });
});
