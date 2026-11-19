import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Pagination } from "./pagination";

describe("Pagination", () => {
  it("首页禁用上一页,点击下一页回调页码", async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={1} pageCount={3} onPageChange={onPageChange} />);

    expect(screen.getByRole("button", { name: "上一页" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "下一页" }));

    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it("末页禁用下一页,并展示总数", () => {
    render(<Pagination page={3} pageCount={3} total={120} onPageChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: "下一页" })).toBeDisabled();
    expect(screen.getByText("共 120 条")).toBeInTheDocument();
    expect(screen.getByText("3 / 3")).toBeInTheDocument();
  });
});
