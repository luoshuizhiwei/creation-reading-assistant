// @vitest-environment jsdom
import React from "react";
import { fireEvent, render, screen, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PassphraseDialog } from "../PassphraseDialog";

describe("PassphraseDialog 扩展（mode / minLength / description）", () => {
  it("confirm 模式：无确认框与勾选，输入口令即可提交", () => {
    const onConfirm = vi.fn();
    render(<PassphraseDialog open mode="confirm" title="输入口令" onConfirm={onConfirm} />);
    expect(screen.queryByTestId("pe-confirm")).toBeNull();
    expect(screen.queryByTestId("pe-ack")).toBeNull();
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "s" } });
    const submit = screen.getByTestId("pe-submit") as HTMLButtonElement;
    expect(submit.disabled).toBe(false);
    fireEvent.click(submit);
    expect(onConfirm).toHaveBeenCalledWith("s");
  });

  it("confirm 模式：autoComplete 不为 new-password，避免密码管理器建议新口令", () => {
    render(<PassphraseDialog open mode="confirm" onConfirm={vi.fn()} />);
    expect(screen.getByTestId("pe-passphrase").getAttribute("autoComplete")).toBe("off");
  });

  it("create 模式：minLength 不足时显示提示并禁用提交", () => {
    render(<PassphraseDialog open minLength={8} onConfirm={vi.fn()} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "abc123" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "abc123" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    expect(screen.getByTestId("pe-too-short").textContent).toContain("8");
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
  });

  it("create 模式：达到 minLength 后提示消失且可提交", () => {
    const onConfirm = vi.fn();
    render(<PassphraseDialog open minLength={8} onConfirm={onConfirm} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "abcdefgh" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "abcdefgh" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    expect(screen.queryByTestId("pe-too-short")).toBeNull();
    fireEvent.click(screen.getByTestId("pe-submit"));
    expect(onConfirm).toHaveBeenCalledWith("abcdefgh");
  });

  it("description 存在时替换默认警示文案", () => {
    render(<PassphraseDialog open mode="confirm" description="口令错误不会改动当前数据。" onConfirm={vi.fn()} />);
    expect(screen.getByTestId("pe-description").textContent).toContain("不会改动当前数据");
    expect(screen.queryByTestId("pe-warning")).toBeNull();
  });

  it("重新打开时清空口令与勾选，避免上一次的输入直接可提交", () => {
    const onConfirm = vi.fn();
    const { rerender } = render(<PassphraseDialog open title="A" onConfirm={onConfirm} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "hunter2" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "hunter2" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    rerender(<PassphraseDialog open={false} title="A" onConfirm={onConfirm} onCancel={vi.fn()} />);
    rerender(<PassphraseDialog open title="B" onConfirm={onConfirm} onCancel={vi.fn()} />);
    expect((screen.getByTestId("pe-passphrase") as HTMLInputElement).value).toBe("");
    expect((screen.getByTestId("pe-ack") as HTMLInputElement).checked).toBe(false);
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByTestId("pe-submit"));
    expect(onConfirm).not.toHaveBeenCalled();
  });
});

afterEach(cleanup);
