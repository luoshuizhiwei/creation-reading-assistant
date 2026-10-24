// @vitest-environment jsdom
/**
 * BD-136：收件箱条目列表的键盘路径。
 *
 * 此前选中条目只有 <li onClick>：键盘用户无法打开任何一条灵感（列表是「写想法」的入口）。
 * li 里嵌着「转为资料卡 / 移出」两颗按钮，给 li 加 role=button 会重演 BD-124 的
 * nested-interactive，所以键盘路径落在标题上：标题改为原生 <button>，点击 / Enter / 空格
 * 都能选中（原生按钮自带键盘激活）；行级 onClick 保留为鼠标便利。
 */
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { InboxItemList } from "@/features/creation/inbox/components/InboxItemList";
import { EMPTY_DRAFT } from "@/features/creation/inbox/components/types";
import type { InboxItem } from "@/types/creation";

function makeItem(overrides: Partial<InboxItem> = {}): InboxItem {
  return {
    id: "i1",
    legacyId: null,
    title: "雨夜开篇",
    body: "窗外正下着雨。",
    type: "note",
    status: "inbox",
    tags: [],
    platformTags: [],
    source: null,
    variants: [],
    revision: 1,
    createdAt: "",
    updatedAt: "",
    ...overrides
  };
}

function renderList(overrides: Partial<React.ComponentProps<typeof InboxItemList>> = {}) {
  const onSelectId = vi.fn();
  const items = [makeItem(), makeItem({ id: "i2", title: "第二个想法" })];
  render(
    <InboxItemList
      items={items}
      filteredItems={items}
      selectedId="i1"
      onSelectId={onSelectId}
      confirmingId={null}
      onConfirmDelete={vi.fn()}
      onDelete={vi.fn()}
      convertingIds={new Set()}
      onConvertToCard={vi.fn()}
      hasMore={false}
      loadingMore={false}
      onLoadMore={vi.fn()}
      draft={EMPTY_DRAFT}
      saveStatus="idle"
      onDraftChange={vi.fn()}
      onSaveDraft={vi.fn()}
      aiAvailable={false}
      aiSettings={null}
      isAIRunning={false}
      onRequestAI={vi.fn()}
      onAdoptVariant={vi.fn()}
      onCopyVariant={vi.fn()}
      onRemoveVariant={vi.fn()}
      {...overrides}
    />
  );
  return { onSelectId };
}

describe("InboxItemList 键盘路径", () => {
  it("标题是原生按钮，可用键盘激活并选中条目", () => {
    const { onSelectId } = renderList();
    const titles = screen.getAllByRole("button", { name: /打开想法/ });
    expect(titles).toHaveLength(2);

    const second = titles[1] as HTMLButtonElement;
    expect(second.tagName).toBe("BUTTON");
    expect(second.getAttribute("type")).toBe("button");
    expect(second.getAttribute("aria-label")).toBe("打开想法：第二个想法");
    expect(second.className).toContain("inbox-item-title");

    // 按钮的 click 由原生 Enter/空格触发；这里验证处理链与冒泡隔离。
    fireEvent.click(second);
    expect(onSelectId).toHaveBeenCalledWith("i2");
    expect(onSelectId).toHaveBeenCalledTimes(1);

    second.focus();
    expect(document.activeElement).toBe(second);
  });

  it("选中的条目在标题按钮上暴露 aria-current", () => {
    renderList();
    const titles = screen.getAllByRole("button", { name: /打开想法/ });
    expect(titles[0]!.getAttribute("aria-current")).toBe("true");
    expect(titles[1]!.getAttribute("aria-current")).toBeNull();
  });

  it("标题按钮不把激活冒泡给行点击（避免重复选中）", () => {
    const { onSelectId } = renderList();
    const first = screen.getAllByRole("button", { name: /打开想法/ })[0]!;

    // 行级 onClick 与标题按钮都调用 onSelectId：若 stopPropagation 失效会收到两次。
    fireEvent.click(first);
    expect(onSelectId).toHaveBeenCalledTimes(1);
  });
});
