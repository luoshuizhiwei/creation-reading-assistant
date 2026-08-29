// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SceneAiReport } from "@/features/creation/ai/SceneAiReport";

afterEach(() => cleanup());

describe("SceneAiReport（D-C2 切片 3：一致性检查报告）", () => {
  it("按空行/【分块呈现报告文本且只读", () => {
    const onClose = vi.fn();
    render(
      <SceneAiReport
        actionLabel="一致性检查"
        content={"【事实矛盾】高 场景1 → 他在雨夜到访，但前文说当天没有下雨。\n\n【伏笔未回收】中 借书卡 → 全篇未回收。\n\n总体结论：主干一致，两处需处理。"}
        model="test-model"
        onClose={onClose}
      />
    );
    const dialog = screen.getByTestId("scene-ai-report");
    expect(dialog.textContent).toContain("【事实矛盾】");
    expect(dialog.textContent).toContain("【伏笔未回收】");
    expect(dialog.textContent).toContain("总体结论");
    expect(dialog.textContent).toContain("test-model");
    // 报告不提供「采纳」类按钮，只有关闭
    expect(screen.queryByTestId("scene-candidate-accept")).toBeNull();
    expect(screen.getByTestId("scene-ai-report-close")).toBeDefined();
  });

  it("关闭回调触发", () => {
    const onClose = vi.fn();
    render(<SceneAiReport actionLabel="一致性检查" content="总体结论：无问题。" model="m" onClose={onClose} />);
    fireEvent.click(screen.getByTestId("scene-ai-report-close"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
