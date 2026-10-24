// @vitest-environment jsdom
import React from "react";
import { beforeAll, afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardExportDialog } from "../CardExportDialog";

vi.mock("../card-import-export-client", () => ({
  exportCards: vi.fn()
}));

beforeAll(() => {
  // jsdom 没有 rAF，Dialog 的首焦点在 rAF 回调里。
  globalThis.requestAnimationFrame = ((cb: FrameRequestCallback) =>
    setTimeout(() => cb(0), 0) as unknown as number) as typeof requestAnimationFrame;
  globalThis.cancelAnimationFrame = ((id: number) => clearTimeout(id)) as typeof cancelAnimationFrame;
});

afterEach(() => cleanup());

describe("卡片导出 · 格式切换", () => {
  // BD-146：导出格式按钮只靠 .active 类表达选中，读屏器不知道当前是 CSV 还是 Markdown。
  it("用 aria-pressed 暴露当前格式", () => {
    render(<CardExportDialog projectId="p1" filter={{}} onClose={vi.fn()} />);

    expect(screen.getByRole("button", { name: "CSV" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "Markdown" }).getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(screen.getByRole("button", { name: "Markdown" }));
    expect(screen.getByRole("button", { name: "Markdown" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "CSV" }).getAttribute("aria-pressed")).toBe("false");
  });
});
