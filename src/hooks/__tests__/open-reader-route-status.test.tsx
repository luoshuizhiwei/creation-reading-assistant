// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { useAppStore } from "@/stores/app-store";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { openBook, openEpub } from "@/services/reader-service";
import { EPUB_OPENING_STATUS } from "@/hooks/useRouteStatus";

/**
 * BD-198 行为账：书库开书入口的到达播报。
 *
 * 阅读器路由挂在 PageTransition 之后，点击瞬间页内没有任何常驻区可播
 * （插入即带内容＝静音，BD-175）；入口必须抢在 IPC await 之前把文案换进
 * 根级常驻区。成功不撤——阅读器页带同一常量接手（同文不换不重声）；
 * 失败只清自己写的文本——槽被他人改写时不碰。TXT 没有忙态覆盖层，保持静音。
 */

vi.mock("@/services/reader-service", () => ({
  openEpub: vi.fn(),
  openBook: vi.fn(),
  getBatchProgress: vi.fn(async () => [])
}));
vi.mock("@/services/library-service", () => ({
  listBooks: vi.fn(async () => []),
  importBook: vi.fn(async () => []),
  importEpub: vi.fn(async () => []),
  removeBook: vi.fn(async () => [])
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
  useAppStore.setState({ errors: [] } as never);
});

describe("BD-198：openReader 的 EPUB 到达播报", () => {
  it("EPUB：点击即换文本，早于 IPC 落定（await 前就能听到）", async () => {
    const gate = deferred<unknown>();
    vi.mocked(openEpub).mockReturnValue(gate.promise as never);
    const { result } = renderHook(() => useLibraryActions());
    const task = result.current.openReader("b-epub");
    // 同步断言：此刻 openEpub 还没 resolve，播报必须已在槽内
    expect(slot()).toBe(EPUB_OPENING_STATUS);
    gate.resolve({ book: epubBook, epubUrl: "epub://x", settings: {} });
    await task;
    // 成功不撤：阅读器页将以同一常量接手，入口撤了反而制造空窗
    expect(slot()).toBe(EPUB_OPENING_STATUS);
  });

  it("EPUB 失败：清自己的文本，错误照常入账", async () => {
    vi.mocked(openEpub).mockRejectedValue(new Error("IPC 断了"));
    const { result } = renderHook(() => useLibraryActions());
    await result.current.openReader("b-epub");
    expect(slot()).toBe("");
    expect(useAppStore.getState().errors.length).toBeGreaterThan(0);
  });

  it("EPUB 失败但槽已被他人改写：不碰他人播报", async () => {
    const gate = deferred<unknown>();
    vi.mocked(openEpub).mockReturnValue(gate.promise as never);
    const { result } = renderHook(() => useLibraryActions());
    const task = result.current.openReader("b-epub");
    expect(slot()).toBe(EPUB_OPENING_STATUS);
    useUIStore.getState().setRouteStatus("正在读取统计…");
    gate.reject(new Error("IPC 断了"));
    await task;
    expect(slot()).toBe("正在读取统计…");
  });

  it("TXT 全程静音：没有开书忙态就不播", async () => {
    vi.mocked(openBook).mockResolvedValue({ book: txtBook, content: "正文", settings: {} } as never);
    const { result } = renderHook(() => useLibraryActions());
    const task = result.current.openReader("b-txt");
    expect(slot()).toBe("");
    await task;
    expect(slot()).toBe("");
    expect(useAppStore.getState().screen).toBe("reader");
  });
});
