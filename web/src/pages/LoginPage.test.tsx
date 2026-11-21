import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./LoginPage";

let fetchMock: ReturnType<typeof vi.fn<typeof fetch>>;

beforeEach(() => {
  fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe("LoginPage", () => {
  it("统一身份已配置时只展示虎符入口", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ enabled: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    render(<LoginPage />);

    const link = await screen.findByRole("link", { name: "使用统一身份登录" });
    expect(link).toHaveAttribute("href", "/auth/oidc/start");
    expect(screen.queryByLabelText("密码")).not.toBeInTheDocument();
  });

  it("未配置时提示联系运维且不出现本地账密", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ enabled: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    render(<LoginPage />);

    await waitFor(() => expect(screen.getByText(/统一身份未配置/)).toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "使用统一身份登录" })).not.toBeInTheDocument();
  });
});
