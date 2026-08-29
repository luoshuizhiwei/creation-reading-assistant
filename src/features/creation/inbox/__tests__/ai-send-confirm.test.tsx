// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AiSendConfirmDialog } from "@/features/creation/inbox/ai-send-confirm";
import { buildAiContextPack } from "@/features/creation/ai/build-ai-context";

afterEach(() => cleanup());

describe("AiSendConfirmDialog 分组模式（D-C2 全量）", () => {
  const pack = buildAiContextPack({
    sceneTitle: "雨夜",
    sceneBodyText: "闭馆铃响过第三遍。",
    planning: { goal: "查明借书卡的主人" },
    cards: [],
    annotations: []
  });

  it("按组展示字符数与 token，确认回传排除后的合成文本", () => {
    const onConfirm = vi.fn();
    render(
      <AiSendConfirmDialog
        actionLabel="润色"
        title="雨夜"
        content=""
        pack={pack}
        target="test-model"
        busy={false}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />
    );
    // 汇总行包含字符数与 token 估算
    expect(screen.getByTestId("ai-send-confirm").textContent).toContain("token");
    expect(screen.getByTestId("ai-send-confirm").textContent).toContain("任务卡");

    // 排除任务卡组
    fireEvent.click(screen.getByLabelText("包含 任务卡"));
    fireEvent.click(screen.getByTestId("ai-send-confirm-go"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    const finalContent = onConfirm.mock.calls[0]![0] as string;
    expect(finalContent).toContain("【正文 · 雨夜】");
    expect(finalContent).not.toContain("【任务卡】");
    expect(onConfirm.mock.calls[0]![1]).toBe(false);
  });

  it("全部组排除时发送按钮禁用", () => {
    render(
      <AiSendConfirmDialog
        actionLabel="润色"
        title="雨夜"
        content=""
        pack={pack}
        target="test-model"
        busy={false}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    );
    for (const group of pack.groups) {
      fireEvent.click(screen.getByLabelText(`包含 ${group.label}`));
    }
    const go = screen.getByTestId("ai-send-confirm-go") as HTMLButtonElement;
    expect(go.disabled).toBe(true);
  });

  it("取消回传不触发 onConfirm", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <AiSendConfirmDialog
        actionLabel="润色"
        title="雨夜"
        content="正文"
        target="test-model"
        busy={false}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "取消" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
