/* 绑定契约客户端侧测试(fix-api-key-binding-identifiers):Key ID 按原值传递——
   不做大小写转换、UUID 校验或前缀过滤(k-1、mk-test-1 等平台自定义标识合法);
   空白输入在页面层被 trim 拦截,不发起请求。fetch 以 stub 注入,模块态用 resetModules 隔离。 */

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

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/admin/key-bindings");
    // 客户端 API 层不得做任何格式改写(大小写/UUID/前缀过滤):提交体与入参逐字节一致;
    // 空白拦截在页面层(提交前 trim + 纯空白禁用按钮),不属本层职责
    expect(JSON.parse(String(init.body))).toEqual({ keyId: rawId, userId: 1 });
  });
});
