// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { LibraryPage } from "@/features/library/LibraryPage";
import { useLibraryStore } from "@/stores/library-store";

// BD-182：排序下拉只响应外部点击，键盘用户按 Esc 毫无反应，菜单也把焦点带走。
// Esc 应关掉菜单并把焦点交还触发钮。

vi.mock("@/hooks/useLibraryActions", () => ({
  useLibraryActions: () => ({
    refreshBooks: vi.fn(async () => undefined),
    importBooks: vi.fn(),
    importEpubBooks: vi.fn(),
    removeBookById: vi.fn(),
    openReader: vi.fn()
  })
}));

beforeEach(() => {
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

function openMenu() {
  const trigger = screen.getByRole("button", { name: "最近阅读" });
  fireEvent.click(trigger);
  expect(trigger.getAttribute("aria-expanded")).toBe("true");
  return trigger;
}

describe("书库 · 排序下拉的键盘出口", () => {
  it("触发钮上按 Esc 关闭菜单", () => {
    const { container } = render(<LibraryPage />);
    const trigger = openMenu();

    fireEvent.keyDown(trigger, { key: "Escape" });

    expect(trigger.getAttribute("aria-expanded")).toBe("false");
    expect(container.querySelector(".z-20")).toBeNull();
  });

  it("焦点在菜单项上按 Esc，关闭菜单并把焦点交还触发钮", () => {
    const { container } = render(<LibraryPage />);
    openMenu();

    const menu = container.querySelector(".z-20") as HTMLElement;
    const item = within(menu).getByRole("button", { name: "书名" });
    item.focus();
    expect(document.activeElement).toBe(item);

    fireEvent.keyDown(item, { key: "Escape" });

    expect(container.querySelector(".z-20")).toBeNull();
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "最近阅读" }));
  });

  it("其它按键不会关菜单", () => {
    const { container } = render(<LibraryPage />);
    const trigger = openMenu();

    fireEvent.keyDown(trigger, { key: "Tab" });

    expect(container.querySelector(".z-20")).toBeTruthy();
    expect(trigger.getAttribute("aria-expanded")).toBe("true");
  });

  it("菜单关着时按 Esc 不会抢焦点", () => {
    render(<LibraryPage />);
    const trigger = screen.getByRole("button", { name: "最近阅读" });
    const filterInput = screen.getByLabelText("筛选书籍");
    filterInput.focus();

    fireEvent.keyDown(filterInput, { key: "Escape" });
    expect(document.activeElement).toBe(filterInput);
    expect(trigger.getAttribute("aria-expanded")).toBe("false");
  });
});
