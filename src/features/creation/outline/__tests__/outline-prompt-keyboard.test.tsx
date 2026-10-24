// @vitest-environment jsdom
/**
 * BD-132：大纲改名/拆章提示框（PromptDialog）的键盘契约。
 *
 * 旧实现 Esc 只挂在输入框上——焦点一到「取消/确定」就关不掉；也没有 Tab 环与关闭后的
 * 焦点归还。改用 useModalKeyboard 后：Esc 在任意控件上生效并归还焦点，Tab 首尾成环。
 */
import React from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { OutlineTree } from "../OutlineTree";
import { makeOutline } from "./fixtures";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

afterEach(cleanup);

function renderTree() {
  render(
    <OutlineTree
      outline={makeOutline()}
      workflow={["草稿", "已完成"]}
      selectedSceneId={undefined}
      onSelectScene={vi.fn()}
      runStructure={vi.fn().mockResolvedValue(true)}
      previewStructure={vi.fn().mockResolvedValue({ ok: true, planId: "p", command: {}, rows: [], stale: false })}
      applyStructureWithProtection={vi.fn().mockResolvedValue({ ok: true, protectionSnapshotId: "s", affected: [], newRevision: 1 })}
    />
  );
}

describe("BD-132 大纲提示框键盘契约", () => {
  it("焦点在确定按钮上按 Esc 也能关闭，并把焦点还给拆章按钮", async () => {
    renderTree();
    const split = screen.getAllByTitle("从该场景拆为新章")[1] as HTMLElement;
    split.focus();
    fireEvent.click(split);
    const dialog = await screen.findByRole("dialog");

    const confirm = within(dialog).getByRole("button", { name: "确定" });
    confirm.focus();
    fireEvent.keyDown(confirm, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(document.activeElement).toBe(split);
  });

  it("Tab 在提示框内首尾成环", async () => {
    renderTree();
    fireEvent.click(screen.getAllByTitle("从该场景拆为新章")[1]);
    const dialog = await screen.findByRole("dialog");
    const input = within(dialog).getByRole("textbox");
    const confirm = within(dialog).getByRole("button", { name: "确定" });

    await waitFor(() => expect(document.activeElement).toBe(input));

    fireEvent.keyDown(confirm, { key: "Tab" });
    expect(document.activeElement).toBe(input);

    fireEvent.keyDown(input, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(confirm);
  });
});
