import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fetchPresets, fetchTopology, type TopologyPayload } from "@/lib/topology-api";
import { renderWithQuery } from "@/test/render";

import TopologyPage from "./TopologyPage";

vi.mock("@/lib/topology-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/topology-api")>();
  return {
    ...actual,
    fetchPresets: vi.fn(),
    fetchTopology: vi.fn(),
    saveTopology: vi.fn(),
  };
});

const TOPOLOGY: TopologyPayload = {
  providers: [
    {
      name: "deepseek",
      adapter: "deepseek",
      base_url: "https://api.deepseek.com",
      keys: [{ env: "DEEPSEEK_API_KEY", weight: 1 }],
      weight: 1,
      priority: 0,
    },
  ],
  model_routes: {},
  models: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchTopology).mockResolvedValue(TOPOLOGY);
  vi.mocked(fetchPresets).mockResolvedValue({ presets: [] });
});

describe("TopologyPage", () => {
  it("加载后渲染供应商列表", async () => {
    renderWithQuery(<TopologyPage />);

    expect(await screen.findByText("供应商(1)")).toBeInTheDocument();
    expect(screen.getAllByText("deepseek").length).toBeGreaterThan(0);
    expect(screen.getAllByText("https://api.deepseek.com").length).toBeGreaterThan(0);
  });

  it("加载失败时展示错误提示", async () => {
    vi.mocked(fetchTopology).mockRejectedValue(new Error("拓扑加载失败"));
    renderWithQuery(<TopologyPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("拓扑加载失败");
  });
});
