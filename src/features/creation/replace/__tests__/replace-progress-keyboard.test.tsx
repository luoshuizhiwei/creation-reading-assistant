// @vitest-environment jsdom
/**
 * BD-134：查找替换进度浮层的键盘契约。
 *
 * 浮层此前只有 role="dialog"：Esc 不生效、打开后焦点留在背后的查找面板、Tab 能穿到
 * 被浮层盖住的控件上。接入 useModalKeyboard 后要求：
 * - 扫描阶段 Esc = 取消；
 * - 事务提交中（isApplying）Esc 必须无效，不能假装取消；
 * - 结果/错误阶段 Esc = 关闭；
 * - Tab 在浮层内首尾成环。
 */
import React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReplaceProgressDialog } from "@/features/creation/replace/ReplaceProgressDialog";
import type { ReplacePlanProgress } from "@/features/creation/replace/types";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

const progress: ReplacePlanProgress = {
  phase: "scanning",
  completedScenes: 3,
  totalScenes: 10,
  completedHits: 4,
  totalHits: 12
};

function renderDialog(overrides: Partial<React.ComponentProps<typeof ReplaceProgressDialog>> = {}) {
  const onCancel = vi.fn();
  const onClose = vi.fn();
  const view = render(
    <div>
      <button type="button">背后的查找面板</button>
      <ReplaceProgressDialog
        open
        progress={progress}
        isApplying={false}
        error={null}
        result={null}
        onCancel={onCancel}
        onClose={onClose}
        {...overrides}
      />
    </div>
  );
  return { view, onCancel, onClose };
}

describe("ReplaceProgressDialog 键盘契约", () => {
  it("扫描阶段 Esc 取消，并把焦点还给打开前的元素", async () => {
    const onCancel = vi.fn();
    function Harness() {
      const [open, setOpen] = React.useState(false);
      return (
        <div>
          <button type="button" onClick={() => setOpen(true)}>开始替换</button>
          {open && (
            <ReplaceProgressDialog
              open
              progress={progress}
              isApplying={false}
              error={null}
              result={null}
              onCancel={() => { onCancel(); setOpen(false); }}
              onClose={() => setOpen(false)}
            />
          )}
        </div>
      );
    }
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: "开始替换" });
    trigger.focus();
    fireEvent.click(trigger);

    const dialog = await screen.findByTestId("replace-progress-dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document.activeElement!, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByTestId("replace-progress-dialog")).toBeNull());
    await waitFor(() => expect(document.activeElement).toBe(trigger));
  });

  it("事务提交中 Esc 不取消也不关闭", async () => {
    const { onCancel, onClose } = renderDialog({ isApplying: true });
    const dialog = screen.getByTestId("replace-progress-dialog");
    expect(screen.getByTestId("replace-apply-locked")).toBeTruthy();

    fireEvent.keyDown(dialog, { key: "Escape" });
    expect(onCancel).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("结果阶段 Esc 关闭", async () => {
    const { onCancel, onClose } = renderDialog({
      progress: null,
      result: {
        planId: "plan-1",
        appliedHitCount: 2,
        modifiedSceneIds: ["s1"],
        snapshotIds: ["sn1"],
        sequence: 3,
        committedAt: ""
      }
    });
    const dialog = screen.getByTestId("replace-progress-dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document.activeElement!, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("Tab 在浮层内首尾成环，不穿到背后控件", async () => {
    const { view } = renderDialog();
    const dialog = screen.getByTestId("replace-progress-dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    const buttons = Array.from(dialog.querySelectorAll<HTMLButtonElement>("button"));
    const first = buttons[0]!;
    const last = buttons[buttons.length - 1]!;

    last.focus();
    fireEvent.keyDown(last, { key: "Tab" });
    expect(document.activeElement).toBe(first);

    first.focus();
    fireEvent.keyDown(first, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(last);

    const behind = view.container.querySelector("button")!;
    expect(dialog.contains(behind)).toBe(false);
    expect(document.activeElement).not.toBe(behind);
  });
});
