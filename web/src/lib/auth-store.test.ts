/* 验证会话恢复、登录、登出和续期失败后的清态。 */

import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from "vitest";

function jsonResponse(status: number, body: unknown): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
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

let fetchMock: Mock<typeof fetch>;

beforeEach(() => {
  fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("auth-store 会话行为", () => {
  it("bootstrap:凭 refresh cookie 恢复会话——页面刷新不掉登录", async () => {
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      if (pathOf(input) === "/auth/refresh") {
        return jsonResponse(200, {
          token: makeJwt({ sub: "sub-alice", username: "alice", role: "ADMIN" }),
        });
      }
      throw new Error("unexpected path: " + pathOf(input));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();

    expect(useAuth.getState().booted).toBe(true);
    expect(useAuth.getState().user).toEqual({
      subject: "sub-alice",
      username: "alice",
      role: "ADMIN",
    });
  });

  it("bootstrap 失败:回落 guest(booted=true 且 user=null)", async () => {
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      if (pathOf(input) === "/auth/refresh") return jsonResponse(401, { detail: "无 refresh" });
      throw new Error("unexpected path: " + pathOf(input));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();

    expect(useAuth.getState().booted).toBe(true);
    expect(useAuth.getState().user).toBeNull();
  });

  it("logout:调用 /auth/logout 并清空本地用户态", async () => {
    fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const path = pathOf(input);
      if (path === "/auth/refresh") {
        return jsonResponse(200, {
          token: makeJwt({ sub: "sub-alice", username: "alice", role: "USER" }),
        });
      }
      if (path === "/auth/logout") return Promise.resolve(new Response(null, { status: 204 }));
      throw new Error("unexpected path: " + path + " method=" + (init?.method ?? "GET"));
    });

    const useAuth = await loadStore();
    await useAuth.getState().bootstrap();
    expect(useAuth.getState().user).not.toBeNull();

    await useAuth.getState().logout();
    expect(useAuth.getState().user).toBeNull();
    expect(fetchMock.mock.calls.some(([u]) => pathOf(u) === "/auth/logout")).toBe(true);
  });
});

it("会话失效(api 层 401 刷新失败)→ 反向通知回落 guest", async () => {
  fetchMock.mockImplementation((input: RequestInfo | URL) => {
    if (pathOf(input) === "/auth/refresh") {
      return jsonResponse(200, {
        token: makeJwt({ sub: "sub-alice", username: "alice", role: "USER" }),
      });
    }
    if (pathOf(input) === "/wallet/balance") return jsonResponse(401, { detail: "denied" });
    throw new Error("unexpected path: " + pathOf(input));
  });

  const useAuth = await loadStore();
  await useAuth.getState().bootstrap();
  expect(useAuth.getState().user).not.toBeNull();

  // 清除 token 时，api 层回调也应清除用户态。
  const { clearAccessToken } = await import("./api");
  clearAccessToken();
  expect(useAuth.getState().user).toBeNull();
});
