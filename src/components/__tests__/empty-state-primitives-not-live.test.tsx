// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { EmptyState } from "@/components/ui";
import { EpubEmptyState } from "@/features/library/epub-reader/EpubEmptyState";

afterEach(cleanup);

/**
 * BD-194：空态原语曾自带 role="status" aria-live="polite"——挂载即带内容的
 * polite 区是哑的（BD-175/180/192 判据），从未播出过东西；带按钮的容器整体
 * 当 status 还会把按钮卷进播报文本（BD-191 判据）。现在退回纯视觉。
 */
describe("空态原语不是假 live region（BD-194）", () => {
  it("EmptyState 根节点不带 role=status/aria-live，文案照常可见", () => {
    const { container } = render(<EmptyState title="书库还是空的" body="导入 TXT、Markdown 或 EPUB 后即可打开。" />);
    expect(screen.getByText("书库还是空的")).toBeTruthy();
    expect(screen.getByText("导入 TXT、Markdown 或 EPUB 后即可打开。")).toBeTruthy();

    const root = container.querySelector("div.grid") as HTMLElement;
    expect(root.getAttribute("role"), "回退：空态原语又挂回 role=status——插入即带内容，本来就是哑的").toBeNull();
    expect(root.getAttribute("aria-live"), "回退：aria-live 又回来了").toBeNull();
    expect(container.querySelectorAll('[role="status"], [aria-live]').length, "空态里不该有 live region").toBe(0);
  });

  it("EpubEmptyState 无空播报，按钮照常可达", () => {
    const onBackToLibrary = vi.fn();
    const { container } = render(<EpubEmptyState onBackToLibrary={onBackToLibrary} onBackToHome={vi.fn()} />);
    expect(screen.getByText("没有打开 EPUB")).toBeTruthy();
    expect(
      container.querySelector('[role="status"], [aria-live]'),
      "回退：带按钮的容器又整体当 status——按钮会被卷进播报文本"
    ).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "返回书库" }));
    expect(onBackToLibrary).toHaveBeenCalledTimes(1);
  });
});
