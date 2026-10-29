import { describe, expect, it } from "vitest";
import { ZOOM_MAX, ZOOM_MIN, resolveZoomFactor } from "../zoom-shortcuts";

/**
 * BD-218 账本（主进程侧）：WCAG 1.4.4 文本缩放。
 * 只测纯函数——按键到 zoomFactor 的换算与上下限；真实按键链由
 * scripts/audit-text-zoom.mjs 走 webContents.sendInputEvent 实测。
 */
describe("resolveZoomFactor（BD-218）", () => {
  it("Ctrl/Cmd + =/+ 每档 10%，到 200% 停住", () => {
    expect(resolveZoomFactor(1, "=", true, false)).toBe(1.1);
    expect(resolveZoomFactor(1, "+", true, false)).toBe(1.1);
    expect(resolveZoomFactor(1.95, "=", true, false)).toBe(ZOOM_MAX);
    expect(resolveZoomFactor(2, "=", true, false)).toBe(ZOOM_MAX);
  });

  it("Ctrl/Cmd + - 每档 10%，到 50% 停住", () => {
    expect(resolveZoomFactor(1, "-", true, false)).toBe(0.9);
    expect(resolveZoomFactor(0.55, "-", true, false)).toBe(ZOOM_MIN);
    expect(resolveZoomFactor(0.5, "-", true, false)).toBe(ZOOM_MIN);
  });

  it("Ctrl/Cmd + 0 还原 100%", () => {
    expect(resolveZoomFactor(2, "0", true, false)).toBe(1);
    expect(resolveZoomFactor(0.5, "0", true, false)).toBe(1);
  });

  it("没有 Ctrl/Cmd 或按着 Alt 都不抢键", () => {
    expect(resolveZoomFactor(1, "=", false, false)).toBeNull();
    expect(resolveZoomFactor(1, "=", true, true)).toBeNull();
    expect(resolveZoomFactor(1, "0", false, false)).toBeNull();
  });

  it("其它键原样放行", () => {
    expect(resolveZoomFactor(1, "k", true, false)).toBeNull();
    expect(resolveZoomFactor(1, "Escape", true, false)).toBeNull();
  });

  it("累加不产生浮点尾巴", () => {
    expect(resolveZoomFactor(1.9, "=", true, false)).toBe(2);
    expect(resolveZoomFactor(0.7, "-", true, false)).toBe(0.6);
  });
});
