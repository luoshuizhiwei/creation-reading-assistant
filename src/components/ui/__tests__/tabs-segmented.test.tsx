// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Tabs } from "../Tabs";

afterEach(cleanup);

describe("Tabs · 分段切换语义", () => {
  // BD-157：这组按钮切的是整块视图，没有 role="tabpanel"/aria-controls 配套，
  // 用 role="tab" 会让读屏器承诺方向键切换的标签页交互（实际只有 Tab 能走）。
  it("容器是 group，每档用 aria-pressed 暴露选中态", () => {
    const onChange = vi.fn();
    render(
      <Tabs
        value="board"
        onChange={onChange}
        ariaLabel="视图切换"
        items={[
          { id: "board", label: "卡片板" },
          { id: "list", label: "列表" }
        ]}
      />
    );

    expect(screen.getByRole("group", { name: "视图切换" })).toBeTruthy();
    expect(screen.queryByRole("tab")).toBeNull();
    expect(screen.getByRole("button", { name: "卡片板" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "列表" }).getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(screen.getByRole("button", { name: "列表" }));
    expect(onChange).toHaveBeenCalledWith("list");
  });
});
