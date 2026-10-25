// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { LibraryPage } from "@/features/library/LibraryPage";
import { useLibraryStore } from "@/stores/library-store";

const refreshBooks = vi.fn(async () => undefined);

vi.mock("@/hooks/useLibraryActions", () => ({
  useLibraryActions: () => ({
    refreshBooks,
    importBooks: vi.fn(),
    importEpubBooks: vi.fn(),
    removeBookById: vi.fn(),
    openReader: vi.fn()
  })
}));

beforeEach(() => {
  refreshBooks.mockClear();
  useLibraryStore.setState({
    books: [
      {
        id: "book-a",
        title: "夜雨书",
        filePath: "/data/books/a.txt",
        format: "txt",
        importedAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
        size: 1024,
        revision: 1,
        deviceId: "d1"
      }
    ],
    progress: {},
    loading: false
  });
});

afterEach(() => cleanup());

describe("书库 · 排序菜单", () => {
  // BD-149：排序项只靠底色表达当前排序，读屏器不知道书库按什么排。
  it("用 aria-pressed 暴露当前排序", () => {
    const { container } = render(<LibraryPage />);
    fireEvent.click(screen.getByRole("button", { name: "最近阅读" }));

    const menu = container.querySelector(".z-20") as HTMLElement;
    expect(menu).toBeTruthy();
    expect(within(menu).getByRole("button", { name: "最近阅读" }).getAttribute("aria-pressed")).toBe("true");
    expect(within(menu).getByRole("button", { name: "书名" }).getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(within(menu).getByRole("button", { name: "书名" }));
    fireEvent.click(screen.getByRole("button", { name: "书名" }));

    const reopened = container.querySelector(".z-20") as HTMLElement;
    expect(within(reopened).getByRole("button", { name: "书名" }).getAttribute("aria-pressed")).toBe("true");
    expect(within(reopened).getByRole("button", { name: "最近阅读" }).getAttribute("aria-pressed")).toBe("false");
  });

  // BD-163：菜单开关只靠底色和有无节点表达，读屏器不知道下拉是开着还是关着。
  it("排序菜单按钮用 aria-expanded 暴露开合", () => {
    render(<LibraryPage />);
    const trigger = screen.getByRole("button", { name: "最近阅读" });
    expect(trigger.getAttribute("aria-expanded")).toBe("false");
    fireEvent.click(trigger);
    expect(trigger.getAttribute("aria-expanded")).toBe("true");
    fireEvent.click(screen.getByRole("button", { name: "书名" }));
    expect(screen.getByRole("button", { name: "书名" }).getAttribute("aria-expanded")).toBe("false");
  });

  // BD-152：书库列表的进度条此前没有名字，读屏器不知道对应哪本书。
  it("进度条带书名的可访问名", () => {
    render(<LibraryPage />);
    expect(screen.getByRole("progressbar", { name: "夜雨书 阅读进度" })).toBeTruthy();
  });
});
