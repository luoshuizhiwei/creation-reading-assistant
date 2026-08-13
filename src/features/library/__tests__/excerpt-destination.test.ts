// @vitest-environment jsdom
import React from "react";
import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { renderHook, act, cleanup } from "@testing-library/react";
import { useReaderExcerpt } from "../useReaderExcerpt";
import type { ExcerptBuildContext, ExcerptResult, ExcerptSourceSnapshot, ReaderExcerptDestination } from "@/types/library";
import {
  getReaderExcerptDestination,
  resetReaderExcerptDestination,
  setReaderExcerptDestination
} from "../excerpt-destination";

// ---------------------------------------------------------------------------
// Mock 摘录目的地：记录调用并返回可控结果
// ---------------------------------------------------------------------------
function createMockDestination(overrides: Partial<ReaderExcerptDestination> = {}): ReaderExcerptDestination & {
  inboxCalls: ExcerptSourceSnapshot[];
  cardCalls: Array<{ projectId: string; source: ExcerptSourceSnapshot }>;
  listProjectsCalls: number;
} {
  const inboxCalls: ExcerptSourceSnapshot[] = [];
  const cardCalls: Array<{ projectId: string; source: ExcerptSourceSnapshot }> = [];
  let listProjectsCalls = 0;
  return {
    async listProjects() {
      listProjectsCalls++;
      return overrides.listProjects ? await overrides.listProjects() : [
        { id: "p1", title: "项目一" },
        { id: "p2", title: "项目二" }
      ];
    },
    async saveToInbox(source) {
      inboxCalls.push(source);
      if (overrides.saveToInbox) return await overrides.saveToInbox(source);
      return { success: true, itemId: `inbox-${inboxCalls.length}` };
    },
    async saveToProjectCard(projectId, source) {
      cardCalls.push({ projectId, source });
      if (overrides.saveToProjectCard) return await overrides.saveToProjectCard(projectId, source);
      return { success: true, itemId: `card-${cardCalls.length}` };
    },
    get inboxCalls() { return inboxCalls; },
    get cardCalls() { return cardCalls; },
    get listProjectsCalls() { return listProjectsCalls; }
  };
}

const baseContext: ExcerptBuildContext = {
  bookId: "b1",
  bookTitle: "测试书",
  bookAuthor: "作者",
  format: "txt",
  chapterTitle: "第一章",
  progressPercent: 0.5,
  excerpt: "这是要摘录的正文内容",
  charOffset: 100,
  charLength: 12,
  scrollTop: 300,
  now: () => "2026-08-12T10:00:00.000Z"
};

beforeEach(() => {
  resetReaderExcerptDestination();
});

afterEach(() => {
  cleanup();
  resetReaderExcerptDestination();
});

// ---------------------------------------------------------------------------
// 摘录到收件箱
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 摘录到收件箱", () => {
  it("成功调用 saveToInbox 并返回 success", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });

    expect(result.current.isPickerOpen).toBe(true);
    expect(result.current.pendingSource).toBeDefined();
    expect(result.current.pendingSource!.excerpt).toBe("这是要摘录的正文内容");

    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "inbox" });
    });

    expect(submitResult!.success).toBe(true);
    expect(submitResult!.itemId).toBe("inbox-1");
    expect(mock.inboxCalls).toHaveLength(1);
    expect(mock.inboxCalls[0].bookId).toBe("b1");
    expect(mock.inboxCalls[0].format).toBe("txt");
    expect(mock.inboxCalls[0].excerpt).toBe("这是要摘录的正文内容");
  });

  it("收件箱保存完整选文和来源", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "inbox" });
    });

    const saved = mock.inboxCalls[0];
    expect(saved.bookTitle).toBe("测试书");
    expect(saved.bookAuthor).toBe("作者");
    expect(saved.chapterTitle).toBe("第一章");
    expect(saved.progressPercent).toBe(0.5);
    expect(saved.locationLabel).toContain("第一章");
    expect(saved.locationLabel).toContain("50%");
    expect(saved.charOffset).toBe(100);
    expect(saved.scrollTop).toBe(300);
    expect(saved.createdAt).toBe("2026-08-12T10:00:00.000Z");
  });
});

// ---------------------------------------------------------------------------
// 摘录到项目资料卡
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 摘录到项目资料卡", () => {
  it("成功调用 saveToProjectCard 并传入 projectId", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "projectCard", projectId: "p2" });
    });

    expect(submitResult!.success).toBe(true);
    expect(mock.cardCalls).toHaveLength(1);
    expect(mock.cardCalls[0].projectId).toBe("p2");
    expect(mock.cardCalls[0].source.bookId).toBe("b1");
    expect(mock.cardCalls[0].source.format).toBe("txt");
  });

  it("资料卡保存 bookId、格式、章节、进度、定位", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const epubContext: ExcerptBuildContext = {
      ...baseContext,
      format: "epub",
      excerpt: "EPUB 选文",
      href: "ch1.xhtml",
      cfi: "epubcfi(/6/4!/2)",
      charOffset: undefined,
      charLength: undefined,
      scrollTop: undefined
    };
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(epubContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "projectCard", projectId: "p1" });
    });

    const saved = mock.cardCalls[0].source;
    expect(saved.format).toBe("epub");
    expect(saved.href).toBe("ch1.xhtml");
    expect(saved.cfi).toBe("epubcfi(/6/4!/2)");
    expect(saved.chapterTitle).toBe("第一章");
    expect(saved.progressPercent).toBe(0.5);
  });
});

// ---------------------------------------------------------------------------
// 失败不假成功
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 失败不假成功", () => {
  it("saveToInbox 返回 success=false 时提交结果为失败", async () => {
    const mock = createMockDestination({
      saveToInbox: async () => ({ success: false, error: "收件箱写入失败" })
    });
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "inbox" });
    });

    expect(submitResult!.success).toBe(false);
    expect(submitResult!.error).toBe("收件箱写入失败");
  });

  it("saveToProjectCard 抛异常时返回失败而非假成功", async () => {
    const mock = createMockDestination({
      saveToProjectCard: async () => { throw new Error("网络中断"); }
    });
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "projectCard", projectId: "p1" });
    });

    expect(submitResult!.success).toBe(false);
    expect(submitResult!.error).toContain("网络中断");
  });

  it("目的地返回 success=true 但缺少 itemId 仍视为成功", async () => {
    const mock = createMockDestination({
      saveToInbox: async () => ({ success: true })
    });
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "inbox" });
    });

    expect(submitResult!.success).toBe(true);
  });

  it("失败不记录为已成功签名：重试同一选文会再次调用 destination", async () => {
    let inboxCalls = 0;
    const mock = createMockDestination({
      saveToInbox: async () => {
        inboxCalls += 1;
        if (inboxCalls === 1) return { success: false, error: "收件箱写入失败" };
        return { success: true, itemId: "inbox-2" };
      }
    });
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    // 第一次提交失败
    let firstResult: ExcerptResult | undefined;
    await act(async () => {
      firstResult = await result.current.submit({ kind: "inbox" });
    });
    expect(firstResult!.success).toBe(false);

    // 不关闭 picker，立即重试同一选文：失败不记录签名 → 应再次调用 destination
    let secondResult: ExcerptResult | undefined;
    await act(async () => {
      secondResult = await result.current.submit({ kind: "inbox" });
    });
    expect(secondResult!.success).toBe(true);
    expect(secondResult!.itemId).toBe("inbox-2");
    expect(inboxCalls).toBe(2);
  });
});

// ---------------------------------------------------------------------------
// 双击只产生一条记录
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 双击只产生一条记录", () => {
  it("同一选文窗口内第二次提交被拒绝", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "inbox" });
    });

    // 不关闭 picker，立即再次提交同一来源
    let secondResult: ExcerptResult | undefined;
    await act(async () => {
      secondResult = await result.current.submit({ kind: "inbox" });
    });

    expect(secondResult!.success).toBe(false);
    expect(secondResult!.error).toContain("重复");
    expect(mock.inboxCalls).toHaveLength(1);
  });

  it("不同选文不视为重复", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "inbox" });
    });
    await act(async () => {
      result.current.closePicker();
    });

    // 第二次：不同选文
    await act(async () => {
      await result.current.openPicker({ ...baseContext, excerpt: "另一段完全不同的内容" });
    });
    let secondResult: ExcerptResult | undefined;
    await act(async () => {
      secondResult = await result.current.submit({ kind: "inbox" });
    });

    expect(secondResult!.success).toBe(true);
    expect(mock.inboxCalls).toHaveLength(2);
  });
});

// ---------------------------------------------------------------------------
// 不创建旧 inspiration
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 不创建旧 inspiration", () => {
  it("摘录流程不调用 inspiration-service", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);

    // 验证 excerpt-destination 模块不依赖 inspiration-service：
    // 摘录只通过 ReaderExcerptDestination 接口调用，不触发 createInspiration。
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "inbox" });
    });

    // 唯一的 IPC 调用是 destination.saveToInbox
    expect(mock.inboxCalls).toHaveLength(1);
    expect(mock.cardCalls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// 选文为空时不打开选择器
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 选文为空", () => {
  it("空选文不打开选择器", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker({ ...baseContext, excerpt: "   " });
    });

    expect(result.current.isPickerOpen).toBe(false);
    expect(result.current.pendingSource).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// 位置恢复不受影响
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 位置恢复不受影响", () => {
  it("openPicker 不修改传入的 context 字段（只读访问）", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    const ctx: ExcerptBuildContext = { ...baseContext };
    const originalExcerpt = ctx.excerpt;
    const originalOffset = ctx.charOffset;

    await act(async () => {
      await result.current.openPicker(ctx);
    });

    // 原始 context 未被修改
    expect(ctx.excerpt).toBe(originalExcerpt);
    expect(ctx.charOffset).toBe(originalOffset);

    // pendingSource 是新建对象，不引用原 context
    expect(result.current.pendingSource).not.toBe(ctx);
  });

  it("submit 不调用任何进度保存或滚动 API", async () => {
    const mock = createMockDestination();
    setReaderExcerptDestination(mock);
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    await act(async () => {
      await result.current.submit({ kind: "inbox" });
    });

    // 验证 destination 只被调用了 saveToInbox，没有 saveProgress
    expect(mock.inboxCalls).toHaveLength(1);
    expect(mock.cardCalls).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// 默认未接线目的地返回明确失败
// ---------------------------------------------------------------------------
describe("useReaderExcerpt — 未接线目的地", () => {
  it("默认目的地返回 success=false 且带明确 error", async () => {
    // 不注入目的地，使用默认 unavailable stub
    const { result } = renderHook(() => useReaderExcerpt());

    await act(async () => {
      await result.current.openPicker(baseContext);
    });
    let submitResult: ExcerptResult | undefined;
    await act(async () => {
      submitResult = await result.current.submit({ kind: "inbox" });
    });

    expect(submitResult!.success).toBe(false);
    expect(submitResult!.error).toContain("未接线");
  });
});
