// @vitest-environment jsdom
import React from "react";
import { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { InboxItemList } from "@/features/creation/inbox/components/InboxItemList";
import { EMPTY_DRAFT } from "@/features/creation/inbox/components/types";
import type { InboxItem } from "@/types/creation";

/**
 * BD-190：确认「移出」后按钮随整行卸载，焦点不得跌回 body（WCAG 2.4.3，BD-179 同型）。
 * 删除前记下一条，列表更新后交接；删空退到列表容器。
 */

function makeItem(id: string, title: string): InboxItem {
  return {
    id,
    legacyId: null,
    title,
    body: "窗外正下着雨。",
    type: "note",
    status: "inbox",
    tags: [],
    platformTags: [],
    source: null,
    variants: [],
    revision: 1,
    createdAt: "",
    updatedAt: ""
  };
}

function Harness({ initial }: { initial: InboxItem[] }) {
  const [items, setItems] = useState(initial);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  return (
    <InboxItemList
      items={items}
      filteredItems={items}
      selectedId={items[0]?.id}
      onSelectId={() => {}}
      confirmingId={confirmingId}
      onConfirmDelete={setConfirmingId}
      onDelete={(id) => {
        setConfirmingId(null);
        setItems((cur) => cur.filter((row) => row.id !== id));
      }}
      convertingIds={new Set()}
      onConvertToCard={() => {}}
      hasMore={false}
      loadingMore={false}
      onLoadMore={() => {}}
      draft={EMPTY_DRAFT}
      saveStatus="idle"
      onDraftChange={() => {}}
      onSaveDraft={async () => {}}
      aiAvailable={false}
      aiSettings={null}
      isAIRunning={false}
      onRequestAI={() => {}}
      onAdoptVariant={() => {}}
      onCopyVariant={() => {}}
      onRemoveVariant={() => {}}
    />
  );
}

function rowOf(title: string): HTMLElement {
  return screen.getByText(title).closest("li") as HTMLElement;
}

function armButton(title: string): HTMLElement {
  return within(rowOf(title)).getByRole("button", { name: "移出" });
}

function confirmButton(title: string): HTMLElement {
  return within(rowOf(title)).getByRole("button", { name: "确认移出" });
}

function press(el: HTMLElement): void {
  el.focus();
  fireEvent.click(el);
}

afterEach(() => cleanup());

describe("收件箱删除后的焦点交接（BD-190）", () => {
  it("删中间行：焦点交接给下一条的标题按钮", async () => {
    render(<Harness initial={[makeItem("i1", "雨夜开篇"), makeItem("i2", "第二个想法"), makeItem("i3", "第三个想法")]} />);
    press(armButton("第二个想法"));
    press(confirmButton("第二个想法"));
    await waitFor(() => expect(screen.queryByText("第二个想法")).toBeNull());
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "打开想法：第三个想法" }));
  });

  it("删末尾行：退回上一条；删空整列：退到列表容器本身", async () => {
    render(<Harness initial={[makeItem("i1", "雨夜开篇"), makeItem("i2", "第二个想法"), makeItem("i3", "第三个想法")]} />);
    press(armButton("第三个想法"));
    press(confirmButton("第三个想法"));
    await waitFor(() => expect(screen.queryByText("第三个想法")).toBeNull());
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "打开想法：第二个想法" }));

    cleanup();
    render(<Harness initial={[makeItem("i1", "雨夜开篇")]} />);
    press(armButton("雨夜开篇"));
    press(confirmButton("雨夜开篇"));
    await waitFor(() => expect(screen.getByText("收件箱为空。点击「新建想法」开始收集，旧灵感迁移后也会出现在这里。")).toBeTruthy());
    const list = document.querySelector("ul.inbox-list") as HTMLElement;
    expect(document.activeElement).toBe(list);
  });

  it("对照：只武装确认态不删除时，焦点原地不动", () => {
    render(<Harness initial={[makeItem("i1", "雨夜开篇"), makeItem("i2", "第二个想法")]} />);
    const arm = armButton("第二个想法");
    arm.focus();
    fireEvent.click(arm);
    expect(document.activeElement).toBe(arm);
    expect(confirmButton("第二个想法")).toBe(arm);
    expect(screen.queryByText("第二个想法")).toBeTruthy();
  });
});
