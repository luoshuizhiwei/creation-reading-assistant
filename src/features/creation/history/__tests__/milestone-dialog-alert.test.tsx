// @vitest-environment jsdom
import React from "react";
import { beforeAll, afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CreateMilestoneDialog } from "@/features/creation/history/CreateMilestoneDialog";
import type { CreationProjectNavigation } from "@/types/creation";

beforeAll(() => {
  // jsdom 没有 rAF，Dialog 的首焦点在 rAF 回调里。
  globalThis.requestAnimationFrame = ((cb: FrameRequestCallback) =>
    setTimeout(() => cb(0), 0) as unknown as number) as typeof requestAnimationFrame;
  globalThis.cancelAnimationFrame = ((id: number) => clearTimeout(id)) as typeof cancelAnimationFrame;
});

afterEach(() => cleanup());

const navigation: CreationProjectNavigation = {
  projectId: "p1",
  chapters: [
    { id: "ch1", title: "第一章", sortOrder: 0, scenes: [{ id: "scene-a", title: "场景 A", revision: 1 }] }
  ]
};

describe("历史里程碑对话框 · 内联校验", () => {
  // BD-151：只输入空白时，内联错误此前只是普通文本，读屏器不会播报。
  it("空白的里程碑名称以 alert 暴露内联错误", () => {
    render(
      <CreateMilestoneDialog
        projectId="p1"
        navigation={navigation}
        cards={[]}
        onCancel={vi.fn()}
        onSubmit={vi.fn(async () => true)}
      />
    );

    fireEvent.change(screen.getByLabelText("里程碑名称"), { target: { value: "   " } });
    expect(screen.getByRole("alert").textContent).toContain("名称不能为空");
  });

  // BD-174：错误只是旁边的一行字，输入框本身既没标错、也没指向它。
  it("空白名称让输入框带上 aria-invalid 并关联错误（BD-174）", () => {
    render(
      <CreateMilestoneDialog
        projectId="p1"
        navigation={navigation}
        cards={[]}
        onCancel={vi.fn()}
        onSubmit={vi.fn(async () => true)}
      />
    );

    const input = screen.getByLabelText("里程碑名称");
    expect(input.getAttribute("aria-invalid")).toBe("false");

    fireEvent.change(input, { target: { value: "   " } });
    expect(input.getAttribute("aria-invalid")).toBe("true");
    expect(input.getAttribute("aria-describedby")).toBe("history-reason-error");
    expect(document.getElementById("history-reason-error")?.textContent).toContain("名称不能为空");

    fireEvent.change(input, { target: { value: "初稿完成" } });
    expect(input.getAttribute("aria-invalid")).toBe("false");
    expect(input.getAttribute("aria-describedby")).toBeNull();
  });
});
