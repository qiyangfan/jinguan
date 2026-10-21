/* 验证 key ID 原样提交；空白输入由页面拦截。 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function loadModules() {
  vi.resetModules();
  return Promise.all([import("./api"), import("./keys-api")]);
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("key 绑定请求形态", () => {
  it.each([
    "k-1", // 平台自定义短标识
    "mk-test-1", // 非 UUID、带 mk- 前缀的合法标识
    "0198c7a2-3f6b-7a1e-9d4c-2b8e5f1a6d30", // UUID 形态标识
    "Mixed.Case_Prefix-42", // 大小写与符号混合标识
  ])("Key ID %s 按原值提交", async (rawId) => {
    fetchMock.mockImplementation(async () => jsonResponse(201, { keyId: rawId, userId: "1" }));

    const [, keysApi] = await loadModules();
    await keysApi.bindKey({ keyId: rawId, userId: 1 });

    // openapi-fetch 将 Request 传给 fetch。
    const [req] = fetchMock.mock.calls[0] as [Request];
    expect(new URL(req.url).pathname).toBe("/admin/key-bindings");
    // API 层原样提交；空白校验由页面负责。
    expect(await req.json()).toEqual({ keyId: rawId, userId: 1 });
  });
});
