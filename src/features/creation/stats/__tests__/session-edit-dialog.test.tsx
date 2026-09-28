// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SessionEditDialog } from "../SessionEditDialog";
import type { SessionEntry } from "@/types/creation";

afterEach(cleanup);

const session: SessionEntry = {
  id: "session-1",
  projectId: "project-1",
  sceneId: "scene-a",
  startedAt: "2026-08-14T08:30:00.000Z",
  activeSeconds: 1200,
  netChars: 400,
  reportedAt: "2026-08-14T08:50:00.000Z"
};

describe("SessionEditDialog（复用 SessionEditForm）", () => {
  it("弹窗内渲染共享表单，未改动直接保存时提交空补丁", async () => {
    const onSave = vi.fn(async () => true);
    render(<SessionEditDialog open session={session} onClose={vi.fn()} onSave={onSave} />);
    expect(screen.getByTestId("session-edit-form")).toBeTruthy();
    fireEvent.click(screen.getByTestId("session-edit-save"));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave.mock.calls[0][0]).toEqual({});
  });

  it("只提交被改动的字段，并在保存成功后关闭", async () => {
    const onClose = vi.fn();
    const onSave = vi.fn(async () => true);
    render(<SessionEditDialog open session={session} onClose={onClose} onSave={onSave} />);
    fireEvent.change(screen.getByTestId("session-edit-active-seconds"), { target: { value: "1500" } });
    fireEvent.click(screen.getByTestId("session-edit-save"));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ activeSeconds: 1500 }));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("沿用 session-policy 校验：非法时长在弹窗内被阻止提交并提示", async () => {
    const onSave = vi.fn();
    render(<SessionEditDialog open session={session} onClose={vi.fn()} onSave={onSave} />);
    fireEvent.change(screen.getByTestId("session-edit-active-seconds"), { target: { value: "90000" } });
    fireEvent.click(screen.getByTestId("session-edit-save"));
    expect(onSave).not.toHaveBeenCalled();
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("0 至 86400 秒");
  });

  it("busy 时表单锁定：字段禁用、按钮显示保存中且不重复提交", async () => {
    const onSave = vi.fn(() => new Promise<boolean>(() => undefined));
    const { rerender } = render(<SessionEditDialog open session={session} onClose={vi.fn()} onSave={onSave} />);
    rerender(<SessionEditDialog open busy session={session} onClose={vi.fn()} onSave={onSave} />);
    expect((screen.getByTestId("session-edit-net-chars") as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByTestId("session-edit-save") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId("session-edit-save").textContent).toBe("保存中…");
    fireEvent.click(screen.getByTestId("session-edit-save"));
    expect(onSave).not.toHaveBeenCalled();
  });
});
