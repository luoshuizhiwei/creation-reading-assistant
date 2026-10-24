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
});
