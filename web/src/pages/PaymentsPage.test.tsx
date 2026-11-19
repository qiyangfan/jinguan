import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { listOrders, mockPay, type PaymentOrderSummary } from "@/lib/payment-api";
import { renderWithQuery } from "@/test/render";

import PaymentsPage from "./PaymentsPage";

vi.mock("@/lib/payment-api", () => ({
  createOrder: vi.fn(),
  listOrders: vi.fn(),
  mockPay: vi.fn(),
}));

const ORDER: PaymentOrderSummary = {
  orderId: "ord-1",
  userId: 1,
  amountCredits: "1000000000",
  currency: "CNY",
  status: "CREATED",
  createdAt: "2026-11-19T00:00:00Z",
  paidAt: null,
  expiresAt: "2026-11-19T01:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listOrders).mockResolvedValue([ORDER]);
  vi.mocked(mockPay).mockResolvedValue({ orderId: "ord-1", status: "PAID", replayed: false });
});

describe("PaymentsPage", () => {
  it("渲染订单及状态标签", async () => {
    renderWithQuery(<PaymentsPage />);

    expect((await screen.findAllByText("ord-1")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("待支付").length).toBeGreaterThan(0);
    expect(screen.getAllByText("¥1.0").length).toBeGreaterThan(0);
  });

  it("点击模拟支付调用 mockPay", async () => {
    renderWithQuery(<PaymentsPage />);

    const [payButton] = await screen.findAllByRole("button", { name: "模拟支付" });
    if (!payButton) throw new Error("未找到模拟支付按钮");
    await userEvent.click(payButton);

    await waitFor(() => expect(mockPay).toHaveBeenCalledWith("ord-1"));
  });

  it("订单为空时显示空态", async () => {
    vi.mocked(listOrders).mockResolvedValue([]);
    renderWithQuery(<PaymentsPage />);

    expect(await screen.findByText("暂无订单")).toBeInTheDocument();
  });
});
