// @vitest-environment jsdom
/**
 * BD-178：书库列表是逐列对齐的记录表（格式 / 书籍 / 本地路径 / 阅读进度 / 最近阅读 / 操作），
 * 但此前全由 div 拼接——读屏器线性念值，听不到列名。用 ARIA 表格角色描述既有结构。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { LibraryPage } from "@/features/library/LibraryPage";
import { useLibraryStore } from "@/stores/library-store";

vi.mock("@/hooks/useLibraryActions", () => ({
  useLibraryActions: () => ({
    refreshBooks: vi.fn(async () => undefined),
    importBooks: vi.fn(),
    importEpubBooks: vi.fn(),
    removeBookById: vi.fn(),
    openReader: vi.fn()
  })
}));

function book(id: string, title: string) {
  return {
    id,
    title,
    filePath: `/data/books/${id}.txt`,
    format: "txt",
    importedAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    size: 1024,
    revision: 1,
    deviceId: "d1"
  };
}

beforeEach(() => {
  useLibraryStore.setState({
    books: [book("book-a", "夜雨书"), book("book-b", "长夜行")],
    progress: {},
    loading: false
  });
});

afterEach(() => cleanup());

describe("书库表格语义", () => {
  it("列头与单元格落在表格角色里（BD-178）", () => {
    render(<LibraryPage />);

    const table = screen.getByRole("table", { name: "书库书籍" });
    const headers = within(table).getAllByRole("columnheader").map((node) => node.textContent);
    expect(headers).toEqual(["格式", "书籍", "本地路径", "阅读进度", "最近阅读", "操作"]);

    const rows = within(table).getAllByRole("row");
    // 1 行表头 + 2 行书
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getAllByRole("cell")).toHaveLength(6);
    expect(within(rows[1]).getByRole("cell", { name: /夜雨书/ })).toBeTruthy();
  });

  it("筛选无结果时仍在表格里给出一行说明（BD-178）", () => {
    render(<LibraryPage />);

    const table = screen.getByRole("table", { name: "书库书籍" });
    fireEvent.change(screen.getByLabelText("筛选书籍"), { target: { value: "查无此书" } });

    const rows = within(table).getAllByRole("row");
    // 表头行 + 空态行
    expect(rows).toHaveLength(2);
    expect(within(rows[1]).getAllByRole("cell")).toHaveLength(1);
    expect(rows[1].textContent).toContain("没有匹配搜索结果的书");
  });
});
