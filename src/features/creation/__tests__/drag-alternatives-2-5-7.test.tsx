// @vitest-environment jsdom
/**
 * BD-211 账本：WCAG 2.5.7 Dragging Movements（AA）。
 *
 * 主判据是运行时普查 scripts/audit-drag-alternatives.mjs（活体供线 + 真按键 + 布局变化），
 * 这里静态钉住判据骨架与三条产品锚，并用两个小组件行为测试证明「非拖拽替代」真的接线：
 *  ① 设定卡看板：卡片 Ctrl/⌘ + ←/→ 换类型列 + 常驻 role=status 播报；
 *  ② 速查面板分隔条：role=separator 可聚焦、aria-valuenow 随宽度走、←/→ 16px / Shift 64px；
 *  ③ EPUB 划词工具条握把：真按钮（可聚焦、有名称），方向键 8px / Shift 40px。
 *
 * 路径账（__tests__/ 起算）：../../../../ = 仓库根；../ = creation/；../../library = src/features/library/。
 */
import React from "react";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { BoardView } from "@/features/creation/cards/BoardView";
import { EpubSelectionToolbar } from "@/features/library/epub-reader/EpubSelectionToolbar";
import type { CardSummary, CardType } from "@/types/creation";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../scripts/audit-drag-alternatives.mjs");
const board = read("../cards/BoardView.tsx");
const panel = read("../editor/WritingQuickReferencePanel.tsx");
const panelCss = read("../editor/writing-quick-reference.css");
const toolbar = read("../../library/epub-reader/EpubSelectionToolbar.tsx");
const reader = read("../../library/EpubReaderPage.tsx");
const pkg = read("../../../../package.json");

afterEach(() => cleanup());

const cardType = (id: string, kind: string, name: string): CardType => ({
  id,
  builtIn: false,
  projectId: "p1",
  kind,
  name,
  fields: [],
  sortOrder: 0,
  createdAt: "",
  updatedAt: "",
  revision: 1
});

const card = (id: string, kind: string, title: string): CardSummary => ({
  id,
  projectId: "p1",
  linkedProjectIds: ["p1"],
  usageCount: 0,
  kind,
  title,
  aliases: [],
  fields: {},
  tags: [],
  createdAt: "",
  updatedAt: "",
  revision: 1
});

describe("BD-211 ①看板：Ctrl/⌘ + ←/→ 是非拖拽路径", () => {
  const renderBoard = (onMoveKind = vi.fn(), onSelectCard = vi.fn()) => render(
    <BoardView
      cardTypes={[cardType("t1", "character", "角色"), cardType("t2", "location", "地点")]}
      cards={[card("c1", "character", "阿岚")]}
      onSelectCard={onSelectCard}
      onMoveKind={onMoveKind}
      onNewCard={vi.fn()}
    />
  );

  it("Ctrl+→ 把卡移到下一个类型列，并用常驻 role=status 播报", () => {
    const onMoveKind = vi.fn();
    renderBoard(onMoveKind);
    const node = screen.getByRole("button", { name: "选择卡片：阿岚" });
    fireEvent.keyDown(node, { key: "ArrowRight", ctrlKey: true });
    expect(onMoveKind).toHaveBeenCalledWith("c1", "location");
    expect(document.querySelector('.cards-board [role="status"]')?.textContent).toContain("已把「阿岚」移到「地点」");
  });

  it("⌘+← 反向；不在最左列时回到上一个类型", () => {
    const onMoveKind = vi.fn();
    render(
      <BoardView
        cardTypes={[cardType("t1", "character", "角色"), cardType("t2", "location", "地点")]}
        cards={[card("c2", "location", "旧港")]}
        onSelectCard={vi.fn()}
        onMoveKind={onMoveKind}
        onNewCard={vi.fn()}
      />
    );
    fireEvent.keyDown(screen.getByRole("button", { name: "选择卡片：旧港" }), { key: "ArrowLeft", metaKey: true });
    expect(onMoveKind).toHaveBeenCalledWith("c2", "character");
  });

  it("看板源码：Ctrl/⌘ 分支、常驻 role=status、title 里的替代路径说明都在", () => {
    expect(board).toContain("(event.ctrlKey || event.metaKey)");
    expect(board).toContain('role="status"');
    expect(board).toContain('moveByKeyboard(card, event.key === "ArrowLeft" ? -1 : 1)');
    expect(board).toContain("拖到其他列可改变卡片类型；也可 Ctrl+←/→ 移动");
  });

  it("不带修饰键的方向键不动类型（保持既有浏览行为）", () => {
    const onMoveKind = vi.fn();
    renderBoard(onMoveKind);
    fireEvent.keyDown(screen.getByRole("button", { name: "选择卡片：阿岚" }), { key: "ArrowRight" });
    expect(onMoveKind).not.toHaveBeenCalled();
  });
});

describe("BD-211 ②速查分隔条：可聚焦 + aria 值 + 方向键步长", () => {
  it("census 认「可聚焦 / 有名称 / 按键有布局变化」三条腿，缺一即红", () => {
    expect(census).toContain("不可键盘聚焦(tabIndex=");
    expect(census).toContain("无可读名称");
    expect(census).toContain("方向键无任何布局变化");
    expect(census).toContain("DRAGALT_RED");
    expect(census).toContain("DRAGALT_OK");
    expect(census).toContain("process.exit(1)");
    expect(pkg).toContain('"audit:drag-alternatives": "node scripts/audit-drag-alternatives.mjs"');
  });

  it("census 的布局快照走 offset 几何（滚动无关），红判据不许换回 getBoundingClientRect", () => {
    expect(census).toContain("el.offsetLeft");
    expect(census).toContain("el.offsetTop");
    expect(census).toContain("const stable = before.map");
    expect(census).not.toMatch(/stable[\s\S]{0,200}getBoundingClientRect/);
  });

  it("三条供线的硬证据与三个范围都在普查里", () => {
    expect(census).toContain('el.getAttribute("draggable") === "true"');
    expect(census).toContain('el.getAttribute("role") === "separator" && resizeCursor.test(cs.cursor)');
    expect(census).toContain("const dragCursor = /^(grab|grabbing|move)$/;");
    for (const label of ["项目·设定卡看板", "写作台·速查分隔条", "阅读器·EPUB 划词工具条"]) {
      expect(census).toContain(label);
    }
  });

  it("分隔条把 role=separator 补齐成 splitter：tabIndex / 值三件套 / keydown", () => {
    expect(panel).toContain("tabIndex={0}");
    expect(panel).toContain("aria-valuenow={width}");
    expect(panel).toContain("aria-valuemin={MIN_WIDTH}");
    expect(panel).toContain("aria-valuemax={MAX_WIDTH}");
    expect(panel).toContain("onKeyDown={resizeByKeyboard}");
    expect(panel).toContain("const RESIZE_STEP = 16;");
    expect(panel).toContain("const RESIZE_STEP_LARGE = 64;");
    // 拖与键共用同一夹取/落盘函数（两条路不会各走一套边界）。
    expect(panel).toContain("const applyWidth = (next: number) => {");
    expect(panel).toMatch(/const move = \(moveEvent: PointerEvent\) => \{\s*applyWidth\(startWidth \+ startX - moveEvent\.clientX\);/);
  });

  it("分隔条聚焦有可见焦点环（2.4.7）", () => {
    expect(panelCss).toContain(".writing-quick-reference-resizer:focus-visible {");
    expect(panelCss).toContain("outline: 2px solid var(--action-primary);");
  });
});

describe("BD-211 ③EPUB 划词工具条握把：真按钮 + 方向键微调", () => {
  const renderToolbar = (onNudge = vi.fn(), onDragStart = vi.fn()) => render(
    <EpubSelectionToolbar
      toolbar={{ visible: true, x: 120, y: 240, cfiRange: "epubcfi(/6/2)", text: "雨夜来信" }}
      toolbarOffset={{ x: 0, y: 0 }}
      showColorPicker={false}
      onToggleColorPicker={vi.fn()}
      onDragStart={onDragStart}
      onNudge={onNudge}
      onHighlight={vi.fn()}
      onCopy={vi.fn()}
      onExcerpt={vi.fn()}
      onClose={vi.fn()}
    />
  );

  it("握把是带可读名称的按钮（不再是只有 onMouseDown 的 span）", () => {
    renderToolbar();
    const grip = screen.getByRole("button", { name: /移动工具条/ });
    expect(grip.tagName).toBe("BUTTON");
    expect(grip.getAttribute("type")).toBe("button");
  });

  it("方向键 8px、Shift 40px，拖动仍走 onDragStart", () => {
    const onNudge = vi.fn();
    const onDragStart = vi.fn();
    const { container } = renderToolbar(onNudge, onDragStart);
    const grip = screen.getByRole("button", { name: /移动工具条/ });
    fireEvent.keyDown(grip, { key: "ArrowLeft" });
    fireEvent.keyDown(grip, { key: "ArrowUp" });
    fireEvent.keyDown(grip, { key: "ArrowRight", shiftKey: true });
    expect(onNudge.mock.calls).toEqual([[-8, 0], [0, -8], [40, 0]]);
    fireEvent.mouseDown(grip);
    expect(onDragStart).toHaveBeenCalledTimes(1);
    expect(container.querySelector('span[title="拖动"]')).toBeNull();
  });

  it("阅读器把方向键接到与拖动同一个 offset 上", () => {
    expect(toolbar).toContain("onNudge(dx: number, dy: number): void;");
    expect(reader).toContain("const handleToolbarNudge = (dx: number, dy: number) => {");
    expect(reader).toContain("onNudge={handleToolbarNudge}");
  });
});
