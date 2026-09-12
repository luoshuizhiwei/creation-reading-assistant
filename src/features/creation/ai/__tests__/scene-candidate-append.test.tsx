// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SceneCandidateReview } from "@/features/creation/ai/SceneCandidateReview";

afterEach(() => cleanup());

describe("SceneCandidateReview 续写追加模式（Stage 4-D）", () => {
  it("续写候选按「原正文 + 新段落」预览，原段落全部显示为保留", () => {
    render(
      <SceneCandidateReview
        candidate={{ action: "continuation", content: "他放下斗篷，没有立刻开口。", model: "m", mode: "append" }}
        currentBodyText={"他推门进来。\n斗篷还在滴水。"}
        busy={false}
        onAccept={vi.fn()}
        onDiscard={vi.fn()}
      />
    );
    const dialog = screen.getByTestId("scene-candidate-review");
    expect(dialog.textContent).toContain("保留 2 段");
    expect(dialog.textContent).toContain("新增 1 段");
    expect(dialog.textContent).toContain("删除 0 段");
    expect(dialog.textContent).toContain("他推门进来。");
    expect(dialog.textContent).toContain("他放下斗篷，没有立刻开口。");
    expect(screen.getByTestId("scene-candidate-append-hint").textContent).toContain("追加到当前正文末尾");
  });

  it("续写采纳回传候选原文：合并预览只用于展示，落库由调用方按 mode 决定", () => {
    const onAccept = vi.fn();
    render(
      <SceneCandidateReview
        candidate={{ action: "continuation", content: "新增一句。", model: "m", mode: "append" }}
        currentBodyText="原有正文。"
        busy={false}
        onAccept={onAccept}
        onDiscard={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId("scene-candidate-accept"));
    expect(onAccept).toHaveBeenCalledWith("新增一句。");
  });

  it("精简候选仍是替换语义，回传候选整篇", () => {
    const onAccept = vi.fn();
    render(
      <SceneCandidateReview
        candidate={{ action: "condensing", content: "精简后正文。", model: "m", mode: "replace" }}
        currentBodyText="冗长的原正文。"
        busy={false}
        onAccept={onAccept}
        onDiscard={vi.fn()}
      />
    );
    expect(screen.queryByTestId("scene-candidate-append-hint")).toBeNull();
    fireEvent.click(screen.getByTestId("scene-candidate-accept"));
    expect(onAccept).toHaveBeenCalledWith("精简后正文。");
  });

  it("标题显示中文动作名而非内部 action 键", () => {
    render(
      <SceneCandidateReview
        candidate={{ action: "continuation", content: "x", model: "m", mode: "append" }}
        currentBodyText="y"
        busy={false}
        onAccept={vi.fn()}
        onDiscard={vi.fn()}
      />
    );
    expect(screen.getByTestId("scene-candidate-review").textContent).toContain("续写场景");
  });

  it("追加模式按钮文案为「追加到正文」", () => {
    render(
      <SceneCandidateReview
        candidate={{ action: "continuation", content: "x", model: "m", mode: "append" }}
        currentBodyText="y"
        busy={false}
        onAccept={vi.fn()}
        onDiscard={vi.fn()}
      />
    );
    expect(screen.getByTestId("scene-candidate-accept").textContent).toContain("追加到正文");
  });
});
