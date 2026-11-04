// @vitest-environment jsdom
/**
 * BD-244（WCAG 3.3.1 错误识别 + 3.3.3 纠错建议）：大纲提示框（PromptDialog）留空提交时
 * 必须「说话」，并把错误挂到那一格输入框上。
 *
 * 运行时实测（/tmp/bd244-probe2.log 的 N1／N1b 两档，大纲页·新建卷）——旧写法
 * `if (!title.trim()) return;` 之后的测量结果：
 *   ❗ 没有出现任何错误文本
 *   控件「新卷名称」值「」 invalid=- describedby=-
 *   焦点去向：button[确定] ｜ 弹层还在：是
 * 也就是说：确定按下去什么也没发生，弹层停在原地，读屏里既没有 alert，被拦下的输入框
 * 也没有任何错误状态——用户只能自己猜是名称不行还是按钮没点着。
 * 对照本仓自家既有形状（CreateProjectWizard.tsx:205-210、ReplacePanel.tsx:227-228）：
 * 文案 + 控件侧 aria-invalid + aria-describedby → 错误元素 id，错误元素带 role="alert"。
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

const runStructure = vi.fn().mockResolvedValue(true);

/** 打开「新建卷」提示框：标题「新卷名称」同时是输入框的可访问名。 */
function openVolumePrompt() {
  runStructure.mockClear();
  render(
    <OutlineTree
      outline={makeOutline()}
      workflow={["草稿", "已完成"]}
      selectedSceneId={undefined}
      onSelectScene={vi.fn()}
      runStructure={runStructure}
      previewStructure={vi.fn().mockResolvedValue({ ok: true, planId: "p", command: {}, rows: [], stale: false })}
      applyStructureWithProtection={vi.fn().mockResolvedValue({ ok: true, protectionSnapshotId: "s", affected: [], newRevision: 1 })}
    />
  );
  fireEvent.click(screen.getByRole("button", { name: /新建卷/ }));
  return screen.findByRole("dialog");
}

function emptyAndSubmit(dialog: HTMLElement, via: "button" | "enter") {
  const input = within(dialog).getByRole("textbox");
  fireEvent.change(input, { target: { value: "" } });
  if (via === "button") fireEvent.click(within(dialog).getByRole("button", { name: "确定" }));
  else fireEvent.keyDown(input, { key: "Enter" });
  return input;
}

describe("BD-244 大纲提示框：留空提交要说话（3.3.1／3.3.3）", () => {
  it("留空点「确定」：出现 role=alert 的错误文本，并点名这一格", async () => {
    const dialog = await openVolumePrompt();
    emptyAndSubmit(dialog, "button");

    const alert = await waitFor(() => {
      const el = within(dialog).getByRole("alert");
      expect(el.textContent).toContain("不能为空");
      return el;
    });
    // 3.3.3：不只是「不行」，还得说要怎么办。
    expect(alert.textContent).toContain("请填一个名字再提交");
    // 3.3.1 的「指认出错项」：本框标题就是输入框的可访问名，文案必须把它念出来。
    const input = within(dialog).getByRole("textbox");
    expect(alert.textContent).toContain(String(input.getAttribute("aria-label")));
  });

  it("留空按回车：同样说话，且不提交、不偷偷关窗", async () => {
    const dialog = await openVolumePrompt();
    emptyAndSubmit(dialog, "enter");

    await waitFor(() => expect(within(dialog).getByRole("alert").textContent).toContain("不能为空"));
    expect(runStructure).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).toBeTruthy();
  });

  it("错误挂在被拦下的控件上：aria-invalid + aria-describedby → 错误元素本身", async () => {
    const dialog = await openVolumePrompt();
    const input = emptyAndSubmit(dialog, "button");

    const alert = await waitFor(() => within(dialog).getByRole("alert"));
    expect(input.getAttribute("aria-invalid")).toBe("true");
    expect(input.getAttribute("aria-describedby")).toBe(alert.id);
    expect(document.getElementById(String(input.getAttribute("aria-describedby")))).toBe(alert);
  });

  it("改回有内容：错误撤掉，关联也撤掉", async () => {
    const dialog = await openVolumePrompt();
    const input = emptyAndSubmit(dialog, "button");
    await waitFor(() => expect(within(dialog).getByRole("alert")).toBeTruthy());

    fireEvent.change(input, { target: { value: "第一卷" } });
    await waitFor(() => expect(within(dialog).queryByRole("alert")).toBeNull());
    expect(input.getAttribute("aria-invalid")).toBeNull();
    expect(input.getAttribute("aria-describedby")).toBeNull();
  });

  it("有内容时照常提交（修好了不能把门焊死）", async () => {
    const dialog = await openVolumePrompt();
    const input = within(dialog).getByRole("textbox");
    fireEvent.change(input, { target: { value: "第一卷" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "确定" }));

    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(within(dialog).queryByRole("alert")).toBeNull();
  });
});
