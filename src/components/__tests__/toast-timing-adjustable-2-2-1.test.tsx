// @vitest-environment jsdom
/**
 * BD-215 账本：WCAG 2.2.1 时限可调（Level A）。
 *
 * 主判据是运行时普查 scripts/audit-timing-adjustable.mjs：用卡片库的确定性失败
 * 路径造出真实 error toast，实测「默认会消失 / 悬停延长 / 焦点延长 / 离开后接回」。
 *
 * 本批真实缺陷：toast 到点消失是一条无条件 setTimeout（错误 6.5 秒、其余 4.2 秒），
 * 错误原因只活在这条提示里，用户既不能关停也不能延长——2.2.1 要求三者至少给一条。
 * 修复：倒计时句柄收进 ui-store，ToastCenter 在指针悬停或焦点停留时暂停、离开后按
 * 剩余时长续计（停留多久延长多久）；关闭键保留（关停/缩短）。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ToastCenter } from "@/components/interaction";
import { useUIStore } from "@/stores/ui-store";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-timing-adjustable.mjs");
const storeSource = read("../../stores/ui-store.ts");
const interaction = read("../interaction.tsx");
const pkg = read("../../../package.json");

const advance = (ms: number) => act(() => { vi.advanceTimersByTime(ms); });
const toastCount = () => useUIStore.getState().toasts.length;

beforeEach(() => {
  vi.useFakeTimers();
  act(() => {
    for (const toast of useUIStore.getState().toasts) useUIStore.getState().dismissToast(toast.id);
  });
});
afterEach(() => {
  act(() => {
    for (const toast of useUIStore.getState().toasts) useUIStore.getState().dismissToast(toast.id);
  });
  cleanup();
  vi.useRealTimers();
});

describe("BD-215 store：时限默认生效，停留多久延长多久，恢复按剩余时长续计", () => {
  it("未干预时普通提示 4.2 秒、错误提示 6.5 秒后消失", () => {
    act(() => { useUIStore.getState().showToast({ tone: "success", title: "已保存" }); });
    advance(4199);
    expect(toastCount()).toBe(1);
    advance(1);
    expect(toastCount()).toBe(0);

    act(() => { useUIStore.getState().showToast({ tone: "error", title: "导出失败", body: "磁盘只读" }); });
    advance(6499);
    expect(toastCount()).toBe(1);
    advance(1);
    expect(toastCount()).toBe(0);
  });

  it("pauseToast 期间时钟停走；resumeToast 只把剩余时长接着计", () => {
    let id = "";
    act(() => { id = useUIStore.getState().showToast({ tone: "error", title: "导出失败", body: "磁盘只读" }); });
    advance(6000);
    act(() => { useUIStore.getState().pauseToast(id); });
    advance(60000);
    expect(toastCount(), "暂停期间必须一直可见").toBe(1);
    act(() => { useUIStore.getState().resumeToast(id); });
    advance(499);
    expect(toastCount()).toBe(1);
    advance(1);
    expect(toastCount(), "剩余 500ms 走完即消失").toBe(0);
  });

  it("手动关闭会清掉倒计时，不会在之后误伤同 id 的新提示", () => {
    let id = "";
    act(() => { id = useUIStore.getState().showToast({ tone: "info", title: "候选已移除" }); });
    act(() => { useUIStore.getState().dismissToast(id); });
    expect(toastCount()).toBe(0);
    advance(10000);
    expect(toastCount()).toBe(0);
  });
});

describe("BD-215 ToastCenter：悬停/焦点即延长", () => {
  it("指针悬停超过默认时长仍可见，离开后正常消失", () => {
    render(<ToastCenter />);
    act(() => { useUIStore.getState().showToast({ tone: "error", title: "导出失败", body: "磁盘只读" }); });
    const region = screen.getByRole("status", { name: "通知" });
    fireEvent.mouseOver(region, { relatedTarget: document.body });
    advance(9000);
    expect(toastCount(), "悬停时不能消失").toBe(1);
    fireEvent.mouseOut(region, { relatedTarget: document.body });
    advance(6499);
    expect(toastCount()).toBe(1);
    advance(1);
    expect(toastCount()).toBe(0);
  });

  it("键盘焦点停在提示内超过默认时长仍可见，失焦后正常消失", () => {
    render(<ToastCenter />);
    act(() => { useUIStore.getState().showToast({ tone: "error", title: "导出失败", body: "磁盘只读" }); });
    const close = screen.getByTitle("关闭提示");
    act(() => close.focus());
    advance(9000);
    expect(toastCount(), "焦点停留时不能消失").toBe(1);
    act(() => close.blur());
    advance(6500);
    expect(toastCount()).toBe(0);
  });

  it("悬停期间新到的提示也会被挂起（进入事件不会再发一次）", () => {
    render(<ToastCenter />);
    act(() => { useUIStore.getState().showToast({ tone: "info", title: "第一条" }); });
    const region = screen.getByRole("status", { name: "通知" });
    fireEvent.mouseOver(region, { relatedTarget: document.body });
    act(() => { useUIStore.getState().showToast({ tone: "error", title: "第二条" }); });
    advance(9000);
    expect(toastCount(), "两条都应在悬停期内存活").toBe(2);
  });

  it("被聚焦的提示被挤掉后，焦点标志不会把后续提示永久挂起", () => {
    render(<ToastCenter />);
    let id = "";
    act(() => { id = useUIStore.getState().showToast({ tone: "error", title: "第一条" }); });
    const close = screen.getByTitle("关闭提示");
    act(() => close.focus());
    act(() => { useUIStore.getState().dismissToast(id); });
    act(() => { useUIStore.getState().showToast({ tone: "success", title: "第二条" }); });
    advance(9000);
    expect(toastCount(), "新提示应正常到点消失").toBe(0);
  });

  it("在提示内按关闭键后，同叠其余提示的倒计时会接回", () => {
    render(<ToastCenter />);
    act(() => { useUIStore.getState().showToast({ tone: "warning", title: "第一条" }); });
    act(() => { useUIStore.getState().showToast({ tone: "error", title: "第二条" }); });
    const close = screen.getAllByTitle("关闭提示")[0];
    act(() => close.focus());
    advance(9000);
    expect(toastCount()).toBe(2);
    act(() => { fireEvent.click(close); });
    expect(toastCount()).toBe(1);
    advance(4199);
    expect(toastCount(), "剩余提示应恢复正常倒计时").toBe(1);
    advance(1);
    expect(toastCount()).toBe(0);
  });
});

describe("BD-215 普查与产品锚", () => {
  it("普查口径、红绿结论与退出码", () => {
    expect(census).toContain("TIME_LIMIT_RED");
    expect(census).toContain("TIME_LIMIT_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("toast.hover()");
    expect(census).toContain("el.focus()");
    expect(census).toContain("el.blur()");
    expect(census).toContain("2.2.1");
  });

  it("store 不再有无条件倒计时，暂停/恢复接口在位", () => {
    expect(storeSource).toContain("pauseToast");
    expect(storeSource).toContain("resumeToast");
    expect(storeSource).not.toContain('window.setTimeout(() => get().dismissToast(id), input.tone === "error" ? 6500 : 4200);');
    expect(storeSource).toContain("toastTimers");
    expect(storeSource).toContain("toastRemaining");
    expect(storeSource).toContain("按**剩余时长**");
  });

  it("ToastCenter 悬停与焦点两路停留都接到暂停，关闭键负责撤掉焦点停留", () => {
    expect(interaction).toContain("onMouseEnter");
    expect(interaction).toContain("onFocusCapture");
    expect(interaction).toContain("onBlurCapture");
    expect(interaction).toContain("focusHeld.current = false;");
  });

  it("npm 脚本已登记", () => {
    expect(pkg).toContain('"audit:timing-adjustable": "node scripts/audit-timing-adjustable.mjs"');
  });
});
