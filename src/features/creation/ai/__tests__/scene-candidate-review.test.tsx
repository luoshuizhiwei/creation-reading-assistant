// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SceneCandidateReview } from "@/features/creation/ai/SceneCandidateReview";

afterEach(() => cleanup());

describe("SceneCandidateReview（D-C2 切片 2）", () => {
  it("渲染段落 diff 统计与逐行差异，采纳回传候选全文", () => {
    const onAccept = vi.fn();
    const onDiscard = vi.fn();
    render(
      <SceneCandidateReview
        candidate={{ action: "润色", content: "开头。\n新中段。\n结尾。", model: "test-model" }}
        currentBodyText={"开头。\n旧中段。\n结尾。"}
        busy={false}
        onAccept={onAccept}
        onDiscard={onDiscard}
      />
    );
    const dialog = screen.getByTestId("scene-candidate-review");
    expect(dialog.textContent).toContain("新增 1 段 · 删除 1 段");
    expect(dialog.textContent).toContain("新中段。");
    expect(dialog.textContent).toContain("旧中段。");
    expect(dialog.textContent).toContain("test-model");

    fireEvent.click(screen.getByTestId("scene-candidate-accept"));
    expect(onAccept).toHaveBeenCalledWith("开头。\n新中段。\n结尾。");
    expect(onDiscard).not.toHaveBeenCalled();
  });

  it("丢弃回调触发且不采纳", () => {
    const onAccept = vi.fn();
    const onDiscard = vi.fn();
    render(
      <SceneCandidateReview
        candidate={{ action: "扩写", content: "全新正文。", model: "m" }}
        currentBodyText="原正文。"
        busy={false}
        onAccept={onAccept}
        onDiscard={onDiscard}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "丢弃候选" }));
    expect(onDiscard).toHaveBeenCalledTimes(1);
    expect(onAccept).not.toHaveBeenCalled();
  });

  it("busy 时按钮禁用", () => {
    render(
      <SceneCandidateReview
        candidate={{ action: "润色", content: "新。", model: "m" }}
        currentBodyText="旧。"
        busy
        onAccept={vi.fn()}
        onDiscard={vi.fn()}
      />
    );
    const accept = screen.getByTestId("scene-candidate-accept") as HTMLButtonElement;
    expect(accept.disabled).toBe(true);
    expect(accept.textContent).toContain("保存中");
  });
});
