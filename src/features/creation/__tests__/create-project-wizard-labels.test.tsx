// @vitest-environment jsdom
/**
 * BD-140：新建作品向导里「添加自定义阶段」输入框的可访问名。
 *
 * 该输入框与步骤 1/2 的其它字段不同：外部没有包裹 <label>，只有 placeholder
 * （「添加自定义阶段，回车确认」），输入后 placeholder 消失即失去全部提示。
 */
import React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
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

describe("CreateProjectWizard 可访问名", () => {
  it("步骤 2 的自定义阶段输入框有可访问名", async () => {
    render(<CreateProjectWizard open onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
    fireEvent.change(screen.getByLabelText(/标题/), { target: { value: "测试项目" } });
    fireEvent.click(screen.getByRole("button", { name: /下一步/ }));

    const input = await screen.findByRole("textbox", { name: "添加自定义阶段" });
    expect(input.getAttribute("placeholder")).toContain("添加自定义阶段");
  });

  // BD-151：标题校验错误此前只是普通文本，读屏器没有反馈。
  it("标题校验错误以 alert 暴露", () => {
    render(<CreateProjectWizard open onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
    const title = screen.getByLabelText(/标题/);
    fireEvent.change(title, { target: { value: "x" } });
    fireEvent.change(title, { target: { value: "" } });

    expect(screen.getByRole("alert").textContent).toBeTruthy();
  });

  // BD-165：标题旁的「必填」只是可见文字，输入框本身没有声明必填。
  it("必填标题声明 aria-required", () => {
    render(<CreateProjectWizard open onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
    expect(screen.getByLabelText(/标题/).getAttribute("aria-required")).toBe("true");
  });

  // BD-174：错误与提示此前只是输入框旁边的文字，字段本身没有指向它们。
  it("标题字段用 aria-describedby 关联提示与错误（BD-174）", () => {
    render(<CreateProjectWizard open onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: /下一步/ }));
    const title = screen.getByLabelText(/标题/);
    expect(title.getAttribute("aria-describedby")).toBe("creation-title-hint");
    expect(document.getElementById("creation-title-hint")?.textContent).toContain("1–200 字");

    fireEvent.change(title, { target: { value: "x" } });
    fireEvent.change(title, { target: { value: "" } });
    expect(title.getAttribute("aria-describedby")).toBe("creation-title-hint creation-title-error");
    expect(document.getElementById("creation-title-error")?.textContent).toBeTruthy();
  });
});
