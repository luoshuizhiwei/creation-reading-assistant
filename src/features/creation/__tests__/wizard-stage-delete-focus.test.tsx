// @vitest-environment jsdom
/**
 * BD-219（BD-179/199 同族）：新建作品向导「删除阶段」后焦点交接。
 *
 * 删除阶段钮随阶段 chip 一起卸载，浏览器把焦点甩回 body（WCAG 2.4.3），键盘
 * 用户得从文档开头重新 Tab。交接判据：原位置下一阶段优先，末尾退前一个；
 * 阶段删空时退「添加自定义阶段」输入框。
 */
import React from "react";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CreateProjectWizard } from "@/features/creation/CreateProjectWizard";

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({ createProject: vi.fn(async () => undefined) })
}));

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

beforeEach(() => cleanup());

function openStepTwo() {
  render(<CreateProjectWizard open onClose={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
  fireEvent.change(screen.getByLabelText(/标题/), { target: { value: "焦点交接测试" } });
  fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
}

function deleteStage(stage: string) {
  const button = screen.getByRole("button", { name: `删除阶段 ${stage}` });
  button.focus();
  fireEvent.click(button);
}

describe("BD-219：向导阶段 chip 卸载焦点交接", () => {
  it("删中间阶段：焦点交给原位置下一阶段", () => {
    openStepTwo();
    deleteStage("写作中");
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "删除阶段 初稿" }));
  });

  it("删末尾阶段：末尾退前一个", () => {
    openStepTwo();
    deleteStage("已发布");
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "删除阶段 定稿" }));
  });

  it("阶段全删空：焦点退到「添加自定义阶段」输入框", () => {
    openStepTwo();
    for (const stage of ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]) {
      deleteStage(stage);
    }
    expect(document.activeElement).toBe(screen.getByRole("textbox", { name: "添加自定义阶段" }));
  });
});
