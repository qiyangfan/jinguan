import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fetchUsage } from "@/lib/usage-api";
import { adminCredit, fetchBalance, fetchEntries } from "@/lib/wallet-api";
import { renderWithQuery } from "@/test/render";

import WalletPage from "./WalletPage";

vi.mock("@/lib/auth-store", () => ({
  useAuth: () => ({ user: { subject: "sub-admin", username: "admin", role: "ADMIN" } }),
}));
vi.mock("@/lib/wallet-api", () => ({
  adminCredit: vi.fn(),
  adminDebit: vi.fn(),
  fetchBalance: vi.fn(),
  fetchEntries: vi.fn(),
}));
vi.mock("@/lib/usage-api", () => ({ fetchUsage: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchBalance).mockResolvedValue({ subject: "sub-u1", balanceCredits: "5000000000" });
  vi.mocked(fetchEntries).mockResolvedValue([]);
  vi.mocked(fetchUsage).mockResolvedValue([]);
  vi.mocked(adminCredit).mockResolvedValue({ refId: "r-1", balanceCredits: 6500000000 });
});

describe("WalletPage", () => {
  it("管理员看到余额、空流水与手工入账表单", async () => {
    renderWithQuery(<WalletPage />);

    expect(await screen.findByText("¥5.0")).toBeInTheDocument();
    expect(screen.getByText("手工入账(管理员兜底)")).toBeInTheDocument();
    expect(screen.getAllByText("暂无流水").length).toBeGreaterThan(0);
    expect(screen.getByText("暂无用量")).toBeInTheDocument();
  });

  it("提交手工入账后显示新余额", async () => {
    renderWithQuery(<WalletPage />);
    await screen.findByText("¥5.0");

    await userEvent.type(screen.getByLabelText("金额(元)"), "1.5");
    await userEvent.click(screen.getByRole("button", { name: "入账" }));

    await waitFor(() =>
      expect(adminCredit).toHaveBeenCalledWith({
        subject: "sub-u1",
        amountCredits: 1_500_000_000,
        refId: undefined,
      }),
    );
    expect(await screen.findByText("已入账,当前余额 ¥6.5")).toBeInTheDocument();
  });
});
