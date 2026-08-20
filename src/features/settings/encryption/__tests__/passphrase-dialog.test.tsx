// @vitest-environment jsdom
import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PassphraseDialog } from "../PassphraseDialog";

describe("PassphraseDialog", () => {
  it("open=false 时不渲染", () => {
    const { container } = render(<PassphraseDialog open={false} onConfirm={vi.fn()} />);
    expect(container.querySelector('[data-testid="passphrase-dialog"]')).toBeNull();
  });

  it("渲染并提示忘记口令无法恢复", () => {
    render(<PassphraseDialog open onConfirm={vi.fn()} />);
    expect(screen.getByTestId("pe-warning").textContent).toContain("忘记口令无法恢复");
  });

  it("初始状态提交按钮禁用", () => {
    render(<PassphraseDialog open onConfirm={vi.fn()} />);
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("口令不一致时显示错误且禁用提交", () => {
    render(<PassphraseDialog open onConfirm={vi.fn()} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "secret1" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "secret2" } });
    expect(screen.getByTestId("pe-mismatch")).toBeTruthy();
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("口令一致且勾选已知晓后可提交，并回传口令", () => {
    const onConfirm = vi.fn();
    render(<PassphraseDialog open confirmLabel="加密备份" onConfirm={onConfirm} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "hunter2" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "hunter2" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    const submit = screen.getByTestId("pe-submit") as HTMLButtonElement;
    expect(submit.disabled).toBe(false);
    expect(submit.textContent).toBe("加密备份");
    fireEvent.click(submit);
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith("hunter2");
  });

  it("显示外部错误且不回传", () => {
    const onConfirm = vi.fn();
    render(<PassphraseDialog open error="口令错误" onConfirm={onConfirm} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "a" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "a" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    fireEvent.click(screen.getByTestId("pe-submit"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("pe-error").textContent).toBe("口令错误");
  });

  it("点击取消触发 onCancel", () => {
    const onCancel = vi.fn();
    render(<PassphraseDialog open onConfirm={vi.fn()} onCancel={onCancel} />);
    fireEvent.click(screen.getByTestId("pe-cancel"));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("busy 时禁用输入与按钮", () => {
    render(<PassphraseDialog open busy onConfirm={vi.fn()} onCancel={vi.fn()} />);
    expect((screen.getByTestId("pe-passphrase") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("pe-cancel") as HTMLButtonElement).disabled).toBe(true);
  });
});
