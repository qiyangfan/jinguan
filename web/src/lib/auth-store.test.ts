/* 验证会话恢复、登录、登出和续期失败后的清态。 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 构造客户端可解码的假 JWT。 */
function b64uJson(o: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(o));
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function makeJwt(payload: Record<string, unknown>): string {
  return `${b64uJson({ alg: "HS256", typ: "JWT" })}.${b64uJson(payload)}.sig`;
}

async function loadStore() {
  vi.resetModules();
  const mod = await import("./auth-store");
  return mod.useAuth;
}

/** 统一从 fetch 参数中读取请求路径。 */
function pathOf(input: RequestInfo | URL): string {
  return new URL(input instanceof Request ? input.url : String(input), "http://localhost").pathname;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("auth-store 会话行为", () => {
  it("bootstrap:凭 refresh cookie 恢复会话——页面刷新不掉登录", async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      if (String(input) === "/auth/refresh") {
        return jsonResponse(200, { token: makeJwt({ uid: 7, sub: "alice", role: "ADMIN" }) });
      }
      throw new Error("unexpected path: " + String(input));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();

    expect(useAuth.getState().booted).toBe(true);
    expect(useAuth.getState().user).toEqual({ uid: 7, username: "alice", role: "ADMIN" });
  });

  it("bootstrap 失败:回落 guest(booted=true 且 user=null)", async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      if (String(input) === "/auth/refresh") return jsonResponse(401, { detail: "无 refresh" });
      throw new Error("unexpected path: " + String(input));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();

    expect(useAuth.getState().booted).toBe(true);
    expect(useAuth.getState().user).toBeNull();
  });

  it("logout:调用 /auth/logout 并清空本地用户态", async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/auth/refresh") {
        return jsonResponse(200, { token: makeJwt({ uid: 7, sub: "alice", role: "USER" }) });
      }
      if (path === "/auth/logout") return new Response(null, { status: 204 });
      throw new Error("unexpected path: " + path + " method=" + (init?.method ?? "GET"));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();
    expect(useAuth.getState().user).not.toBeNull();

    await useAuth.getState().logout();
    expect(useAuth.getState().user).toBeNull();
    expect(fetchMock.mock.calls.some(([u]) => String(u) === "/auth/logout")).toBe(true);
  });

  it("login:写内存 token 并解码用户(幂等 ref 等业务请求随即携带凭证)", async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      if (pathOf(input) === "/auth/login") {
        return jsonResponse(200, { token: makeJwt({ uid: 3, sub: "bob", role: "USER" }) });
      }
      throw new Error("unexpected path: " + String(input));
    });

    const useAuth = await loadStore();
    await useAuth.getState().login("bob", "password-123");

    expect(useAuth.getState().user).toEqual({ uid: 3, username: "bob", role: "USER" });
  });
});

it("会话失效(api 层 401 刷新失败)→ 反向通知回落 guest", async () => {
  fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
    if (String(input) === "/auth/refresh") {
      return jsonResponse(200, { token: makeJwt({ uid: 7, sub: "alice", role: "USER" }) });
    }
    if (pathOf(input) === "/wallet/balance") return jsonResponse(401, { detail: "denied" });
    throw new Error("unexpected path: " + String(input));
  });

  const useAuth = await loadStore();
  await useAuth.getState().bootstrap();
  expect(useAuth.getState().user).not.toBeNull();

  // 清除 token 时，api 层回调也应清除用户态。
  const { clearAccessToken } = await import("./api");
  clearAccessToken();
  expect(useAuth.getState().user).toBeNull();
});
