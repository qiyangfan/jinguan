import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatusBadge, type StatusMeta } from "./status-badge";

const META: StatusMeta = {
  PAID: { label: "已支付", variant: "secondary" },
};

describe("StatusBadge", () => {
  it("命中映射时显示中文标签", () => {
    render(<StatusBadge meta={META} value="PAID" />);
    expect(screen.getByText("已支付")).toBeInTheDocument();
  });

  it("未知状态优先显示 fallbackLabel", () => {
    render(<StatusBadge meta={META} value="WEIRD" fallbackLabel="未知状态" />);
    expect(screen.getByText("未知状态")).toBeInTheDocument();
  });

  it("无值且无回退时显示破折号", () => {
    render(<StatusBadge meta={META} value={null} />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
