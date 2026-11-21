import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppShell } from "./app-shell";

const { logout, toggle, userState } = vi.hoisted(() => ({
  logout: vi.fn<() => Promise<void>>(),
  toggle: vi.fn(),
  userState: { current: { subject: "sub-alice", username: "alice", role: "ADMIN" } },
}));

vi.mock("@/lib/auth-store", () => ({ useAuth: () => ({ user: userState.current, logout }) }));
vi.mock("@/lib/theme-store", () => ({ useTheme: () => ({ theme: "light", toggle }) }));

function renderShell() {
  return render(
    <MemoryRouter initialEntries={["/wallet"]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/wallet" element={<div>钱包内容</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  userState.current = { subject: "sub-alice", username: "alice", role: "ADMIN" };
  logout.mockResolvedValue(undefined);
});

describe("AppShell", () => {
  it("管理员看到全部导航、子路由与账户信息", () => {
    renderShell();

    expect(screen.getByText("钱包内容")).toBeInTheDocument();
    for (const label of ["钱包", "支付", "价目管理", "渠道拓扑", "密钥绑定"]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
    expect(screen.getAllByText("alice · 管理员").length).toBeGreaterThan(0);
  });

  it("普通用户看不到管理员导航", () => {
    userState.current = { subject: "sub-bob", username: "bob", role: "USER" };
    renderShell();

    expect(screen.getAllByText("钱包").length).toBeGreaterThan(0);
    expect(screen.queryByText("价目管理")).toBeNull();
    expect(screen.queryByText("渠道拓扑")).toBeNull();
    expect(screen.queryByText("密钥绑定")).toBeNull();
  });

  it("退出登录调用 logout", async () => {
    renderShell();

    await userEvent.click(screen.getByRole("button", { name: /退出登录/ }));

    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
  });
});
