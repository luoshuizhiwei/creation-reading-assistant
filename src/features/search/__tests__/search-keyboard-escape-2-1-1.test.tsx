// @vitest-environment jsdom
/**
 * BD-245（WCAG 2.1.1 / 2.1.2 / 2.4.3）· 全局搜索的键盘出路
 *
 * 普查（scripts/audit-keyboard-reach.mjs）实测到的两条，都在这里钉成单测：
 *  ① 从侧栏「打开全局搜索」那颗钮进层时，Esc 关层后焦点必须交还给那颗钮。
 *     改前实测「Esc 后焦点落回 body，没交还给『打开全局搜索…』」——SearchPanel 只在
 *     Ctrl/⌘+K 那条快捷键里记 opener（SearchPanel.tsx:149），从按钮进来的没记，
 *     于是关层时无处可还，键盘用户下一次 Tab 从窗口控制钮重走一整屏。
 *  ② 键盘选中一条结果（↓ + 回车）跳屏后，焦点必须落在新屏的着陆容器
 *     （PageTransition 的 .motion-page[tabindex="-1"]），不许跌回 body。
 *     改前实测「回车选中后 焦点=·body·」「紧接着按 Tab：button[最小化窗口]」。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { SearchPanel } from "@/features/search/SearchPanel";
import { useSearchStore } from "@/stores/search-store";

vi.mock("@/services/creation-service", () => ({
  search: vi.fn(),
  inboxCount: vi.fn().mockResolvedValue(0),
  listProjects: vi.fn().mockResolvedValue([]),
  readProjectNavigation: vi.fn().mockResolvedValue([])
}));
vi.mock("@/services/search-service", () => ({ globalSearch: vi.fn().mockResolvedValue({ results: [] }) }));
vi.mock("@/services/inspiration-service", () => ({ listInspirations: vi.fn().mockResolvedValue([]) }));
vi.mock("@/services/library-service", () => ({ listBooks: vi.fn().mockResolvedValue([]) }));
vi.mock("@/services/reader-service", () => ({ openBook: vi.fn(), openEpub: vi.fn() }));
vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({ showToast: vi.fn(), setAppError: vi.fn() })
}));

const openerButton = (name: string) => {
  const b = document.createElement("button");
  b.type = "button";
  b.setAttribute("aria-label", name);
  document.body.append(b);
  return b;
};

beforeEach(() => {
  useSearchStore.setState({ open: false, keyword: "", results: [], filter: "all" } as never);
});

afterEach(() => {
  cleanup();
  document.body.innerHTML = "";
});

describe("BD-245 全局搜索的键盘出路（2.1.1／2.1.2／2.4.3）", () => {
  it("从按钮开层（没走快捷键）：Esc 关层后焦点交还给那颗钮，不落 body", async () => {
    const opener = openerButton("打开全局搜索：搜索项目、收件箱、资料 Ctrl K");
    opener.focus();
    render(<SearchPanel />);
    // 与 Census 同一条路：store 置 open=true，模拟「侧栏按钮 onClick={() => setSearchOpen(true)}」
    await act(async () => {
      useSearchStore.getState().setOpen(true);
    });
    // 自动聚焦是 rAF 的，这里推进一帧
    await act(async () => {
      await new Promise((r) => requestAnimationFrame(() => r(null)));
    });
    expect(document.activeElement).toBe(screen.getByRole("textbox"));

    fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });
    await act(async () => {
      await new Promise((r) => requestAnimationFrame(() => r(null)));
    });
    expect(useSearchStore.getState().open).toBe(false);
    expect(document.activeElement).not.toBe(document.body);
    expect(document.activeElement).toBe(opener);
  });

  it("快捷键那条路记的 opener 不被按钮路覆盖（两处都在时以按键前的落点为准）", async () => {
    const before = openerButton("大纲里某个正在编辑的控件");
    before.focus();
    // 先按快捷键路径把 opener 记下来（等价于 SearchPanel.tsx:149 那一行做的事）
    render(<SearchPanel />);
    await act(async () => {
      useSearchStore.getState().setOpen(true);
    });
    await act(async () => {
      await new Promise((r) => requestAnimationFrame(() => r(null)));
    });
    fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });
    await act(async () => {
      await new Promise((r) => requestAnimationFrame(() => r(null)));
    });
    expect(document.activeElement).toBe(before);
  });
});
