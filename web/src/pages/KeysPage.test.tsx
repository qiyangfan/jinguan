import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { bindKey } from "@/lib/keys-api";
import { renderWithQuery } from "@/test/render";

import KeysPage from "./KeysPage";

vi.mock("@/lib/keys-api", () => ({ bindKey: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(bindKey).mockResolvedValue({ keyId: "k-1", userId: "7" });
});

describe("KeysPage", () => {
  it("提交绑定并显示成功信息", async () => {
    renderWithQuery(<KeysPage />);

    await userEvent.type(screen.getByRole("textbox", { name: /密钥标识/ }), "k-1");
    await userEvent.type(screen.getByLabelText("用户 ID"), "7");
    await userEvent.click(screen.getByRole("button", { name: "绑定" }));

    await waitFor(() => expect(bindKey).toHaveBeenCalledWith({ keyId: "k-1", userId: 7 }));
    expect(await screen.findByText("密钥 k-1 已绑定到用户(ID 7)")).toBeInTheDocument();
  });

  it("绑定失败时显示错误", async () => {
    vi.mocked(bindKey).mockRejectedValue(new Error("密钥已吊销"));
    renderWithQuery(<KeysPage />);

    await userEvent.type(screen.getByRole("textbox", { name: /密钥标识/ }), "k-2");
    await userEvent.type(screen.getByLabelText("用户 ID"), "8");
    await userEvent.click(screen.getByRole("button", { name: "绑定" }));

    expect(await screen.findByText("密钥已吊销")).toBeInTheDocument();
  });
});
