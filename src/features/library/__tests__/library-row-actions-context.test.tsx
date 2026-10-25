// @vitest-environment jsdom
/**
 * BD-177：书库每行都有两个纯图标按钮，旧名是「打开资料」「移除书籍」——
 * 读屏器逐行听下去只有一串同样的名字，分不清是哪本书。
 * 这一批把书名带进名字，和 BD-169/BD-171 建立的写法一致。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
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

describe("书库行内操作的可访问名", () => {
  it("打开与移除按钮都带书名（BD-177）", () => {
    render(<LibraryPage />);

    expect(screen.getByRole("button", { name: "打开「夜雨书」" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "打开「长夜行」" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "移除书籍「夜雨书」" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "移除书籍「长夜行」" })).toBeTruthy();
  });
});
