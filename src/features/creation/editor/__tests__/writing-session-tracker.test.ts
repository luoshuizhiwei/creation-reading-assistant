import { describe, expect, it, vi } from "vitest";
import {
  createWritingSessionTracker,
  SESSION_DEFAULT_IDLE_TIMEOUT_MS,
  type SessionSettleReport
} from "../writing-session-tracker";

function createTracker(overrides: { idleTimeoutMs?: number; minActiveMs?: number } = {}) {
  let now = 100_000;
  const settle = vi.fn();
  const tracker = createWritingSessionTracker({
    idleTimeoutMs: overrides.idleTimeoutMs ?? SESSION_DEFAULT_IDLE_TIMEOUT_MS,
    minActiveMs: overrides.minActiveMs ?? 0,
    now: () => now,
    onSettle: settle
  });
  return { tracker, settle, advance: (ms: number) => { now += ms; } };
}

describe("writing-session-tracker 活动信号", () => {
  it("输入信号开启段并计时；鼠标移动/焦点等无意义事件不产生活动（无对应 API）", () => {
    const { tracker, advance } = createTracker();
    expect(tracker.hasActiveSegment()).toBe(false);
    tracker.signalActivity("input", "scene-a", 10);
    expect(tracker.hasActiveSegment()).toBe(true);
    // 无鼠标/焦点信号 API：不调用任何方法则 lastActivity 不更新。
    advance(10_000);
    tracker.checkIdle();
    expect(tracker.hasActiveSegment()).toBe(true);
  });

  it("三种活动种类（输入/选择/结构）都计时且被记录在结算报告中", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 5);
    advance(2_000);
    tracker.signalActivity("selection", "scene-a", 5);
    advance(1_000);
    tracker.signalActivity("structure", "scene-a", 5);
    advance(1_000);
    tracker.settle();
    expect(settle).toHaveBeenCalledTimes(1);
    const report = settle.mock.calls[0][0] as SessionSettleReport;
    expect(report.activityKinds.sort()).toEqual(["input", "selection", "structure"]);
    expect(report.activeMs).toBe(3_000);
  });

  it("净增字数 = 最新字符数 - 段起始字符数（允许删除为负）", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 100);
    advance(2_000);
    tracker.signalActivity("input", "scene-a", 230);
    advance(1_000);
    tracker.signalActivity("input", "scene-a", 30);
    advance(1_000);
    tracker.settle();
    expect((settle.mock.calls[0][0] as SessionSettleReport).netChars).toBe(-70);
  });

  it("不记录任何按键内容 / 选中文本 / 正文内容（API 只接受字符数）", () => {
    const { tracker, settle } = createTracker();
    tracker.signalActivity("input", "scene-a", 42);
    tracker.settle();
    const report = settle.mock.calls[0][0] as SessionSettleReport;
    expect(report).not.toHaveProperty("text");
    expect(report).not.toHaveProperty("content");
    expect(report).not.toHaveProperty("keys");
    expect(report).not.toHaveProperty("selection");
    expect(report).toEqual({
      sceneId: "scene-a",
      startedAt: 100_000,
      activeMs: 0,
      netChars: 0,
      reason: "settle",
      activityKinds: ["input"]
    });
  });
});

describe("writing-session-tracker 空闲边界", () => {
  it("空闲超过阈值自动结算；之后重新活动创建新段", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 10);
    advance(100);
    tracker.signalActivity("input", "scene-a", 20);
    advance(SESSION_DEFAULT_IDLE_TIMEOUT_MS);
    tracker.checkIdle();
    expect(settle).toHaveBeenCalledTimes(1);
    expect((settle.mock.calls[0][0] as SessionSettleReport).reason).toBe("idle");
    expect((settle.mock.calls[0][0] as SessionSettleReport).netChars).toBe(10);
    expect(tracker.hasActiveSegment()).toBe(false);
    // 重新活动 → 新段（startedAt 重置、净增重新从 0 累计）。
    tracker.signalActivity("input", "scene-a", 25);
    const segment = tracker.getActiveSegment();
    expect(segment?.startedAt).toBe(100_000 + SESSION_DEFAULT_IDLE_TIMEOUT_MS + 100);
    expect(segment?.netChars).toBe(0);
  });

  it("空闲刚好未达阈值不结算", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 1);
    advance(SESSION_DEFAULT_IDLE_TIMEOUT_MS - 1);
    tracker.checkIdle();
    expect(settle).not.toHaveBeenCalled();
  });

  it("段不足最短时长时不上报（丢弃噪声段）", () => {
    const { tracker, settle } = createTracker({ minActiveMs: 1_000 });
    tracker.signalActivity("input", "scene-a", 1);
    tracker.settle();
    expect(settle).not.toHaveBeenCalled();
    expect(tracker.hasActiveSegment()).toBe(false);
  });
});

describe("writing-session-tracker 场景与生命周期结算", () => {
  it("场景切换先结算旧段再开新段（reason=scene-switch）", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 10);
    advance(3_000);
    tracker.signalActivity("input", "scene-a", 12);
    advance(2_000);
    tracker.signalActivity("input", "scene-b", 0);
    expect(settle).toHaveBeenCalledTimes(1);
    const report = settle.mock.calls[0][0] as SessionSettleReport;
    expect(report.sceneId).toBe("scene-a");
    expect(report.reason).toBe("scene-switch");
    expect(report.netChars).toBe(2);
    expect(tracker.getActiveSegment()?.sceneId).toBe("scene-b");
  });

  it("连续写作多场景：各场景独立段、连续结算并上报（场景归属正确）", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 10);
    advance(2_000);
    tracker.signalActivity("input", "scene-b", 5);
    advance(2_000);
    tracker.signalActivity("input", "scene-b", 9);
    advance(2_000);
    tracker.signalActivity("input", "scene-c", 0);
    advance(2_000);
    tracker.settle();
    expect(settle).toHaveBeenCalledTimes(3);
    const [a, b, c] = settle.mock.calls.map((call) => call[0] as SessionSettleReport);
    expect(a.sceneId).toBe("scene-a");
    expect(a.netChars).toBe(0);
    expect(b.sceneId).toBe("scene-b");
    expect(b.netChars).toBe(4);
    expect(c.sceneId).toBe("scene-c");
  });

  it("显式结算（窗口隐藏/卸载/切项目）reason=settle，可多次调用且幂等", () => {
    const { tracker, settle, advance } = createTracker();
    tracker.signalActivity("input", "scene-a", 8);
    advance(5_000);
    tracker.settle();
    tracker.settle();
    expect(settle).toHaveBeenCalledTimes(1);
    expect((settle.mock.calls[0][0] as SessionSettleReport).reason).toBe("settle");
  });

  it("无活动段时 settle / checkIdle 无副作用", () => {
    const { tracker, settle } = createTracker();
    tracker.settle();
    tracker.checkIdle();
    expect(settle).not.toHaveBeenCalled();
  });
});
