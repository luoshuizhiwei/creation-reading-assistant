// @vitest-environment jsdom
/**
 * BD-133：AI 侧三个 aria-modal 弹层（发送确认 / 报告 / 候选评审）此前都没有任何键盘处理：
 * Esc 不关、焦点留在背后、Tab 可穿出。接入 useModalKeyboard 后统一钉住 Esc + Tab 环。
 */
import React from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AiSendConfirmDialog } from "@/features/creation/inbox/ai-send-confirm";
import { SceneAiReport } from "@/features/creation/ai/SceneAiReport";
import { SceneCandidateReview } from "@/features/creation/ai/SceneCandidateReview";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});
afterEach(cleanup);

const sendProps = {
  actionLabel: "润色",
  title: "",
  content: "窗外正下着雨。",
  target: "openai / gpt",
  onConfirm: vi.fn(),
  onCancel: vi.fn()
};

describe("BD-133 AI 弹层键盘契约", () => {
  it("发送确认：Esc 取消；busy 期间 Esc 不关闭", async () => {
    const onCancel = vi.fn();
    const { rerender } = render(<AiSendConfirmDialog {...sendProps} busy={false} onCancel={onCancel} />);
    const dialog = screen.getByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document.activeElement as Element, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledTimes(1);

    rerender(<AiSendConfirmDialog {...sendProps} busy onCancel={onCancel} />);
    const dialog2 = screen.getByRole("dialog");
    fireEvent.keyDown(dialog2, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("发送确认：Tab 在弹层内首尾成环", async () => {
    render(<AiSendConfirmDialog {...sendProps} busy={false} />);
    const dialog = screen.getByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    const remember = within(dialog).getByRole("checkbox");
    const send = within(dialog).getByTestId("ai-send-confirm-go");
    send.focus();
    fireEvent.keyDown(send, { key: "Tab" });
    expect(document.activeElement).toBe(remember);

    fireEvent.keyDown(remember, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(send);
  });

  it("AI 报告：Esc 关闭", async () => {
    const onClose = vi.fn();
    render(<SceneAiReport actionLabel="一致性检查" content="【问题】第一段" model="m1" onClose={onClose} />);
    const dialog = screen.getByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
    fireEvent.keyDown(document.activeElement as Element, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("候选评审：Esc 等同丢弃候选，Tab 环闭合", async () => {
    const onDiscard = vi.fn();
    render(
      <SceneCandidateReview
        candidate={{ action: "polish", content: "窗外正下着大雨。", model: "m1" }}
        currentBodyText="窗外正下着雨。"
        busy={false}
        onAccept={vi.fn()}
        onDiscard={onDiscard}
      />
    );
    const dialog = screen.getByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    const discard = within(dialog).getByRole("button", { name: "丢弃候选" });
    const close = within(dialog).getByRole("button", { name: "关闭候选评审" });
    close.focus();
    fireEvent.keyDown(close, { key: "Tab" });
    expect(document.activeElement).toBe(discard);

    fireEvent.keyDown(discard, { key: "Escape" });
    expect(onDiscard).toHaveBeenCalledTimes(1);
  });
});
