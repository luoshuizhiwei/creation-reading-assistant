// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useSearchActions } from "@/hooks/useSearchActions";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { openBook, openEpub } from "@/services/reader-service";
import { EPUB_OPENING_STATUS } from "@/hooks/useRouteStatus";
import type { SearchNavigateIntent } from "@/types/search";

/**
 * BD-198 行为账：搜索直达开书的到达播报。
 *
 * 与书库开书入口同病同治：确认目标后、IPC await 前把「正在打开 EPUB…」
 * 换进根级常驻区；半途抛错清自己的文本再上抛，成功交给阅读器页接手。
 * TXT 直达没有开书忙态，全程静音。
 */

vi.mock("@/services/reader-service", () => ({
  openEpub: vi.fn(),
  openBook: vi.fn(),
  getBatchProgress: vi.fn(async () => [])
}));
vi.mock("@/services/library-service", () => ({
  listBooks: vi.fn(async () => [])
}));

const slot = () => useUIStore.getState().routeStatus;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const epubBook = { id: "b-epub", format: "epub", title: "测试书" };
const txtBook = { id: "b-txt", format: "txt", title: "文本书" };

beforeEach(() => {
  vi.clearAllMocks();
  useLibraryStore.setState({ books: [epubBook, txtBook] } as never);
  useUIStore.setState({ routeStatus: "" });
});

describe("BD-198：搜索直达开书的播报与清账", () => {
  it("EPUB 直达：IPC await 前即换文本，成功不撤（交给阅读器页接手）", async () => {
    const gate = deferred<unknown>();
    vi.mocked(openEpub).mockReturnValue(gate.promise as never);
    const { result } = renderHook(() => useSearchActions());
    const intent = { kind: "book", bookId: "b-epub" } as SearchNavigateIntent;
    const task = result.current.openIntent(intent);
    expect(slot()).toBe(EPUB_OPENING_STATUS);
    gate.resolve({ book: epubBook, epubUrl: "epub://x", settings: {} });
    await expect(task).resolves.toBe("navigated");
    expect(slot()).toBe(EPUB_OPENING_STATUS);
  });

  it("EPUB 直达失败：清自己的文本，错误继续上抛", async () => {
    vi.mocked(openEpub).mockRejectedValue(new Error("IPC 断了"));
    const { result } = renderHook(() => useSearchActions());
    const intent = { kind: "book", bookId: "b-epub" } as SearchNavigateIntent;
    await expect(result.current.openIntent(intent)).rejects.toThrow("IPC 断了");
    expect(slot()).toBe("");
  });

  it("EPUB 直达失败但槽已被他人改写：不碰他人播报", async () => {
    const gate = deferred<unknown>();
    vi.mocked(openEpub).mockReturnValue(gate.promise as never);
    const { result } = renderHook(() => useSearchActions());
    const task = result.current.openIntent({ kind: "book", bookId: "b-epub" } as SearchNavigateIntent);
    useUIStore.getState().setRouteStatus("正在读取统计…");
    gate.reject(new Error("IPC 断了"));
    await expect(task).rejects.toThrow();
    expect(slot()).toBe("正在读取统计…");
  });

  it("TXT 直达全程静音", async () => {
    vi.mocked(openBook).mockResolvedValue({ book: txtBook, content: "正文", settings: {} } as never);
    const { result } = renderHook(() => useSearchActions());
    const task = result.current.openIntent({ kind: "book", bookId: "b-txt" } as SearchNavigateIntent);
    expect(slot()).toBe("");
    await expect(task).resolves.toBe("navigated");
    expect(slot()).toBe("");
  });
});
