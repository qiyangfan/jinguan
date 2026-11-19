import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MobileCard } from "./mobile-card";

describe("MobileCard", () => {
  it("渲染标题、强调值、详情行与操作区", () => {
    render(
      <MobileCard
        headline={<span>订单 ord-1</span>}
        headlineEnd={<span>¥1.0</span>}
        rows={[{ label: "状态", value: "待支付" }]}
        footer={<span>操作区</span>}
      />,
    );

    expect(screen.getByText("订单 ord-1")).toBeInTheDocument();
    expect(screen.getByText("¥1.0")).toBeInTheDocument();
    expect(screen.getByText("状态")).toBeInTheDocument();
    expect(screen.getByText("待支付")).toBeInTheDocument();
    expect(screen.getByText("操作区")).toBeInTheDocument();
  });

  it("无详情行与操作区时不渲染空容器", () => {
    const { container } = render(<MobileCard headline={<span>仅标题</span>} />);

    expect(screen.getByText("仅标题")).toBeInTheDocument();
    expect(container.querySelector("dl")).toBeNull();
  });
});
