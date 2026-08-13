// @vitest-environment jsdom
import React from "react";
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { renderHook, act, cleanup } from "@testing-library/react";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import * as libraryService from "@/services/library-service";
import * as annotationService from "@/services/annotation-service";
import * as readerService from "@/services/reader-service";

// ---------------------------------------------------------------------------
// Mock library-service：记录 removeBook 调用，返回剩余书籍列表
// ---------------------------------------------------------------------------
vi.mock("@/services/library-service", () => ({
  importBook: vi.fn(),
  importEpub: vi.fn(),
  listBooks: vi.fn(),
  removeBook: vi.fn()
}));

vi.mock("@/services/reader-service", () => ({
  openBook: vi.fn(),
  openEpub: vi.fn(),
  saveProgress: vi.fn(),
  getProgress: vi.fn(),
  saveEpubLocation: vi.fn(),
  getEpubLocation: vi.fn(),
  startSession: vi.fn(),
  updateSession: vi.fn(),
  endSession: vi.fn(),
  recoverActiveSession: vi.fn(),
  getSessions: vi.fn(),
  getReadingStats: vi.fn(),
  getReaderSettings: vi.fn(),
  updateReaderSettings: vi.fn()
}));

vi.mock("@/services/annotation-service", () => ({
  getHighlightsByBook: vi.fn(),
  saveHighlight: vi.fn(),
  deleteHighlight: vi.fn(),
  getBookmarksByBook: vi.fn(),
  saveBookmark: vi.fn(),
  deleteBookmark: vi.fn()
}));

const bookA = {
  id: "book-a", title: "资料 A", filePath: "/data/books/a.txt", format: "txt" as const,
  importedAt: "", updatedAt: "", size: 1024, revision: 1, deviceId: "d1"
};
const bookB = {
  id: "book-b", title: "资料 B", filePath: "/data/books/b.md", format: "md" as const,
  importedAt: "", updatedAt: "", size: 2048, revision: 1, deviceId: "d1"
};

/** 保存原始 store 函数，测试后恢复 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let originalConfirmAction: any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let originalShowToast: any;

beforeEach(() => {
  useLibraryStore.setState({
    books: [bookA, bookB],
    progress: {},
    activeContent: "",
    activity: {
      isReaderPageActive: false, isWindowFocused: true, isUserActive: false,
      lastInteractionAt: Date.now(), isTracking: false
    },
    loading: false
  });
  useUIStore.setState({ toasts: [], confirmRequest: undefined });
  originalConfirmAction = useUIStore.getState().confirmAction;
  originalShowToast = useUIStore.getState().showToast;
  vi.mocked(libraryService.removeBook).mockReset();
  vi.mocked(libraryService.removeBook).mockResolvedValue([bookB]);
  vi.mocked(readerService.getProgress).mockReset();
  vi.mocked(readerService.getProgress).mockResolvedValue(undefined);
});

afterEach(() => {
  useUIStore.setState({ confirmAction: originalConfirmAction, showToast: originalShowToast });
  cleanup();
});

// ---------------------------------------------------------------------------
// 移除资料不删除原文件
// ---------------------------------------------------------------------------
describe("removeBookById — 移除资料不删除原文件", () => {
  it("调用 library.removeBook IPC 移除记录", async () => {
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a", { skipConfirm: true });
    });

    expect(libraryService.removeBook).toHaveBeenCalledWith("book-a");
  });

  it("移除后更新书籍列表（移除的书籍不再出现）", async () => {
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a", { skipConfirm: true });
    });

    const books = useLibraryStore.getState().books;
    expect(books).toHaveLength(1);
    expect(books[0].id).toBe("book-b");
  });

  it("确认对话框文案明确说明书籍文件不会被删除", async () => {
    let capturedBody: string | undefined;
    useUIStore.setState({
      confirmAction: async (input) => {
        capturedBody = input.body;
        return true;
      }
    });

    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a");
    });

    expect(capturedBody).toContain("书籍文件不会被删除");
    expect(capturedBody).toContain("仅移除该书的记录");
  });

  it("取消确认时不调用 removeBook", async () => {
    useUIStore.setState({ confirmAction: async () => false });
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a");
    });

    expect(libraryService.removeBook).not.toHaveBeenCalled();
  });

  it("成功后 toast 提示原始文件不会被删除", async () => {
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a", { skipConfirm: true });
    });

    const toasts = useUIStore.getState().toasts;
    const successToast = toasts.find((t) => t.tone === "success");
    expect(successToast).toBeDefined();
    expect(successToast!.body).toContain("原始文件不会被删除");
  });
});

// ---------------------------------------------------------------------------
// 旧高亮、书签和统计数据未被删除
// ---------------------------------------------------------------------------
describe("removeBookById — 不影响旧高亮、书签和统计数据", () => {
  it("移除资料时不调用 annotation-service 的删除接口", async () => {
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a", { skipConfirm: true });
    });

    expect(annotationService.deleteHighlight).not.toHaveBeenCalled();
    expect(annotationService.deleteBookmark).not.toHaveBeenCalled();
  });

  it("移除资料时不调用 reader.getStats", async () => {
    const { result } = renderHook(() => useLibraryActions());

    await act(async () => {
      await result.current!.removeBookById("book-a", { skipConfirm: true });
    });

    expect(readerService.getReadingStats).not.toHaveBeenCalled();
  });
});
