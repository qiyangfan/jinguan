import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./LoginPage";

const { navigate, login, register } = vi.hoisted(() => ({
  navigate: vi.fn(),
  login: vi.fn<(username: string, password: string) => Promise<void>>(),
  register: vi.fn<(username: string, password: string) => Promise<void>>(),
}));

vi.mock("react-router", () => ({ useNavigate: () => navigate }));
vi.mock("@/lib/auth-store", () => ({ useAuth: () => ({ login, register }) }));

beforeEach(() => {
  vi.clearAllMocks();
  login.mockResolvedValue(undefined);
  register.mockResolvedValue(undefined);
});

describe("LoginPage", () => {
  it("登录成功后跳转钱包页", async () => {
    render(<LoginPage />);

    await userEvent.type(screen.getByLabelText("用户名"), "alice");
    await userEvent.type(screen.getByLabelText("密码"), "password-123");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    await waitFor(() => expect(login).toHaveBeenCalledWith("alice", "password-123"));
    expect(navigate).toHaveBeenCalledWith("/wallet");
  });

  it("登录失败时显示错误信息且不跳转", async () => {
    login.mockRejectedValue(new Error("密码错误"));
    render(<LoginPage />);

    await userEvent.type(screen.getByLabelText("用户名"), "alice");
    await userEvent.type(screen.getByLabelText("密码"), "password-123");
    await userEvent.click(screen.getByRole("button", { name: "登录" }));

    expect(await screen.findByText("密码错误")).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  it("切换到注册模式后调用 register", async () => {
    render(<LoginPage />);

    await userEvent.click(screen.getByRole("button", { name: "没有账号?去注册" }));
    await userEvent.type(screen.getByLabelText("用户名"), "bob");
    await userEvent.type(screen.getByLabelText("密码"), "password-123");
    await userEvent.click(screen.getByRole("button", { name: "注册并登录" }));

    await waitFor(() => expect(register).toHaveBeenCalledWith("bob", "password-123"));
    expect(navigate).toHaveBeenCalledWith("/wallet");
  });
});
