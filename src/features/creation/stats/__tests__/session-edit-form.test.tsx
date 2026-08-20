// @vitest-environment jsdom
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { SessionEditForm } from "../SessionEditForm";
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

describe("SessionEditForm 会话修正", () => {
  it("预填会话当前值", () => {
    render(<SessionEditForm session={session} projectId="project-1" onCancel={() => {}} onSave={vi.fn()} />);
    expect((screen.getByLabelText(/活动时长/) as HTMLInputElement).value).toBe("1200");
    expect((screen.getByLabelText(/净增字数/) as HTMLInputElement).value).toBe("400");
    expect(screen.getByLabelText(/开始时间/)).toBeDefined();
  });

  it("提交合法修正：onSave 收到含归属与规范化值的载荷", async () => {
    const onSave = vi.fn(async () => true);
    const onCancel = vi.fn();
    render(<SessionEditForm session={session} projectId="project-1" onCancel={onCancel} onSave={onSave} />);
    fireEvent.change(screen.getByLabelText(/活动时长/), { target: { value: "1500" } });
    fireEvent.change(screen.getByLabelText(/净增字数/), { target: { value: "520" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修正" }));
    await vi.waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const draft = onSave.mock.calls[0][0] as { projectId: string; sessionId: string; activeSeconds: number; netChars: number };
    expect(draft.projectId).toBe("project-1");
    expect(draft.sessionId).toBe("session-1");
    expect(draft.activeSeconds).toBe(1500);
    expect(draft.netChars).toBe(520);
    expect(draft).not.toHaveProperty("text");
    expect(onCancel).toHaveBeenCalled();
  });

  it("非法时长被阻止提交并逐项提示", async () => {
    const onSave = vi.fn();
    render(<SessionEditForm session={session} projectId="project-1" onCancel={() => {}} onSave={onSave} />);
    fireEvent.change(screen.getByLabelText(/活动时长/), { target: { value: "90000" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修正" }));
    expect(onSave).not.toHaveBeenCalled();
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("0 至 86400 秒");
  });

  it("多个非法字段同时提示", async () => {
    const onSave = vi.fn();
    render(<SessionEditForm session={session} projectId="project-1" onCancel={() => {}} onSave={onSave} />);
    fireEvent.change(screen.getByLabelText(/活动时长/), { target: { value: "-5" } });
    fireEvent.change(screen.getByLabelText(/净增字数/), { target: { value: "999999999" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修正" }));
    expect(onSave).not.toHaveBeenCalled();
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("0 至 86400 秒");
    expect(alert.textContent).toContain("±1,000,000");
  });

  it("onSave 失败时保持表单打开并展示错误", async () => {
    const onSave = vi.fn(async () => false);
    const onCancel = vi.fn();
    render(<SessionEditForm session={session} projectId="project-1" onCancel={onCancel} onSave={onSave} error="会话已被删除或所属项目不匹配。" />);
    fireEvent.click(screen.getByRole("button", { name: "保存修正" }));
    await vi.waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onCancel).not.toHaveBeenCalled();
    expect(screen.getByText(/会话已被删除或所属项目不匹配/)).toBeDefined();
  });
});
