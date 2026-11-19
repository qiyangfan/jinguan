import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EmptyState } from "./empty-state";
import { ErrorAlert } from "./error-alert";

describe("ErrorAlert", () => {
  it("使用 alert 语义并显示传入文本", () => {
    render(<ErrorAlert text="加载失败" />);
    expect(screen.getByRole("alert")).toHaveTextContent("加载失败");
  });

  it("无文本时显示默认提示", () => {
    render(<ErrorAlert />);
    expect(screen.getByRole("alert")).toHaveTextContent("加载失败,请稍后重试");
  });
});

describe("EmptyState", () => {
  it("展示空态内容", () => {
    render(<EmptyState>暂无数据</EmptyState>);
    expect(screen.getByText("暂无数据")).toBeInTheDocument();
  });
});
