import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { adminList, type PricingView } from "@/lib/pricing-api";
import { renderWithQuery } from "@/test/render";

import PricingPage from "./PricingPage";

vi.mock("@/lib/pricing-api", () => ({
  adminList: vi.fn(),
  ingressList: vi.fn(),
  remove: vi.fn(),
  upsert: vi.fn(),
}));

const PRICING: PricingView = {
  alias: "gpt-4o",
  source: "admin",
  currency: "CNY",
  input: "2000000000",
  cached_input: "1000000000",
  output: "8000000000",
  timezone: null,
  time_windows: [],
  updated_at: "2026-11-19T00:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(adminList).mockResolvedValue([PRICING]);
});

describe("PricingPage", () => {
  it("渲染现有效期目", async () => {
    renderWithQuery(<PricingPage />);

    expect((await screen.findAllByText("gpt-4o")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("CNY").length).toBeGreaterThan(0);
    expect(screen.getByText("价目管理")).toBeInTheDocument();
  });

  it("查询失败时展示错误提示", async () => {
    vi.mocked(adminList).mockRejectedValue(new Error("价目加载失败"));
    renderWithQuery(<PricingPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("价目加载失败");
  });

  it("空列表显示空态", async () => {
    vi.mocked(adminList).mockResolvedValue([]);
    renderWithQuery(<PricingPage />);

    expect((await screen.findAllByText("暂无价目")).length).toBeGreaterThan(0);
  });
});
